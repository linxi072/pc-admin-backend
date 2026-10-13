package com.acme.scaffold.integration.menu;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import com.acme.scaffold.system.dto.MenuSortItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单排序集成测试：验证拖拽排序 / 批量排序持久化全链路——
 * 创建同级测试菜单 → 批量提交新排序 → 树接口按 sortNo（同值按 id）稳定返回 → 清理。
 *
 * <p>运行前提（本机 / CI，不使用 Docker）：原生 MySQL 8.x，库名 java_admin，Flyway 自动建表并执行
 * V25 迁移（菜单排序接口资源种子）；admin/admin123 属于 SUPER_ADMIN。
 *
 * <p>运行：{@code mvn -o test -Dtest=MenuSortIT -Dspring.profiles.active=integration
 *   -DTEST_DB_URL=... -DTEST_DB_USER=... -DTEST_DB_PASSWORD=...}
 */
class MenuSortIT extends AbstractHttpIntegrationTest {

    private final List<Long> createdMenuIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // 逆序删除，先子后父（本用例只建顶级菜单，保底）
        for (int i = createdMenuIds.size() - 1; i >= 0; i--) {
            rest.exchange(baseUrl() + "/api/system/menus/" + createdMenuIds.get(i),
                    HttpMethod.DELETE, new org.springframework.http.HttpEntity<>(bearer(token())),
                    Result.class);
        }
        createdMenuIds.clear();
    }

    private String cachedToken;

    private String token() {
        if (cachedToken == null) {
            cachedToken = login("admin", "admin123");
        }
        return cachedToken;
    }

    /**
     * 创建顶级测试菜单，记录 id 供清理。
     *
     * <p>menuCode 追加纳秒后缀：菜单编码租户内唯一，历史残留（如上一轮全量回归失败时
     * 未清理的菜单）会导致固定编码创建冲突，唯一化保证任意数据状态下可重复执行。
     */
    private Long createMenu(String code, int sortNo) {
        String unique = code + "-" + System.nanoTime();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("menuCode", unique);
        body.put("menuName", unique);
        body.put("menuType", "M");
        body.put("routePath", "/it/sort/" + unique);
        body.put("sortNo", sortNo);
        body.put("status", "ACTIVE");
        ResponseEntity<Result> resp = rest.postForEntity(baseUrl() + "/api/system/menus",
                new org.springframework.http.HttpEntity<>(body, bearer(token())), Result.class);
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess(), "创建菜单应成功");
        // 创建端点返回 Result<String>（雪花 ID 字符串化，防前端精度丢失）
        Long id = Long.parseLong(resp.getBody().data().toString());
        createdMenuIds.add(id);
        return id;
    }

    /** 批量排序后，树接口必须按新 sortNo 返回（同值时按 id 稳定排序）。 */
    @Test
    void reorderPersistsAndTreeReflectsNewOrder() {
        Long a = createMenu("it:sort:a", 900);
        Long b = createMenu("it:sort:b", 901);
        Long c = createMenu("it:sort:c", 902);

        // 逆序提交：c → a → b
        List<MenuSortItem> items = List.of(
                new MenuSortItem(c, 10),
                new MenuSortItem(a, 20),
                new MenuSortItem(b, 30));
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/system/menus/sort",
                HttpMethod.PUT, new org.springframework.http.HttpEntity<>(items, bearer(token())), Result.class);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess(), "批量排序应成功");

        // 树接口顶层应出现 c 在 a 前、a 在 b 前
        ResponseEntity<Result> treeResp = get("/api/system/menus/tree", Result.class, token());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tree = (List<Map<String, Object>>) treeResp.getBody().data();
        List<Long> topOrder = new ArrayList<>();
        for (Map<String, Object> m : tree) {
            // 树 VO 的 id 为字符串序列化（雪花 ID 精度契约），需解析后比较
            long id = Long.parseLong(m.get("id").toString());
            if (id == a || id == b || id == c) {
                topOrder.add(id);
            }
        }
        assertEquals(List.of(c, a, b), topOrder, "树顶层应按新 sortNo 返回 c → a → b");
    }

    /** 同 sortNo 时按 id 升序（创建顺序）稳定返回——排序规则二级键契约。 */
    @Test
    void sameSortNoOrdersById() {
        Long a = createMenu("it:sort:x", 950);
        Long b = createMenu("it:sort:y", 950);
        assertTrue(a < b, "雪花 id 递增");

        ResponseEntity<Result> treeResp = get("/api/system/menus/tree", Result.class, token());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tree = (List<Map<String, Object>>) treeResp.getBody().data();
        List<Long> topOrder = new ArrayList<>();
        for (Map<String, Object> m : tree) {
            // 树 VO 的 id 为字符串序列化（雪花 ID 精度契约），需解析后比较
            long id = Long.parseLong(m.get("id").toString());
            if (id == a || id == b) {
                topOrder.add(id);
            }
        }
        assertEquals(List.of(a, b), topOrder, "同 sortNo 应按 id 升序返回");
    }

    /** 不存在的菜单 id 提交排序应被拒绝（404），保证批量失败可见而非静默。 */
    @Test
    void reorderUnknownIdFails() {
        List<MenuSortItem> items = List.of(new MenuSortItem(999999999999L, 10));
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/system/menus/sort",
                HttpMethod.PUT, new org.springframework.http.HttpEntity<>(items, bearer(token())), Result.class);
        assertEquals(404, resp.getStatusCode().value(), "不存在的菜单应返回 404");
    }
}
