package com.acme.scaffold.security.jwt;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Optional;

/**
 * 轮换友好型 JWT 解码器：优先用当前生效密钥解码；失败（如签名密钥不匹配）时，
 * 若存在上一版本密钥，则再用上一版本密钥尝试一次，从而实现密钥轮换过渡期旧 token 仍可校验。
 *
 * <p>说明：本类不依赖具体版本私有的 builder API（如 secretKeyResolver），仅组合两个标准
 * {@link JwtDecoder}，兼容性好、易于离线单测。
 */
public class RotatingJwtDecoder implements JwtDecoder {

    private final JwtDecoder active;
    private final JwtDecoder previous;

    public RotatingJwtDecoder(JwtDecoder active, JwtDecoder previous) {
        this.active = active;
        this.previous = previous;
    }

    public RotatingJwtDecoder(JwtDecoder active) {
        this(active, null);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            return active.decode(token);
        } catch (JwtException primaryError) {
            if (previous != null) {
                try {
                    return previous.decode(token);
                } catch (JwtException ignored) {
                    // 上一版本密钥也无法解码：以「上一版本」的失败信息为准抛出
                    throw primaryError;
                }
            }
            throw primaryError;
        }
    }

    /** 仅提供静态便捷构造：有上一版本密钥时返回轮换解码器，否则返回单密钥解码器。 */
    public static JwtDecoder of(JwtDecoder active, Optional<JwtDecoder> previous) {
        return previous.map(p -> (JwtDecoder) new RotatingJwtDecoder(active, p))
                .orElse(active);
    }
}
