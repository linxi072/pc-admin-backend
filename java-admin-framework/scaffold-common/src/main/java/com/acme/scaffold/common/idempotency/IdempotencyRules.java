package com.acme.scaffold.common.idempotency;

import com.acme.scaffold.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 幂等性纯规则（无 Spring / 无 DB 依赖，便于单测）。
 *
 * <p>把「键合法性」「请求指纹」「命中既有记录后的动作」三件事收敛到静态方法，
 * 是因为这三点决定了幂等是否安全：键太短易撞、指纹不严格会误重放、
 * 状态判定错则要么漏拦（重复扣款/重复审批）要么误拦（正常重试被拒）。
 */
public final class IdempotencyRules {

    /** 键最短长度：低于此值随机碰撞概率上升，常见于前端误传时间戳。 */
    public static final int MIN_KEY_LENGTH = 8;
    public static final int MAX_KEY_LENGTH = 128;

    /** 仅允许 URL 安全字符，避免脏数据与日志注入。 */
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    /** 指纹分隔符：用不可能出现在 URI/JSON 里的控制字符，降低拼串歧义。 */
    private static final char SEP = '\u001F';

    private IdempotencyRules() {
    }

    /** 键是否合法：非空、长度在区间内、仅含允许字符。 */
    public static boolean isValidKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String k = key.trim();
        return k.length() >= MIN_KEY_LENGTH && k.length() <= MAX_KEY_LENGTH && KEY_PATTERN.matcher(k).matches();
    }

    /** 校验失败直接抛 400，消息里不带原始 key（避免把可疑输入回显到响应）。 */
    public static void requireValidKey(String key) {
        if (!isValidKey(key)) {
            throw new BusinessException(IdempotencyErrorCode.KEY_INVALID,
                    String.format(Locale.ROOT, "Idempotency-Key 需为 %d~%d 位的字母、数字、下划线或连字符",
                            MIN_KEY_LENGTH, MAX_KEY_LENGTH));
        }
    }

    /**
     * 作用域：显式声明优先，否则用切点签名，保证同一 key 在不同接口间互不干扰
     * （例：同一次提交的审批与转办不会互相拦截）。
     */
    public static String resolveScope(String declared, String signature) {
        if (StringUtils.hasText(declared)) {
            return declared.trim();
        }
        return StringUtils.hasText(signature) ? signature.trim() : "default";
    }

    /**
     * 请求指纹：方法 + 路径 + 请求体。同 key 不同内容必须识别出差异，
     * 否则「改了金额再重发」会被静默重放成上一次的结果。
     *
     * @return 64 位小写十六进制 SHA-256
     */
    public static String fingerprint(String method, String uri, String body) {
        String raw = (method == null ? "" : method) + SEP
                + (uri == null ? "" : uri) + SEP
                + (body == null ? "" : body);
        return sha256Hex(raw);
    }

    /**
     * 命中既有记录后的动作判定。
     *
     * @param status             既有记录状态，null 表示无记录
     * @param fingerprintMatched 本次指纹与首次是否一致
     */
    public static IdempotencyAction decide(IdempotencyStatus status, boolean fingerprintMatched) {
        if (status == null) {
            return IdempotencyAction.PROCEED;
        }
        if (!fingerprintMatched) {
            return IdempotencyAction.MISMATCH;
        }
        return switch (status) {
            case SUCCESS -> IdempotencyAction.REPLAY;
            case PROCESSING -> IdempotencyAction.CONFLICT;
            case FAILED -> IdempotencyAction.PROCEED;
        };
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制实现，走到这里说明运行环境被裁剪，属于不可恢复错误
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 是否把当前异常视为「业务失败」——是则记录 FAILED 允许重试，否则释放记录。 */
    public static boolean isBusinessFailure(Throwable t) {
        return t instanceof BusinessException;
    }
}
