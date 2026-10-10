package com.acme.scaffold.security.jwt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;

/**
 * JWT 解码配置：构建「当前生效密钥」解码器，若存在上一版本密钥则封装为轮换解码器，
 * 使旧密钥签发的 token 在轮换过渡期仍可校验。
 */
@Configuration(proxyBeanMethods = false)
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(JwtKeyRotationService rotation) {
        JwtDecoder active = NimbusJwtDecoder.withSecretKey(rotation.activeKey()).build();
        JwtDecoder previous = rotation.previousKey()
                .map(prevKey -> (JwtDecoder) NimbusJwtDecoder.withSecretKey(prevKey).build())
                .orElse(null);
        return previous == null ? new RotatingJwtDecoder(active) : new RotatingJwtDecoder(active, previous);
    }
}
