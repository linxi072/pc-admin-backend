package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.ConfigGroupVO;
import com.acme.scaffold.system.dto.ConfigVO;
import com.acme.scaffold.system.dto.CreateConfigRequest;
import com.acme.scaffold.system.dto.UpdateConfigRequest;
import com.acme.scaffold.system.entity.SysConfigDO;
import com.acme.scaffold.system.rules.ConfigGroupRules;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 系统配置（参数）服务。
 * <p>
 * 通过 {@code getByKey} 提供"动态读取"能力：以 configKey 为键的内存缓存，
 * 写操作（新增/更新/删除）时同步失效；并支持定时/主动刷新（M36 热更新），
 * 从而在直接改库或经由参数中心界面调整后无需重启即可生效。
 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final DSLContext dsl;
    private final Map<String, ConfigVO> cache = new ConcurrentHashMap<>();

    public List<ConfigVO> list() {
        List<SysConfigDO> all = JooqWriters.fetchList(dsl, JooqTables.SYS_CONFIG, SysConfigDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_CONFIG),
                JooqTables.SYS_CONFIG.field("config_key", String.class).asc());
        return all.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 按命名空间前缀分组（M36）：聚合为「分组 → 配置项列表」，便于前端分区展示与聚合返回。
     */
    public List<ConfigGroupVO> listGrouped() {
        List<ConfigVO> all = list();
        Map<String, List<ConfigVO>> byGroup = all.stream()
                .collect(Collectors.groupingBy(c -> ConfigGroupRules.deriveGroup(c.configKey())));
        List<ConfigGroupVO> groups = new ArrayList<>();
        byGroup.forEach((group, items) ->
                groups.add(new ConfigGroupVO(group, ConfigGroupRules.groupLabel(group), items)));
        groups.sort(Comparator.comparing(ConfigGroupVO::group));
        return groups;
    }

    /**
     * 主动刷新配置缓存（M36 热更新）：清空后从库重新全量加载，使直接改库或经参数中心界面
     * 的调整立即生效，无需重启。供 {@code POST /api/system/configs/refresh} 调用，亦由定时任务周期触发。
     */
    public void forceRefresh() {
        List<ConfigVO> all = list();
        cache.clear();
        all.forEach(vo -> cache.put(vo.configKey(), vo));
    }

    /**
     * 定时刷新（M36 热更新）：默认每 30s 重建缓存，覆盖「直连数据库变更未走应用写接口」的场景。
     * 仅重建内存视图，无外部副作用；与写接口的缓存失效互补，保证最终一致。
     */
    @Scheduled(fixedDelay = 30_000)
    public void scheduledRefresh() {
        forceRefresh();
    }

    /**
     * 按配置键动态读取：命中缓存直接返回；未命中则从库加载并回填缓存。
     */
    public ConfigVO getByKey(String key) {
        ConfigVO cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        SysConfigDO c = JooqWriters.fetchOne(dsl, JooqTables.SYS_CONFIG, SysConfigDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_CONFIG),
                        JooqTables.SYS_CONFIG.field("config_key", String.class).eq(key)));
        if (c == null) {
            return null;
        }
        ConfigVO vo = toVO(c);
        cache.put(key, vo);
        return vo;
    }

    @Transactional
    public Long create(CreateConfigRequest req) {
        if (existsByKey(req.configKey())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "配置键已存在");
        }
        SysConfigDO c = new SysConfigDO();
        c.setConfigKey(req.configKey());
        c.setConfigName(req.configName());
        c.setConfigValue(req.configValue());
        c.setDefaultValue(req.defaultValue());
        c.setConfigType(req.configType() == null ? "STRING" : req.configType());
        c.setRemark(req.remark());
        c.setEditable(req.editable() == null ? 1 : req.editable());
        c.setStatus(req.status() == null ? "ACTIVE" : req.status());
        JooqWriters.insert(dsl, JooqTables.SYS_CONFIG, c);
        cache.put(req.configKey(), toVO(c));
        return c.getId();
    }

    @Transactional
    public void update(Long id, UpdateConfigRequest req) {
        SysConfigDO existing = JooqWriters.fetchById(dsl, JooqTables.SYS_CONFIG, SysConfigDO.class, id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "配置不存在");
        }
        if (!existing.getConfigKey().equals(req.configKey()) && existsByKey(req.configKey())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "配置键已存在");
        }
        String oldKey = existing.getConfigKey();
        SysConfigDO c = new SysConfigDO();
        c.setConfigKey(req.configKey());
        c.setConfigName(req.configName());
        c.setConfigValue(req.configValue());
        c.setDefaultValue(req.defaultValue());
        c.setConfigType(req.configType() == null ? existing.getConfigType() : req.configType());
        c.setRemark(req.remark());
        c.setEditable(req.editable() == null ? existing.getEditable() : req.editable());
        c.setStatus(req.status() == null ? existing.getStatus() : req.status());
        JooqWriters.updateById(dsl, JooqTables.SYS_CONFIG, id, c);
        cache.remove(oldKey);
        cache.put(req.configKey(), toVO(c));
    }

    @Transactional
    public void delete(Long id) {
        SysConfigDO existing = JooqWriters.fetchById(dsl, JooqTables.SYS_CONFIG, SysConfigDO.class, id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "配置不存在");
        }
        JooqWriters.delete(dsl, JooqTables.SYS_CONFIG, id, true);
        cache.remove(existing.getConfigKey());
    }

    private ConfigVO toVO(SysConfigDO c) {
        return new ConfigVO(c.getId(), c.getConfigKey(), c.getConfigName(), c.getConfigValue(),
                c.getDefaultValue(), c.getConfigType(), c.getRemark(), c.getEditable(), c.getStatus());
    }

    private boolean existsByKey(String key) {
        return JooqWriters.count(dsl, JooqTables.SYS_CONFIG,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_CONFIG),
                        JooqTables.SYS_CONFIG.field("config_key", String.class).eq(key))) > 0;
    }
}
