package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.CreateDictDataRequest;
import com.acme.scaffold.system.dto.DictDataVO;
import com.acme.scaffold.system.dto.UpdateDictDataRequest;
import com.acme.scaffold.system.entity.SysDictDataDO;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DictDataService {

    private final DSLContext dsl;

    public List<DictDataVO> listByType(String dictTypeCode) {
        Condition cond = JooqWriters.notDeleted(JooqTables.SYS_DICT_DATA);
        if (dictTypeCode != null && !dictTypeCode.isBlank()) {
            cond = DSL.and(cond, JooqTables.SYS_DICT_DATA.field("dict_type_code", String.class).eq(dictTypeCode));
        }
        List<SysDictDataDO> all = JooqWriters.fetchList(dsl, JooqTables.SYS_DICT_DATA, SysDictDataDO.class,
                cond, JooqTables.SYS_DICT_DATA.field("dict_sort", Integer.class).asc());
        return all.stream().map(d -> new DictDataVO(d.getId(), d.getDictTypeCode(), d.getDictLabel(),
                d.getDictValue(), d.getDictSort(), d.getStatus(), d.getRemark())).collect(Collectors.toList());
    }

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
