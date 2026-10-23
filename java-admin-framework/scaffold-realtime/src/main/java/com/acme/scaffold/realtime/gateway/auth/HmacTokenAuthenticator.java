package com.acme.scaffold.realtime.gateway.auth;

import com.acme.scaffold.realtime.gateway.HandshakeAuthenticator;

import java.util.Optional;

/**
 * HMAC 短期令牌鉴权（设计稿 §3.2 的生产形态实现）。
 *
 * <p>校验链：签名 → exp 过期 → iat 时钟偏差（防重放/时钟漂移）。
 * 任一步失败即拒绝，拒绝原因显式返回（便于客户端区分「重新登录」与「重试」）。
 */
public final class HmacTokenAuthenticator implements HandshakeAuthenticator {

    /** NTP 偏差窗口（§3.2）：iat 超出该窗口按重放/漂移拒绝。 */
    public static final long DEFAULT_CLOCK_SKEW_MS = 60_000L;

    private final String secret;
    private final long clockSkewMs;

    public HmacTokenAuthenticator(String secret) {
        this(secret, DEFAULT_CLOCK_SKEW_MS);
    }

    public HmacTokenAuthenticator(String secret, long clockSkewMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("签名密钥不能为空（生产环境必须外部注入，禁止默认值）");
        }
        if (clockSkewMs < 0) {
            throw new IllegalArgumentException("时钟偏差窗口不能为负: " + clockSkewMs);
        }
        this.secret = secret;
        this.clockSkewMs = clockSkewMs;
    }

    @Override
    public AuthResult authenticate(String token, long now) {
        Optional<TokenCodec.ParsedToken> parsed = TokenCodec.parse(token);
        if (parsed.isEmpty()) {
            return AuthResult.reject("令牌格式非法");
        }
        if (!TokenCodec.verify(secret, token)) {
            return AuthResult.reject("签名校验失败");
        }
        TokenCodec.ParsedToken body = parsed.get();
        if (body.exp() <= now) {
            return AuthResult.reject("令牌已过期");
        }
        if (body.iat() > now + clockSkewMs) {
            return AuthResult.reject("iat 超出时钟偏差窗口（疑似重放）");
        }
        return AuthResult.ok(body.userId());
    }
}
