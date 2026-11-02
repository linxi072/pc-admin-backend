package com.acme.scaffold.security.vo;

/**
 * 登录/刷新返回的令牌视图。原始刷新令牌仅在此时返回一次，客户端须安全存储。
 */
public record TokenView(String accessToken, String refreshToken, long expiresIn) {
}
