package com.acme.scaffold.integration.org;

import com.acme.scaffold.BaseIntegrationTest;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.CreateOrgRequest;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.dto.OrgTreeVO;
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
 * 部门负责人姓名映射集成测试（service + DB 层）。
 *
 * <p>验证 {@code OrgService.tree()} 将 {@code leaderUserId} 批量映射为
 * {@code leaderUserName}（displayName）填充到 {@link OrgTreeVO}，供前端「负责人」列展示；
 * 部门未设置负责人时姓名为 null。
 *
 * <p>采用 {@code @Transactional} 包裹测试，写操作同事务内完成、测试结束回滚，避免脏数据。
 * 鉴权无关：{@code @DataScope} 在无认证上下文（userId == null）时解析为 ALL，
 * 与 {@link com.acme.scaffold.integration.user.UserIntegrationIT} 同模式。
 */
@Transactional
class OrgLeaderNameIT extends BaseIntegrationTest {

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

    @Test
    void treeResolvesLeaderNameByLeaderUserId() {
        List<SysRoleDO> roles = roles();
        assertTrue(!roles.isEmpty(), "库中应存在角色种子");
        Long roleId = roles.get(0).getId();
        long deptId = firstOrgId();
        String uniq = "it-leader-" + System.nanoTime();

        // 造一个用户作为负责人候选
        Long userId = userService.create(new CreateUserRequest(
                uniq, "Password123!", "张三丰", null, null,
                List.of(roleId), null, List.of(deptId), null));

        // 造两个部门：一个设置负责人、一个不设置
        String codeA = "it-org-a-" + System.nanoTime();
        orgService.create(new CreateOrgRequest(
                null, codeA, "IT 部门甲", "DEPARTMENT", 0, userId, "ACTIVE"));
        String codeB = "it-org-b-" + System.nanoTime();
        orgService.create(new CreateOrgRequest(
                null, codeB, "IT 部门乙", "DEPARTMENT", 0, null, "ACTIVE"));

        List<OrgTreeVO> tree = orgService.tree();
        OrgTreeVO voA = find(tree, codeA);
        OrgTreeVO voB = find(tree, codeB);
        assertNotNull(voA, "新建部门应出现在机构树中");
        assertNotNull(voB, "新建部门应出现在机构树中");

        // 设置负责人的部门：leaderUserId 原样返回（字符串防精度丢失），leaderUserName 为 displayName
        assertEquals(String.valueOf(userId), String.valueOf(voA.leaderUserId()),
                "leaderUserId 应按字符串序列化契约返回");
        assertEquals("张三丰", voA.leaderUserName(),
                "leaderUserName 应为负责人的 displayName");

        // 未设置负责人的部门：姓名为 null（前端展示「未设置」）
        assertNull(voB.leaderUserId(), "未设置负责人的部门 leaderUserId 应为 null");
        assertNull(voB.leaderUserName(), "未设置负责人的部门 leaderUserName 应为 null");
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
}
