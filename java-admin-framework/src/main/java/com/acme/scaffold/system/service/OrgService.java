package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.CreateOrgRequest;
import com.acme.scaffold.system.dto.OrgTreeVO;
import com.acme.scaffold.system.entity.SysOrgDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 机构服务：机构树与维护（ancestors 物化路径）。持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Service
@RequiredArgsConstructor
public class OrgService {

    private final DSLContext dsl;

    public List<OrgTreeVO> tree() {
        List<SysOrgDO> all = JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ORG),
                JooqTables.SYS_ORG.field("sort_no", Integer.class).asc());
        Map<Long, List<SysOrgDO>> byParent = all.stream()
                .collect(Collectors.groupingBy(o -> o.getParentId() == null ? 0L : o.getParentId()));
        return buildChildren(0L, byParent);
    }

    @Transactional
    public Long create(CreateOrgRequest request) {
        if (existsByCode(request.orgCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "机构编码已存在");
        }
        Long parentId = request.parentId() == null ? 0L : request.parentId();
        SysOrgDO org = new SysOrgDO();
        org.setParentId(parentId);
        org.setOrgCode(request.orgCode());
        org.setOrgName(request.orgName());
        org.setOrgType(request.orgType() == null ? "DEPARTMENT" : request.orgType());
        org.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        org.setLeaderUserId(request.leaderUserId());
        org.setStatus(request.status() == null ? "ACTIVE" : request.status());
        if (parentId != 0L) {
            SysOrgDO parent = JooqWriters.fetchById(dsl, JooqTables.SYS_ORG, SysOrgDO.class, parentId);
            org.setAncestors(parent == null ? "0" : parent.getAncestors() + "," + parentId);
        } else {
            org.setAncestors("0");
        }
        JooqWriters.insert(dsl, JooqTables.SYS_ORG, org);
        return org.getId();
    }

    private boolean existsByCode(String code) {
        return JooqWriters.count(dsl, JooqTables.SYS_ORG,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ORG),
                        JooqTables.SYS_ORG.field("org_code", String.class).eq(code))) > 0;
    }

    private List<OrgTreeVO> buildChildren(Long parentId, Map<Long, List<SysOrgDO>> byParent) {
        List<SysOrgDO> children = byParent.getOrDefault(parentId, List.of());
        List<OrgTreeVO> result = new ArrayList<>();
        for (SysOrgDO o : children) {
            result.add(new OrgTreeVO(o.getId(), o.getParentId(), o.getOrgCode(), o.getOrgName(),
                    o.getOrgType(), o.getSortNo(), o.getStatus(), buildChildren(o.getId(), byParent)));
        }
        return result;
    }
}
