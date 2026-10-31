package com.acme.scaffold.job.port;

import com.acme.scaffold.job.domain.JobDagRules;
import com.acme.scaffold.job.domain.JobStatus;
import com.acme.scaffold.job.entity.SysJobDO;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 基于手写 jOOQ DSL 的 {@link JobDagPort} 实现：从 {@code sys_job} 全量加载并构建反向依赖索引。
 *
 * <p>由于任务依赖图规模很小（平台级定时任务通常数十到数百条），每次调用全量加载并内存构建索引，
 * 避免为依赖查询引入额外索引或复杂递归 SQL，逻辑清晰且易于单测。
 *
 * <p>状态写回（{@link #setRunning} / {@link #setStatus}）使用精确列更新，
 * 仅写目标列、不触碰统计数据等其他字段（规避 {@code new SysJobDO()} 默认值污染的问题）。
 */
@Component
@RequiredArgsConstructor
public class JooqJobDagAdapter implements JobDagPort {
    private static final JooqTables.TableRef SYS_JOB = JooqTables.SYS_JOB;


    private final DSLContext dsl;

    private Map<Long, SysJobDO> loadAll() {
        List<SysJobDO> all = JooqWriters.fetchList(dsl, SYS_JOB, SysJobDO.class,
                JooqWriters.notDeleted(SYS_JOB));
        Map<Long, SysJobDO> map = new HashMap<>();
        for (SysJobDO j : all) {
            map.put(j.getId(), j);
        }
        return map;
    }

    @Override
    public Set<Long> allIds() {
        return loadAll().keySet();
    }

    @Override
    public Optional<SysJobDO> findById(Long id) {
        return Optional.ofNullable(JooqWriters.fetchById(dsl, SYS_JOB, SysJobDO.class, id));
    }

    @Override
    public List<SysJobDO> findDependents(Long id) {
        List<Long> ids = dependentIds(id);
        Map<Long, SysJobDO> all = loadAll();
        List<SysJobDO> result = new ArrayList<>();
        for (Long did : ids) {
            SysJobDO j = all.get(did);
            if (j != null) {
                result.add(j);
            }
        }
        return result;
    }

    @Override
    public List<Long> dependentIds(Long id) {
        Map<Long, SysJobDO> all = loadAll();
        List<Long> result = new ArrayList<>();
        for (SysJobDO j : all.values()) {
            if (JobDagRules.parseDependsOn(j.getDependsOn()).contains(id)) {
                result.add(j.getId());
            }
        }
        return result;
    }

    @Override
    public Map<Long, List<Long>> dependentsMap(Set<Long> ids) {
        Map<Long, SysJobDO> all = loadAll();
        Map<Long, List<Long>> map = new HashMap<>();
        for (Long id : ids) {
            map.put(id, new ArrayList<>());
        }
        for (SysJobDO j : all.values()) {
            for (Long dep : JobDagRules.parseDependsOn(j.getDependsOn())) {
                if (map.containsKey(dep)) {
                    map.get(dep).add(j.getId());
                }
            }
        }
        return map;
    }

    @Override
    public void setRunning(Long id, String schedulerRef, LocalDateTime nextFireTime) {
        var t = SYS_JOB;
        dsl.update(t.table())
                .set(t.field("status", String.class), JobStatus.RUNNING.name())
                .set(t.field("scheduler_ref", String.class), schedulerRef)
                .set(t.field("next_fire_time", LocalDateTime.class), nextFireTime)
                .where(t.field("id", Long.class).eq(id))
                .execute();
    }

    @Override
    public void setStatus(Long id, String status) {
        var t = SYS_JOB;
        dsl.update(t.table())
                .set(t.field("status", String.class), status)
                .where(t.field("id", Long.class).eq(id))
                .execute();
    }
}
