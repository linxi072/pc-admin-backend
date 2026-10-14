package com.acme.scaffold.integration.org;

import com.acme.scaffold.BaseIntegrationTest;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.CreateOrgRequest;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.dto.OrgTreeVO;
import com.acme.scaffold.system.dto.UpdateOrgRequest;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.service.OrgService;
import com.acme.scaffold.system.service.UserService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 部门负责人「编辑后未生效」问题复现与回归测试（service + DB 层）。
 *
 * <p>覆盖三种编辑场景：① 无负责人 → 设置 ② 更换负责人 ③ 清除负责人（回传 null）。
 * 场景 ③ 曾因 {@code JooqWriters.updateById} 的 NOT_NULL 策略（跳过 null 字段）导致
 * 清除不落库，本类用于复现该缺陷并在修复后回归。
 *
 * <p>{@code @Transactional} 包裹测试，写操作同事务内完成、测试结束回滚。
 * 鉴权无关：{@code @DataScope} 在无认证上下文（userId == null）时解析为 ALL。
 */
@Transactional
class OrgLeaderUpdateIT extends BaseIntegrationTest {

    @Autowired
    private OrgService orgService;

    @Autowired
    private UserService userService;

    @Autowired
    private DSLContext dsl;

    private List<SysRoleDO> roles() {
        return JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ROLE));
    }

    private long firstOrgId() {
        List<SysOrgDO> orgs = JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ORG));
        assertTrue(!orgs.isEmpty(), "库中应存在部门种子（V26）");
        return orgs.get(0).getId();
    }

    private Long createLeaderUser(String name) {
        List<SysRoleDO> roles = roles();
        assertTrue(!roles.isEmpty(), "库中应存在角色种子");
        return userService.create(new CreateUserRequest(
                "it-upd-" + System.nanoTime(), "Password123!", name, null, null,
                List.of(roles.get(0).getId()), null, List.of(firstOrgId()), null));
    }

    private Long createOrg(String code) {
        return orgService.create(new CreateOrgRequest(
                null, code, "IT 更新部门 " + code, "DEPARTMENT", 0, null, "ACTIVE"));
    }

    private OrgTreeVO find(String orgCode) {
        return find(orgService.tree(), orgCode);
    }

    private OrgTreeVO find(List<OrgTreeVO> nodes, String orgCode) {
        for (OrgTreeVO n : nodes) {
            if (orgCode.equals(n.orgCode())) {
                return n;
            }
            OrgTreeVO hit = find(n.children() == null ? List.of() : n.children(), orgCode);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private void updateLeader(String code, Long orgId, Long leaderUserId) {
        OrgTreeVO current = find(code);
        assertNotNull(current, "部门应存在");
        orgService.update(orgId, new UpdateOrgRequest(
                null, current.orgCode(), current.orgName(), current.orgType(),
                current.sortNo(), leaderUserId, current.status()));
    }

    @Test
    void setLeaderOnOrgWithoutLeader() {
        String code = "it-upd-" + System.nanoTime();
        Long orgId = createOrg(code);
        Long userId = createLeaderUser("李四");

        updateLeader(code, orgId, userId);

        assertEquals(String.valueOf(userId), String.valueOf(find(code).leaderUserId()),
                "无负责人部门设置负责人后应生效");
    }

    @Test
    void replaceLeaderWithAnotherUser() {
        String code = "it-upd-" + System.nanoTime();
        Long orgId = createOrg(code);
        Long userA = createLeaderUser("王五");
        Long userB = createLeaderUser("赵六");
        updateLeader(code, orgId, userA);

        updateLeader(code, orgId, userB);

        assertEquals(String.valueOf(userB), String.valueOf(find(code).leaderUserId()),
                "更换负责人后应生效");
    }

    @Test
    void clearLeaderBackfillsNull() {
        String code = "it-upd-" + System.nanoTime();
        Long orgId = createOrg(code);
        Long userId = createLeaderUser("孙七");
        updateLeader(code, orgId, userId);
        assertNotNull(find(code).leaderUserId(), "前置条件：负责人已设置");

        // 前端 clearable 清空后提交 null；后端须把 leader_user_id 显式置空
        updateLeader(code, orgId, null);

        assertNull(find(code).leaderUserId(), "清除负责人后应落库为 null（曾因 updateById 跳过 null 而失效）");
        assertNull(find(code).leaderUserName(), "清除负责人后姓名映射应为 null");
    }
}
