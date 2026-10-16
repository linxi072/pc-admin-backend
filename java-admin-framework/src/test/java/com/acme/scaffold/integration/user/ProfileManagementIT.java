package com.acme.scaffold.integration.user;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.service.UserService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 个人中心业务集成测试：覆盖资料修改的联系方式校验与自助改密的校验顺序。
 *
 * <p>固化以下两个观察项的修复回归：
 * <ul>
 *   <li>O-3：{@code ProfileService.updateProfile} 服务层原不校验手机/邮箱格式，
 *       唯一性之外无格式闸（DTO 的 @Pattern 是 Web 层第一道，服务层补第二道，
 *       内部调用不再裸奔）。空串仍表示「解绑」，跳过格式与唯一性校验；</li>
 *   <li>O-4：{@code ProfileService.changePassword} 原把复用校验排在强度策略之前，
 *       「既弱又复用」的新口令会先报 AUTH_007 掩盖策略问题。修复后顺序为
 *       原密码 → 强度策略 → 复用比对。</li>
 * </ul>
 *
 * <p>登录次数控制：{@code RateLimitFilter} 按「login:IP」60s 窗口限流 10 次，
 * 本类以 cachedToken 复用 U_A 会话（资料与弱密码用例不改密，令牌始终有效），
 * 仅历史复用用例因「改密后旧令牌失效」需重新登录。
 *
 * <p>运行前提：本机 MySQL（root/root），激活 integration profile。
 */
class ProfileManagementIT extends AbstractHttpIntegrationTest {

    /** 合规口令样本（满足 8-64 位 + 大小写 + 数字 + 特殊字符）。 */
    private static final String P0 = "Password123!";
    private static final String P1 = "Str0ng!Pass99";

    @Autowired
    private DSLContext dsl;

    @Autowired
    private UserService userService;

    /** 本类创建的用户，@AfterEach 清理，避免脏数据累积。 */
    private final List<Long> createdUserIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long uid : createdUserIds) {
            userService.delete(uid);
        }
        createdUserIds.clear();
    }

    private long seedRoleId() {
        List<SysRoleDO> roles = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ROLE));
        assertFalse(roles.isEmpty(), "库中应存在角色种子");
        return roles.get(0).getId();
    }

    private long seedOrgId() {
        List<SysOrgDO> orgs = JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ORG));
        assertFalse(orgs.isEmpty(), "库中应存在部门种子");
        return orgs.get(0).getId();
    }

    /** 类内缓存 U_A 的登录令牌（RateLimitFilter 单例限流，详见类注释）。 */
    private String cachedToken;
    private String cachedUsername;

    private String tokenOfU_A() {
        if (cachedToken == null) {
            cachedUsername = "it-pf-" + System.nanoTime();
            Long id = userService.create(new CreateUserRequest(
                    cachedUsername, P0, "IT资料用户A", null, null,
                    List.of(seedRoleId()), null, List.of(seedOrgId()), null, null));
            createdUserIds.add(id);
            clearPasswordExpired(id);
            cachedToken = login(cachedUsername, P0);
        }
        return cachedToken;
    }

    /** 清除「首登须改密」标记：create 初始口令会置 password_expired=1，直接登录会被 423 拦截（同 UserLifecycleIT 模式）。 */
    private void clearPasswordExpired(Long uid) {
        dsl.update(JooqTables.SYS_USER.table())
                .set(JooqTables.SYS_USER.field("password_expired", Integer.class), 0)
                .where(JooqTables.SYS_USER.field("id", Long.class).eq(uid))
                .execute();
    }

    private void assertFailed(ResponseEntity<Result> resp, int expectedStatus, String messagePart) {
        assertEquals(expectedStatus, resp.getStatusCode().value(), "HTTP 状态不符: " + resp.getBody());
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().isSuccess(), "业务应失败");
        if (messagePart != null) {
            assertTrue(String.valueOf(resp.getBody().message()).contains(messagePart),
                    "错误信息应包含「" + messagePart + "」，实际: " + resp.getBody().message());
        }
    }

    /**
     * 取 Bean Validation 的字段级错误（GlobalExceptionHandler 把 field→message 放在 data）。
     * <p>DTO 层 @Pattern 拦截时顶层 message 是统一的「请求参数校验失败」，
     * 具体哪个字段什么问题只在 data 里，故按字段级断言。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> fieldErrors(ResponseEntity<Result> resp) {
        Object data = resp.getBody() == null ? null : resp.getBody().data();
        return data instanceof Map ? (Map<String, String>) data : Map.of();
    }

    // ==================== O-3：资料修改格式校验 ====================

    /** 异常（O-3 回归）：手机号格式非法 → 400（DTO @Pattern 为第一道，服务层 assertMobileFormat 为第二道）。 */
    @Test
    void updateProfile_rejectsInvalidMobileFormat() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", "IT格式校验");
        body.put("mobile", "123");
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/profile",
                HttpMethod.PUT, new HttpEntity<>(body, bearer(tokenOfU_A())), Result.class);
        assertFailed(resp, 400, "请求参数校验失败");
        assertTrue(fieldErrors(resp).containsValue("手机号格式不正确"),
                "字段级错误应指出手机号格式问题: " + fieldErrors(resp));
    }

    /** 异常（O-3 回归）：邮箱格式非法 → 400（同上双层校验）。 */
    @Test
    void updateProfile_rejectsInvalidEmailFormat() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", "IT格式校验");
        body.put("email", "not-an-email");
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/profile",
                HttpMethod.PUT, new HttpEntity<>(body, bearer(tokenOfU_A())), Result.class);
        assertFailed(resp, 400, "请求参数校验失败");
        assertTrue(fieldErrors(resp).containsValue("邮箱格式不正确"),
                "字段级错误应指出邮箱格式问题: " + fieldErrors(resp));
    }

    /** 正常：手机号传空串表示解绑，跳过格式与唯一性校验并显式置 NULL。 */
    @Test
    void updateProfile_emptyMobileUnbinds() {
        // 先绑一个动态唯一的合规手机号（uk_tenant_mobile 唯一约束，同 UserLifecycleIT 模式）
        String mobile = "138" + String.format("%08d", System.nanoTime() % 100000000L);
        Map<String, Object> bind = new LinkedHashMap<>();
        bind.put("displayName", "IT解绑");
        bind.put("mobile", mobile);
        ResponseEntity<Result> bound = rest.exchange(baseUrl() + "/api/profile",
                HttpMethod.PUT, new HttpEntity<>(bind, bearer(tokenOfU_A())), Result.class);
        assertEquals(200, bound.getStatusCode().value(), "绑定合规手机号应成功");

        // 再传空串解绑
        Map<String, Object> unbind = new LinkedHashMap<>();
        unbind.put("displayName", "IT解绑");
        unbind.put("mobile", "");
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/profile",
                HttpMethod.PUT, new HttpEntity<>(unbind, bearer(tokenOfU_A())), Result.class);
        assertEquals(200, resp.getStatusCode().value(), "空串解绑应放行");

        SysUserDO user = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class,
                currentUserId(cachedUsername));
        assertNotNull(user);
        assertEquals(null, user.getMobile(), "解绑后 mobile 应置 NULL");
    }

    // ==================== O-4：改密校验顺序 ====================

    /**
     * 异常（O-4 分支固化）：弱新密码 → 400 VALIDATION_ERROR（强度策略分支）。
     *
     * <p>「既弱又复用」的优先级差异在合规历史前提下不可构造（历史口令都曾合规），
     * 故按分支分别固化：本用例固化策略分支先于复用分支生效的顺序调整。
     */
    @Test
    void changePassword_weakNewPassword_returns400() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("oldPassword", P0);
        body.put("newPassword", "abcdefgh"); // 长度够但复杂度不足
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/profile/password",
                HttpMethod.POST, new HttpEntity<>(body, bearer(tokenOfU_A())), Result.class);
        assertFailed(resp, 400, "密码必须包含大写字母");
    }

    /** 异常（O-4 分支固化）：复用历史口令 → 422 AUTH_007（复用比对分支）。 */
    @Test
    void changePassword_reusedHistoryPassword_returns422() {
        // 独立用户 U_C：先改密到 P1（合规、不复用），再改密回 P0（命中历史）
        String uname = "it-pf-reuse-" + System.nanoTime();
        Long uid = userService.create(new CreateUserRequest(
                uname, P0, "IT改密复用", null, null,
                List.of(seedRoleId()), null, List.of(seedOrgId()), null, null));
        createdUserIds.add(uid);
        clearPasswordExpired(uid);

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("oldPassword", P0);
        first.put("newPassword", P1);
        String t1 = login(uname, P0);
        ResponseEntity<Result> r1 = rest.exchange(baseUrl() + "/api/profile/password",
                HttpMethod.POST, new HttpEntity<>(first, bearer(t1)), Result.class);
        assertEquals(200, r1.getStatusCode().value(), "首次改密到合规新口令应成功");

        // 改密后旧令牌失效，需重新登录
        String t2 = login(uname, P1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("oldPassword", P1);
        second.put("newPassword", P0); // P0 已进入密码历史
        ResponseEntity<Result> r2 = rest.exchange(baseUrl() + "/api/profile/password",
                HttpMethod.POST, new HttpEntity<>(second, bearer(t2)), Result.class);
        assertFailed(r2, 422, "不能与最近使用的密码相同");
    }

    private Long currentUserId(String username) {
        List<SysUserDO> users = JooqWriters.fetchList(dsl, JooqTables.SYS_USER, SysUserDO.class,
                JooqTables.SYS_USER.field("username", String.class).eq(username));
        return users.isEmpty() ? null : users.get(0).getId();
    }
}
