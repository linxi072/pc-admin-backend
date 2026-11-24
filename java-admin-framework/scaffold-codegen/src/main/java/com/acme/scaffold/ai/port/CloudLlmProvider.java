package com.acme.scaffold.ai.port;

import com.acme.scaffold.ai.AiScaffoldException;
import com.acme.scaffold.ai.LlmNotImplementedException;
import com.acme.scaffold.ai.config.AiProperties;
import com.acme.scaffold.ai.domain.ColumnSpec;
import com.acme.scaffold.ai.domain.TableSpec;
import com.acme.scaffold.codegen.service.JavaTypeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 条件化可选云大模型提供者：仅当 {@code ai.llm.enabled=true} 时装配。
 *
 * <p>基于 OpenAI 兼容 chat/completions 协议（POST {@code ai.llm.endpoint}），
 * 使用 JDK 内置 {@link HttpClient}（无新增依赖），请求体由 Jackson 序列化。
 * 模型被要求返回严格 JSON 表规格（tableName/comment/columns），本类负责：
 * <ol>
 *   <li>配置校验：endpoint/model 缺失时抛 {@link LlmNotImplementedException}（映射 501）；</li>
 *   <li>调用与超时控制：非 2xx / 网络 IO / 中断均包装为 {@link LlmInvocationException}；</li>
 *   <li>响应解析：剥离 Markdown 代码围栏与前后缀噪声，提取 JSON 并归一化为 {@link TableSpec}；</li>
 *   <li>类型映射：JDBC 类型经 {@link JavaTypeMapper} 统一映射，与 codegen 渲染对齐。</li>
 * </ol>
 * 运行期失败是否降级回退启发式由 {@code ai.llm.fallback-on-failure} 控制（编排层处理）。
 */
@Component
@ConditionalOnProperty(prefix = "ai.llm", name = "enabled", havingValue = "true")
public class CloudLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(CloudLlmProvider.class);

    private static final String SYSTEM_PROMPT = """
            你是数据库架构助手。根据用户的自然语言描述，推断一张 MySQL 表结构，
            只输出一个 JSON 对象，不要输出任何解释文字或 Markdown 代码围栏。格式：
            {"tableName":"snake_case 表名，如 sys_order","comment":"中文实体名",
             "columns":[{"columnName":"snake_case 列名","dataType":"JDBC 类型(varchar/bigint/decimal/int/datetime/text 等)",
                         "comment":"中文注释","primaryKey":false,"nullable":true,"autoIncrement":false}]}
            要求：必须包含一个自增主键 id(bigint)；业务字段之后补充 created_at/updated_at(datetime) 审计列；
            列名使用小写 snake_case；不要输出 JSON 以外的任何内容。""";

    /** 合法列名（snake_case）校验；非法一律拒收，避免注入 DDL。 */
    private static final java.util.regex.Pattern SNAKE_RE =
            java.util.regex.Pattern.compile("^[a-z][a-z0-9_]*$");

    /** 允许的 JDBC 类型白名单（与 JavaTypeMapper 支持域对齐），其余归一为 varchar。 */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "tinyint", "smallint", "mediumint", "int", "integer", "bigint",
            "decimal", "numeric", "float", "double", "real",
            "char", "varchar", "tinytext", "text", "mediumtext", "longtext", "json", "enum", "set",
            "date", "time", "datetime", "timestamp",
            "binary", "varbinary", "blob", "tinyblob", "mediumblob", "longblob");

    private final AiProperties.Llm cfg;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public CloudLlmProvider(AiProperties props) {
        this.cfg = props.getLlm();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(cfg.getTimeoutMs(), 5_000)))
                .build();
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public String name() {
        return "cloud";
    }

    @Override
    public TableSpec parse(String prompt) {
        if (cfg.getEndpoint() == null || cfg.getEndpoint().isBlank()) {
            // 已启用但未配置端点：能力不可用，映射 501（F5），而非笼统 500
            throw new LlmNotImplementedException(
                    "云大模型已启用但未配置 ai.llm.endpoint，无法生成表结构（请配置后重试）");
        }
        if (cfg.getModel() == null || cfg.getModel().isBlank()) {
            throw new LlmNotImplementedException(
                    "云大模型已启用但未配置 ai.llm.model（请配置后重试）");
        }

        String content = callChatCompletions(prompt);
        JsonNode root = extractJson(content);
        return toTableSpec(root);
    }

    /** 调用 OpenAI 兼容 chat/completions 端点，返回首个 choice 的 message.content。 */
    private String callChatCompletions(String prompt) {
        String body;
        try {
            body = mapper.writeValueAsString(mapper.createObjectNode()
                    .put("model", cfg.getModel())
                    .put("temperature", 0.2)
                    .set("messages", mapper.createArrayNode()
                            .add(mapper.createObjectNode().put("role", "system").put("content", SYSTEM_PROMPT))
                            .add(mapper.createObjectNode().put("role", "user").put("content", prompt))));
        } catch (IOException e) {
            throw new LlmInvocationException("构造大模型请求失败：" + e.getMessage(), e);
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(cfg.getEndpoint()))
                .timeout(Duration.ofMillis(cfg.getTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
            rb.header("Authorization", "Bearer " + cfg.getApiKey());
        }

        HttpResponse<String> resp;
        try {
            resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new LlmInvocationException("云大模型网络调用失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmInvocationException("云大模型调用被中断", e);
        }

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String excerpt = resp.body() == null ? "" : resp.body();
            if (excerpt.length() > 300) {
                excerpt = excerpt.substring(0, 300) + "…";
            }
            throw new LlmInvocationException(
                    "云大模型返回非 2xx（HTTP " + resp.statusCode() + "）：" + excerpt);
        }

        try {
            JsonNode content = mapper.readTree(resp.body())
                    .path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new LlmInvocationException("云大模型响应缺少 choices[0].message.content");
            }
            return content.asText();
        } catch (IOException e) {
            throw new LlmInvocationException("解析云大模型响应失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从模型输出中提取 JSON：剥离 Markdown 代码围栏，截取首个 {@code {}} 平衡片段。
     *
     * @throws AiScaffoldException 输出中不含合法 JSON 对象
     */
    static JsonNode extractJson(String content, ObjectMapper om) {
        String text = stripFences(content == null ? "" : content).trim();
        int start = text.indexOf('{');
        if (start < 0) {
            throw new LlmInvocationException("云大模型输出中未找到 JSON 对象");
        }
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++; // 跳过转义字符
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    try {
                        return om.readTree(text.substring(start, i + 1));
                    } catch (IOException e) {
                        throw new LlmInvocationException("云大模型输出不是合法 JSON：" + e.getMessage(), e);
                    }
                }
            }
        }
        throw new LlmInvocationException("云大模型输出 JSON 不完整（花括号未闭合）");
    }

    private JsonNode extractJson(String content) {
        return extractJson(content, mapper);
    }

    /** 剥离 ``` / ```json 围栏行。 */
    static String stripFences(String text) {
        return text.replaceAll("(?m)^\\s*```\\w*\\s*$", "").trim();
    }

    /**
     * 将模型返回的 JSON 归一化为 {@link TableSpec}：表名/列名校验、类型白名单、Java 类型映射。
     *
     * @throws AiScaffoldException tableName 缺失或无有效业务列
     */
    TableSpec toTableSpec(JsonNode root) {
        String tableName = root.path("tableName").asText("").trim().toLowerCase(Locale.ROOT);
        if (!SNAKE_RE.matcher(tableName).matches() || tableName.length() > 64) {
            throw new LlmInvocationException(
                    "云大模型返回的表名非法（需小写 snake_case ≤64 字符）：" + tableName);
        }
        String comment = root.path("comment").asText("").trim();

        JsonNode cols = root.path("columns");
        if (!cols.isArray() || cols.isEmpty()) {
            throw new LlmInvocationException("云大模型返回的 columns 为空");
        }

        List<ColumnSpec> list = new ArrayList<>();
        boolean hasId = false;
        for (JsonNode c : cols) {
            String colName = c.path("columnName").asText("").trim().toLowerCase(Locale.ROOT);
            if (!SNAKE_RE.matcher(colName).matches() || colName.length() > 64) {
                throw new LlmInvocationException(
                        "列名非法（需小写 snake_case ≤64 字符）：" + colName);
            }
            String dataType = normalizeType(c.path("dataType").asText("varchar"));
            boolean pk = c.path("primaryKey").asBoolean(false);
            if (pk) {
                hasId = true;
            }
            // tinyint(1) 布尔语义：模型返回 tinyint 且列名/注释含 is_/是否 前缀
            boolean tinyintOne = "tinyint".equals(dataType)
                    && (colName.startsWith("is_") || c.path("comment").asText("").contains("是否"));
            list.add(new ColumnSpec(
                    colName,
                    dataType,
                    JavaTypeMapper.toJavaType(dataType, tinyintOne),
                    snakeToCamel(colName),
                    c.path("comment").asText("").trim(),
                    pk,
                    c.path("nullable").asBoolean(!pk),
                    c.path("autoIncrement").asBoolean(pk)));
        }
        if (!hasId) {
            throw new LlmInvocationException("云大模型返回的表结构缺少主键列（primaryKey=true）");
        }
        return new TableSpec(tableName, comment, List.copyOf(list));
    }

    /** 类型归一：小写、剥离长度说明（varchar(64) → varchar），未知类型回退 varchar。 */
    static String normalizeType(String dataType) {
        String t = dataType == null ? "" : dataType.trim().toLowerCase(Locale.ROOT);
        int p = t.indexOf('(');
        if (p > 0) {
            t = t.substring(0, p).trim();
        }
        return ALLOWED_TYPES.contains(t) ? t : "varchar";
    }

    /** snake_case → camelCase（列名已通过合法性校验）。 */
    static String snakeToCamel(String snake) {
        String[] parts = snake.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)))
                        .append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * 云大模型运行期调用失败异常（网络 / 非响应式 2xx / 协议解析）：业务异常（默认 500），
     * 与 {@link LlmNotImplementedException}（501，能力未就绪）区分；
     * 编排层据此在 {@code fallback-on-failure=true} 时降级回退启发式提供者。
     */
    public static class LlmInvocationException extends AiScaffoldException {

        public LlmInvocationException(String message) {
            super(message);
        }

        public LlmInvocationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
