package com.acme.scaffold.integration.job;

import com.acme.scaffold.BaseIntegrationTest;
import com.acme.scaffold.common.api.Result;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在线定时任务管理端到端集成测试（HTTP 全链路，真实 MySQL）。
 *
 * <p><b>背景</b>：{@code sys_job} 主键为 18 位雪花 ID（{@link com.acme.scaffold.common.IdGenerator}），
 * 超出 JavaScript {@code Number.MAX_SAFE_INTEGER}（2^53）。修复前 VO 以 JSON number 上链，
 * 前端 {@code JSON.parse} 后精度丢失，回传的 id 与库中记录失配，导致创建后「启动/暂停/编辑/删除/日志」
 * 全部提示「定时任务不存在」。修复后 id 统一按字符串序列化（{@code @JsonSerialize(ToStringSerializer)}），
 * 本测试锁定该线上契约并回归全链路操作。
 *
 * <p>运行：{@code mvn -o test -Dtest=JobManagementIT}
 * （integration profile，自动连接本机 MySQL {@code java_admin} 库并由 Flyway 建表）。
 *
 * <p>可选：{@code -DdumpClassPath=/tmp/app-classpath.txt} 导出运行时依赖 classpath，
 * 供沙箱内在缺少 spring-boot-maven-plugin 依赖时以 {@code java -cp} 直启服务验证。
 */
class JobManagementIT extends BaseIntegrationTest {

    /** 匹配 JSON 字符串形态的数字 id（17~19 位雪花 ID）。 */
    private static final Pattern STRING_ID = Pattern.compile("\"id\"\\s*:\\s*\"(\\d{17,19})\"");

    /** 创建接口返回 Result&lt;String&gt;，id 位于 data 字段。 */
    private static final Pattern STRING_DATA_ID = Pattern.compile("\"data\"\\s*:\\s*\"(\\d{17,19})\"");

    @Autowired
    private TestRestTemplate rest;

    @BeforeAll
    static void dumpClasspathIfRequested() {
        String target = System.getProperty("dumpClassPath");
        if (target == null) {
            return;
        }
        try {
            Files.writeString(Path.of(target), System.getProperty("java.class.path"));
        } catch (Exception ignored) {
            // 仅验证辅助手段，失败不影响测试
        }
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return h;
    }

    private String login() {
        System.out.println("[DEBUG] baseUrl=" + baseUrl() + " port=" + port);
        try {
            System.out.println("[DEBUG] livez=" + rest.getForEntity(baseUrl() + "/livez", String.class).getStatusCode());
        } catch (Exception e) {
            System.out.println("[DEBUG] livez ex=" + e.getMessage());
        }
        try {
            ResponseEntity<String> health = rest.exchange(baseUrl() + "/actuator/health", HttpMethod.GET,
                    new HttpEntity<>(authHeaders(null)), String.class);
            System.out.println("[DEBUG] health status=" + health.getStatusCode() + " body=" + health.getBody());
        } catch (Exception e) {
            System.out.println("[DEBUG] health ex=" + e.getMessage());
        }
        ResponseEntity<String> raw = rest.exchange(baseUrl() + "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "admin", "password", "admin123"), authHeaders(null)),
                String.class);
        System.out.println("[DEBUG] login status=" + raw.getStatusCode() + " headers=" + raw.getHeaders()
                + " body=" + raw.getBody());
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "admin", "password", "admin123"), authHeaders(null)),
                Result.class);
        assertNotNull(resp.getBody(), "登录响应不应为空");
        assertTrue(resp.getBody().isSuccess(), "登录应成功: " + resp.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().data();
        return (String) data.get("accessToken");
    }

    @Test
    void createThenStartDetailLogsPauseDeleteRoundTrip() {
        String token = login();
        assertNotNull(token, "应返回 accessToken");

        String jobName = "IT验证任务-" + System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobName", jobName);
        payload.put("jobGroup", "it");
        payload.put("taskKey", "demo-echo");
        payload.put("triggerType", "CRON");
        payload.put("cronExpression", "0 0 4 * * *");
        payload.put("jobParams", "{\"msg\":\"it\"}");
        payload.put("remark", "job id precision regression");

        // 1. 创建：原始报文中的 id 必须为 JSON 字符串（修复点：防前端精度丢失）
        ResponseEntity<String> created = rest.exchange(baseUrl() + "/api/jobs", HttpMethod.POST,
                new HttpEntity<>(payload, authHeaders(token)), String.class);
        assertEquals(200, created.getStatusCode().value(), "创建 HTTP 状态");
        assertNotNull(created.getBody());
        assertTrue(created.getBody().contains("\"code\":\"0\""), "创建应成功: " + created.getBody());
        Matcher m = STRING_DATA_ID.matcher(created.getBody());
        assertTrue(m.find(), "创建响应 data 应为字符串形态 id: " + created.getBody());
        String id = m.group(1);

        // 2. 列表：id 同为字符串，且能按名称命中
        ResponseEntity<String> list = rest.exchange(
                baseUrl() + "/api/jobs?jobName=" + jobName + "&size=10",
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), String.class);
        assertEquals(200, list.getStatusCode().value(), "列表 HTTP 状态");
        assertTrue(list.getBody().contains("\"id\":\"" + id + "\""),
                "列表应含字符串 id " + id + ": " + list.getBody());

        // 3. 启动（修复前此步即提示「定时任务不存在」）
        assertOk(rest.exchange(baseUrl() + "/api/jobs/" + id + "/start", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), String.class), "启动");

        // 4. 详情与执行日志
        ResponseEntity<String> detail = rest.exchange(baseUrl() + "/api/jobs/" + id,
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), String.class);
        assertEquals(200, detail.getStatusCode().value(), "详情 HTTP 状态");
        assertTrue(detail.getBody().contains("\"id\":\"" + id + "\""), "详情应含字符串 id");

        ResponseEntity<String> logs = rest.exchange(
                baseUrl() + "/api/jobs/" + id + "/logs?page=1&size=5",
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), String.class);
        assertEquals(200, logs.getStatusCode().value(), "日志 HTTP 状态");
        assertTrue(logs.getBody().contains("\"code\":\"0\""), "日志查询应成功: " + logs.getBody());

        // 5. 暂停与删除
        assertOk(rest.exchange(baseUrl() + "/api/jobs/" + id + "/pause", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), String.class), "暂停");
        assertOk(rest.exchange(baseUrl() + "/api/jobs/" + id, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), String.class), "删除");

        // 6. 删除后按同一 id 再操作应业务失败（证明 id 语义精确，未误命中其他记录）
        ResponseEntity<String> afterDelete = rest.exchange(baseUrl() + "/api/jobs/" + id + "/start",
                HttpMethod.POST, new HttpEntity<>(authHeaders(token)), String.class);
        assertEquals(200, afterDelete.getStatusCode().value(), "删除后启动 HTTP 状态");
        assertFalse(afterDelete.getBody().contains("\"code\":\"0\""),
                "删除后启动应业务失败: " + afterDelete.getBody());
    }

    private void assertOk(ResponseEntity<String> resp, String op) {
        assertEquals(200, resp.getStatusCode().value(), op + " HTTP 状态");
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("\"code\":\"0\""), op + "应成功: " + resp.getBody());
    }
}
