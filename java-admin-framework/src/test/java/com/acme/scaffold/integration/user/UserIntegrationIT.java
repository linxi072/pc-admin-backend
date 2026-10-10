package com.acme.scaffold.integration.user;

import com.acme.scaffold.BaseIntegrationTest;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysUserRoleDO;
import com.acme.scaffold.system.service.UserService;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户-角色关联集成测试（service + DB 层）：验证主角色/主部门回退逻辑与 M1 越界守卫。
 *
 * <p>采用 {@code @Transactional} 包裹测试，所有写操作在同一事务内、测试结束后回滚，避免脏数据。
 * 鉴权无关（直接调用 Spring 托管的 {@link UserService} Bean），聚焦于
 * 「创建用户 → 关联写入 sys_user_role/sys_user_org → 主维度标记」的数据一致性。
 *
 * <p>运行前提：同 {@link com.acme.scaffold.integration.menu.MenuIntegrationIT}（本机 MySQL，含角色/部门种子）。
 *
 * 运行：{@code mvn -o test -Dtest=UserIntegrationIT -Dspring.profiles.active=integration ...}
 */
@Transactional
class UserIntegrationIT extends BaseIntegrationTest {

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
        assertTrue(!orgs.isEmpty(), "库中应存在部门种子");
        return orgs.get(0).getId();
    }

    private List<SysUserRoleDO> userRoles(Long userId) {
        Condition cond = JooqTables.SYS_USER_ROLE.field("user_id", Long.class).eq(userId);
        return JooqWriters.fetchList(dsl, JooqTables.SYS_USER_ROLE, SysUserRoleDO.class, cond);
    }

    private long primaryRoleOf(Long userId) {
        return userRoles(userId).stream()
                .filter(u -> u.getIsPrimary() != null && u.getIsPrimary() == 1)
                .map(SysUserRoleDO::getRoleId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("应存在且仅存在一个主角色"));
    }

    /** 正常路径：未指定主角色时，主角色应回退为角色集合的首位。 */
    @Test
    void createAssignsFirstRoleAsPrimaryWhenNotSpecified() {
        List<SysRoleDO> r = roles();
        assertTrue(r.size() >= 2, "库中应至少存在 2 个角色");
        long r1 = r.get(0).getId();
        long r2 = r.get(1).getId();
        long org = firstOrgId();

        Long uid = userService.create(new CreateUserRequest(
                "it-u-" + System.nanoTime(), "Password123", "IT User A", null, null,
                List.of(r1, r2), null, List.of(org), null));

        assertEquals(2, userRoles(uid).size());
        assertEquals(r1, primaryRoleOf(uid), "未指定主角色时应取角色集合首位");
    }

    /** 正常路径：显式指定主角色时，应以显式值为准。 */
    @Test
    void createHonorsExplicitPrimaryRole() {
        List<SysRoleDO> r = roles();
        long r1 = r.get(0).getId();
        long r2 = r.get(1).getId();
        long org = firstOrgId();

        Long uid = userService.create(new CreateUserRequest(
                "it-u-" + System.nanoTime(), "Password123", "IT User B", null, null,
                List.of(r1, r2), r2, List.of(org), null));

        assertEquals(r2, primaryRoleOf(uid), "显式主角色 r2 应被标记为主");
    }

    /**
     * 关键异常边界（M1 修复点）：调用方传入<b>空角色集合</b>时，
     * {@code assignRoles} 不应因 {@code get(0)} 抛 {@code IndexOutOfBoundsException}，
     * 而应将原关联清空（primary 取 null、循环不执行）。
     */
    @Test
    void assignRolesWithEmptyRoleListDoesNotThrow() {
        List<SysRoleDO> r = roles();
        long uid = userService.create(new CreateUserRequest(
                "it-u-" + System.nanoTime(), "Password123", "IT User C", null, null,
                List.of(r.get(0).getId()), null, List.of(firstOrgId()), null));
        assertEquals(1, userRoles(uid).size(), "前置：应已绑定 1 个角色");

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                userService, "assignRoles", uid, List.of(), null),
                "空角色集合不应触发越界异常（M1 守卫）");

        assertTrue(userRoles(uid).isEmpty(), "空角色集合应将原关联清空");
    }
}
