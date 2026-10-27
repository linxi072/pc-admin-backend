package com.acme.scaffold.security.oauth2;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;

/**
 * OAuth2 授权服务器解码配置：RS256 解码器（校验 OAuth2 客户端令牌）+ 复合解码器（{@code @Primary}，
 * 先 HS256 后 RS256，使登录令牌与 OAuth2 客户端令牌在同一资源服务器共存）。
 *
 * <p>复合解码器通过 {@link #compositeJwtDecoder(JwtDecoder, JwtDecoder)} 引用既有 HS256
 * {@code jwtDecoder} Bean（按名称限定），再追加 RS256 解码器，二者算法不同互不干扰。</p>
 */
@Configuration(proxyBeanMethods = false)
public class OAuth2ServerConfig {

    /** RS256 解码器：用 OAuth2 公钥校验客户端令牌签名。 */
    @Bean
    public JwtDecoder oauth2JwtDecoder(RsaKeyProvider keys) {
        return NimbusJwtDecoder.withPublicKey(keys.getPublicKey()).build();
    }

    /** 复合解码器（主）：HS256（登录令牌）优先，失败回退 RS256（OAuth2 客户端令牌）。 */
    @Bean
    @Primary
    public JwtDecoder compositeJwtDecoder(@Qualifier("jwtDecoder") JwtDecoder hs256Decoder,
                                          JwtDecoder oauth2JwtDecoder) {
        return new CompositeJwtDecoder(hs256Decoder, oauth2JwtDecoder);
    }
}
