package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooqgen.Tables;
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

    /** 类型安全生成表（逗号桥接自手写 TableRef，列名与 JooqTables.SYS_DICT_TYPE 严格一致）。 */
    private static final JooqTables.TableRef TYPE = JooqTables.toTableRef(Tables.SYS_DICT_TYPE);

    /** 字典数据表（删除前回查关联数据用，列名与 JooqTables.SYS_DICT_DATA 严格一致）。 */
    private static final JooqTables.TableRef DATA = JooqTables.toTableRef(Tables.SYS_DICT_DATA);

    private final DSLContext dsl;

    public List<DictTypeVO> list() {
        List<SysDictTypeDO> all = JooqWriters.fetchList(dsl, TYPE, SysDictTypeDO.class,
                JooqWriters.notDeleted(TYPE),
                Tables.SYS_DICT_TYPE.SORT_NO.asc());
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
        JooqWriters.insert(dsl, TYPE, t);
        return t.getId();
    }

    @Transactional
    public void update(Long id, UpdateDictTypeRequest req) {
        SysDictTypeDO existing = JooqWriters.fetchById(dsl, TYPE, SysDictTypeDO.class, id);
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
        JooqWriters.updateById(dsl, TYPE, id, t);
    }

    @Transactional
    public void delete(Long id) {
        SysDictTypeDO existing = JooqWriters.fetchById(dsl, TYPE, SysDictTypeDO.class, id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "字典类型不存在");
        }
        long dataCount = JooqWriters.count(dsl, DATA,
                DSL.and(JooqWriters.notDeleted(DATA),
                        Tables.SYS_DICT_DATA.DICT_TYPE_CODE.eq(existing.getDictCode())));
        if (dataCount > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "请先删除该字典类型下的字典数据");
        }
        JooqWriters.delete(dsl, TYPE, id, true);
    }

    private boolean existsByCode(String code) {
        return JooqWriters.count(dsl, TYPE,
                DSL.and(JooqWriters.notDeleted(TYPE),
                        Tables.SYS_DICT_TYPE.DICT_CODE.eq(code))) > 0;
    }
}
