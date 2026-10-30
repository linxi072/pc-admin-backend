package com.acme.scaffold.system.form.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormSubmissionRules} 离线单测：按 schema 字段校验提交值（必填 / 类型 / 长度 /
 * 选项白名单 / 未知字段拒绝 / 空值 / 非法 Schema 兜底）。全部为纯函数断言，
 * 不依赖 Spring 上下文与数据库。
 */
class FormSubmissionRulesTest {

    /** 合法 Schema（覆盖 INPUT/TEXTAREA/SELECT/SWITCH/DATE 五类控件 + 必填/长度/选项约束）。 */
    private static final String VALID_SCHEMA = """
            {
              "version": 1,
              "title": "联系反馈",
              "fields": [
                {"key": "name", "label": "姓名", "type": "INPUT", "required": true, "maxLength": 32},
                {"key": "mobile", "label": "手机号", "type": "INPUT", "required": true, "maxLength": 11},
                {"key": "channel", "label": "反馈渠道", "type": "SELECT", "required": true,
                 "options": [{"label": "电话", "value": "PHONE"}, {"label": "邮件", "value": "EMAIL"}]},
                {"key": "urgent", "label": "是否紧急", "type": "SWITCH"},
                {"key": "content", "label": "反馈内容", "type": "TEXTAREA", "required": true,
                 "minLength": 10, "maxLength": 500, "rows": 4},
                {"key": "followUpAt", "label": "期望回访日期", "type": "DATE"}
              ]
            }
            """;

    private static final Map<String, Object> VALID_VALUES = Map.of(
            "name", "张三",
            "mobile", "13800000000",
            "channel", "PHONE",
            "urgent", false,
            "content", "这是一条足够长的反馈内容用于覆盖最小长度校验",
            "followUpAt", "2026-11-01");

    @Test
    @DisplayName("validateSubmission：合法提交值零错误")
    void validSubmission() {
        assertTrue(FormSubmissionRules.validateSubmission(VALID_SCHEMA, VALID_VALUES).isEmpty());
    }

    @Test
    @DisplayName("validateSubmission：必填字段缺失报错（按 label 提示）")
    void requiredMissing() {
        Map<String, Object> v = new java.util.HashMap<>(VALID_VALUES);
        v.remove("name");
        assertTrue(FormSubmissionRules.validateSubmission(VALID_SCHEMA, v)
                .stream().anyMatch(e -> e.contains("姓名不能为空")));
    }

    @Test
    @DisplayName("validateSubmission：INPUT/TEXTAREA 非文本报错")
    void textTypeMismatch() {
        Map<String, Object> v = new java.util.HashMap<>(VALID_VALUES);
        v.put("content", 12345);
        assertTrue(FormSubmissionRules.validateSubmission(VALID_SCHEMA, v)
                .stream().anyMatch(e -> e.contains("反馈内容应为文本")));
    }

    @Test
    @DisplayName("validateSubmission：文本长度越界（min/maxLength）报错")
    void textLengthBounds() {
        Map<String, Object> v = new java.util.HashMap<>(VALID_VALUES);
        v.put("content", "太短");
        assertTrue(FormSubmissionRules.validateSubmission(VALID_SCHEMA, v)
                .stream().anyMatch(e -> e.contains("反馈内容至少 10 个字符")));

        Map<String, Object> v2 = new java.util.HashMap<>(VALID_VALUES);
        v2.put("content", "x".repeat(501));
        assertTrue(FormSubmissionRules.validateSubmission(VALID_SCHEMA, v2)
                .stream().anyMatch(e -> e.contains("反馈内容不超过 500 个字符")));
    }

    @Test
    @DisplayName("validateSubmission：SELECT 取值不在选项白名单报错")
    void selectOutOfWhitelist() {
        Map<String, Object> v = new java.util.HashMap<>(VALID_VALUES);
        v.put("channel", "WECHAT");
        assertTrue(FormSubmissionRules.validateSubmission(VALID_SCHEMA, v)
                .stream().anyMatch(e -> e.contains("反馈渠道取值非法")));
    }

    @Test
    @DisplayName("validateSubmission：SWITCH 非布尔报错")
    void switchNotBoolean() {
        Map<String, Object> v = new java.util.HashMap<>(VALID_VALUES);
        v.put("urgent", "yes");
        assertTrue(FormSubmissionRules.validateSubmission(VALID_SCHEMA, v)
                .stream().anyMatch(e -> e.contains("是否紧急应为布尔值")));
    }

    @Test
    @DisplayName("validateSubmission：DATE 非 yyyy-MM-dd 报错")
    void dateInvalidFormat() {
        Map<String, Object> v = new java.util.HashMap<>(VALID_VALUES);
        v.put("followUpAt", "2026/11/01");
        assertTrue(FormSubmissionRules.validateSubmission(VALID_SCHEMA, v)
                .stream().anyMatch(e -> e.contains("期望回访日期日期格式应为 yyyy-MM-dd")));
    }

    @Test
    @DisplayName("validateSubmission：未知字段（不在 schema 中）一律拒绝")
    void unknownFieldRejected() {
        Map<String, Object> v = new java.util.HashMap<>(VALID_VALUES);
        v.put("hack", "x");
        assertTrue(FormSubmissionRules.validateSubmission(VALID_SCHEMA, v)
                .stream().anyMatch(e -> e.contains("未知字段：hack")));
    }

    @Test
    @DisplayName("validateSubmission：提交值为 null/空 报错")
    void emptyValues() {
        assertTrue(FormSubmissionRules.validateSubmission(VALID_SCHEMA, null)
                .stream().anyMatch(e -> e.contains("提交数据不能为空")));
        assertTrue(FormSubmissionRules.validateSubmission(VALID_SCHEMA, Map.of())
                .stream().anyMatch(e -> e.contains("提交数据不能为空")));
    }

    @Test
    @DisplayName("validateSubmission：Schema 非法/空 兜底报错，不抛异常")
    void invalidSchemaSafe() {
        assertFalse(FormSubmissionRules.validateSubmission(null, VALID_VALUES).isEmpty());
        assertFalse(FormSubmissionRules.validateSubmission("{not json", VALID_VALUES).isEmpty());
        assertTrue(FormSubmissionRules.validateSubmission("{not json", VALID_VALUES)
                .stream().anyMatch(e -> e.contains("Schema 非法")));
    }
}
