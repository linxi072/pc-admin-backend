package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.ConfigVO;
import com.acme.scaffold.system.dto.CreateConfigRequest;
import com.acme.scaffold.system.dto.UpdateConfigRequest;
import com.acme.scaffold.system.entity.SysConfigDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 系统配置（参数）服务。
 * <p>
 * 通过 {@code getByKey} 提供"动态读取"能力：以 configKey 为键的内存缓存，
 * 写操作（新增/更新/删除）时同步失效，从而在多次读取时避免重复查询数据库，
 * 且能及时反映配置变更。注意：本缓存仅随应用内写操作失效，直接改库需重启或后续接入 Redis。
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
        c.setConfigType(req.configType() == null ? "STRING" : req.configType());
        c.setRemark(req.remark());
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
        c.setConfigType(req.configType() == null ? existing.getConfigType() : req.configType());
        c.setRemark(req.remark());
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
                c.getConfigType(), c.getRemark(), c.getStatus());
    }

    private boolean existsByKey(String key) {
        return JooqWriters.count(dsl, JooqTables.SYS_CONFIG,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_CONFIG),
                        JooqTables.SYS_CONFIG.field("config_key", String.class).eq(key))) > 0;
    }
}
