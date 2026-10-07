package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.permission.DataScopeType;
import com.acme.scaffold.system.dto.DataScopeRuleView;
import com.acme.scaffold.system.dto.SaveDataScopeRequest;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysRoleDataScopeDO;
import com.acme.scaffold.system.entity.SysRoleDataScopeOrgDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色数据权限配置服务。
 *
 * <p><b>此前该能力是「有表无入口」的半成品</b>：{@code sys_role_data_scope} /
 * {@code sys_role_data_scope_org} 两张表自 V1 建表起零写入方、零配置界面，
 * 导致 {@link com.acme.scaffold.security.permission.DataScopeProvider} 永远查不到规则，
 * 数据权限形同虚设。本服务补齐配置入口，使这两张表真正可用。
 *
 * <p>写入语义为<b>整体覆盖</b>：按 (roleId, resourceCode) 先删后插。
 * 该表有唯一键 {@code uk_role_resource}，覆盖式写入天然幂等，
 * 且避免前端做「新增/修改/删除」三态 diff。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleDataScopeService {

    /** 单条规则允许配置的自定义机构数量上限，防止误传超大列表拖垮事务。 */
    private static final int MAX_CUSTOM_ORGS = 500;

    private final DSLContext dsl;

    /**
     * 查询某角色已配置的全部数据权限规则。
     *
     * @param roleId 角色 ID
     * @return 规则列表；未配置时返回空列表（页面据此展示「未配置 = 不限制」）
     */
    public List<DataScopeRuleView> list(Long roleId) {
        requireRole(roleId);
        List<SysRoleDataScopeDO> rules = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_DATA_SCOPE,
                SysRoleDataScopeDO.class,
                JooqTables.SYS_ROLE_DATA_SCOPE.field("role_id", Long.class).eq(roleId));
        if (rules.isEmpty()) {
            return List.of();
        }
        // 一次性取出所有 CUSTOM 规则的机构，避免逐规则查询造成 N+1
        Set<Long> ruleIds = rules.stream().map(SysRoleDataScopeDO::getId).collect(Collectors.toSet());
        Map<Long, List<Long>> orgsByRule = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_DATA_SCOPE_ORG,
                        SysRoleDataScopeOrgDO.class,
                        JooqTables.SYS_ROLE_DATA_SCOPE_ORG.field("rule_id", Long.class).in(ruleIds))
                .stream().collect(Collectors.groupingBy(SysRoleDataScopeOrgDO::getRuleId,
                        Collectors.mapping(SysRoleDataScopeOrgDO::getOrgId, Collectors.toList())));

        Set<Long> allOrgIds = orgsByRule.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, String> orgNames = loadOrgNames(allOrgIds);

        return rules.stream().map(r -> toView(r, orgsByRule.getOrDefault(r.getId(), List.of()), orgNames))
                .collect(Collectors.toList());
    }

    /**
     * 保存（覆盖）某角色在某资源上的数据权限规则。
     *
     * @param roleId  角色 ID
     * @param request 规则内容
     */
    @Transactional
    public void save(Long roleId, SaveDataScopeRequest request) {
        requireRole(roleId);
        String resourceCode = request.resourceCode().trim();
        SaveDataScopeRequest.DataScopeTypeView scopeType = request.scopeType();

        // 未配置资源 => 删除该角色该资源的全部规则，回到「不限制」
        if (scopeType == SaveDataScopeRequest.DataScopeTypeView.ALL) {
            deleteRules(roleId, resourceCode);
            return;
        }

        List<Long> orgIds = normalizeOrgIds(request.orgIds());
        validateScopeInput(scopeType, orgIds, resourceCode);

        // 覆盖式写入：先清旧规则（含其 CUSTOM 机构关联），再插新规则
        deleteRules(roleId, resourceCode);

        SysRoleDataScopeDO rule = new SysRoleDataScopeDO();
        rule.setRoleId(roleId);
        rule.setResourceCode(resourceCode);
        rule.setScopeType(scopeType.name());
        rule.setCombineMode("UNION");
        JooqWriters.insert(dsl, JooqTables.SYS_ROLE_DATA_SCOPE, rule);

        if (scopeType == SaveDataScopeRequest.DataScopeTypeView.CUSTOM) {
            for (Long orgId : orgIds) {
                SysRoleDataScopeOrgDO ro = new SysRoleDataScopeOrgDO();
                ro.setRuleId(rule.getId());
                ro.setOrgId(orgId);
                JooqWriters.insert(dsl, JooqTables.SYS_ROLE_DATA_SCOPE_ORG, ro);
            }
        }
        log.info("数据权限规则已更新: roleId={}, resourceCode={}, scopeType={}, orgCount={}",
                roleId, resourceCode, scopeType, orgIds.size());
    }

    /**
     * 校验范围类型与机构集合的组合合法性，并确保机构真实存在。
     *
     * @throws BusinessException 组合非法或机构不存在时抛出
     */
    private void validateScopeInput(SaveDataScopeRequest.DataScopeTypeView scopeType,
                                    List<Long> orgIds, String resourceCode) {
        if (scopeType == SaveDataScopeRequest.DataScopeTypeView.CUSTOM) {
            if (orgIds.isEmpty()) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "自定义范围必须至少选择一个部门");
            }
            if (orgIds.size() > MAX_CUSTOM_ORGS) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "自定义部门数量不能超过 " + MAX_CUSTOM_ORGS + " 个");
            }
            // 校验机构存在且未删除：写入不存在的机构会让规则静默失效（查不到任何数据）
            Set<Long> existing = JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                            DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ORG),
                                    JooqTables.SYS_ORG.field("id", Long.class).in(orgIds)))
                    .stream().map(SysOrgDO::getId).collect(Collectors.toSet());
            List<Long> missing = orgIds.stream().filter(id -> !existing.contains(id)).toList();
            if (!missing.isEmpty()) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "部门不存在或已删除: " + missing);
            }
            return;
        }
        // SELF / DEPT / DEPT_AND_CHILD 都不需要机构集合，传入即视为参数错误，避免前端误以为生效
        if (!orgIds.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    scopeType + " 范围不适用自定义部门列表，请清空后重试");
        }
        // resourceCode 已 trim 且非空（@NotNull + 手工校验空白串）
        if (resourceCode.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "资源编码不能为空");
        }
    }

    /** 去重并保持提交顺序，同时剔除 null。 */
    private List<Long> normalizeOrgIds(List<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return List.of();
        }
        Set<Long> deduped = new LinkedHashSet<>();
        for (Long id : orgIds) {
            if (id != null) {
                deduped.add(id);
            }
        }
        return new ArrayList<>(deduped);
    }

    /** 删除该角色该资源的全部规则及其机构关联。 */
    private void deleteRules(Long roleId, String resourceCode) {
        List<SysRoleDataScopeDO> existing = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_DATA_SCOPE,
                SysRoleDataScopeDO.class,
                DSL.and(JooqTables.SYS_ROLE_DATA_SCOPE.field("role_id", Long.class).eq(roleId),
                        JooqTables.SYS_ROLE_DATA_SCOPE.field("resource_code", String.class).eq(resourceCode)));
        if (existing.isEmpty()) {
            return;
        }
        List<Long> ruleIds = existing.stream().map(SysRoleDataScopeDO::getId).toList();
        // 关联表无外键约束，必须先删子表再删主表，否则残留孤儿数据
        dsl.delete(JooqTables.SYS_ROLE_DATA_SCOPE_ORG.table())
                .where(JooqTables.SYS_ROLE_DATA_SCOPE_ORG.field("rule_id", Long.class).in(ruleIds))
                .execute();
        dsl.delete(JooqTables.SYS_ROLE_DATA_SCOPE.table())
                .where(JooqTables.SYS_ROLE_DATA_SCOPE.field("role_id", Long.class).eq(roleId))
                .and(JooqTables.SYS_ROLE_DATA_SCOPE.field("resource_code", String.class).eq(resourceCode))
                .execute();
    }

    private Map<Long, String> loadOrgNames(Set<Long> orgIds) {
        if (orgIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ORG),
                        JooqTables.SYS_ORG.field("id", Long.class).in(orgIds)))
                .forEach(o -> map.put(o.getId(), o.getOrgName()));
        return map;
    }

    private DataScopeRuleView toView(SysRoleDataScopeDO rule, List<Long> orgIds, Map<Long, String> orgNames) {
        DataScopeType type;
        try {
            type = DataScopeType.valueOf(rule.getScopeType());
        } catch (Exception e) {
            // 库里存在脏数据时不让整个列表接口 500，降级展示为 ALL 并保留原值语义
            log.warn("数据权限规则 scope_type 非法: {}, ruleId={}", rule.getScopeType(), rule.getId());
            type = DataScopeType.ALL;
        }
        // 非 CUSTOM 的机构列表没有意义，统一返回空，避免前端误显示残留关联
        List<Long> viewOrgIds = type == DataScopeType.CUSTOM ? orgIds : List.of();
        List<String> names = viewOrgIds.stream().map(id -> orgNames.getOrDefault(id, "已删除部门(" + id + ")")).toList();
        return new DataScopeRuleView(rule.getId(), rule.getRoleId(), rule.getResourceCode(), type, viewOrgIds, names);
    }

    /** 校验角色存在，返回角色 ID。与 {@link RoleService#get} 保持同一套判定。 */
    private Long requireRole(Long roleId) {
        SysRoleDO role = JooqWriters.fetchById(dsl, JooqTables.SYS_ROLE, SysRoleDO.class, roleId);
        if (role == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "角色不存在");
        }
        return roleId;
    }
}
