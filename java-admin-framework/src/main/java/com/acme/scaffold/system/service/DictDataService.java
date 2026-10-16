package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.CreateDictDataRequest;
import com.acme.scaffold.system.dto.DictDataVO;
import com.acme.scaffold.system.dto.UpdateDictDataRequest;
import com.acme.scaffold.system.entity.SysDictDataDO;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictDataService {

    /** 字典数据缓存名（监控页 / 清理端点按此定位）。 */
    public static final String CACHE_NAME = "dict-data";

    /**
     * 缓存键规范化：空类型码归一为 {@value #ALL_TYPES}。
     * 查询体与 {@code @Cacheable} key 表达式共用，保证「同一口径同一键」。
     */
    public static final String ALL_TYPES = "ALL";

    public static String cacheKey(String dictTypeCode) {
        return (dictTypeCode == null || dictTypeCode.isBlank()) ? ALL_TYPES : dictTypeCode.trim();
    }

    private final DSLContext dsl;

    /**
     * 按字典类型查询（缓存试点：字典属低频变更热点读，TTL 由 {@code app.cache.ttl} 控制）。
     *
     * <p>key 与查询口径均经 {@link #cacheKey(String)} 归一：{@code null}/空白 归为
     * {@value #ALL_TYPES}（查询全部），非空则 trim 后精确匹配。写路径
     * （{@link #create} / {@link #update} / {@link #delete}）同步驱逐，保证读旧写新一致。
     */
    @Cacheable(cacheNames = CACHE_NAME,
            key = "T(com.acme.scaffold.system.service.DictDataService).cacheKey(#dictTypeCode)")
    public List<DictDataVO> listByType(String dictTypeCode) {
        String typeCode = cacheKey(dictTypeCode);
        Condition cond = JooqWriters.notDeleted(JooqTables.SYS_DICT_DATA);
        if (!ALL_TYPES.equals(typeCode)) {
            cond = DSL.and(cond, JooqTables.SYS_DICT_DATA.field("dict_type_code", String.class).eq(typeCode));
        }
        List<SysDictDataDO> all = JooqWriters.fetchList(dsl, JooqTables.SYS_DICT_DATA, SysDictDataDO.class,
                cond, JooqTables.SYS_DICT_DATA.field("dict_sort", Integer.class).asc());
        return all.stream().map(d -> new DictDataVO(d.getId(), d.getDictTypeCode(), d.getDictLabel(),
                d.getDictValue(), d.getDictSort(), d.getStatus(), d.getRemark())).toList();
    }

    @CacheEvict(cacheNames = CACHE_NAME,
            key = "T(com.acme.scaffold.system.service.DictDataService).cacheKey(#req.dictTypeCode())")
    @Transactional
    public Long create(CreateDictDataRequest req) {
        if (existsByTypeAndValue(req.dictTypeCode(), req.dictValue(), null)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该字典类型下字典值已存在");
        }
        SysDictDataDO d = new SysDictDataDO();
        d.setDictTypeCode(req.dictTypeCode());
        d.setDictLabel(req.dictLabel());
        d.setDictValue(req.dictValue());
        d.setDictSort(req.dictSort() == null ? 0 : req.dictSort());
        d.setStatus(req.status() == null ? "ACTIVE" : req.status());
        d.setRemark(req.remark());
        JooqWriters.insert(dsl, JooqTables.SYS_DICT_DATA, d);
        return d.getId();
    }

    /**
     * 更新字典数据。类型可能被改写，故整缓存驱逐（字典数据量小，全清成本可忽略）；
     * 精确驱逐需同时知道新旧类型码，反而易漏。
     */
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    @Transactional
    public void update(Long id, UpdateDictDataRequest req) {
        SysDictDataDO existing = JooqWriters.fetchById(dsl, JooqTables.SYS_DICT_DATA, SysDictDataDO.class, id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "字典数据不存在");
        }
        if (!existing.getDictValue().equals(req.dictValue())
                && existsByTypeAndValue(req.dictTypeCode(), req.dictValue(), id)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该字典类型下字典值已存在");
        }
        SysDictDataDO d = new SysDictDataDO();
        d.setDictTypeCode(req.dictTypeCode());
        d.setDictLabel(req.dictLabel());
        d.setDictValue(req.dictValue());
        d.setDictSort(req.dictSort() == null ? existing.getDictSort() : req.dictSort());
        d.setStatus(req.status() == null ? existing.getStatus() : req.status());
        d.setRemark(req.remark());
        JooqWriters.updateById(dsl, JooqTables.SYS_DICT_DATA, id, d);
    }

    /** 逻辑删除。删除前不额外回查类型码，统一整缓存驱逐。 */
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    @Transactional
    public void delete(Long id) {
        JooqWriters.delete(dsl, JooqTables.SYS_DICT_DATA, id, true);
    }

    private boolean existsByTypeAndValue(String typeCode, String value, Long excludeId) {
        Condition cond = DSL.and(JooqWriters.notDeleted(JooqTables.SYS_DICT_DATA),
                JooqTables.SYS_DICT_DATA.field("dict_type_code", String.class).eq(typeCode),
                JooqTables.SYS_DICT_DATA.field("dict_value", String.class).eq(value));
        if (excludeId != null) {
            cond = DSL.and(cond, JooqTables.SYS_DICT_DATA.field("id", Long.class).ne(excludeId));
        }
        return JooqWriters.count(dsl, JooqTables.SYS_DICT_DATA, cond) > 0;
    }
}
