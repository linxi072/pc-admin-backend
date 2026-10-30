package com.acme.scaffold.integration.workbench;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工作台统计集成测试：验证 {@code GET /api/workbench/stats} 的接口契约与统计卡片口径。
 *
 * <p>运行前提：同 {@link com.acme.scaffold.integration.menu.MenuIntegrationIT}（本机 MySQL + admin 账号）。
 *
 * 运行：{@code mvn -o test -Dtest=WorkbenchIntegrationIT -Dspring.profiles.active=integration ...}
 */
class WorkbenchIntegrationIT extends AbstractHttpIntegrationTest {

    /** 后端 {@code WorkbenchService} 固定产出的 5 张卡片 key。 */
    private static final Set<String> EXPECTED_KEYS = Set.of(
            "NEW_USER_TODAY", "NEW_INSTANCE_TODAY", "MY_TODO_TASKS", "RUNNING_INSTANCE", "ACTIVE_API");

    /** 快照型指标（无历史基线，趋势恒为持平）。 */
    private static final Set<String> SNAPSHOT_KEYS = Set.of(
            "MY_TODO_TASKS", "RUNNING_INSTANCE", "ACTIVE_API");

    @Test
    @SuppressWarnings("unchecked")
    void statsReturnsFiveCardsWithExpectedKeys() {
        String token = login("admin", "admin123");
        ResponseEntity<Result> resp = get("/api/workbench/stats", Result.class, token);
        assertEquals(200, resp.getStatusCode().value());
        Result r = resp.getBody();
        assertNotNull(r);
        assertTrue(r.isSuccess());

        Map<String, Object> data = (Map<String, Object>) r.data();
        List<Map<String, Object>> cards = (List<Map<String, Object>>) data.get("cards");
        assertNotNull(cards);
        assertEquals(5, cards.size(), "应返回 5 张统计卡片");

        Set<String> keys = new HashSet<>();
        for (Map<String, Object> c : cards) {
            keys.add((String) c.get("key"));
            assertNotNull(c.get("label"), "卡片文案应由后端下发");
            assertNotNull(c.get("unit"), "卡片单位应由后端下发");
            assertNotNull(c.get("trend"), "趋势方向不得为空");
            assertTrue(((Number) c.get("value")).longValue() >= 0, "统计值不应为负");
        }
        assertTrue(keys.containsAll(EXPECTED_KEYS), "卡片 key 集合应与契约一致: " + keys);
    }

    /** 快照型指标无历史可比，趋势必须恒为 FLAT，不得伪造涨跌（StatCardVO.TREND_FLAT="FLAT"）。 */
    @Test
    @SuppressWarnings("unchecked")
    void snapshotCardsHaveFlatTrend() {
        String token = login("admin", "admin123");
        ResponseEntity<Result> resp = get("/api/workbench/stats", Result.class, token);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().data();
        List<Map<String, Object>> cards = (List<Map<String, Object>>) data.get("cards");

        for (Map<String, Object> c : cards) {
            String key = (String) c.get("key");
            if (SNAPSHOT_KEYS.contains(key)) {
                assertEquals("FLAT", c.get("trend"), "快照型指标 " + key + " 趋势应为持平");
            }
        }
    }
}
