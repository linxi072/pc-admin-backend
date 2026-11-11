package com.acme.scaffold.monitor.alert.service;

import com.acme.scaffold.monitor.entity.AlertRuleDO;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

/**
 * 基于自研 MyBatis 风格 DSL 的告警规则存储实现（{@code sys_alert_rule} 表；V84 由 {@code alert_rule} 重命名）。
 *
 * <p>写入/读取/逻辑删除统一走 {@link SqlWriters}：自动填充 id / tenant_id / deleted /
 * version / 时间字段，未启用强制租户隔离时 tenant_id 写 0；逻辑删除复用
 * {@link SqlWriters#delete} 的 logical 开关。</p>
 */
@Repository
@RequiredArgsConstructor
public class SqlAlertRuleStore implements AlertRuleStore {

    private static final SqlTables.TableRef T = SqlTables.ALERT_RULE;

    private final DSLContext dsl;

    @Override
    public List<AlertRuleDO> findAll() {
        return SqlWriters.fetchList(dsl, T, AlertRuleDO.class, SqlWriters.notDeleted(T));
    }

    @Override
    public List<AlertRuleDO> findEnabled() {
        Condition cond = SqlWriters.notDeleted(T)
                .and(T.field("enabled", Integer.class).eq(1));
        return SqlWriters.fetchList(dsl, T, AlertRuleDO.class, cond);
    }

    @Override
    public AlertRuleDO findById(Long id) {
        return SqlWriters.fetchById(dsl, T, AlertRuleDO.class, id);
    }

    @Override
    public void insert(AlertRuleDO rule) {
        SqlWriters.insert(dsl, T, rule);
    }

    @Override
    public void update(Long id, AlertRuleDO rule) {
        SqlWriters.updateById(dsl, T, id, rule);
    }

    @Override
    public void delete(Long id) {
        SqlWriters.delete(dsl, T, id, true);
    }
}
