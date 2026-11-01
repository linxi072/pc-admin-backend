package com.acme.scaffold.monitor.alert.service;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.monitor.entity.AlertRuleDO;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 基于 jOOQ 动态 DSL 的告警规则存储实现（alert_rule 表）。
 *
 * <p>写入/读取/逻辑删除统一走 {@link JooqWriters}：自动填充 id / tenant_id / deleted /
 * version / 时间字段，未启用强制租户隔离时 tenant_id 写 0；逻辑删除复用
 * {@link JooqWriters#delete} 的 logical 开关。</p>
 */
@Repository
@RequiredArgsConstructor
public class JooqAlertRuleStore implements AlertRuleStore {

    private static final JooqTables.TableRef T = JooqTables.ALERT_RULE;

    private final DSLContext dsl;

    @Override
    public List<AlertRuleDO> findAll() {
        return JooqWriters.fetchList(dsl, T, AlertRuleDO.class, JooqWriters.notDeleted(T));
    }

    @Override
    public List<AlertRuleDO> findEnabled() {
        Condition cond = JooqWriters.notDeleted(T)
                .and(T.field("enabled", Integer.class).eq(1));
        return JooqWriters.fetchList(dsl, T, AlertRuleDO.class, cond);
    }

    @Override
    public AlertRuleDO findById(Long id) {
        return JooqWriters.fetchById(dsl, T, AlertRuleDO.class, id);
    }

    @Override
    public void insert(AlertRuleDO rule) {
        JooqWriters.insert(dsl, T, rule);
    }

    @Override
    public void update(Long id, AlertRuleDO rule) {
        JooqWriters.updateById(dsl, T, id, rule);
    }

    @Override
    public void delete(Long id) {
        JooqWriters.delete(dsl, T, id, true);
    }
}
