package com.acme.scaffold.security.token.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.security.entity.SysRefreshTokenDO;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.token.rules.SessionLimitRules;
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

    private static final JooqTables.TableRef RT =
            JooqTables.SYS_REFRESH_TOKEN;

    @Override
    public String issue(Long userId, String clientId, String deviceId, String userAgent, String ip) {
        // 全新登录：独立新家族
        return issueWithFamily(userId, clientId, deviceId, userAgent, ip, randomHex(16));
    }

    /**
     * 签发刷新令牌（可指定家族 ID）。
     *
     * <p>轮换时必须沿用旧令牌的 {@code familyId}：{@link #revokeFamily(String)} 只吊销
     * {@code family_id 相同且未吊销} 的行，若轮换产生新家族，则该家族恒只有 1 个成员（旧成员已被本次
     * 轮换标记吊销），重放检测将命中 0 行——「吊销整个家族」形同虚设。继承家族后，旧令牌被重放时能
     * 连带吊销同族的新令牌，检测才真正生效。</p>
     */
    private String issueWithFamily(Long userId, String clientId, String deviceId, String userAgent,
                                   String ip, String familyId) {
        String raw = randomHex(32);
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
        JooqWriters.insert(dsl, RT, entity);
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
        dsl.update(RT.table())
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
        // 继承旧令牌家族，保证同族令牌可被重放检测连带吊销（历史数据 familyId 为空时回落新家族）
        String inheritedFamily = (old.getFamilyId() == null || old.getFamilyId().isBlank())
                ? randomHex(16) : old.getFamilyId();
        String newRaw = issueWithFamily(old.getUserId(), old.getClientId(), old.getDeviceId(),
                old.getUserAgent(), old.getIpAddress(), inheritedFamily);
        // 标记旧令牌已被本次轮换取代
        dsl.update(RT.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "ROTATED")
                .where(idField().eq(old.getId()))
                .execute();
        return newRaw;
    }

    @Override
    public void revokeByUser(Long userId) {
        dsl.update(RT.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "LOGOUT")
                .where(userIdField().eq(userId).and(revokedAtField().isNull()))
                .execute();
    }

    @Override
    public int revokeBySession(Long userId, String sessionId) {
        return dsl.update(RT.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "DEVICE_REVOKED")
                .where(userIdField().eq(userId)
                        .and(RT.field("session_id", String.class).eq(sessionId))
                        .and(revokedAtField().isNull()))
                .execute();
    }

    @Override
    public int revokeByUserAndClient(Long userId, String clientId) {
        return dsl.update(RT.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "DEVICE_REVOKED")
                .where(userIdField().eq(userId)
                        .and(RT.field("client_id", String.class).eq(clientId))
                        .and(revokedAtField().isNull()))
                .execute();
    }

    @Override
    public int revokeByUserAndDevice(Long userId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            // 无设备标识时无法精确吊销，退化为按用户全量吊销（调用方已限定 userId）
            revokeByUser(userId);
            return -1;
        }
        return dsl.update(RT.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "DEVICE_REVOKED")
                .where(userIdField().eq(userId)
                        .and(RT.field("device_id", String.class).eq(deviceId))
                        .and(revokedAtField().isNull()))
                .execute();
    }

    @Override
    public int countActiveSessions(Long userId) {
        return dsl.fetchCount(RT.table(),
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
                .from(RT.table())
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
        dsl.update(RT.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "SESSION_LIMIT")
                .where(idField().in(toRevoke))
                .execute();
    }

    private void revokeFamily(String familyId) {
        dsl.update(RT.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "REUSE_DETECTED")
                .set(RT.field("reuse_detected", Integer.class), 1)
                .where(familyIdField().eq(familyId).and(revokedAtField().isNull()))
                .execute();
        log.warn("检测到刷新令牌重放，已吊销家族 familyId={}", familyId);
    }

    private SysRefreshTokenDO findByRaw(String rawToken) {
        String h = hash(rawToken);
        SysRefreshTokenDO token = JooqWriters.fetchOne(dsl, RT,
                SysRefreshTokenDO.class, tokenHashField().eq(h));
        if (token == null) {
            throw new BusinessException(SecurityErrorCode.REFRESH_INVALID, "刷新令牌无效");
        }
        return token;
    }

    // --- 字段引用集中定义，避免散落的魔法字符串 ---

    private Field<Long> idField() {
        return RT.field("id", Long.class);
    }

    private Field<Long> userIdField() {
        return RT.field("user_id", Long.class);
    }

    private Field<String> familyIdField() {
        return RT.field("family_id", String.class);
    }

    private Field<String> tokenHashField() {
        return RT.field("token_hash", String.class);
    }

    private Field<Timestamp> lastUsedAtField() {
        return RT.field("last_used_at", Timestamp.class);
    }

    private Field<Timestamp> revokedAtField() {
        return RT.field("revoked_at", Timestamp.class);
    }

    private Field<Timestamp> issuedAtField() {
        return RT.field("issued_at", Timestamp.class);
    }

    private Field<Timestamp> expiresAtField() {
        return RT.field("expires_at", Timestamp.class);
    }

    private Field<String> revokeReasonField() {
        return RT.field("revoke_reason", String.class);
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
