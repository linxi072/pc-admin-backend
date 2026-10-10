package com.acme.scaffold.security.password;

import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.config.JwtProperties;
import com.acme.scaffold.system.entity.SysPasswordHistoryDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 密码历史读写服务：落地设计文档 §5.3「保留最近 5 次密码哈希，禁止重复使用」。
 *
 * <ul>
 *   <li>{@link #record(Long, String)}：落库一次口令变更（密文），随后裁剪超出上限的旧记录；</li>
 *   <li>{@link #isReused(Long, String)}：取最近若干条历史哈希比对，判定候选明文是否复用。</li>
 * </ul>
 *
 * <p>与 {@link PasswordHistoryRules} 配合：规则类承载无副作用判定，本服务承载 DB 交互，
 * 二者解耦以便离线单测纯逻辑。
 */
@Service
@RequiredArgsConstructor
public class PasswordHistoryService {

    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;

    /** 记录一次口令变更（密文），并裁剪超出保留上限的旧记录。 */
    @Transactional
    public void record(Long userId, String encodedPasswordHash) {
        SysPasswordHistoryDO entity = new SysPasswordHistoryDO();
        entity.setUserId(userId);
        entity.setPasswordHash(encodedPasswordHash);
        entity.setCreatedAt(LocalDateTime.now());
        JooqWriters.insert(dsl, JooqTables.SYS_PASSWORD_HISTORY, entity);
        prune(userId);
    }

    /** 候选明文是否与最近若干次历史口令重复。 */
    public boolean isReused(Long userId, String candidateRaw) {
        int limit = Math.max(1, jwtProperties.getPasswordHistoryLimit());
        List<SysPasswordHistoryDO> recent = JooqWriters.fetchList(dsl, JooqTables.SYS_PASSWORD_HISTORY,
                SysPasswordHistoryDO.class,
                JooqTables.SYS_PASSWORD_HISTORY.field("user_id", Long.class).eq(userId),
                JooqTables.SYS_PASSWORD_HISTORY.field("created_at", LocalDateTime.class).desc());
        List<String> hashes = recent.stream().limit(limit).map(SysPasswordHistoryDO::getPasswordHash).toList();
        return PasswordHistoryRules.isReused(candidateRaw, hashes, passwordEncoder);
    }

    private void prune(Long userId) {
        int limit = Math.max(1, jwtProperties.getPasswordHistoryLimit());
        List<SysPasswordHistoryDO> all = JooqWriters.fetchList(dsl, JooqTables.SYS_PASSWORD_HISTORY,
                SysPasswordHistoryDO.class,
                JooqTables.SYS_PASSWORD_HISTORY.field("user_id", Long.class).eq(userId));
        for (Long id : PasswordHistoryRules.idsToPrune(all, limit)) {
            JooqWriters.delete(dsl, JooqTables.SYS_PASSWORD_HISTORY, id, false);
        }
    }
}
