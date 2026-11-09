package com.acme.scaffold.system.password.adapter;

import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.security.password.PasswordHistoryDataPort;
import com.acme.scaffold.security.password.PasswordHistoryRecord;
import com.acme.scaffold.system.password.domain.model.SysPasswordHistoryDO;
import lombok.RequiredArgsConstructor;
import com.acme.scaffold.mybatis.dsl.DSLContext;
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

    private static final SqlTables.TableRef PH = SqlTables.SYS_PASSWORD_HISTORY;

    private final DSLContext dsl;

    @Override
    public void insert(Long userId, String encodedHash, LocalDateTime createdAt) {
        SysPasswordHistoryDO entity = new SysPasswordHistoryDO();
        entity.setUserId(userId);
        entity.setPasswordHash(encodedHash);
        entity.setCreatedAt(createdAt);
        SqlWriters.insert(dsl, PH, entity);
    }

    @Override
    public List<String> recentHashes(Long userId, int limit) {
        List<SysPasswordHistoryDO> recent = SqlWriters.fetchList(dsl, PH,
                SysPasswordHistoryDO.class,
                SqlTables.SYS_PASSWORD_HISTORY.field("user_id", Long.class).eq(userId),
                SqlTables.SYS_PASSWORD_HISTORY.field("created_at", java.time.LocalDateTime.class).desc());
        return recent.stream().limit(limit).map(SysPasswordHistoryDO::getPasswordHash).collect(Collectors.toList());
    }

    @Override
    public List<PasswordHistoryRecord> allRecords(Long userId) {
        return SqlWriters.fetchList(dsl, PH, SysPasswordHistoryDO.class,
                        SqlTables.SYS_PASSWORD_HISTORY.field("user_id", Long.class).eq(userId)).stream()
                .map(d -> new PasswordHistoryRecord(d.getId(), d.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        SqlWriters.delete(dsl, PH, id, false);
    }
}
