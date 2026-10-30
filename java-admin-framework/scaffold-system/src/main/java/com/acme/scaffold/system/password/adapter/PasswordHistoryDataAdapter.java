package com.acme.scaffold.system.password.adapter;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooqgen.Tables;
import com.acme.scaffold.security.password.PasswordHistoryDataPort;
import com.acme.scaffold.security.password.PasswordHistoryRecord;
import com.acme.scaffold.system.entity.SysPasswordHistoryDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link PasswordHistoryDataPort} 的 system 侧适配器实现。
 *
 * <p>承载原 {@code PasswordHistoryService} 中对 {@code sys_password_history} 的全部 jOOQ 直查
 * （B1/B5 同款解耦：security.password 不再直连 system.entity）。本类位于 {@code system} 包，
 * 允许 import {@code system.entity}。</p>
 */
@Component
@RequiredArgsConstructor
public class PasswordHistoryDataAdapter implements PasswordHistoryDataPort {

    private static final JooqTables.TableRef PH = JooqTables.toTableRef(Tables.SYS_PASSWORD_HISTORY);

    private final DSLContext dsl;

    @Override
    public void insert(Long userId, String encodedHash, LocalDateTime createdAt) {
        SysPasswordHistoryDO entity = new SysPasswordHistoryDO();
        entity.setUserId(userId);
        entity.setPasswordHash(encodedHash);
        entity.setCreatedAt(createdAt);
        JooqWriters.insert(dsl, PH, entity);
    }

    @Override
    public List<String> recentHashes(Long userId, int limit) {
        List<SysPasswordHistoryDO> recent = JooqWriters.fetchList(dsl, PH,
                SysPasswordHistoryDO.class,
                Tables.SYS_PASSWORD_HISTORY.USER_ID.eq(userId),
                Tables.SYS_PASSWORD_HISTORY.CREATED_AT.desc());
        return recent.stream().limit(limit).map(SysPasswordHistoryDO::getPasswordHash).collect(Collectors.toList());
    }

    @Override
    public List<PasswordHistoryRecord> allRecords(Long userId) {
        return JooqWriters.fetchList(dsl, PH, SysPasswordHistoryDO.class,
                        Tables.SYS_PASSWORD_HISTORY.USER_ID.eq(userId)).stream()
                .map(d -> new PasswordHistoryRecord(d.getId(), d.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        JooqWriters.delete(dsl, PH, id, false);
    }
}
