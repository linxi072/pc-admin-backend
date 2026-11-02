package com.acme.scaffold.security.oauth2;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.List;

/**
 * 复合 JWT 解码器：依次尝试内部解码器，捕获 {@link JwtException} 逐个回退，
 * 全部失败才抛出首个错误。用于同一资源服务器同时接纳 HS256（登录令牌）与
 * RS256（OAuth2 客户端令牌）两种算法签发的令牌。
 */
public class CompositeJwtDecoder implements JwtDecoder {

    private final List<JwtDecoder> decoders;

    public CompositeJwtDecoder(JwtDecoder... decoders) {
        this.decoders = List.of(decoders);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        JwtException last = null;
        for (JwtDecoder decoder : decoders) {
            try {
                return decoder.decode(token);
            } catch (JwtException e) {
                last = e;
            }
        }
        throw last == null ? new JwtException("未配置任何 JWT 解码器") : last;
    }
}
