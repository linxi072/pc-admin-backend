package com.acme.scaffold.security.token.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.security.entity.SysRefreshTokenDO;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.token.rules.SessionLimitRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.Field;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 刷新令牌服务（单类实现）：使用不透明令牌 + 哈希存储 + 家族轮换（refresh token rotation），
 * 服务端仅存储 SHA-256 哈希，原始令牌只在签发时返回一次；一旦检测到已吊销令牌被再次使用，
 * 即吊销整个家族以阻止令牌盗用。持久层使用自研 MyBatis 风格 DSL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final DSLContext dsl;
    private final JwtProperties jwtProps;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final SqlTables.TableRef RT =
            SqlTables.SYS_REFRESH_TOKEN;

    /** 签发新刷新令牌，返回原始令牌（仅此一次返回给客户端）。 */
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
        SqlWriters.insert(dsl, RT, entity);
        return raw;
    }

    /** 校验刷新令牌，返回对应用户 ID；无效/过期/已吊销则抛异常。 */
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

    /** 轮换：校验旧令牌后签发新令牌并令旧令牌失效，返回新原始令牌。 */
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

    /** 吊销某用户所有刷新令牌（登出/改密时使用）。 */
    public void revokeByUser(Long userId) {
        dsl.update(RT.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "LOGOUT")
                .where(userIdField().eq(userId).and(revokedAtField().isNull()))
                .execute();
    }

    /**
     * 按会话精确吊销单个登录会话，返回受影响行数。
     * <p>用于「账号安全 - 登录设备」下线某一次登录。
     *
     * <p><b>为何不用 clientId</b>：clientId 表示客户端类型（如 web），同一浏览器多次登录
     * 会共享同一个 clientId，按它吊销会误伤其他会话；sessionId 才是「一次登录」的准确标识。
     *
     * @param userId 当前用户，限定范围防止越权吊销他人会话
     * @param sessionId 目标会话标识
     * @return 受影响行数；0 表示会话不存在或已失效
     */
    public int revokeBySession(Long userId, String sessionId) {
        return dsl.update(RT.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "DEVICE_REVOKED")
                .where(userIdField().eq(userId)
                        .and(RT.field("session_id", String.class).eq(sessionId))
                        .and(revokedAtField().isNull()))
                .execute();
    }

    /**
     * 吊销某用户在指定客户端（设备）上的全部刷新令牌，返回受影响行数。
     * <p>用于「账号安全 - 登录设备」的单个设备下线。
     * <p>安全说明：只能由服务端在鉴权后调用，且必须同时限定 userId，
     * 防止用户 A 通过伪造 clientId 吊销用户 B 的会话。
     *
     * @return 受影响的会话行数；0 表示该设备不存在或已失效
     */
    public int revokeByUserAndClient(Long userId, String clientId) {
        return dsl.update(RT.table())
                .set(revokedAtField(), Timestamp.valueOf(LocalDateTime.now()))
                .set(revokeReasonField(), "DEVICE_REVOKED")
                .where(userIdField().eq(userId)
                        .and(RT.field("client_id", String.class).eq(clientId))
                        .and(revokedAtField().isNull()))
                .execute();
    }

    /**
     * 吊销某用户在指定设备（device_id）上的全部刷新令牌，返回受影响行数。
     * <p>用于「在线用户管理」强制下线单个设备会话：device_id 即前端 X-Device-Id 头，
     * 与在线会话表 sys_online_session 的业务键同源。
     *
     * <p>安全说明：必须同时限定 userId，防止通过伪造 deviceId 越权吊销他人会话。
     *
     * @param userId 目标用户
     * @param deviceId 目标设备标识
     * @return 受影响行数；0 表示该设备无活跃会话
     */
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

    /**
     * 统计某用户当前活跃会话数（未吊销且未过期）。用于多端登录并发限制。
     *
     * @param userId 当前用户
     * @return 活跃会话数（每个活跃刷新令牌代表一个在线设备/会话）
     */
    public int countActiveSessions(Long userId) {
        return dsl.fetchCount(RT.table(),
                userIdField().eq(userId)
                        .and(revokedAtField().isNull())
                        .and(expiresAtField().greaterThan(Timestamp.valueOf(LocalDateTime.now()))));
    }

    /**
     * 吊销某用户<b>最早登录</b>的若干会话，仅保留最晚登录的 {@code keepCount} 个。
     * 用于多端登录限制策略 evict_oldest（会话挤占）：新登录占用名额，
     * 最早上线的会话被挤出。
     *
     * <p>挑选依据（纯函数 {@code SessionLimitRules.selectToEvict}）：按 {@code issued_at} 升序，
     * 登录时间最早的排在最前、最先被淘汰；登录时间相同则按 {@code last_used_at} 升序兜底。
     * 仅作用于未吊销且未过期的会话。
     *
     * @param userId    当前用户
     * @param keepCount 保留的最晚登录会话数（自动收敛为不小于 0）
     */
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
        SysRefreshTokenDO token = SqlWriters.fetchOne(dsl, RT,
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
