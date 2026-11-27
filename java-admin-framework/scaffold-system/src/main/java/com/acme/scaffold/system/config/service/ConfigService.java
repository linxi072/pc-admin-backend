package com.acme.scaffold.system.config.service;
import com.acme.scaffold.common.error.CommonErrorCode;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.sensitive.SensitiveConfigKeys;
import com.acme.scaffold.common.validation.RecordStatus;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.FetchSupport;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.config.domain.model.SysConfig;
import com.acme.scaffold.system.config.domain.rule.ConfigGroupRules;
import com.acme.scaffold.system.config.domain.ConfigGroupView;
import com.acme.scaffold.system.config.domain.ConfigView;
import com.acme.scaffold.system.config.domain.CreateConfigRequest;
import com.acme.scaffold.system.config.domain.UpdateConfigRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final Map<String, ConfigView> cache = new ConcurrentHashMap<>();

    /** 桥接自代码生成层 {@code Tables.SYS_CONFIG} 的类型安全表引用（B14）。 */
    private static final SqlTables.TableRef CONFIG = SqlTables.SYS_CONFIG;

    public List<ConfigView> list() {
        List<SysConfig> all = SqlWriters.fetchList(dsl, CONFIG, SysConfig.class,
                SqlWriters.notDeleted(CONFIG),
                SqlTables.SYS_CONFIG.field("config_key", String.class).asc());
        return all.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 按命名空间前缀分组（M36）：聚合为「分组 → 配置项列表」，便于前端分区展示与聚合返回。
     */
    public List<ConfigGroupView> listGrouped() {
        List<ConfigView> all = list();
        Map<String, List<ConfigView>> byGroup = all.stream()
                .collect(Collectors.groupingBy(c -> ConfigGroupRules.deriveGroup(c.configKey())));
        List<ConfigGroupView> groups = new ArrayList<>();
        byGroup.forEach((group, items) ->
                groups.add(new ConfigGroupView(group, ConfigGroupRules.groupLabel(group), items)));
        groups.sort(Comparator.comparing(ConfigGroupView::group));
        return groups;
    }

    /**
     * 主动刷新配置缓存（M36 热更新）：清空后从库重新全量加载，使直接改库或经参数中心界面
     * 的调整立即生效，无需重启。供 {@code POST /api/system/configs/refresh} 调用，亦由定时任务周期触发。
     */
    public void forceRefresh() {
        List<ConfigView> all = list();
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
    public ConfigView getByKey(String key) {
        ConfigView cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        SysConfig c = SqlWriters.fetchOne(dsl, CONFIG, SysConfig.class,
                DSL.and(SqlWriters.notDeleted(CONFIG),
                        SqlTables.SYS_CONFIG.field("config_key", String.class).eq(key)));
        if (c == null) {
            return null;
        }
        ConfigView vo = toVO(c);
        cache.put(key, vo);
        return vo;
    }

    /** 管理端展示用列表：敏感配置值与默认值统一掩码，键名保留供审计。 */
    public List<ConfigView> listForDisplay() {
        return list().stream().map(this::maskForDisplay).toList();
    }

    /** 管理端分组展示：组内条目同样脱敏，分组维度不变。 */
    public List<ConfigGroupView> listGroupedForDisplay() {
        return listGrouped().stream()
                .map(g -> new ConfigGroupView(g.group(), g.label(),
                        g.items().stream().map(this::maskForDisplay).toList()))
                .toList();
    }

    /** 管理端按键展示：内部读取仍走 {@link #getByKey(String)} 保持原文，不在此处改动。 */
    public ConfigView getByKeyForDisplay(String key) {
        ConfigView vo = getByKey(key);
        return vo == null ? null : maskForDisplay(vo);
    }

    private ConfigView maskForDisplay(ConfigView vo) {
        String value = SensitiveConfigKeys.mask(vo.configKey(), vo.configValue());
        String defaultValue = SensitiveConfigKeys.mask(vo.configKey(), vo.defaultValue());
        if (Objects.equals(value, vo.configValue()) && Objects.equals(defaultValue, vo.defaultValue())) {
            return vo;
        }
        return new ConfigView(vo.id(), vo.configKey(), vo.configName(), value, defaultValue,
                vo.configType(), vo.remark(), vo.editable(), vo.status());
    }

    @Transactional
    public Long create(CreateConfigRequest req) {
        if (existsByKey(req.configKey())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "配置键已存在");
        }
        SysConfig c = new SysConfig();
        c.setConfigKey(req.configKey());
        c.setConfigName(req.configName());
        c.setConfigValue(req.configValue());
        c.setDefaultValue(req.defaultValue());
        c.setConfigType(req.configType() == null ? "STRING" : req.configType());
        c.setRemark(req.remark());
        c.setEditable(req.editable() == null ? 1 : req.editable());
        c.setStatus(RecordStatus.normalize(req.status()));
        try {
            SqlWriters.insert(dsl, CONFIG, c);
        } catch (DataIntegrityViolationException e) {
            // 并发同键创建：应用层查重与插入的时间窗由唯一键 uk_config_key_active 兜底裁决，
            // 转换为与预校验一致的清晰 409，避免落到通用异常文案。
            throw new BusinessException(CommonErrorCode.CONFLICT, "配置键已存在");
        }
        cache.put(req.configKey(), toVO(c));
        return c.getId();
    }

    @Transactional
    public void update(Long id, UpdateConfigRequest req) {
        SysConfig existing = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, CONFIG, SysConfig.class, id), "配置不存在");
        if (!existing.getConfigKey().equals(req.configKey()) && existsByKey(req.configKey())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "配置键已存在");
        }
        String oldKey = existing.getConfigKey();
        SysConfig c = new SysConfig();
        c.setConfigKey(req.configKey());
        c.setConfigName(req.configName());
        c.setConfigValue(req.configValue());
        c.setDefaultValue(req.defaultValue());
        c.setConfigType(req.configType() == null ? existing.getConfigType() : req.configType());
        c.setRemark(req.remark());
        c.setEditable(req.editable() == null ? existing.getEditable() : req.editable());
        c.setStatus(RecordStatus.mergeOrKeep(req.status(), existing.getStatus()));
        try {
            SqlWriters.updateById(dsl, CONFIG, id, c);
        } catch (DataIntegrityViolationException e) {
            // 并发改键/建键：唯一键兜底裁决，转换为清晰 409；异常发生在缓存更新之前，旧缓存无需补偿。
            throw new BusinessException(CommonErrorCode.CONFLICT, "配置键已存在");
        }
        cache.remove(oldKey);
        cache.put(req.configKey(), toVO(c));
    }

    @Transactional
    public void delete(Long id) {
        SysConfig existing = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, CONFIG, SysConfig.class, id), "配置不存在");
        SqlWriters.delete(dsl, CONFIG, id, true);
        cache.remove(existing.getConfigKey());
    }

    private ConfigView toVO(SysConfig c) {
        return new ConfigView(c.getId(), c.getConfigKey(), c.getConfigName(), c.getConfigValue(),
                c.getDefaultValue(), c.getConfigType(), c.getRemark(), c.getEditable(), c.getStatus());
    }

    private boolean existsByKey(String key) {
        return SqlWriters.count(dsl, CONFIG,
                DSL.and(SqlWriters.notDeleted(CONFIG),
                        SqlTables.SYS_CONFIG.field("config_key", String.class).eq(key))) > 0;
    }
}
