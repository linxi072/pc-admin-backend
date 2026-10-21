package com.acme.scaffold.workflow.dto.design;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归：WorkflowDesignView.id 必须按字符串序列化。
 *
 * <p>18 位雪花 ID 超出 JS Number 安全整数范围（2^53-1），若以 JSON number 下发，
 * 前端回传路径参数时精度丢失（例如 384233927386075140 会被 round 成 ...7136），
 * 导致「工作流设计不存在」类故障。
 */
class WorkflowDesignViewSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void idSerializesAsStringPreservingFullPrecision() throws Exception {
        long id = 384233927386075140L;
        WorkflowDesignView view = new WorkflowDesignView(id, "expense", "费用报销", "desc",
                "DRAFT", 0, null, List.of(), List.of(), null, null, null, null, null, null);

        String json = mapper.writeValueAsString(view);
        JsonNode node = mapper.readTree(json);

        assertTrue(node.get("id").isTextual(), "id 必须序列化为字符串（防 JS 精度丢失）");
        assertEquals("384233927386075140", node.get("id").asText());

        // 字符串回传后反序列化值不丢失
        WorkflowDesignView back = mapper.readValue(json, WorkflowDesignView.class);
        assertEquals(id, back.id());
    }
}
