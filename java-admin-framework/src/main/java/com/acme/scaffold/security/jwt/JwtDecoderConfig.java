package com.acme.scaffold.security.jwt;

import com.acme.scaffold.security.config.JwtProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;

@Configuration(proxyBeanMethods = false)
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey key = properties.resolveSigningKey();
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
