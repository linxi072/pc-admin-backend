package com.acme.scaffold.security.config;

import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * JWT 与账号安全策略配置，绑定自 {@code app.security.*}。
 */
@ConfigurationProperties(prefix = "app.security")
public class JwtProperties {

    private String issuer = "java-admin-framework";
    private String audience = "admin-api";
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(7);
    private String jwtSecret;
    private int maxLoginFailures = 5;
    private Duration lockDuration = Duration.ofMinutes(15);

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public int getMaxLoginFailures() {
        return maxLoginFailures;
    }

    public void setMaxLoginFailures(int maxLoginFailures) {
        this.maxLoginFailures = maxLoginFailures;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }

    private static final Logger log = LoggerFactory.getLogger(JwtProperties.class);
    private SecretKey signingKey;

    /**
     * 解析 HS256 签名密钥，全程不使用任何硬编码默认值。
     *
     * <ul>
     *   <li>若通过环境变量 {@code JWT_SECRET}（Base64 编码、≥256 位）显式配置，则使用之；</li>
     *   <li>若未配置，则生成<b>随机临时密钥</b>且仅在当前进程有效（重启失效，已签发 token 同步失效），
     *       适用于本地开发/测试；生产环境<b>必须</b>通过环境变量设置 {@code JWT_SECRET}。</li>
     * </ul>
     *
     * <p>结果按进程内单例记忆，确保同一组 JwtProvider / JwtAuthConverter / JwtDecoderConfig 使用同一把密钥。
     */
    public SecretKey resolveSigningKey() {
        if (signingKey == null) {
            if (!StringUtils.hasText(jwtSecret)) {
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                signingKey = Keys.hmacShaKeyFor(random);
                log.warn("JWT_SECRET 未配置，已使用随机临时签名密钥（仅限开发/测试，重启后失效）。生产环境请通过环境变量设置 JWT_SECRET。");
            } else {
                signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
            }
        }
        return signingKey;
    }
}
