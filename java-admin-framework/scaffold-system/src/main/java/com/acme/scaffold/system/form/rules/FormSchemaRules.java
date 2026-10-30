package com.acme.scaffold.system.form.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 表单 Schema 纯函数规则（V47 低代码表单试点 / M22 spike）。
 *
 * <p>无副作用、不依赖 Spring 上下文与数据库，可离线单测；供
 * {@link com.acme.scaffold.system.form.FormDefinitionService}（落库前校验）与
 * {@link com.acme.scaffold.system.form.FormRenderController}（下发前复核）共用，
 * 保证「存什么、下发什么」口径同源。</p>
 *
 * <p><b>Schema 契约</b>（spike 最小集）：
 * <pre>
 * {
 *   "version": 1,                 // 必填，schema 版本（当前仅支持 1）
 *   "title": "表单标题",           // 可选
 *   "fields": [                   // 必填非空
 *     { "key": "name", "label": "姓名", "type": "INPUT", "required": true,
 *       "maxLength": 32, "placeholder": "请输入姓名" },
 *     { "key": "channel", "label": "渠道", "type": "SELECT", "required": true,
 *       "options": [ { "label": "电话", "value": "PHONE" } ] }
 *   ]
 * }
 * </pre>
 * 字段类型白名单：INPUT / TEXTAREA / NUMBER / SELECT / RADIO / SWITCH / DATE；
 * SELECT / RADIO 必须声明非空 {@code options}（label + value 均非空）。</p>
 */
public final class FormSchemaRules {

    /** 支持的字段类型（与前端 DynamicForm.vue 渲染器白名单严格一致）。 */
    public static final Set<String> SUPPORTED_TYPES = Set.of(
            "INPUT", "TEXTAREA", "NUMBER", "SELECT", "RADIO", "SWITCH", "DATE");

    /** 必须声明 options 的类型。 */
    public static final Set<String> OPTION_TYPES = Set.of("SELECT", "RADIO");

    /** 当前支持的 schema 版本。 */
    public static final int SUPPORTED_VERSION = 1;

    /** 字段键格式：驼峰标识符（字母开头，可含数字/下划线）。 */
    public static final String FIELD_KEY_PATTERN = "^[a-zA-Z][a-zA-Z0-9_]*$";

    /** 单表单字段数上限（防异常大 schema 拖垮渲染与校验）。 */
    public static final int MAX_FIELDS = 100;

    /** 长度属性上限（maxLength/minLength/rows）。 */
    public static final int MAX_LENGTH_LIMIT = 4000;

    /** 绑定目标占位取值（决策点①未定，仅作枚举约束）。 */
    public static final Set<String> TARGET_TYPES = Set.of("NONE", "BUSINESS_TABLE", "FORM_SUBMISSION");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FormSchemaRules() {
    }

    // ------------------------------------------------------------------
    // 归一
    // ------------------------------------------------------------------

    /** 归一字段类型：白名单内（大小写不敏感）返回规范大写名，否则 null。 */
    public static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String t = type.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_TYPES.contains(t) ? t : null;
    }

    /** 归一绑定目标：白名单内返回规范大写，否则 null（null/空按 NONE 兜底）。 */
    public static String normalizeTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return "NONE";
        }
        String t = targetType.trim().toUpperCase(Locale.ROOT);
        return TARGET_TYPES.contains(t) ? t : null;
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    /**
     * 校验 Schema JSON 字符串的结构合法性。返回错误列表，空列表表示通过。
     *
     * <p>与 {@link #validateSchema(JsonNode)} 等价：解析失败（含非 JSON/非对象）也作为一条错误返回，
     * 便于管理面把「Schema 非法」一次性反馈给配置者。</p>
     */
    public static List<String> validate(String schemaJson) {
        List<String> errors = new ArrayList<>();
        if (schemaJson == null || schemaJson.isBlank()) {
            errors.add("Schema 不能为空");
            return errors;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(schemaJson);
        } catch (Exception e) {
            errors.add("Schema 不是合法 JSON");
            return errors;
        }
        errors.addAll(validateSchema(root));
        return errors;
    }

    /** 校验已解析的 Schema 节点结构（version/title/fields 及各字段契约）。 */
    public static List<String> validateSchema(JsonNode root) {
        List<String> errors = new ArrayList<>();
        if (root == null || !root.isObject()) {
            errors.add("Schema 必须为 JSON 对象");
            return errors;
        }
        ObjectNode obj = (ObjectNode) root;

        int version = obj.path("version").asInt(-1);
        if (version != SUPPORTED_VERSION) {
            errors.add("version 必填且当前仅支持 " + SUPPORTED_VERSION);
        }
        JsonNode title = obj.path("title");
        if (title != null && !title.isNull() && !title.isTextual()) {
            errors.add("title 必须为字符串");
        }

        JsonNode fields = obj.path("fields");
        if (!fields.isArray() || fields.isEmpty()) {
            errors.add("fields 必填且为非空数组");
            return errors;
        }
        if (fields.size() > MAX_FIELDS) {
            errors.add("字段数超过上限 " + MAX_FIELDS);
            return errors;
        }
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < fields.size(); i++) {
            JsonNode f = fields.get(i);
            errors.addAll(validateField(i, f, keys));
        }
        return errors;
    }

    /** 校验单个字段节点；keys 用于跨字段查重（累积已见键）。 */
    private static List<String> validateField(int index, JsonNode f, Set<String> keys) {
        List<String> errors = new ArrayList<>();
        String at = "fields[" + index + "]";
        if (f == null || !f.isObject()) {
            errors.add(at + " 必须为 JSON 对象");
            return errors;
        }
        String key = f.path("key").asText(null);
        if (key == null || key.isBlank()) {
            errors.add(at + ".key 必填");
        } else if (!key.matches(FIELD_KEY_PATTERN)) {
            errors.add(at + ".key 须为驼峰标识符（字母开头，可含数字/下划线）");
        } else if (!keys.add(key)) {
            errors.add(at + ".key 重复：" + key);
        }
        if (!f.path("label").isTextual() || f.path("label").asText("").isBlank()) {
            errors.add(at + ".label 必填");
        }
        String type = f.path("type").asText(null);
        if (type == null || type.isBlank()) {
            errors.add(at + ".type 必填");
            return errors;
        }
        String norm = normalizeType(type);
        if (norm == null) {
            errors.add(at + ".type 非法（须为 " + String.join("/", SUPPORTED_TYPES) + "）");
            return errors;
        }
        if (OPTION_TYPES.contains(norm)) {
            JsonNode options = f.path("options");
            if (!options.isArray() || options.isEmpty()) {
                errors.add(at + ".options 对 " + norm + " 必填且为非空数组");
            } else {
                for (int j = 0; j < options.size(); j++) {
                    JsonNode o = options.get(j);
                    if (o == null || !o.isObject()
                            || !o.path("label").isTextual() || o.path("label").asText("").isBlank()
                            || o.path("value") == null || o.path("value").isNull()
                            || o.path("value").asText("").isBlank()) {
                        errors.add(at + ".options[" + j + "] 须包含非空 label/value");
                    }
                }
            }
        }
        errors.addAll(validateLengthAttrs(at, f));
        return errors;
    }

    /** 校验长度类属性（maxLength/minLength/rows）：须为非负整数且不超上限；minLength 不得大于 maxLength。 */
    private static List<String> validateLengthAttrs(String at, JsonNode f) {
        List<String> errors = new ArrayList<>();
        for (String attr : new String[]{"maxLength", "minLength", "rows"}) {
            JsonNode n = f.path(attr);
            if (n.isMissingNode() || n.isNull()) {
                continue;
            }
            if (!n.canConvertToInt() || n.asInt() < 0 || n.asInt() > MAX_LENGTH_LIMIT) {
                errors.add(at + "." + attr + " 须为 0~" + MAX_LENGTH_LIMIT + " 的整数");
            }
        }
        int min = f.path("minLength").canConvertToInt() ? f.path("minLength").asInt() : -1;
        int max = f.path("maxLength").canConvertToInt() ? f.path("maxLength").asInt() : -1;
        if (min >= 0 && max >= 0 && min > max) {
            errors.add(at + ".minLength 不得大于 maxLength");
        }
        return errors;
    }
}
