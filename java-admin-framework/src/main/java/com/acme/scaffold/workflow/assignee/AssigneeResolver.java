package com.acme.scaffold.workflow.assignee;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.workflow.dto.design.WorkflowNodeDTO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 审批节点受让人解析：支持按人员(USER)、角色(ROLE)、部门(ORG)、发起人(INITIATOR)、发起人主管(INITIATOR_MANAGER)解析。
 * 基于多对多关联表（sys_user_role / sys_user_org）解析，支持用户多角色、多部门归属。
 */
@Component
@RequiredArgsConstructor
public class AssigneeResolver {

    /** 主管角色码，用于 INITIATOR_MANAGER 解析（可按实际部署调整）。 */
    private static final String MANAGER_ROLE_CODE = "MANAGER";

    private final DSLContext dsl;

    public List<Long> resolve(WorkflowNodeDTO node, Long starterUserId, Long starterOrgId) {
        String type = node.assigneeType() == null ? "USER" : node.assigneeType().toUpperCase();
        return switch (type) {
            case "ROLE" -> resolveByRole(orDefault(node.assigneeExpression(), ""));
            case "ORG" -> resolveByOrg(orDefault(node.assigneeExpression(), ""));
            case "INITIATOR" -> List.of(starterUserId);
            case "INITIATOR_MANAGER" -> resolveManager(starterUserId);
            default -> resolveByUserIds(orDefault(node.assigneeExpression(), ""));
        };
    }

    private List<Long> resolveByUserIds(String expr) {
        List<Long> ids = new ArrayList<>();
        if (expr.isBlank()) {
            return ids;
        }
        for (String part : expr.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                ids.add(Long.parseLong(t));
            }
        }
        return ids;
    }

    private List<Long> resolveByRole(String roleCode) {
        if (roleCode.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "ROLE 受让人需指定角色码");
        }
        Long roleId = dsl.select(JooqTables.SYS_ROLE.field("id", Long.class))
                .from(JooqTables.SYS_ROLE.table())
                .where(JooqTables.SYS_ROLE.field("role_code", String.class).eq(roleCode))
                .fetchOne(0, Long.class);
        if (roleId == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "角色不存在: " + roleCode);
        }
        return userIdsByRole(roleId);
    }

    private List<Long> resolveByOrg(String orgId) {
        if (orgId.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "ORG 受让人需指定部门ID");
        }
        long org = Long.parseLong(orgId);
        return userIdsByOrg(org);
    }

    private List<Long> resolveManager(Long starterUserId) {
        Long orgId = primaryOrgOf(starterUserId);
        if (orgId == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "发起人所在主部门未知");
        }
        Long managerRoleId = dsl.select(JooqTables.SYS_ROLE.field("id", Long.class))
                .from(JooqTables.SYS_ROLE.table())
                .where(JooqTables.SYS_ROLE.field("role_code", String.class).eq(MANAGER_ROLE_CODE))
                .fetchOne(0, Long.class);
        if (managerRoleId == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "未配置主管角色(" + MANAGER_ROLE_CODE + ")");
        }
        return userIdsByRoleInOrg(managerRoleId, orgId);
    }

    private Long primaryOrgOf(Long userId) {
        return dsl.select(JooqTables.SYS_USER_ORG.field("org_id", Long.class))
                .from(JooqTables.SYS_USER_ORG.table())
                .where(org.jooq.impl.DSL.and(
                        JooqTables.SYS_USER_ORG.field("user_id", Long.class).eq(userId),
                        JooqTables.SYS_USER_ORG.field("is_primary", Integer.class).eq(1)))
                .fetchOne(0, Long.class);
    }

    /** 取拥有指定角色的全部用户ID（关联表 sys_user_role，多对多）。 */
    private List<Long> userIdsByRole(Long roleId) {
        return dsl.select(JooqTables.SYS_USER_ROLE.field("user_id", Long.class))
                .from(JooqTables.SYS_USER_ROLE.table())
                .where(org.jooq.impl.DSL.and(
                        JooqTables.SYS_USER_ROLE.field("role_id", Long.class).eq(roleId),
                        JooqTables.SYS_USER_ROLE.field("user_id", Long.class).in(activeUserIds())))
                .fetch().map(r -> r.get(0, Long.class));
    }

    /** 取归属指定部门的全部用户ID（关联表 sys_user_org，多对多）。 */
    private List<Long> userIdsByOrg(Long orgId) {
        return dsl.select(JooqTables.SYS_USER_ORG.field("user_id", Long.class))
                .from(JooqTables.SYS_USER_ORG.table())
                .where(org.jooq.impl.DSL.and(
                        JooqTables.SYS_USER_ORG.field("org_id", Long.class).eq(orgId),
                        JooqTables.SYS_USER_ORG.field("user_id", Long.class).in(activeUserIds())))
                .fetch().map(r -> r.get(0, Long.class));
    }

    /** 取某部门内拥有指定角色的用户ID（org + role 组合，多对多）。 */
    private List<Long> userIdsByRoleInOrg(Long roleId, Long orgId) {
        return dsl.select(JooqTables.SYS_USER_ROLE.field("user_id", Long.class))
                .from(JooqTables.SYS_USER_ROLE.table())
                .where(org.jooq.impl.DSL.and(
                        JooqTables.SYS_USER_ROLE.field("role_id", Long.class).eq(roleId),
                        JooqTables.SYS_USER_ROLE.field("user_id", Long.class).in(
                                dsl.select(JooqTables.SYS_USER_ORG.field("user_id", Long.class))
                                        .from(JooqTables.SYS_USER_ORG.table())
                                        .where(JooqTables.SYS_USER_ORG.field("org_id", Long.class).eq(orgId))),
                        JooqTables.SYS_USER_ROLE.field("user_id", Long.class).in(activeUserIds())))
                .fetch().map(r -> r.get(0, Long.class));
    }

    /** 活跃（未删除）用户ID子查询，过滤已注销账号。 */
    private org.jooq.Select<org.jooq.Record1<Long>> activeUserIds() {
        return dsl.select(JooqTables.SYS_USER.field("id", Long.class))
                .from(JooqTables.SYS_USER.table())
                .where(JooqTables.SYS_USER.field("deleted", Integer.class).eq(0));
    }

    private static String orDefault(String value, String def) {
        return (value == null || value.isBlank()) ? def : value;
    }
}
