package com.acme.scaffold.common.oauth2;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 开放平台客户端纯函数规则（V48 API 开放平台 / M24）。
 *
 * <p>无副作用、不依赖 Spring 上下文与数据库，可离线单测；供
 * {@link com.acme.scaffold.system.openapi.OpenApiClientService}（注册/更新校验、密钥生成）
 * 与 Controller（入参约束）共用，保证「注册什么、治理什么」口径同源。</p>
 *
 * <p><b>密钥生成</b>：{@link SecureRandom} 取 32 字节随机 → Base64 URL 无填充编码（43 字符），
 * 满足 OAuth2 客户端密钥的熵要求；哈希委托 Spring Security {@code PasswordEncoder}，本规则只负责生成。</p>
 */
public final class OpenApiClientRules {

    /** client_id 格式：字母/数字开头，总长 8~64，可含中划线与下划线（OAuth2 client_id 惯例）。 */
    public static final String CLIENT_ID_PATTERN = "^[a-zA-Z0-9][a-zA-Z0-9_-]{7,63}$";

    /** scope 单项格式：小写字母开头，总长 1~32，可含数字/冒号/中划线/下划线。 */
    public static final String SCOPE_PATTERN = "^[a-z][a-z0-9:_-]{0,31}$";

    /** scope 个数上限。 */
    public static final int MAX_SCOPES = 10;

    /** 密钥随机字节数（256 位）。 */
    public static final int SECRET_BYTES = 32;

    /** 状态白名单。 */
    public static final Set<String> STATUSES = Set.of("ENABLED", "DISABLED");

    /** 授权码回调地址（redirect_uri）白名单上限。 */
    public static final int MAX_REDIRECT_URIS = 8;

    /** 单个 redirect_uri 长度上限。 */
    public static final int MAX_REDIRECT_URI_LEN = 256;

    /** 默认 scope（注册时未声明）。 */
    public static final String DEFAULT_SCOPES = "read";

    /** 默认状态。 */
    public static final String DEFAULT_STATUS = "ENABLED";

    private static final SecureRandom RANDOM = new SecureRandom();

    private OpenApiClientRules() {
    }

    // ------------------------------------------------------------------
    // 归一
    // ------------------------------------------------------------------

    /** 校验 client_id 格式（不含唯一性，唯一性由 Service 查库裁决）。 */
    public static boolean isValidClientId(String clientId) {
        return clientId != null && clientId.matches(CLIENT_ID_PATTERN);
    }

    /** 归一状态：白名单内（大小写不敏感）返回规范大写，否则 null。 */
    public static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        return STATUSES.contains(s) ? s : null;
    }

    /**
     * 解析 scope 串：逗号/分号/空白分隔，去空白去重保序；空串返回空集合。
     * 返回只读视图且<b>严格保持声明顺序</b>（LinkedHashSet 语义，供 normalizeScopes 产出稳定落库串）；
     * 注意不可用 {@code Set.copyOf}——其迭代顺序未定义，会破坏顺序契约。
     */
    public static Set<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : scopes.split("[,;\\s]+")) {
            String s = part.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** scope 集合 → 规范化落库串（逗号分隔，保序）；空集合回落默认 scope。 */
    public static String normalizeScopes(String scopes) {
        Set<String> parsed = parseScopes(scopes);
        if (parsed.isEmpty()) {
            return DEFAULT_SCOPES;
        }
        return String.join(",", parsed);
    }

    // ------------------------------------------------------------------
    // 密钥
    // ------------------------------------------------------------------

    /**
     * 生成客户端密钥明文：SecureRandom 32 字节 → Base64 URL 无填充（43 字符）。
     * 明文仅在创建/轮换成功时返回一次，调用方负责展示与丢弃，严禁落库。
     */
    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    /**
     * 全字段结构校验（创建/部分更新合并后统一调用）。返回错误列表，空列表表示通过。
     * client_id 唯一性由 Service 查库裁决。
     *
     * @param expiredAt 过期时间（可空）；非空时必须晚于当前时刻
     */
    public static List<String> validate(String clientId, String clientName, String scopes,
                                        String status, LocalDateTime expiredAt) {
        List<String> errors = new ArrayList<>();
        if (clientId == null || clientId.isBlank()) {
            errors.add("客户端 ID 必填");
        } else if (!isValidClientId(clientId)) {
            errors.add("客户端 ID 须为字母/数字开头、总长 8~64、可含中划线/下划线");
        }
        if (clientName == null || clientName.isBlank()) {
            errors.add("客户端名称必填");
        } else if (clientName.length() > 128) {
            errors.add("客户端名称不超过 128 字符");
        }
        Set<String> parsed = parseScopes(scopes);
        if (parsed.isEmpty()) {
            errors.add("授权范围必填（至少一个 scope）");
        } else if (parsed.size() > MAX_SCOPES) {
            errors.add("授权 scope 不超过 " + MAX_SCOPES + " 个");
        } else {
            for (String s : parsed) {
                if (!s.matches(SCOPE_PATTERN)) {
                    errors.add("scope 非法：" + s + "（小写字母开头，可含数字/冒号/中划线/下划线）");
                }
            }
        }
        if (status != null && !status.isBlank() && normalizeStatus(status) == null) {
            errors.add("状态非法（须为 ENABLED/DISABLED）");
        }
        if (expiredAt != null && !expiredAt.isAfter(LocalDateTime.now())) {
            errors.add("过期时间须晚于当前时刻");
        }
        return errors;
    }

    // ------------------------------------------------------------------
    // redirect_uri 白名单
    // ------------------------------------------------------------------

    /**
     * 解析 redirect_uri 白名单串：逗号/分号/空白分隔，去空白去重保序；空串返回空集合。
     * 返回只读视图且<b>严格保持声明顺序</b>（与 {@link #parseScopes} 同语义）。
     */
    public static Set<String> parseRedirectUris(String redirectUris) {
        if (redirectUris == null || redirectUris.isBlank()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : redirectUris.split("[,;\\s]+")) {
            String s = part.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** redirect_uri 集合 → 规范化落库串（逗号分隔，保序）；空集合返回空串。 */
    public static String normalizeRedirectUris(String redirectUris) {
        Set<String> parsed = parseRedirectUris(redirectUris);
        return String.join(",", parsed);
    }

    /**
     * 校验 redirect_uri 白名单结构（创建/更新时调用）。返回错误列表，空列表表示通过。
     * 允许空白（不约束回调地址，兼容存量客户端）。
     */
    public static List<String> validateRedirectUris(String redirectUris) {
        List<String> errors = new ArrayList<>();
        Set<String> parsed = parseRedirectUris(redirectUris);
        if (parsed.size() > MAX_REDIRECT_URIS) {
            errors.add("redirect_uri 不超过 " + MAX_REDIRECT_URIS + " 个");
            return errors;
        }
        for (String uri : parsed) {
            if (uri.length() > MAX_REDIRECT_URI_LEN) {
                errors.add("redirect_uri 过长（≤ " + MAX_REDIRECT_URI_LEN + " 字符）：" + uri);
                continue;
            }
            if (!isValidRedirectUri(uri)) {
                errors.add("redirect_uri 非法（须为 https，或 http 的 localhost/127.0.0.1）：" + uri);
            }
        }
        return errors;
    }

    /**
     * 判断请求的 redirect_uri 是否被客户端登记白名单允许。
     *
     * <p>白名单为空（存量客户端未登记）→ 不约束 redirect_uri（向后兼容，任意取值含 null 均放行）；
     * 白名单非空 → 请求必须精确命中其中一项（RFC 6749 §3.1 精确匹配），且不可为空。</p>
     */
    public static boolean isRedirectUriAllowed(Set<String> registered, String requested) {
        if (registered == null || registered.isEmpty()) {
            return true;
        }
        return requested != null && registered.contains(requested);
    }

    /** redirect_uri 合法性：https 任意主机；http 仅允许 localhost / 127.0.0.1 / [::1]（开发态）。 */
    private static boolean isValidRedirectUri(String uri) {
        if (uri.startsWith("https://")) {
            return uri.length() > "https://".length();
        }
        if (uri.startsWith("http://")) {
            String host = uri.substring("http://".length());
            int slash = host.indexOf('/');
            if (slash >= 0) {
                host = host.substring(0, slash);
            }
            int colon = host.indexOf(':');
            if (colon >= 0) {
                host = host.substring(0, colon);
            }
            return "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        }
        return false;
    }
}
