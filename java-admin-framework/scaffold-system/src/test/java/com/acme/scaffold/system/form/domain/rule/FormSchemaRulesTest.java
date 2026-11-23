package com.acme.scaffold.system.form.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 表单 Schema 纯函数规则单测（E6）：归一、版本/字段/options/长度属性校验。 */
class FormSchemaRulesTest {

    private static final String VALID = """
            {"version":1,"title":"表单","fields":[
              {"key":"name","label":"姓名","type":"input","required":true,"maxLength":32},
              {"key":"channel","label":"渠道","type":"SELECT","options":[{"label":"电话","value":"PHONE"}]}
            ]}""";

    @Test
    void shouldNormalizeTypesAndTargets() {
        assertEquals("INPUT", FormSchemaRules.normalizeType(" input "));
        assertEquals("SELECT", FormSchemaRules.normalizeType("Select"));
        assertNull(FormSchemaRules.normalizeType("BOGUS"));
        assertNull(FormSchemaRules.normalizeType(null));
        assertEquals("NONE", FormSchemaRules.normalizeTargetType(null));
        assertEquals("FORM_SUBMISSION", FormSchemaRules.normalizeTargetType(" form_submission "));
        assertNull(FormSchemaRules.normalizeTargetType("bogus"));
    }

    @Test
    void shouldAcceptValidSchema() {
        assertTrue(FormSchemaRules.validate(VALID).isEmpty());
    }

    @Test
    void shouldRejectEmptyNonJsonAndNonObject() {
        assertTrue(FormSchemaRules.validate(null).get(0).contains("不能为空"));
        assertTrue(FormSchemaRules.validate("not-json").get(0).contains("合法 JSON"));
        assertTrue(FormSchemaRules.validateSchema(new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode())
                .get(0).contains("必须为 JSON 对象"));
    }

    @Test
    void shouldAllowMissingOptionalTitle() {
        assertTrue(FormSchemaRules.validate(
                "{\"version\":1,\"fields\":[{\"key\":\"x\",\"label\":\"x\",\"type\":\"INPUT\"}]}").isEmpty());
        assertTrue(FormSchemaRules.validate(
                "{\"version\":1,\"title\":123,\"fields\":[{\"key\":\"x\",\"label\":\"x\",\"type\":\"INPUT\"}]}")
                .stream().anyMatch(e -> e.contains("title 必须为字符串")));
    }

    @Test
    void shouldRejectBadVersionAndFields() {
        assertTrue(FormSchemaRules.validate("{\"version\":2,\"fields\":[{}]}").get(0).contains("version"));
        assertTrue(FormSchemaRules.validate("{\"version\":1,\"fields\":[]}").get(0).contains("fields"));
        assertTrue(FormSchemaRules.validate("{\"version\":1}").get(0).contains("fields"));
    }

    @Test
    void shouldRejectBadFieldKeysAndTypes() {
        String bad = """
                {"version":1,"fields":[
                  {"key":"1bad","label":"x","type":"INPUT"},
                  {"key":"ok","label":"","type":"INPUT"},
                  {"key":"ok","label":"y","type":"BOGUS"},
                  {"key":"ok","label":"z"}
                ]}""";
        List<String> errors = FormSchemaRules.validate(bad);
        assertTrue(errors.stream().anyMatch(e -> e.contains("驼峰")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("label 必填")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("type 非法")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("type 必填")));
    }

    @Test
    void shouldRejectDuplicateKeys() {
        String dup = """
                {"version":1,"fields":[
                  {"key":"same","label":"a","type":"INPUT"},
                  {"key":"same","label":"b","type":"INPUT"}
                ]}""";
        assertTrue(FormSchemaRules.validate(dup).stream().anyMatch(e -> e.contains("重复")));
    }

    @Test
    void shouldRejectMissingOrEmptyOptions() {
        String noOptions = """
                {"version":1,"fields":[{"key":"c","label":"渠道","type":"SELECT"}]}""";
        assertTrue(FormSchemaRules.validate(noOptions).stream().anyMatch(e -> e.contains("options")));
        String badOption = """
                {"version":1,"fields":[{"key":"c","label":"渠道","type":"RADIO",
                  "options":[{"label":"","value":"A"}]}]}""";
        assertTrue(FormSchemaRules.validate(badOption).stream().anyMatch(e -> e.contains("非空 label/value")));
    }

    @Test
    void shouldRejectOutOfRangeLengthAttrs() {
        String tooLong = """
                {"version":1,"fields":[{"key":"x","label":"x","type":"INPUT","maxLength":4001}]}""";
        assertTrue(FormSchemaRules.validate(tooLong).stream().anyMatch(e -> e.contains("4000")));
        String inverted = """
                {"version":1,"fields":[{"key":"x","label":"x","type":"INPUT","minLength":10,"maxLength":5}]}""";
        assertTrue(FormSchemaRules.validate(inverted).stream().anyMatch(e -> e.contains("不得大于")));
    }

    @Test
    void shouldRejectTooManyFields() {
        var fields = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            if (i > 0) fields.append(',');
            fields.append("{\"key\":\"f").append(i).append("\",\"label\":\"x\",\"type\":\"INPUT\"}");
        }
        assertFalse(FormSchemaRules.validate("{\"version\":1,\"fields\":[" + fields + "]}").isEmpty());
    }
}
