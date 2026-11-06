package com.acme.scaffold.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link JsonTruncator} 单测：覆盖三级降级，核心不变量为
 * 「输出必为合法 JSON 且长度 ≤ max」。
 */
class JsonTruncatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void nullReturnsNull() {
        assertEquals(null, JsonTruncator.safeTruncate(null, 2000, mapper));
    }

    @Test
    void shortInputReturnedAsIs() {
        String s = "{\"a\":1}";
        assertEquals(s, JsonTruncator.safeTruncate(s, 2000, mapper));
    }

    @Test
    void validObjectOverLimitCompactedThenTrimmedStillValid() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 200; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"k").append(i).append("\":\"v").append(i).append("\"");
        }
        sb.append("}");
        String longJson = sb.toString();
        assertTrue(longJson.length() > 100);

        String result = JsonTruncator.safeTruncate(longJson, 100, mapper);

        assertNotNull(result);
        assertTrue(result.length() <= 100, "result length=" + result.length());
        assertValidJson(result); // 必为合法 JSON
    }

    @Test
    void arrayOverLimitTrimsTailElements() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(i);
        }
        sb.append("]");
        String longArr = sb.toString();

        String result = JsonTruncator.safeTruncate(longArr, 50, mapper);

        assertTrue(result.length() <= 50);
        int count = parseLenient(result).size();
        assertTrue(count < 100 && count > 0, "remaining elements=" + count);
    }

    @Test
    void shortInvalidJsonFallsBackToWrappedScalar() {
        // D2 回归：短小但非法的 JSON（如被截断的 "{"）此前被 early-return 直接放过，
        // 写入 MySQL JSON 列会触发 3140 拒绝。修复后必须退化为合法 JSON 字符串标量。
        String broken = "{"; // 长度 1 << 2000，但非法 JSON
        String result = JsonTruncator.safeTruncate(broken, 2000, mapper);

        assertNotNull(result);
        assertTrue(result.startsWith("\"") && result.endsWith("\""), "短非法 JSON 应退化为字符串标量, result=" + result);
        assertValidJson(result); // 必为合法 JSON，否则 MySQL 3140 会拒绝
    }

    @Test
    void invalidJsonFallsBackToWrappedScalar() {
        // 未闭合且超过上限，进入文本兜底：包装为 JSON 字符串标量
        String broken = "{\"a\":1".repeat(20); // 非合法 JSON，长度 140 > 50
        String result = JsonTruncator.safeTruncate(broken, 50, mapper);

        assertTrue(result.length() <= 50);
        // 兜底包装为 JSON 字符串标量，仍是合法 JSON，且不破坏列类型
        assertTrue(result.startsWith("\"") && result.endsWith("\""), "result=" + result);
        assertValidJson(result);
    }

    @Test
    void deeplyNestedObjectTrimsToFit() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 50; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"field").append(i).append("\":\"some-value\"");
        }
        sb.append("}");
        String result = JsonTruncator.safeTruncate(sb.toString(), 80, mapper);
        assertTrue(result.length() <= 80);
        assertValidJson(result);
    }

    /** 解析结果并断言其为合法 JSON（readTree 抛受检异常，此处统一捕获）。 */
    private void assertValidJson(String s) {
        parseLenient(s);
    }

    /** 解析为 JsonNode，解析失败则直接令用例失败。 */
    private com.fasterxml.jackson.databind.JsonNode parseLenient(String s) {
        try {
            return mapper.readTree(s);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            fail("结果不是合法 JSON: " + s);
            return null; // 不可达，fail 已中断
        }
    }
}
