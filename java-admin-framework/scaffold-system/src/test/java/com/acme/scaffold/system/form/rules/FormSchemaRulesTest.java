package com.acme.scaffold.system.form.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormSchemaRules} 离线单测：字段类型/绑定目标归一、Schema 结构校验
 * （version/title/fields/字段键/类型白名单/options/长度属性）。全部为纯函数断言，
 * 不依赖 Spring 上下文与数据库。
 */
class FormSchemaRulesTest {

    /** 合法的最小 Schema（覆盖 INPUT/TEXTAREA/SELECT/SWITCH/DATE 五类控件）。 */
    private static final String VALID_SCHEMA = """
            {
              "version": 1,
              "title": "联系反馈",
              "fields": [
                {"key": "name", "label": "姓名", "type": "INPUT", "required": true, "maxLength": 32},
                {"key": "channel", "label": "反馈渠道", "type": "SELECT", "required": true,
                 "options": [{"label": "电话", "value": "PHONE"}, {"label": "邮件", "value": "EMAIL"}]},
                {"key": "urgent", "label": "是否紧急", "type": "SWITCH"},
                {"key": "content", "label": "反馈内容", "type": "TEXTAREA", "required": true,
                 "minLength": 10, "maxLength": 500, "rows": 4},
                {"key": "followUpAt", "label": "期望回访日期", "type": "DATE"}
              ]
            }
            """;

    // ------------------------------------------------------------------
    // 归一
    // ------------------------------------------------------------------

    @Test
    @DisplayName("normalizeType：白名单七类大小写不敏感，非法/空返回 null")
    void normalizeType() {
        assertEquals("INPUT", FormSchemaRules.normalizeType("input"));
        assertEquals("TEXTAREA", FormSchemaRules.normalizeType(" TextArea "));
        assertEquals("NUMBER", FormSchemaRules.normalizeType("NUMBER"));
        assertEquals("SELECT", FormSchemaRules.normalizeType("select"));
        assertEquals("RADIO", FormSchemaRules.normalizeType("RADIO"));
        assertEquals("SWITCH", FormSchemaRules.normalizeType("switch"));
        assertEquals("DATE", FormSchemaRules.normalizeType("date"));
        assertNull(FormSchemaRules.normalizeType("COLOR_PICKER"));
        assertNull(FormSchemaRules.normalizeType(""));
        assertNull(FormSchemaRules.normalizeType(null));
    }

    @Test
    @DisplayName("normalizeTargetType：白名单三值；null/空按 NONE 兜底，非法返回 null")
    void normalizeTargetType() {
        assertEquals("NONE", FormSchemaRules.normalizeTargetType(null));
        assertEquals("NONE", FormSchemaRules.normalizeTargetType("  "));
        assertEquals("BUSINESS_TABLE", FormSchemaRules.normalizeTargetType("business_table"));
        assertEquals("FORM_SUBMISSION", FormSchemaRules.normalizeTargetType("FORM_SUBMISSION"));
        assertNull(FormSchemaRules.normalizeTargetType("OTHER"));
    }

    // ------------------------------------------------------------------
    // Schema 校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("validate：合法 Schema 零错误（V47 试点表单同款结构）")
    void validate_validSchema() {
        assertTrue(FormSchemaRules.validate(VALID_SCHEMA).isEmpty());
    }

    @Test
    @DisplayName("validate：空/非 JSON/非对象均报错")
    void validate_malformed() {
        assertEquals(List.of("Schema 不能为空"), FormSchemaRules.validate(null));
        assertEquals(List.of("Schema 不能为空"), FormSchemaRules.validate("   "));
        assertEquals(List.of("Schema 不是合法 JSON"), FormSchemaRules.validate("{not json"));
        assertFalse(FormSchemaRules.validate("[1,2,3]").isEmpty());
    }

    @Test
    @DisplayName("validateSchema：version 缺失或非受支持版本报错")
    void validateSchema_version() {
        assertTrue(errorsOf("""
                {"fields": [{"key": "a", "label": "A", "type": "INPUT"}]}
                """).stream().anyMatch(e -> e.contains("version")));

        assertTrue(errorsOf("""
                {"version": 2, "fields": [{"key": "a", "label": "A", "type": "INPUT"}]}
                """).stream().anyMatch(e -> e.contains("version")));
    }

    @Test
    @DisplayName("validateSchema：fields 缺失/空数组/超上限报错；title 非字符串报错")
    void validateSchema_fields() {
        assertTrue(errorsOf("""
                {"version": 1}
                """).stream().anyMatch(e -> e.contains("fields")));

        assertTrue(errorsOf("""
                {"version": 1, "fields": []}
                """).stream().anyMatch(e -> e.contains("fields")));

        assertTrue(errorsOf("""
                {"version": 1, "title": 5, "fields": [{"key": "a", "label": "A", "type": "INPUT"}]}
                """).stream().anyMatch(e -> e.contains("title")));
    }

    @Test
    @DisplayName("validateSchema：字段键必填、须为驼峰标识符、租户内不得重复")
    void validateSchema_fieldKey() {
        List<String> missing = errorsOf("""
                {"version": 1, "fields": [{"label": "A", "type": "INPUT"}]}
                """);
        assertTrue(missing.stream().anyMatch(e -> e.contains("key 必填")));

        List<String> badPattern = errorsOf("""
                {"version": 1, "fields": [{"key": "1abc", "label": "A", "type": "INPUT"}]}
                """);
        assertTrue(badPattern.stream().anyMatch(e -> e.contains("驼峰标识符")));

        List<String> dup = errorsOf("""
                {"version": 1, "fields": [
                  {"key": "name", "label": "A", "type": "INPUT"},
                  {"key": "name", "label": "B", "type": "INPUT"}]}
                """);
        assertTrue(dup.stream().anyMatch(e -> e.contains("key 重复：name")));
    }

    @Test
    @DisplayName("validateSchema：type 必填且须在白名单；label 必填")
    void validateSchema_typeAndLabel() {
        assertTrue(errorsOf("""
                {"version": 1, "fields": [{"key": "a", "label": "A"}]}
                """).stream().anyMatch(e -> e.contains("type 必填")));

        assertTrue(errorsOf("""
                {"version": 1, "fields": [{"key": "a", "type": "INPUT"}]}
                """).stream().anyMatch(e -> e.contains("label 必填")));

        assertTrue(errorsOf("""
                {"version": 1, "fields": [{"key": "a", "label": "A", "type": "COLOR_PICKER"}]}
                """).stream().anyMatch(e -> e.contains("type 非法")));
    }

    @Test
    @DisplayName("validateSchema：SELECT/RADIO 必须声明非空 options，选项 label/value 均非空")
    void validateSchema_options() {
        assertTrue(errorsOf("""
                {"version": 1, "fields": [{"key": "c", "label": "C", "type": "SELECT"}]}
                """).stream().anyMatch(e -> e.contains("options 对 SELECT 必填")));

        assertTrue(errorsOf("""
                {"version": 1, "fields": [{"key": "c", "label": "C", "type": "RADIO", "options": []}]}
                """).stream().anyMatch(e -> e.contains("options 对 RADIO 必填")));

        assertTrue(errorsOf("""
                {"version": 1, "fields": [{"key": "c", "label": "C", "type": "SELECT",
                 "options": [{"label": "电话", "value": "PHONE"}, {"label": "", "value": "EMAIL"}]}]}
                """).stream().anyMatch(e -> e.contains("options[1] 须包含非空 label/value")));

        assertTrue(errorsOf("""
                {"version": 1, "fields": [{"key": "c", "label": "C", "type": "SELECT",
                 "options": [{"label": "电话", "value": "PHONE"}, {"label": "邮件", "value": ""}]}]}
                """).stream().anyMatch(e -> e.contains("options[1] 须包含非空 label/value")));
    }

    @Test
    @DisplayName("validateSchema：长度属性须为非负且不超上限，minLength 不得大于 maxLength")
    void validateSchema_lengthAttrs() {
        assertTrue(errorsOf("""
                {"version": 1, "fields": [{"key": "a", "label": "A", "type": "INPUT", "maxLength": -1}]}
                """).stream().anyMatch(e -> e.contains("maxLength 须为")));

        assertTrue(errorsOf("""
                {"version": 1, "fields": [{"key": "a", "label": "A", "type": "INPUT", "rows": 99999}]}
                """).stream().anyMatch(e -> e.contains("rows 须为")));

        assertTrue(errorsOf("""
                {"version": 1, "fields": [{"key": "a", "label": "A", "type": "INPUT",
                 "minLength": 20, "maxLength": 10}]}
                """).stream().anyMatch(e -> e.contains("minLength 不得大于 maxLength")));

        assertTrue(errorsOf("""
                {"version": 1, "fields": [{"key": "a", "label": "A", "type": "INPUT", "maxLength": "32"}]}
                """).stream().anyMatch(e -> e.contains("maxLength 须为")));
    }

    @Test
    @DisplayName("validateSchema：字段节点非对象报错")
    void validateSchema_fieldNotObject() {
        assertTrue(errorsOf("""
                {"version": 1, "fields": ["oops"]}
                """).stream().anyMatch(e -> e.contains("fields[0] 必须为 JSON 对象")));
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private static List<String> errorsOf(String schemaJson) {
        return FormSchemaRules.validate(schemaJson);
    }
}
