package com.acme.scaffold.system.dict.service;
import com.acme.scaffold.common.error.CommonErrorCode;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.validation.RecordStatus;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.FetchSupport;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.dict.domain.model.SysDictType;
import com.acme.scaffold.system.dict.domain.CreateDictTypeRequest;
import com.acme.scaffold.system.dict.domain.DictTypeView;
import com.acme.scaffold.system.dict.domain.UpdateDictTypeRequest;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DictTypeService {

    /** 类型安全生成表（逗号桥接自手写 TableRef，列名与 SqlTables.SYS_DICT_TYPE 严格一致）。 */
    private static final SqlTables.TableRef TYPE = SqlTables.SYS_DICT_TYPE;

    /** 字典数据表（删除前回查关联数据用，列名与 SqlTables.SYS_DICT_DATA 严格一致）。 */
    private static final SqlTables.TableRef DATA = SqlTables.SYS_DICT_DATA;

    private final DSLContext dsl;

    public List<DictTypeView> list() {
        List<SysDictType> all = SqlWriters.fetchList(dsl, TYPE, SysDictType.class,
                SqlWriters.notDeleted(TYPE),
                SqlTables.SYS_DICT_TYPE.field("sort_no", Integer.class).asc());
        return all.stream().map(t -> new DictTypeView(t.getId(), t.getDictCode(), t.getDictName(),
                t.getStatus(), t.getSortNo(), t.getRemark())).collect(Collectors.toList());
    }

    @Transactional
    public Long create(CreateDictTypeRequest req) {
        if (existsByCode(req.dictCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "字典编码已存在");
        }
        SysDictType t = new SysDictType();
        t.setDictCode(req.dictCode());
        t.setDictName(req.dictName());
        t.setStatus(RecordStatus.normalize(req.status()));
        t.setSortNo(req.sortNo() == null ? 0 : req.sortNo());
        t.setRemark(req.remark());
        try {
            SqlWriters.insert(dsl, TYPE, t);
        } catch (DataIntegrityViolationException e) {
            // 并发同编码创建：应用层查重与插入的时间窗由唯一键 uk_dict_code_active 兜底裁决，
            // 转换为与预校验一致的清晰 409。
            throw new BusinessException(CommonErrorCode.CONFLICT, "字典编码已存在");
        }
        return t.getId();
    }

    @Transactional
    public void update(Long id, UpdateDictTypeRequest req) {
        SysDictType existing = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, TYPE, SysDictType.class, id), "字典类型不存在");
        if (!existing.getDictCode().equals(req.dictCode()) && existsByCode(req.dictCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "字典编码已存在");
        }
        SysDictType t = new SysDictType();
        t.setDictCode(req.dictCode());
        t.setDictName(req.dictName());
        t.setStatus(RecordStatus.mergeOrKeep(req.status(), existing.getStatus()));
        t.setSortNo(req.sortNo() == null ? existing.getSortNo() : req.sortNo());
        t.setRemark(req.remark());
        try {
            SqlWriters.updateById(dsl, TYPE, id, t);
        } catch (DataIntegrityViolationException e) {
            // 并发改码 / 并发同码创建：唯一键 uk_dict_code_active 兜底裁决，
            // 转换为与预校验一致的清晰 409（update 允许改写 dict_code，冲突面含更新路径）。
            throw new BusinessException(CommonErrorCode.CONFLICT, "字典编码已存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        SysDictType existing = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, TYPE, SysDictType.class, id), "字典类型不存在");
        long dataCount = SqlWriters.count(dsl, DATA,
                DSL.and(SqlWriters.notDeleted(DATA),
                        SqlTables.SYS_DICT_DATA.field("dict_type_code", String.class).eq(existing.getDictCode())));
        if (dataCount > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "请先删除该字典类型下的字典数据");
        }
        SqlWriters.delete(dsl, TYPE, id, true);
    }

    private boolean existsByCode(String code) {
        return SqlWriters.count(dsl, TYPE,
                DSL.and(SqlWriters.notDeleted(TYPE),
                        SqlTables.SYS_DICT_TYPE.field("dict_code", String.class).eq(code))) > 0;
    }
}
