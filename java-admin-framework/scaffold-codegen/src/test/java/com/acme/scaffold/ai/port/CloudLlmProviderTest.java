package com.acme.scaffold.ai.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.ai.LlmNotImplementedException;
import com.acme.scaffold.ai.config.AiProperties;
import com.acme.scaffold.ai.domain.TableSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 云大模型提供者单元测试：响应解析、JSON 提取、类型归一与 501 未配置语义（离线，无网络）。
 */
class CloudLlmProviderTest {

    private final ObjectMapper om = new ObjectMapper();

    private CloudLlmProvider newProvider() {
        return new CloudLlmProvider(new AiProperties());
    }

    @Test
    void stripFencesShouldRemoveMarkdownFences() {
        assertEquals("{\"a\":1}", CloudLlmProvider.stripFences("```json\n{\"a\":1}\n```"));
        assertEquals("plain", CloudLlmProvider.stripFences("plain"));
    }

    @Test
    void extractJsonShouldPickBalancedObjectWithNoise() throws Exception {
        JsonNode node = CloudLlmProvider.extractJson(
                "好的，以下是表结构：\n```json\n{\"tableName\":\"sys_order\",\"columns\":[{\"columnName\":\"id\",\"desc\":\"}{\"}]}\n```\n希望有帮助",
                om);
        assertEquals("sys_order", node.path("tableName").asText());
        assertEquals(1, node.path("columns").size());
    }

    @Test
    void extractJsonShouldRejectNonJsonOutput() {
        assertThrows(CloudLlmProvider.LlmInvocationException.class,
                () -> CloudLlmProvider.extractJson("抱歉，我无法生成", om));
        assertThrows(CloudLlmProvider.LlmInvocationException.class,
                () -> CloudLlmProvider.extractJson("{\"tableName\":\"a\"", om));
    }

    @Test
    void normalizeTypeShouldStripLengthAndFallbackUnknown() {
        assertEquals("varchar", CloudLlmProvider.normalizeType("VARCHAR(64)"));
        assertEquals("datetime", CloudLlmProvider.normalizeType(" DATETIME "));
        assertEquals("varchar", CloudLlmProvider.normalizeType("geometry"));
        assertEquals("varchar", CloudLlmProvider.normalizeType(""));
    }

    @Test
    void snakeToCamelShouldConvert() {
        assertEquals("createdAt", CloudLlmProvider.snakeToCamel("created_at"));
        assertEquals("id", CloudLlmProvider.snakeToCamel("id"));
        assertEquals("orderNo", CloudLlmProvider.snakeToCamel("order_no"));
    }

    @Test
    void toTableSpecShouldNormalizeAndMapJavaTypes() {
        String json = """
                {"tableName":"SYS_ORDER","comment":"订单",
                 "columns":[
                   {"columnName":"id","dataType":"bigint","primaryKey":true},
                   {"columnName":"amount","dataType":"decimal(10,2)","comment":"金额","nullable":false},
                   {"columnName":"created_at","dataType":"datetime","comment":"创建时间"}
                 ]}""";
        TableSpec spec = newProvider().toTableSpec(parse(json));
        assertEquals("sys_order", spec.tableName());
        assertEquals(3, spec.columns().size());

        var id = spec.columns().get(0);
        assertEquals("id", id.columnName());
        assertEquals("Long", id.javaType());
        assertTrue(id.primaryKey());
        assertTrue(id.autoIncrement());
        assertTrue(!id.nullable());

        var amount = spec.columns().get(1);
        assertEquals("BigDecimal", amount.javaType());
        assertEquals("amount", amount.javaField());

        var createdAt = spec.columns().get(2);
        assertEquals("LocalDateTime", createdAt.javaType());
        assertEquals("createdAt", createdAt.javaField());
    }

    @Test
    void toTableSpecShouldRejectMissingPkOrEmptyColumnsOrBadNames() {
        assertThrows(CloudLlmProvider.LlmInvocationException.class, () -> newProvider().toTableSpec(parse("""
                {"tableName":"t1","columns":[{"columnName":"name","dataType":"varchar"}]}""")));
        assertThrows(CloudLlmProvider.LlmInvocationException.class, () -> newProvider().toTableSpec(parse("""
                {"tableName":"t1","columns":[]}""")));
        assertThrows(CloudLlmProvider.LlmInvocationException.class, () -> newProvider().toTableSpec(parse("""
                {"tableName":"t1","columns":[{"columnName":"bad-name","dataType":"varchar","primaryKey":true}]}""")));
        assertThrows(CloudLlmProvider.LlmInvocationException.class, () -> newProvider().toTableSpec(parse("""
                {"tableName":"Bad Name","columns":[{"columnName":"id","dataType":"bigint","primaryKey":true}]}""")));
    }

    @Test
    void parseWithoutEndpointShouldMap501() {
        CloudLlmProvider provider = newProvider();
        LlmNotImplementedException e = assertThrows(
                LlmNotImplementedException.class, () -> provider.parse("订单表"));
        assertTrue(e.getMessage().contains("ai.llm.endpoint"));
    }

    private JsonNode parse(String json) {
        try {
            return om.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
