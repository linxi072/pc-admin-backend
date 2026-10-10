package com.acme.scaffold.security.token;

import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.security.config.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.or;

/**
 * 异常退出会话回收服务：吊销空闲超时的刷新令牌（进程崩溃 / 连接中断后客户端不再刷新，
 * 其 last_used_at 停滞，达到空闲阈值即被回收），并清理已自然过期的令牌。
 *
 * <p>调用方：
 * <ul>
 *   <li>{@link SessionCleanupJob}（@Scheduled 周期执行）；</li>
 *   <li>{@link SessionCleanupRunner}（应用启动执行一次，用于进程崩溃后的恢复）。</li>
 * </ul>
 * 不新增任何表字段，复用 SYS_REFRESH_TOKEN 的 last_used_at / issued_at / expires_at。
 */
@Slf4j
@Service
public class SessionCleanupService {

    private final DSLContext dsl;
    private final JwtProperties jwtProps;

    public SessionCleanupService(DSLContext dsl, JwtProperties jwtProps) {
        this.dsl = dsl;
        this.jwtProps = jwtProps;
    }

    /** 周期 / 启动统一入口：按配置执行回收；禁用时直接返回。 */
    public void runCleanup() {
        if (!jwtProps.isSessionCleanupEnabled()) {
            log.debug("会话空闲回收已禁用（app.security.session-cleanup-enabled=false），跳过");
            return;
        }
        int idle = revokeIdleSessions();
        int expired = purgeExpired();
        if (idle > 0 || expired > 0) {
            log.info("会话回收完成：空闲吊销={} 过期清理={}", idle, expired);
        }
    }

    /** 吊销空闲超时的刷新令牌，返回受影响行数。 */
    public int revokeIdleSessions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(jwtProps.getSessionMaxInactive());
        Timestamp nowTs = Timestamp.valueOf(now);
        Timestamp cutoffTs = Timestamp.valueOf(cutoff);
        Field<Timestamp> lastUsed = JooqTables.SYS_REFRESH_TOKEN.field("last_used_at", Timestamp.class);
        Field<Timestamp> issuedAt = JooqTables.SYS_REFRESH_TOKEN.field("issued_at", Timestamp.class);
        Field<Timestamp> expiresAt = JooqTables.SYS_REFRESH_TOKEN.field("expires_at", Timestamp.class);
        Field<Timestamp> revokedAt = JooqTables.SYS_REFRESH_TOKEN.field("revoked_at", Timestamp.class);
        Field<String> reason = JooqTables.SYS_REFRESH_TOKEN.field("revoke_reason", String.class);
        int updated = dsl.update(JooqTables.SYS_REFRESH_TOKEN.table())
                .set(revokedAt, nowTs)
                .set(reason, "INACTIVITY_TIMEOUT")
                .where(and(
                        revokedAt.isNull(),
                        expiresAt.greaterThan(nowTs),
                        or(
                                and(lastUsed.isNull(), issuedAt.lessThan(cutoffTs)),
                                and(lastUsed.isNotNull(), lastUsed.lessThan(cutoffTs))
                        )))
                .execute();
        if (updated > 0) {
            log.info("已吊销 {} 个空闲超时的刷新令牌（idle>{}）", updated, jwtProps.getSessionMaxInactive());
        }
        return updated;
    }

    /** 清理已自然过期但 revoked_at 仍为空的刷新令牌（仅置状态，便于审计），返回受影响行数。 */
    public int purgeExpired() {
        Timestamp nowTs = Timestamp.valueOf(LocalDateTime.now());
        Field<Timestamp> expiresAt = JooqTables.SYS_REFRESH_TOKEN.field("expires_at", Timestamp.class);
        Field<Timestamp> revokedAt = JooqTables.SYS_REFRESH_TOKEN.field("revoked_at", Timestamp.class);
        Field<String> reason = JooqTables.SYS_REFRESH_TOKEN.field("revoke_reason", String.class);
        return dsl.update(JooqTables.SYS_REFRESH_TOKEN.table())
                .set(revokedAt, nowTs)
                .set(reason, "EXPIRED_CLEANUP")
                .where(and(revokedAt.isNull(), expiresAt.lessThan(nowTs)))
                .execute();
    }
}
