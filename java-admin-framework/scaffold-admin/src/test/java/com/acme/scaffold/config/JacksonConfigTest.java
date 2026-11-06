package com.acme.scaffold.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证全局 {@link Long} → 字符串序列化：仅装箱 {@link Long} 受影响，基础类型 {@code long} 保持数值。
 */
class JacksonConfigTest {

    record Sample(Long id, long count, String name) {
    }

    private ObjectMapper mapper() {
        JacksonConfig config = new JacksonConfig();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        config.longToStringSerializer().customize(builder);
        return builder.build();
    }

    @Test
    @DisplayName("Long 类型序列化为字符串，避免前端精度丢失")
    void longSerializedAsString() throws Exception {
        ObjectMapper mapper = mapper();
        JsonNode node = mapper.readTree(
                mapper.writeValueAsString(new Sample(123456789012345678L, 42L, "x")));

        assertTrue(node.get("id").isTextual(), "Long id 应序列化为字符串");
        assertEquals("123456789012345678", node.get("id").asText());
    }

    @Test
    @DisplayName("基础类型 long 保持数值，不影响计数等字段")
    void primitiveLongStaysNumber() throws Exception {
        ObjectMapper mapper = mapper();
        JsonNode node = mapper.readTree(
                mapper.writeValueAsString(new Sample(1L, 42L, "x")));

        assertTrue(node.get("count").isNumber(), "long count 应保持数值");
        assertEquals(42, node.get("count").asInt());
    }
}
