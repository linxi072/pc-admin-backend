package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.permission.DataScope;
import com.acme.scaffold.security.permission.DataScopeConditions;
import com.acme.scaffold.system.dto.CreateOrgRequest;
import com.acme.scaffold.system.dto.OrgTreeVO;
import com.acme.scaffold.system.dto.UpdateOrgRequest;
import com.acme.scaffold.system.entity.SysOrgDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 机构服务：机构树与维护（ancestors 物化路径）。持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Service
@RequiredArgsConstructor
public class OrgService {

    /** 部门资源的编码，与 {@code sys_role_data_scope.resource_code} 对应。 */
    public static final String RESOURCE_CODE = "system:org";

    private final DSLContext dsl;

    /**
     * 机构树。数据权限由 {@link DataScope} 切面解析：ALL 时返回完整树；
     * 受限时按可见机构集合过滤，并以「父节点不在可见集合内」的节点作为根（森林）。
     */
    @DataScope(resourceCode = RESOURCE_CODE)
    public List<OrgTreeVO> tree() {
        List<SysOrgDO> all = JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ORG),
                        DataScopeConditions.of(JooqTables.SYS_ORG, "id")),
                JooqTables.SYS_ORG.field("sort_no", Integer.class).asc());
        Map<Long, List<SysOrgDO>> byParent = all.stream()
                .collect(Collectors.groupingBy(o -> o.getParentId() == null ? 0L : o.getParentId()));
        Set<Long> visible = all.stream().map(SysOrgDO::getId).collect(Collectors.toSet());
        List<OrgTreeVO> result = new ArrayList<>();
        for (SysOrgDO o : all) {
            Long parentId = o.getParentId() == null ? 0L : o.getParentId();
            // 顶层节点：无父，或其父被数据权限过滤掉（此时它应升为根，避免整棵子树丢失）
            if (parentId == 0L || !visible.contains(parentId)) {
                result.add(toTree(o, byParent));
            }
        }
        return result;
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

    private OrgTreeVO toTree(SysOrgDO node, Map<Long, List<SysOrgDO>> byParent) {
        List<OrgTreeVO> children = new ArrayList<>();
        for (SysOrgDO child : byParent.getOrDefault(node.getId(), List.of())) {
            children.add(toTree(child, byParent));
        }
        return new OrgTreeVO(node.getId(), node.getParentId(), node.getOrgCode(), node.getOrgName(),
                node.getOrgType(), node.getSortNo(), node.getStatus(), node.getLeaderUserId(), children);
    }

    @Transactional
    public void update(Long id, UpdateOrgRequest request) {
        SysOrgDO existing = JooqWriters.fetchById(dsl, JooqTables.SYS_ORG, SysOrgDO.class, id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "机构不存在");
        }
        Long parentId = request.parentId() == null ? 0L : request.parentId();
        // 机构编码唯一性（排除自身）
        if (!existing.getOrgCode().equals(request.orgCode()) && existsByCode(request.orgCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "机构编码已存在");
        }
        // 层级移动：父级变更时做防环校验并重算 ancestors 物化路径
        if (!parentId.equals(existing.getParentId())) {
            if (parentId.equals(id)) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "不能将机构挂到自身之下");
            }
            if (parentId != 0L) {
                List<SysOrgDO> all = loadAll();
                Set<Long> subtree = subtreeIds(all, id);
                if (subtree.contains(parentId)) {
                    throw new BusinessException(CommonErrorCode.CONFLICT, "不能将机构移动到其子机构之下");
                }
                boolean parentExists = all.stream().anyMatch(o -> o.getId().equals(parentId));
                if (!parentExists) {
                    throw new BusinessException(CommonErrorCode.NOT_FOUND, "父机构不存在");
                }
            }
            recomputeSubtreeAncestors(id, parentId);
        }
        SysOrgDO org = new SysOrgDO();
        org.setParentId(parentId);
        org.setOrgCode(request.orgCode());
        org.setOrgName(request.orgName());
        org.setOrgType(request.orgType() == null ? "DEPARTMENT" : request.orgType());
        org.setSortNo(request.sortNo() == null ? existing.getSortNo() : request.sortNo());
        org.setLeaderUserId(request.leaderUserId());
        org.setStatus(request.status() == null ? existing.getStatus() : request.status());
        JooqWriters.updateById(dsl, JooqTables.SYS_ORG, id, org);
    }

    @Transactional
    public void delete(Long id) {
        long childCount = JooqWriters.count(dsl, JooqTables.SYS_ORG,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ORG),
                        JooqTables.SYS_ORG.field("parent_id", Long.class).eq(id)));
        if (childCount > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "请先删除子机构");
        }
        JooqWriters.delete(dsl, JooqTables.SYS_ORG, id, true);
    }

    private List<SysOrgDO> loadAll() {
        return JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ORG),
                JooqTables.SYS_ORG.field("sort_no", Integer.class).asc());
    }

    // 收集节点及其全部子孙的 id 集合（用于层级移动的防环校验）
    private Set<Long> subtreeIds(List<SysOrgDO> all, Long rootId) {
        Set<Long> result = new HashSet<>();
        result.add(rootId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SysOrgDO o : all) {
                if (!result.contains(o.getId()) && o.getParentId() != null && result.contains(o.getParentId())) {
                    result.add(o.getId());
                    changed = true;
                }
            }
        }
        return result;
    }

    // 父级变更后，重算节点及其全部子孙的 ancestors 物化路径并持久化
    private void recomputeSubtreeAncestors(Long nodeId, Long newParentId) {
        List<SysOrgDO> all = loadAll();
        Map<Long, SysOrgDO> byId = all.stream().collect(Collectors.toMap(SysOrgDO::getId, o -> o));
        SysOrgDO node = byId.get(nodeId);
        if (node == null) return;
        String newAncestors = newParentId == 0L ? "0"
                : byId.get(newParentId).getAncestors() + "," + newParentId;
        node.setAncestors(newAncestors);
        Queue<SysOrgDO> queue = new LinkedList<>();
        queue.add(node);
        Set<Long> touched = new HashSet<>();
        touched.add(nodeId);
        while (!queue.isEmpty()) {
            SysOrgDO cur = queue.poll();
            for (SysOrgDO child : all) {
                if (child.getParentId() != null && child.getParentId().equals(cur.getId())
                        && !touched.contains(child.getId())) {
                    child.setAncestors(cur.getAncestors() + "," + cur.getId());
                    touched.add(child.getId());
                    queue.add(child);
                }
            }
        }
        for (SysOrgDO o : all) {
            if (touched.contains(o.getId())) {
                SysOrgDO patch = new SysOrgDO();
                patch.setAncestors(o.getAncestors());
                JooqWriters.updateById(dsl, JooqTables.SYS_ORG, o.getId(), patch);
            }
        }
    }
}
