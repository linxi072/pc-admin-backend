package com.acme.scaffold.system.openapi.rules;

import com.acme.scaffold.common.oauth2.OpenApiClientRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OpenApiClientRules} 离线单测：client_id 格式、scope 解析与归一、状态归一、
 * 密钥生成（字符集/长度/唯一性）、全字段结构校验。全部为纯函数断言，不触库。
 */
class OpenApiClientRulesTest {

    // ------------------------------------------------------------------
    // 归一
    // ------------------------------------------------------------------

    @Test
    @DisplayName("isValidClientId：字母/数字开头 8~64 位，可含中划线/下划线")
    void isValidClientId() {
        assertTrue(OpenApiClientRules.isValidClientId("acme-partner-01"));
        assertTrue(OpenApiClientRules.isValidClientId("1abc_def-ghij"));
        assertFalse(OpenApiClientRules.isValidClientId("short"));      // 5 位 < 8
        assertFalse(OpenApiClientRules.isValidClientId("-leading"));   // 非字母/数字开头
        assertFalse(OpenApiClientRules.isValidClientId("has space 1234"));
        assertFalse(OpenApiClientRules.isValidClientId("X".repeat(65))); // 65 位 > 64
        assertFalse(OpenApiClientRules.isValidClientId(null));
    }

    @Test
    @DisplayName("normalizeStatus：白名单两值大小写不敏感，非法返回 null")
    void normalizeStatus() {
        assertEquals("ENABLED", OpenApiClientRules.normalizeStatus("enabled"));
        assertEquals("DISABLED", OpenApiClientRules.normalizeStatus(" Disabled "));
        assertNull(OpenApiClientRules.normalizeStatus("PAUSED"));
        assertNull(OpenApiClientRules.normalizeStatus(null));
    }

    @Test
    @DisplayName("parseScopes：逗号/分号/空白分隔去空白去重保序，空串返回空集")
    void parseScopes() {
        assertEquals(List.of("read", "write", "report:export"),
                List.copyOf(OpenApiClientRules.parseScopes("read, write ; report:export read")));
        assertEquals(Set.of(), OpenApiClientRules.parseScopes(""));
        assertEquals(Set.of(), OpenApiClientRules.parseScopes(null));
    }

    @Test
    @DisplayName("normalizeScopes：规范化逗号分隔严格保序（按声明顺序）；空回落默认 read")
    void normalizeScopes() {
        assertEquals("write,read", OpenApiClientRules.normalizeScopes(" write, read "));
        assertEquals("read", OpenApiClientRules.normalizeScopes(null));
        assertEquals("read", OpenApiClientRules.normalizeScopes("  "));
    }

    // ------------------------------------------------------------------
    // 密钥生成
    // ------------------------------------------------------------------

    @Test
    @DisplayName("generateSecret：Base64 URL 无填充 43 字符，两次生成不重复")
    void generateSecret() {
        String a = OpenApiClientRules.generateSecret();
        String b = OpenApiClientRules.generateSecret();
        assertEquals(43, a.length());
        assertTrue(a.matches("^[A-Za-z0-9_-]{43}$"), "应为 URL 安全无填充字符集：" + a);
        assertNotEquals(a, b);
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("validate：合法入参零错误")
    void validate_valid() {
        assertTrue(OpenApiClientRules.validate(
                "acme-partner-01", "合作伙伴系统", "read,write", "ENABLED",
                LocalDateTime.now().plusDays(30)).isEmpty());
        assertTrue(OpenApiClientRules.validate(
                "acme-partner-01", "合作伙伴系统", "read", null, null).isEmpty());
    }

    @Test
    @DisplayName("validate：client_id 格式非法/缺失报错")
    void validate_clientId() {
        assertTrue(OpenApiClientRules.validate(
                "bad", "名称", "read", null, null).stream().anyMatch(e -> e.contains("客户端 ID")));
        assertTrue(OpenApiClientRules.validate(
                null, "名称", "read", null, null).stream().anyMatch(e -> e.contains("客户端 ID 必填")));
    }

    @Test
    @DisplayName("validate：名称必填与长度上限")
    void validate_clientName() {
        assertTrue(OpenApiClientRules.validate(
                "acme-partner-01", "  ", "read", null, null).stream().anyMatch(e -> e.contains("客户端名称必填")));
        assertTrue(OpenApiClientRules.validate(
                "acme-partner-01", "N".repeat(129), "read", null, null).stream().anyMatch(e -> e.contains("128")));
    }

    @Test
    @DisplayName("validate：scope 必填/超个数/单项非法")
    void validate_scopes() {
        assertTrue(OpenApiClientRules.validate(
                "acme-partner-01", "名称", "  ", null, null).stream().anyMatch(e -> e.contains("授权范围必填")));

        List<String> tooMany = OpenApiClientRules.validate(
                "acme-partner-01", "名称",
                "s1,s2,s3,s4,s5,s6,s7,s8,s9,s10,s11", null, null);
        assertTrue(tooMany.stream().anyMatch(e -> e.contains("不超过 10 个")));

        assertTrue(OpenApiClientRules.validate(
                "acme-partner-01", "名称", "Read_Write", null, null)
                .stream().anyMatch(e -> e.contains("scope 非法")));
    }

    @Test
    @DisplayName("validate：状态非法与过期时间须晚于当前")
    void validate_statusAndExpiry() {
        assertTrue(OpenApiClientRules.validate(
                "acme-partner-01", "名称", "read", "PAUSED", null)
                .stream().anyMatch(e -> e.contains("状态非法")));

        assertTrue(OpenApiClientRules.validate(
                "acme-partner-01", "名称", "read", null, LocalDateTime.now().minusMinutes(1))
                .stream().anyMatch(e -> e.contains("过期时间须晚于")));
    }
}
