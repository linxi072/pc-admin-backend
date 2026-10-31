package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.tree.TreeBuilder;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooqgen.Tables;
import com.acme.scaffold.security.permission.DataScope;
import com.acme.scaffold.security.permission.DataScopeConditions;
import com.acme.scaffold.security.permission.DataScopeContext;
import com.acme.scaffold.security.permission.DataScopeResult;
import com.acme.scaffold.system.dto.CreateOrgRequest;
import com.acme.scaffold.system.dto.OrgTreeVO;
import com.acme.scaffold.system.dto.UpdateOrgRequest;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysUserDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 机构服务：机构树与维护（ancestors 物化路径）。持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Service
@RequiredArgsConstructor
public class OrgService {
    private static final JooqTables.TableRef USER = JooqTables.toTableRef(Tables.SYS_USER);

    /** 部门资源的编码，与 {@code sys_role_data_scope.resource_code} 对应。 */
    public static final String RESOURCE_CODE = "system:org";

    /** 机构树缓存名；键为数据权限范围签名（见 {@link #orgScopeKey()}），确保不同权限用户互不串缓存。 */
    public static final String ORG_TREE_CACHE = "org-tree";

    /** 数据权限范围签名的「不限制」键。 */
    public static final String SCOPE_ALL_KEY = "ALL";

    private final DSLContext dsl;

    /** 桥接自代码生成层 {@code Tables.SYS_ORG} 的类型安全表引用（B16）。 */
    private static final JooqTables.TableRef ORG = JooqTables.toTableRef(Tables.SYS_ORG);

    /**
     * 机构树（数据权限敏感热点读）。
     *
     * <p>缓存按「数据权限范围签名」分键（{@link #orgScopeKey()}），而非单一 {@value #SCOPE_ALL_KEY} 键：
     * 若不隔离，首调（多为 ALL 权限管理员）会把全量树写入缓存，受限用户随后命中缓存读到越权子树。
     * 写入路径（create/update/delete）统一整缓存驱逐；{@link DataScopeAspect} 已置为最外层，
     * 保证缓存键计算时 {@link DataScopeContext} 已就绪。命中缓存时跳过打库与 {@link DataScopeConditions} 取条件。
     */
    @DataScope(resourceCode = RESOURCE_CODE, name = "部门管理")
    @Cacheable(cacheNames = ORG_TREE_CACHE, key = "#root.target.orgScopeKey()")
    public List<OrgTreeVO> tree() {
        List<SysOrgDO> all = JooqWriters.fetchList(dsl, ORG, SysOrgDO.class,
                DSL.and(JooqWriters.notDeleted(ORG),
                        DataScopeConditions.of(ORG, "id")),
                Tables.SYS_ORG.SORT_NO.asc());
        // 负责人姓名映射：一次 IN 批量查询（displayName 优先、回落 username），
        // 避免逐节点按 leaderUserId 回查用户表形成 N+1
        Map<Long, String> leaderNames = loadLeaderNames(all.stream()
                .map(SysOrgDO::getLeaderUserId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0L)
                .collect(Collectors.toSet()));
        Set<Long> visible = all.stream().map(SysOrgDO::getId).collect(Collectors.toSet());
        // 层级组装复用 common/tree/TreeBuilder（与 MenuService 同一实现，含环防护）。
        // 顶层判定：无父，或其父被数据权限过滤掉（此时它应升为根，避免整棵子树丢失）
        return TreeBuilder.build(all, SysOrgDO::getId, OrgService::parentIdOf,
                o -> {
                    Long parentId = parentIdOf(o);
                    return parentId == 0L || !visible.contains(parentId);
                },
                (node, children) -> {
                    String leaderName = node.getLeaderUserId() == null
                            ? null : leaderNames.get(node.getLeaderUserId());
                    return new OrgTreeVO(node.getId(), node.getParentId(), node.getOrgCode(), node.getOrgName(),
                            node.getOrgType(), node.getSortNo(), node.getStatus(), node.getLeaderUserId(),
                            leaderName, children);
                });
    }

    /**
     * 机构树缓存键：以数据权限范围签名隔离，避免跨权限缓存串味。
     * 与 {@link DataScopeConditions#of} 的 org 维度语义保持一致：
     * 不限制 / 空集合降级 → {@value #SCOPE_ALL_KEY}；仅本人 → {@code SELF:<userId>}；
     * 机构集合 → {@code ORG:<升序逗号分隔的 id>}。
     */
    public String orgScopeKey() {
        DataScopeContext.Scope scope = DataScopeContext.current();
        if (scope == null || scope.result() == null || scope.result().unrestricted()) {
            return SCOPE_ALL_KEY;
        }
        DataScopeResult result = scope.result();
        if (result.selfOnly()) {
            return "SELF:" + scope.userId();
        }
        Set<Long> orgIds = result.orgIds();
        if (orgIds == null || orgIds.isEmpty()) {
            return SCOPE_ALL_KEY;
        }
        return "ORG:" + orgIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** parentId 归一：null 视作顶级（0）。TreeBuilder 分组 / 顶层判定共用。 */
    private static Long parentIdOf(SysOrgDO o) {
        return o.getParentId() == null ? 0L : o.getParentId();
    }

    /** 按用户 id 集合批量取姓名（displayName 为空时回落 username），空集合不发查询。 */
    private Map<Long, String> loadLeaderNames(Set<Long> leaderIds) {
        if (leaderIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserDO> users = JooqWriters.fetchList(dsl, USER, SysUserDO.class,
                DSL.and(JooqWriters.notDeleted(USER),
                        USER.field("id", Long.class).in(leaderIds)));
        Map<Long, String> names = new HashMap<>();
        for (SysUserDO u : users) {
            names.put(u.getId(), StringUtils.hasText(u.getDisplayName()) ? u.getDisplayName() : u.getUsername());
        }
        return names;
    }

    @Transactional
    @CacheEvict(cacheNames = ORG_TREE_CACHE, allEntries = true)
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
            SysOrgDO parent = JooqWriters.fetchById(dsl, ORG, SysOrgDO.class, parentId);
            org.setAncestors(parent == null ? "0" : parent.getAncestors() + "," + parentId);
        } else {
            org.setAncestors("0");
        }
        JooqWriters.insert(dsl, ORG, org);
        return org.getId();
    }

    private boolean existsByCode(String code) {
        return JooqWriters.count(dsl, ORG,
                DSL.and(JooqWriters.notDeleted(ORG),
                        Tables.SYS_ORG.ORG_CODE.eq(code))) > 0;
    }

    @Transactional
    @CacheEvict(cacheNames = ORG_TREE_CACHE, allEntries = true)
    public void update(Long id, UpdateOrgRequest request) {
        SysOrgDO existing = JooqWriters.fetchById(dsl, ORG, SysOrgDO.class, id);
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
        if (request.leaderUserId() == null) {
            // 清除负责人：updateById 的 NOT_NULL 策略会跳过 null 字段导致旧值残留，
            // 须显式置空（与 ProfileService 解绑手机号/邮箱同一范式）
            JooqWriters.updateByIdWithNulls(dsl, ORG, id, org, Set.of("leader_user_id"));
        } else {
            JooqWriters.updateById(dsl, ORG, id, org);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = ORG_TREE_CACHE, allEntries = true)
    public void delete(Long id) {
        long childCount = JooqWriters.count(dsl, ORG,
                DSL.and(JooqWriters.notDeleted(ORG),
                        Tables.SYS_ORG.PARENT_ID.eq(id)));
        if (childCount > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "请先删除子机构");
        }
        JooqWriters.delete(dsl, ORG, id, true);
    }

    private List<SysOrgDO> loadAll() {
        return JooqWriters.fetchList(dsl, ORG, SysOrgDO.class,
                JooqWriters.notDeleted(ORG),
                Tables.SYS_ORG.SORT_NO.asc());
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
                JooqWriters.updateById(dsl, ORG, o.getId(), patch);
            }
        }
    }
}
