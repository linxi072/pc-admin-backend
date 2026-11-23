package com.acme.scaffold.system.form.domain.rule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 表单提交校验规则单测（代码审查专项 III）：覆盖必填/类型/长度/未知字段，
 * 并回归「选项缺 value 时字符串 'null' 被误判为合法值」的缺陷。
 */
class FormSubmissionRulesTest {

    private static String schemaWithOptions(boolean includeValue) {
        // 一个 SELECT 字段，选项可带或不带 value
        String option = includeValue ? "{\"label\":\"A\",\"value\":\"a\"}" : "{\"label\":\"A\"}";
        return "{\"fields\":[{\"key\":\"choice\",\"label\":\"选择\",\"type\":\"SELECT\","
                + "\"required\":true,\"options\":[" + option + "]}]}";
    }

    @Test
    void validSelectValueShouldPass() {
        List<String> errors = FormSubmissionRules.validateSubmission(schemaWithOptions(true),
                Map.of("choice", "a"));
        assertTrue(errors.isEmpty(), "合法选项值应通过: " + errors);
    }

    @Test
    void missingOptionValueShouldNotMakeStringNullLegal() {
        // 回归缺陷：选项缺 value 时，原实现 String.valueOf(asText(null)) 产生 "null"
        // 字面量混入白名单，导致提交字符串 "null" 被误判为合法选项。
        List<String> errors = FormSubmissionRules.validateSubmission(schemaWithOptions(false),
                Map.of("choice", "null"));
        assertFalse(errors.isEmpty(), "缺 value 的选项不得让字符串 'null' 通过校验");
        assertTrue(errors.stream().anyMatch(e -> e.contains("取值非法")));
    }

    @Test
    void unknownFieldShouldBeRejected() {
        List<String> errors = FormSubmissionRules.validateSubmission(
                "{\"fields\":[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"INPUT\"}]}",
                Map.of("name", "张三", "extra", "x"));
        assertTrue(errors.stream().anyMatch(e -> e.contains("未知字段")));
    }

    @Test
    void requiredFieldShouldBeRejectedWhenBlank() {
        List<String> errors = FormSubmissionRules.validateSubmission(
                "{\"fields\":[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"INPUT\",\"required\":true}]}",
                Map.of("name", "  "));
        assertTrue(errors.stream().anyMatch(e -> e.contains("不能为空")));
    }

    @Test
    void malformedSchemaShouldBeRejected() {
        assertFalse(FormSubmissionRules.validateSubmission("not-json", Map.of("x", "y")).isEmpty());
    }
}
