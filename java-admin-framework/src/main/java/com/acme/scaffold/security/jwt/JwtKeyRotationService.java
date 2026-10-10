package com.acme.scaffold.security.jwt;

import com.acme.scaffold.security.config.JwtProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Optional;

/**
 * JWT 密钥轮换解析：统一管理「当前生效密钥」与「上一版本密钥（仅校验）」。
 *
 * <p>签发始终用 {@link #activeKey()}（携带 {@link #activeKid()} 写入 JWT 头 kid）；
 * 校验时按 token 头中的 kid 选择对应密钥，从而支持旧密钥签发 token 在轮换过渡期继续可用。
 * 当未配置 {@code previousJwtSecret} 时，仅存在一把密钥，旧 token（无 kid 或 kid 匹配）照常校验。
 */
@Service
public class JwtKeyRotationService {

    private final JwtProperties properties;

    public JwtKeyRotationService(JwtProperties properties) {
        this.properties = properties;
    }

    /** 当前生效密钥的 kid。 */
    public String activeKid() {
        return properties.getKeyId();
    }

    /** 当前生效密钥。 */
    public SecretKey activeKey() {
        return properties.resolveSigningKey();
    }

    /**
     * 按 kid 解析校验密钥。
     * <ul>
     *   <li>kid 为 null → 兼容旧 token，回退到当前生效密钥；</li>
     *   <li>kid 等于当前/上一版本 kid 且对应密钥已配置 → 返回该密钥；</li>
     *   <li>其余 → {@link Optional#empty()}（未知密钥，拒绝校验）。</li>
     * </ul>
     */
    public Optional<SecretKey> keyFor(String kid) {
        if (kid == null) {
            return Optional.of(properties.resolveSigningKey());
        }
        if (properties.getKeyId().equals(kid)) {
            return Optional.of(properties.resolveSigningKey());
        }
        if (properties.getPreviousKeyId() != null && properties.getPreviousKeyId().equals(kid)
                && properties.getPreviousJwtSecret() != null) {
            return Optional.ofNullable(properties.resolvePreviousSigningKey());
        }
        return Optional.empty();
    }

    /** 上一版本密钥（仅校验用）。未配置 {@code previousJwtSecret} 时返回 {@link Optional#empty()}。 */
    public Optional<SecretKey> previousKey() {
        return keyFor(properties.getPreviousKeyId());
    }
}
