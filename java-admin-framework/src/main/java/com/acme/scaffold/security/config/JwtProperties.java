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
    private Duration accessTokenTtl = Duration.ofMinutes(30);
    private Duration refreshTokenTtl = Duration.ofDays(30);
    private String jwtSecret;
    private int maxLoginFailures = 5;
    private Duration lockDuration = Duration.ofMinutes(15);

    /**
     * 渐进式锁定封顶时长（设计文档 §5.3「继续失败采用渐进式锁定」）。
     * 锁定时长从 {@link #lockDuration} 起逐级翻倍，但不超过该上限。默认 24h。
     */
    private Duration lockDurationMax = Duration.ofHours(24);

    /**
     * 密码历史保留条数（设计文档 §5.3「保留最近 5 次密码哈希，禁止重复使用」）。默认 5。
     */
    private int passwordHistoryLimit = 5;

    /** 同一账号允许同时在线的最大设备/会话数；<=0 表示不限制。默认 3。 */
    private int maxConcurrentSessions = 3;

    /** 超出限制时的处理策略：reject（拒绝新登录）或 evict_oldest（淘汰最早登录的会话，实现会话挤占）。默认 evict_oldest。 */
    private String sessionEvictionStrategy = "evict_oldest";

    /** 当前生效密钥的 kid（JWT 头 kid 声明）。默认 "1"。 */
    private String keyId = "1";

    /** 上一版本密钥的 kid，仅用于校验旧 token（轮换过渡期）。默认 "0"。 */
    private String previousKeyId = "0";

    /** 上一版本密钥（Base64 编码、≥256 位）；留空表示未启用密钥轮换。 */
    private String previousJwtSecret;

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

    public Duration getLockDurationMax() {
        return lockDurationMax;
    }

    public void setLockDurationMax(Duration lockDurationMax) {
        this.lockDurationMax = lockDurationMax;
    }

    public int getPasswordHistoryLimit() {
        return passwordHistoryLimit;
    }

    public void setPasswordHistoryLimit(int passwordHistoryLimit) {
        this.passwordHistoryLimit = passwordHistoryLimit;
    }

    public int getMaxConcurrentSessions() {
        return maxConcurrentSessions;
    }

    public void setMaxConcurrentSessions(int maxConcurrentSessions) {
        this.maxConcurrentSessions = maxConcurrentSessions;
    }

    public String getSessionEvictionStrategy() {
        return sessionEvictionStrategy;
    }

    public void setSessionEvictionStrategy(String sessionEvictionStrategy) {
        this.sessionEvictionStrategy = sessionEvictionStrategy;
    }

    /**
     * 异常退出会话回收：刷新令牌空闲（未刷新）超过该时长即被吊销，实现「进程崩溃 / 连接中断后 token 及时失效」。
     * 与 access-token-ttl 协同：access token 在空闲阈值内自然过期，refresh token 超过该阈值后被回收。默认 30m。
     */
    private Duration sessionMaxInactive = Duration.ofMinutes(30);

    /** 是否启用会话空闲回收定时任务与启动回收。默认 true。 */
    private boolean sessionCleanupEnabled = true;

    /** 会话空闲回收定时任务执行间隔（毫秒）。默认 300000（5 分钟）。 */
    private long sessionCleanupIntervalMs = 300_000;

    public Duration getSessionMaxInactive() {
        return sessionMaxInactive;
    }

    public void setSessionMaxInactive(Duration sessionMaxInactive) {
        this.sessionMaxInactive = sessionMaxInactive;
    }

    public boolean isSessionCleanupEnabled() {
        return sessionCleanupEnabled;
    }

    public void setSessionCleanupEnabled(boolean sessionCleanupEnabled) {
        this.sessionCleanupEnabled = sessionCleanupEnabled;
    }

    public long getSessionCleanupIntervalMs() {
        return sessionCleanupIntervalMs;
    }

    public void setSessionCleanupIntervalMs(long sessionCleanupIntervalMs) {
        this.sessionCleanupIntervalMs = sessionCleanupIntervalMs;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getPreviousKeyId() {
        return previousKeyId;
    }

    public void setPreviousKeyId(String previousKeyId) {
        this.previousKeyId = previousKeyId;
    }

    public String getPreviousJwtSecret() {
        return previousJwtSecret;
    }

    public void setPreviousJwtSecret(String previousJwtSecret) {
        this.previousJwtSecret = previousJwtSecret;
    }

    private static final Logger log = LoggerFactory.getLogger(JwtProperties.class);
    private SecretKey signingKey;
    private SecretKey previousSigningKey;

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

    /**
     * 解析上一版本签名密钥（用于轮换过渡期校验旧 token）。未配置 {@code previousJwtSecret} 时返回 null。
     */
    public SecretKey resolvePreviousSigningKey() {
        if (previousSigningKey == null && StringUtils.hasText(previousJwtSecret)) {
            previousSigningKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(previousJwtSecret));
        }
        return previousSigningKey;
    }
}
