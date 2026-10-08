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
 * 基于收敛后的用户模型（sys_user 含 role_id / org_id 单列）直接查询，避免引入额外服务耦合。
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
        return userIds(JooqTables.SYS_USER.field("role_id", Long.class).eq(roleId));
    }

    private List<Long> resolveByOrg(String orgId) {
        if (orgId.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "ORG 受让人需指定部门ID");
        }
        long org = Long.parseLong(orgId);
        return userIds(JooqTables.SYS_USER.field("org_id", Long.class).eq(org));
    }

    private List<Long> resolveManager(Long starterUserId) {
        Long orgId = dsl.select(JooqTables.SYS_USER.field("org_id", Long.class))
                .from(JooqTables.SYS_USER.table())
                .where(JooqTables.SYS_USER.field("id", Long.class).eq(starterUserId))
                .fetchOne(0, Long.class);
        if (orgId == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "发起人所在部门未知");
        }
        Long managerRoleId = dsl.select(JooqTables.SYS_ROLE.field("id", Long.class))
                .from(JooqTables.SYS_ROLE.table())
                .where(JooqTables.SYS_ROLE.field("role_code", String.class).eq(MANAGER_ROLE_CODE))
                .fetchOne(0, Long.class);
        if (managerRoleId == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "未配置主管角色(" + MANAGER_ROLE_CODE + ")");
        }
        return userIds(orgAndRole(orgId, managerRoleId));
    }

    private List<Long> userIds(org.jooq.Condition cond) {
        return dsl.select(JooqTables.SYS_USER.field("id", Long.class))
                .from(JooqTables.SYS_USER.table())
                .where(org.jooq.impl.DSL.and(cond, JooqTables.SYS_USER.field("deleted", Integer.class).eq(0)))
                .fetch()
                .map(r -> r.get(0, Long.class));
    }

    private org.jooq.Condition orgAndRole(Long orgId, Long roleId) {
        return org.jooq.impl.DSL.and(
                JooqTables.SYS_USER.field("org_id", Long.class).eq(orgId),
                JooqTables.SYS_USER.field("role_id", Long.class).eq(roleId));
    }

    private static String orDefault(String value, String def) {
        return (value == null || value.isBlank()) ? def : value;
    }
}
