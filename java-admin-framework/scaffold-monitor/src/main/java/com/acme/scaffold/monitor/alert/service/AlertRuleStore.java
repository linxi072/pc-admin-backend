package com.acme.scaffold.monitor.alert.service;

import com.acme.scaffold.monitor.entity.AlertRuleDO;

import java.util.List;

/**
 * 告警规则存储端口（持久化抽象）。
 *
 * <p>当前实现为 {@code JooqAlertRuleStore}（jOOQ 动态 DSL）。
 * 接口隔离便于在单测中以内存桩替换，稳定覆盖规则引擎与控制器编排逻辑。</p>
 */
public interface AlertRuleStore {

    /** 查询全部规则（含逻辑删除过滤）。 */
    List<AlertRuleDO> findAll();

    /** 查询全部已启用规则（评估引擎数据源）。 */
    List<AlertRuleDO> findEnabled();

    /** 按 id 查询单条（含逻辑删除过滤），不存在返回 null。 */
    AlertRuleDO findById(Long id);

    /** 插入规则（id / tenant_id / 时间字段由 JooqWriters 自动填充）。 */
    void insert(AlertRuleDO rule);

    /** 按 id 全字段（非空）更新。 */
    void update(Long id, AlertRuleDO rule);

    /** 逻辑删除（deleted=1）。 */
    void delete(Long id);
}
