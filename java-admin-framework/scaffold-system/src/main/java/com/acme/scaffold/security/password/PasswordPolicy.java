package com.acme.scaffold.security.password;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 密码强度策略：纯函数校验器，与 Spring 上下文解耦，便于离线单测。
 *
 * <p>默认规则（{@link #validate(String)}）：
 * <ul>
 *   <li>长度 8–64 位；</li>
 *   <li>同时包含大写字母、小写字母、数字、特殊字符；</li>
 *   <li>不得为常见弱口令（如 password / 12345678 / admin123 等）。</li>
 * </ul>
 *
 * <p>在 {@code UserService.create / resetPassword} 与 {@code ProfileService.changePassword}
 * 三处设置密码的入口统一调用，保证策略唯一且可集中调整。登录环节不做策略校验（历史口令以哈希存储）。
 */
public final class PasswordPolicy {

    private static final Pattern HAS_UPPER = Pattern.compile("[A-Z]");
    private static final Pattern HAS_LOWER = Pattern.compile("[a-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");
    private static final Pattern HAS_SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    private static final Set<String> COMMON_WEAK = Set.of(
            "password", "passwd", "12345678", "123456789", "qwerty", "abc12345",
            "admin123", "root1234", "password123", "administrator", "letmein", "welcome1");

    private PasswordPolicy() {
    }

    /** 使用内置默认策略校验；不通过抛 {@link BusinessException}（VALIDATION_ERROR）。 */
    public static void validate(String raw) {
        validate(raw, new PolicyConfig());
    }

    /** 使用自定义策略校验。 */
    public static void validate(String raw, PolicyConfig config) {
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "密码不能为空");
        }
        int len = raw.length();
        if (len < config.minLength() || len > config.maxLength()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "密码长度需在 " + config.minLength() + "–" + config.maxLength() + " 位之间");
        }
        if (config.requireUpper() && !HAS_UPPER.matcher(raw).find()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "密码必须包含大写字母");
        }
        if (config.requireLower() && !HAS_LOWER.matcher(raw).find()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "密码必须包含小写字母");
        }
        if (config.requireDigit() && !HAS_DIGIT.matcher(raw).find()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "密码必须包含数字");
        }
        if (config.requireSpecial() && !HAS_SPECIAL.matcher(raw).find()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "密码必须包含特殊字符");
        }
        if (config.denyCommon() && COMMON_WEAK.contains(raw.toLowerCase())) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "密码过于常见，请更换");
        }
    }

    /** 密码策略配置（不可变）。 */
    public record PolicyConfig(
            int minLength,
            int maxLength,
            boolean requireUpper,
            boolean requireLower,
            boolean requireDigit,
            boolean requireSpecial,
            boolean denyCommon) {

        public PolicyConfig() {
            this(8, 64, true, true, true, true, true);
        }
    }

    /** 暴露常见弱口令集合，供测试与运维审计。 */
    public static List<String> commonWeakPasswords() {
        return List.copyOf(COMMON_WEAK);
    }
}
