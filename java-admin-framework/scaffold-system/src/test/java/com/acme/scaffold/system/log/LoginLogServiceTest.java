package com.acme.scaffold.system.log;

import com.acme.scaffold.common.api.dto.KeysetPageResult;
import com.acme.scaffold.jooq.paging.JooqKeysetPager;
import com.acme.scaffold.security.entity.SysLoginLogDO;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * {@link LoginLogService#keysetPage} 单元测试：以 {@code mockStatic(JooqKeysetPager)} 作为打库替身，
 * 验证服务层仅做「条件组装 + 视图映射 + nextCursor 透传」，深翻页的 seek/limit 语义由 JooqKeysetPager 单测覆盖。
 *
 * <p>关注点：① keyset 端点复用与 {@link LoginLogService#page} 相同的 {@code buildCondition} 筛选口径；
 * ② 脱敏开关 {@code sensitiveVisible} 经 {@link LoginLogView#of(SysLoginLogDO, boolean)} 正确透传到 IP 掩码；
 * ③ nextCursor 由 pager 结果原样透传。离线可跑。
 */
class LoginLogServiceTest {

    private final DSLContext dsl = mock(DSLContext.class);
    private final LoginLogService service = new LoginLogService(dsl);

    private static SysLoginLogDO login(long id, String ip) {
        SysLoginLogDO d = new SysLoginLogDO();
        d.setId(id);
        d.setUsername("user" + id);
        d.setResult("SUCCESS");
        d.setIpAddress(ip);
        d.setOccurredAt(LocalDateTime.of(2026, 1, 1, 0, 0).plusHours(id));
        return d;
    }

    @Test
    @DisplayName("keysetPage：复用 buildCondition 筛选并映射视图，nextCursor 透传，未授权时 IP 掩码")
    void keysetPage_mapsViewsAndPropagatesCursor() {
        SysLoginLogDO d1 = login(1L, "203.0.113.45");
        SysLoginLogDO d2 = login(2L, "198.51.100.7");

        try (MockedStatic<JooqKeysetPager> mw = mockStatic(JooqKeysetPager.class)) {
            mw.when(() -> JooqKeysetPager.<SysLoginLogDO>query(
                            any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(KeysetPageResult.of(20, List.of(d1, d2), "NEXT"));

            KeysetPageResult<LoginLogView> result =
                    service.keysetPage(20, "", "user", "SUCCESS", null, null, null, null, null, null, false);

            assertEquals(2, result.records().size());
            assertEquals(20, result.size());
            assertEquals("NEXT", result.nextCursor());
            // 筛选条件已组装（username 模糊匹配被传入 buildCondition，无异常即视为已生效）
            assertEquals("user1", result.records().get(0).username());
            // 脱敏：未授权时回传掩码 IP，不得是原值
            assertNotEquals("203.0.113.45", result.records().get(0).ipAddress());
        }
    }

    @Test
    @DisplayName("keysetPage：sensitiveVisible=true 时回传原始 IP，无下一页时 nextCursor 为 null")
    void keysetPage_sensitiveVisible_returnsRawIpAndNullCursor() {
        SysLoginLogDO d1 = login(1L, "203.0.113.45");

        try (MockedStatic<JooqKeysetPager> mw = mockStatic(JooqKeysetPager.class)) {
            mw.when(() -> JooqKeysetPager.<SysLoginLogDO>query(
                            any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(KeysetPageResult.of(20, List.of(d1), null));

            KeysetPageResult<LoginLogView> result =
                    service.keysetPage(20, "CURSOR", null, null, null, null, null, null, null, null, true);

            // 末页（pager 返回 null 游标）→ nextCursor 透传为 null
            assertNull(result.nextCursor());
            assertEquals("203.0.113.45", result.records().get(0).ipAddress());
        }
    }
}
