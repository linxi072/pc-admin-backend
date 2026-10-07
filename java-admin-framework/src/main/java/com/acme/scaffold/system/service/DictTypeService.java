package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.CreateDictTypeRequest;
import com.acme.scaffold.system.dto.DictTypeVO;
import com.acme.scaffold.system.dto.UpdateDictTypeRequest;
import com.acme.scaffold.system.entity.SysDictTypeDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DictTypeService {

    private final DSLContext dsl;

    public List<DictTypeVO> list() {
        List<SysDictTypeDO> all = JooqWriters.fetchList(dsl, JooqTables.SYS_DICT_TYPE, SysDictTypeDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_DICT_TYPE),
                JooqTables.SYS_DICT_TYPE.field("sort_no", Integer.class).asc());
        return all.stream().map(t -> new DictTypeVO(t.getId(), t.getDictCode(), t.getDictName(),
                t.getStatus(), t.getSortNo(), t.getRemark())).collect(Collectors.toList());
    }

    @Transactional
    public Long create(CreateDictTypeRequest req) {
        if (existsByCode(req.dictCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "字典编码已存在");
        }
        SysDictTypeDO t = new SysDictTypeDO();
        t.setDictCode(req.dictCode());
        t.setDictName(req.dictName());
        t.setStatus(req.status() == null ? "ACTIVE" : req.status());
        t.setSortNo(req.sortNo() == null ? 0 : req.sortNo());
        t.setRemark(req.remark());
        JooqWriters.insert(dsl, JooqTables.SYS_DICT_TYPE, t);
        return t.getId();
    }

    @Transactional
    public void update(Long id, UpdateDictTypeRequest req) {
        SysDictTypeDO existing = JooqWriters.fetchById(dsl, JooqTables.SYS_DICT_TYPE, SysDictTypeDO.class, id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "字典类型不存在");
        }
        if (!existing.getDictCode().equals(req.dictCode()) && existsByCode(req.dictCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "字典编码已存在");
        }
        SysDictTypeDO t = new SysDictTypeDO();
        t.setDictCode(req.dictCode());
        t.setDictName(req.dictName());
        t.setStatus(req.status() == null ? existing.getStatus() : req.status());
        t.setSortNo(req.sortNo() == null ? existing.getSortNo() : req.sortNo());
        t.setRemark(req.remark());
        JooqWriters.updateById(dsl, JooqTables.SYS_DICT_TYPE, id, t);
    }

    @Transactional
    public void delete(Long id) {
        SysDictTypeDO existing = JooqWriters.fetchById(dsl, JooqTables.SYS_DICT_TYPE, SysDictTypeDO.class, id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "字典类型不存在");
        }
        long dataCount = JooqWriters.count(dsl, JooqTables.SYS_DICT_DATA,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_DICT_DATA),
                        JooqTables.SYS_DICT_DATA.field("dict_type_code", String.class).eq(existing.getDictCode())));
        if (dataCount > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "请先删除该字典类型下的字典数据");
        }
        JooqWriters.delete(dsl, JooqTables.SYS_DICT_TYPE, id, true);
    }

    private boolean existsByCode(String code) {
        return JooqWriters.count(dsl, JooqTables.SYS_DICT_TYPE,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_DICT_TYPE),
                        JooqTables.SYS_DICT_TYPE.field("dict_code", String.class).eq(code))) > 0;
    }
}
