package com.acme.scaffold.integration.system;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公告业务与审计日志集成测试：覆盖公告状态机（草稿→发布→下线→重新发布）、
 * 有效期校验、编辑守卫，以及「写操作 → 审计日志异步落库 → 可查询」链路。
 *
 * <p>其中「重新发布清空 offlineAt」断言<b>正确行为</b>（修复后通过），用于固化缺陷 D-3：
 * {@code AnnouncementService.publish} 注释声明「重新发布时清空下线时间」，但
 * {@code setOfflineAt(null)} 经 {@code updateById} 的 NOT_NULL 跳过策略被丢弃，
 * 重新发布的公告仍带历史 offlineAt，与 {@code isEffective} 口径相悖。
 *
 * <p>运行前提：本机 MySQL（root/root），admin/admin123 属 SUPER_ADMIN，激活 integration profile。
 */
class AnnouncementAuditIT extends AbstractHttpIntegrationTest {

    private String cachedToken;

    private String token() {
        if (cachedToken == null) {
            cachedToken = login("admin", "admin123");
        }
        return cachedToken;
    }

    /** 本类创建的公告 id，@AfterEach 清理。 */
    private final List<Long> createdAnnouncementIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        String t = token();
        for (Long id : createdAnnouncementIds) {
            rest.exchange(baseUrl() + "/api/system/announcements/" + id,
                    org.springframework.http.HttpMethod.DELETE,
                    new org.springframework.http.HttpEntity<>(bearer(t)), Result.class);
        }
        createdAnnouncementIds.clear();
    }

    private Long createAnnouncement(String title, LocalDateTime publishAt, LocalDateTime expireAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", "IT公告内容-" + System.nanoTime());
        if (publishAt != null) {
            body.put("publishAt", publishAt.toString());
        }
        if (expireAt != null) {
            body.put("expireAt", expireAt.toString());
        }
        ResponseEntity<Result> resp = post("/api/system/announcements", body, Result.class, token());
        assertEquals(200, resp.getStatusCode().value(), "创建公告应成功: " + resp.getBody());
        Long id = Long.valueOf((String) resp.getBody().data());
        createdAnnouncementIds.add(id);
        return id;
    }

    private void call(String path) {
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + path,
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(new LinkedHashMap<String, Object>(), bearer(token())),
                Result.class);
        assertEquals(200, resp.getStatusCode().value(), path + " 应成功: " + resp.getBody());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> announcementDetail(Long id) {
        ResponseEntity<Result> resp = get("/api/system/announcements/" + id, Result.class, token());
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess());
        return (Map<String, Object>) resp.getBody().data();
    }

    /** 正常+异常（D-3 回归）：发布→下线→重新发布后，offlineAt 必须被清空。 */
    @Test
    void republish_clearsOfflineAt() {
        Long id = createAnnouncement("IT公告-重发布-" + System.nanoTime(), null, null);
        call("/api/system/announcements/" + id + "/publish");
        call("/api/system/announcements/" + id + "/offline");
        assertNotNull(announcementDetail(id).get("offlineAt"), "下线后应记录 offlineAt");

        call("/api/system/announcements/" + id + "/publish");
        Map<String, Object> after = announcementDetail(id);
        assertEquals("PUBLISHED", after.get("status"), "重新发布后应为已发布");
        assertNull(after.get("offlineAt"), "重新发布后 offlineAt 应被清空（D-3 修复回归）");
    }

    /** 正常：有效期校验——截止必须晚于生效，否则 400。 */
    @Test
    void invalidPeriod_returns400() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "IT公告-非法有效期");
        body.put("content", "x");
        body.put("publishAt", now.plusDays(2).toString());
        body.put("expireAt", now.plusDays(1).toString());
        ResponseEntity<Result> resp = post("/api/system/announcements", body, Result.class, token());
        assertEquals(400, resp.getStatusCode().value(), "截止早于生效应返回 400");
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().isSuccess());
    }

    /** 异常：已发布公告不可编辑 → 409（先下线再改）。 */
    @Test
    void updatePublishedAnnouncement_returns409() {
        Long id = createAnnouncement("IT公告-已发布编辑-" + System.nanoTime(), null, null);
        call("/api/system/announcements/" + id + "/publish");
        Map<String, Object> body = Map.of("title", "改后的标题", "content", "改后的内容");
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/system/announcements/" + id,
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(body, bearer(token())), Result.class);
        assertEquals(409, resp.getStatusCode().value(), "已发布公告不可编辑");
    }

    /** 正常：状态机闭环——草稿创建 → 发布后 onlyValid 可见 → 下线后不可见。 */
    @Test
    void lifecycle_onlyValidVisibility() {
        String keyword = "IT公告-可见性-" + System.nanoTime();
        Long id = createAnnouncement(keyword, null, null);
        assertEquals("DRAFT", announcementDetail(id).get("status"), "新建应为草稿");

        call("/api/system/announcements/" + id + "/publish");
        ResponseEntity<Result> visible = get(
                "/api/system/announcements/page?onlyValid=true&keyword=" + keyword, Result.class, token());
        assertEquals(200, visible.getStatusCode().value());
        assertTrue(containsAnnouncement(visible, id), "发布后 onlyValid 列表应包含该公告");

        call("/api/system/announcements/" + id + "/offline");
        ResponseEntity<Result> hidden = get(
                "/api/system/announcements/page?onlyValid=true&keyword=" + keyword, Result.class, token());
        assertFalse(containsAnnouncement(hidden, id), "下线后 onlyValid 列表不应包含该公告");
    }

    /** 置顶切换：0 → 1 → 0。 */
    @Test
    void toggleTop_flipsValue() {
        Long id = createAnnouncement("IT公告-置顶-" + System.nanoTime(), null, null);
        call("/api/system/announcements/" + id + "/toggle-top");
        assertEquals(1, ((Number) announcementDetail(id).get("isTop")).intValue(), "首次切换应置顶");
        call("/api/system/announcements/" + id + "/toggle-top");
        assertEquals(0, ((Number) announcementDetail(id).get("isTop")).intValue(), "再次切换应取消置顶");
    }

    // ==================== 审计日志 ====================

    /**
     * 正常：写操作（创建公告）经 @AuditOperation + 异步落库后，审计日志可查询到完整记录
     * （操作人、模块、类型、traceId、成功标记）。
     *
     * <p>定位策略：审计异步落库（afterCommit + auditExecutor），按「本测试起始时间 +
     * POST /api/system/announcements 路径」轮询匹配，避免历史记录干扰。
     */
    @Test
    void auditLog_recordsWriteOperation() throws Exception {
        // startTime 走 @DateTimeFormat(ISO.DATE_TIME)：LocalDateTime.toString() 的
        // 9 位纳秒无法被解析（500），截断到秒后序列化为 "yyyy-MM-ddTHH:mm:ss"。
        LocalDateTime since = LocalDateTime.now().minusSeconds(10).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String keyword = "IT公告-审计-" + System.nanoTime();
        createAnnouncement(keyword, null, null);

        // 审计异步落库，轮询等待可见。
        // startTime 直接传 ISO 原文（截断到秒）：查询串中的冒号无需也不应 URL 编码，
        // %3A 形式会导致 Spring 参数绑定失败（MethodArgumentTypeMismatch → 500）。
        Map<String, Object> found = null;
        for (int i = 0; i < 20 && found == null; i++) {
            ResponseEntity<Result> resp = get(
                    "/api/system/audit-logs/page?moduleCode=system&operationType=CREATE&startTime=" + since,
                    Result.class, token());
            assertEquals(200, resp.getStatusCode().value());
            found = findAuditRecord(resp);
            if (found == null) {
                Thread.sleep(200);
            }
        }
        assertNotNull(found, "创建公告应留下审计记录");
        assertEquals("admin", found.get("operatorName"), "操作人应为 admin");
        assertEquals("CREATE", found.get("operationType"), "操作类型应为 CREATE");
        assertEquals("system", found.get("moduleCode"), "模块应为 system");
        assertEquals("创建公告", found.get("operationName"), "操作名称应为创建公告");
        assertNotNull(found.get("traceId"), "审计记录应带 traceId");
        assertEquals(true, found.get("success"), "应标记成功");
    }

    @SuppressWarnings("unchecked")
    private boolean containsAnnouncement(ResponseEntity<Result> resp, Long id) {
        assertNotNull(resp.getBody());
        Map<String, Object> page = (Map<String, Object>) resp.getBody().data();
        List<Map<String, Object>> records = (List<Map<String, Object>>) page.get("records");
        return records.stream().anyMatch(r -> String.valueOf(id).equals(String.valueOf(r.get("id"))));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findAuditRecord(ResponseEntity<Result> resp) {
        assertNotNull(resp.getBody());
        Map<String, Object> page = (Map<String, Object>) resp.getBody().data();
        List<Map<String, Object>> records = (List<Map<String, Object>>) page.get("records");
        return records.stream()
                .filter(r -> String.valueOf(r.get("requestPath")).contains("/api/system/announcements"))
                .findFirst().orElse(null);
    }
}
