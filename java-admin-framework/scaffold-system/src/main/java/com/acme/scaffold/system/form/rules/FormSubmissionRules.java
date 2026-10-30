package com.acme.scaffold.system.form.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表单提交值校验纯函数（V53 / M22 正式立项）。
 *
 * <p>无副作用、不依赖 Spring 上下文与数据库，可离线单测；供
 * {@link com.acme.scaffold.system.form.FormSubmissionService#submit} 在落库前校验提交值，
 * 保证「渲染契约」与「提交校验」口径同源（均源自同一 schema）。</p>
 *
 * <p><b>校验维度</b>：</p>
 * <ul>
 *   <li>整体：提交值非空、Schema 可解析且为合法结构；</li>
 *   <li>逐字段：必填、类型（INPUT/TEXTAREA→文本、NUMBER→数字、SELECT/RADIO→选项白名单、
 *       SWITCH→布尔、DATE→yyyy-MM-dd）、文本长度（min/maxLength）；</li>
 *   <li>越界防御：提交值中出现 schema 未声明的「未知字段」一律拒绝，防止 data_json 注入无关键。</li>
 * </ul>
 */
public final class FormSubmissionRules {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FormSubmissionRules() {
    }

    /**
     * 校验提交值相对 Schema 的合法性。返回错误列表，空列表表示通过。
     *
     * @param schemaJson 表单 Schema（与渲染面同一来源；非法时整次提交被拒）
     * @param values     提交值（字段键 → 值），允许为 null（作为一条错误返回）
     */
    public static List<String> validateSubmission(String schemaJson, Map<String, Object> values) {
        List<String> errors = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            errors.add("提交数据不能为空");
            return errors;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(schemaJson);
        } catch (Exception e) {
            errors.add("表单 Schema 非法，无法校验提交值");
            return errors;
        }
        if (root == null || !root.isObject() || !root.path("fields").isArray()) {
            errors.add("表单 Schema 非法，无法校验提交值");
            return errors;
        }

        JsonNode fields = root.path("fields");
        Set<String> knownKeys = new HashSet<>();
        for (int i = 0; i < fields.size(); i++) {
            JsonNode f = fields.get(i);
            if (f == null || !f.isObject()) {
                continue;
            }
            String key = f.path("key").asText(null);
            if (key == null || key.isBlank()) {
                continue;
            }
            knownKeys.add(key);
            errors.addAll(validateField(f, values.get(key)));
        }

        // 未知字段拒绝（防止提交值与 schema 不一致造成的注入/脏数据）
        for (String submittedKey : values.keySet()) {
            if (!knownKeys.contains(submittedKey)) {
                errors.add("未知字段：" + submittedKey);
            }
        }
        return errors;
    }

    /** 校验单个字段的提交值（必填 + 类型 + 文本长度）。 */
    private static List<String> validateField(JsonNode field, Object value) {
        List<String> errors = new ArrayList<>();
        String label = field.path("label").asText(field.path("key").asText("字段"));
        String type = FormSchemaRules.normalizeType(field.path("type").asText(null));
        boolean required = field.path("required").asBoolean(false);

        if (required && isEmpty(value)) {
            errors.add(label + "不能为空");
            return errors;
        }
        // 非必填且为空：跳过类型校验
        if (isEmpty(value)) {
            return errors;
        }

        switch (type) {
            case "INPUT", "TEXTAREA" -> errors.addAll(validateText(label, value, field));
            case "NUMBER" -> {
                if (!(value instanceof Number)) {
                    errors.add(label + "应为数字");
                }
            }
            case "SELECT", "RADIO" -> errors.addAll(validateOption(label, value, field));
            case "SWITCH" -> {
                if (!(value instanceof Boolean)) {
                    errors.add(label + "应为布尔值");
                }
            }
            case "DATE" -> errors.addAll(validateDate(label, value));
            default -> errors.add(label + "类型不支持校验：" + type);
        }
        return errors;
    }

    /** INPUT/TEXTAREA：值须为字符串且长度在 [minLength, maxLength] 内。 */
    private static List<String> validateText(String label, Object value, JsonNode field) {
        List<String> errors = new ArrayList<>();
        if (!(value instanceof String s)) {
            errors.add(label + "应为文本");
            return errors;
        }
        int len = s.length();
        JsonNode min = field.path("minLength");
        JsonNode max = field.path("maxLength");
        if (!min.isMissingNode() && !min.isNull() && min.canConvertToInt() && len < min.asInt()) {
            errors.add(label + "至少 " + min.asInt() + " 个字符");
        }
        if (!max.isMissingNode() && !max.isNull() && max.canConvertToInt() && len > max.asInt()) {
            errors.add(label + "不超过 " + max.asInt() + " 个字符");
        }
        return errors;
    }

    /** SELECT/RADIO：值须在 schema 声明的 options.value 白名单内。 */
    private static List<String> validateOption(String label, Object value, JsonNode field) {
        List<String> errors = new ArrayList<>();
        JsonNode options = field.path("options");
        if (!options.isArray() || options.isEmpty()) {
            errors.add(label + "缺少选项定义");
            return errors;
        }
        Set<String> allowed = new HashSet<>();
        for (JsonNode o : options) {
            allowed.add(String.valueOf(o.path("value").asText(null)));
        }
        if (!allowed.contains(String.valueOf(value))) {
            errors.add(label + "取值非法");
        }
        return errors;
    }

    /** DATE：值须为 yyyy-MM-dd 可解析日期字符串。 */
    private static List<String> validateDate(String label, Object value) {
        List<String> errors = new ArrayList<>();
        if (!(value instanceof String s) || s.isBlank()) {
            errors.add(label + "日期格式应为 yyyy-MM-dd");
            return errors;
        }
        try {
            LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            errors.add(label + "日期格式应为 yyyy-MM-dd");
        }
        return errors;
    }

    /** 判定提交值是否「空」：null / 空串 / 全空白。布尔与数字不存在空语义，非 null 即非空前置已由调用方担保。 */
    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isBlank();
        }
        return false;
    }
}
