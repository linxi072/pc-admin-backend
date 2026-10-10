package com.acme.scaffold.security.token;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.config.JwtProperties;
import com.acme.scaffold.security.error.SecurityErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 刷新令牌实现。服务端仅存储 SHA-256 哈希，原始令牌只在签发时返回一次。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final DSLContext dsl;
    private final JwtProperties jwtProps;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String issue(Long userId, String clientId, String deviceId, String userAgent, String ip) {
        String raw = randomHex(32);
        String familyId = randomHex(16);
        String sessionId = randomHex(16);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(jwtProps.getRefreshTokenTtl());

        SysRefreshTokenDO entity = new SysRefreshTokenDO();
        entity.setUserId(userId);
        entity.setClientId(clientId);
        entity.setSessionId(sessionId);
        entity.setFamilyId(familyId);
        entity.setTokenHash(hash(raw));
        entity.setDeviceId(deviceId);
        entity.setUserAgent(truncate(userAgent, 500));
        entity.setIpAddress(ip);
        entity.setIssuedAt(now);
        entity.setExpiresAt(expiresAt);
        JooqWriters.insert(dsl, JooqTables.SYS_REFRESH_TOKEN, entity);
        return raw;
    }

    @Override
    public Long verify(String rawToken) {
        SysRefreshTokenDO token = findByRaw(rawToken);
        Long userId = token.getUserId();
        if (token.getRevokedAt() != null) {
            // 重放检测：已吊销令牌被再次使用，吊销整个家族
            revokeFamily(token.getFamilyId());
            throw new BusinessException(SecurityErrorCode.TOKEN_REVOKED, "凭证已失效，请重新登录");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(SecurityErrorCode.REFRESH_INVALID, "刷新令牌已过期");
        }
        dsl.update(JooqTables.SYS_REFRESH_TOKEN.table())
                .set(lastUsedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .where(idField().eq(token.getId()))
                .execute();
        return userId;
    }

    @Override
    public String rotate(String rawToken) {
        SysRefreshTokenDO old = findByRaw(rawToken);
        if (old.getRevokedAt() != null) {
            revokeFamily(old.getFamilyId());
            throw new BusinessException(SecurityErrorCode.TOKEN_REVOKED, "凭证已失效，请重新登录");
        }
        if (old.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(SecurityErrorCode.REFRESH_INVALID, "刷新令牌已过期");
        }
        String newRaw = issue(old.getUserId(), old.getClientId(), old.getDeviceId(),
                old.getUserAgent(), old.getIpAddress());
        // 标记旧令牌已被本次轮换取代
        dsl.update(JooqTables.SYS_REFRESH_TOKEN.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "ROTATED")
                .where(idField().eq(old.getId()))
                .execute();
        return newRaw;
    }

    @Override
    public void revokeByUser(Long userId) {
        dsl.update(JooqTables.SYS_REFRESH_TOKEN.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "LOGOUT")
                .where(userIdField().eq(userId).and(revokedAtField().isNull()))
                .execute();
    }

    @Override
    public int revokeBySession(Long userId, String sessionId) {
        return dsl.update(JooqTables.SYS_REFRESH_TOKEN.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "DEVICE_REVOKED")
                .where(userIdField().eq(userId)
                        .and(JooqTables.SYS_REFRESH_TOKEN.field("session_id", String.class).eq(sessionId))
                        .and(revokedAtField().isNull()))
                .execute();
    }

    @Override
    public int revokeByUserAndClient(Long userId, String clientId) {
        return dsl.update(JooqTables.SYS_REFRESH_TOKEN.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "DEVICE_REVOKED")
                .where(userIdField().eq(userId)
                        .and(JooqTables.SYS_REFRESH_TOKEN.field("client_id", String.class).eq(clientId))
                        .and(revokedAtField().isNull()))
                .execute();
    }

    @Override
    public int countActiveSessions(Long userId) {
        return dsl.fetchCount(JooqTables.SYS_REFRESH_TOKEN.table(),
                userIdField().eq(userId)
                        .and(revokedAtField().isNull())
                        .and(expiresAtField().greaterThan(Timestamp.valueOf(LocalDateTime.now()))));
    }

    @Override
    public void revokeOldestSessions(Long userId, int keepCount) {
        if (keepCount < 0) {
            keepCount = 0;
        }
        // 取活跃会话（id + 登录时间 + 最近使用时间），交由纯函数挑选「最早登录」的待淘汰会话
        List<SessionLimitRules.SessionInfo> infos = dsl
                .select(idField(), issuedAtField(), lastUsedAtField())
                .from(JooqTables.SYS_REFRESH_TOKEN.table())
                .where(userIdField().eq(userId)
                        .and(revokedAtField().isNull())
                        .and(expiresAtField().greaterThan(Timestamp.valueOf(LocalDateTime.now()))))
                .fetch(r -> new SessionLimitRules.SessionInfo(
                        r.value1(),
                        r.value2() != null ? r.value2().toLocalDateTime() : null,
                        r.value3() != null ? r.value3().toLocalDateTime() : null));
        List<Long> toRevoke = SessionLimitRules.selectToEvict(infos, keepCount);
        if (toRevoke.isEmpty()) {
            return;
        }
        dsl.update(JooqTables.SYS_REFRESH_TOKEN.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "SESSION_LIMIT")
                .where(idField().in(toRevoke))
                .execute();
    }

    private void revokeFamily(String familyId) {
        dsl.update(JooqTables.SYS_REFRESH_TOKEN.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "REUSE_DETECTED")
                .set(JooqTables.SYS_REFRESH_TOKEN.field("reuse_detected", Integer.class), 1)
                .where(familyIdField().eq(familyId).and(revokedAtField().isNull()))
                .execute();
        log.warn("检测到刷新令牌重放，已吊销家族 familyId={}", familyId);
    }

    private SysRefreshTokenDO findByRaw(String rawToken) {
        String h = hash(rawToken);
        SysRefreshTokenDO token = JooqWriters.fetchOne(dsl, JooqTables.SYS_REFRESH_TOKEN,
                SysRefreshTokenDO.class, tokenHashField().eq(h));
        if (token == null) {
            throw new BusinessException(SecurityErrorCode.REFRESH_INVALID, "刷新令牌无效");
        }
        return token;
    }

    // --- 字段引用集中定义，避免散落的魔法字符串 ---

    private Field<Long> idField() {
        return JooqTables.SYS_REFRESH_TOKEN.field("id", Long.class);
    }

    private Field<Long> userIdField() {
        return JooqTables.SYS_REFRESH_TOKEN.field("user_id", Long.class);
    }

    private Field<String> familyIdField() {
        return JooqTables.SYS_REFRESH_TOKEN.field("family_id", String.class);
    }

    private Field<String> tokenHashField() {
        return JooqTables.SYS_REFRESH_TOKEN.field("token_hash", String.class);
    }

    private Field<Timestamp> lastUsedAtField() {
        return JooqTables.SYS_REFRESH_TOKEN.field("last_used_at", Timestamp.class);
    }

    private Field<Timestamp> revokedAtField() {
        return JooqTables.SYS_REFRESH_TOKEN.field("revoked_at", Timestamp.class);
    }

    private Field<Timestamp> issuedAtField() {
        return JooqTables.SYS_REFRESH_TOKEN.field("issued_at", Timestamp.class);
    }

    private Field<Timestamp> expiresAtField() {
        return JooqTables.SYS_REFRESH_TOKEN.field("expires_at", Timestamp.class);
    }

    private Field<String> revokeReasonField() {
        return JooqTables.SYS_REFRESH_TOKEN.field("revoke_reason", String.class);
    }

    private String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        secureRandom.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
