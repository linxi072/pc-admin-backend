package com.acme.scaffold.ai.service;

import com.acme.scaffold.ai.AiScaffoldException;
import com.acme.scaffold.ai.config.AiProperties;
import com.acme.scaffold.ai.dto.ColumnSpec;
import com.acme.scaffold.ai.dto.ScaffoldResult;
import com.acme.scaffold.ai.port.CloudLlmProvider;
import com.acme.scaffold.ai.port.HeuristicLlmProvider;
import com.acme.scaffold.codegen.dto.GeneratedFileVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AiScaffoldService 单测：覆盖「订单表含金额/状态」解析、迁移 SQL 与代码草稿产出、
 * 空提示词与超长提示词的成本门禁拒绝。
 */
class AiScaffoldServiceTest {

    private AiScaffoldService service() {
        AiProperties props = new AiProperties();
        props.setMaxPromptTokens(2000);
        props.setAuthor("tester");
        HeuristicLlmProvider heuristic = new HeuristicLlmProvider();
        ObjectProvider<CloudLlmProvider> cloud = mock(ObjectProvider.class);
        when(cloud.getIfAvailable()).thenReturn(null);
        return new AiScaffoldService(heuristic, cloud, props);
    }

    @Test
    void orderTableProducesDdlAndCode() {
        ScaffoldResult r = service().generate("订单表含金额/状态", "order");

        assertTrue(r.needsReview(), "AI 生成物应标记需人工复核");
        assertEquals("sys_order", r.tableSpec().tableName());
        assertEquals("heuristic", r.provider());

        // 金额 → decimal / BigDecimal
        ColumnSpec amount = r.tableSpec().columns().stream()
                .filter(c -> "amount".equals(c.columnName())).findFirst().orElseThrow();
        assertEquals("BigDecimal", amount.javaType());

        // 状态 → varchar / String
        ColumnSpec status = r.tableSpec().columns().stream()
                .filter(c -> "status".equals(c.columnName())).findFirst().orElseThrow();
        assertEquals("String", status.javaType());

        // 迁移 SQL：引号小写标识符 + 主键 + 独立索引
        assertTrue(r.ddl().contains("CREATE TABLE \"sys_order\""), "DDL 应含建表语句");
        assertTrue(r.ddl().contains("PRIMARY KEY (\"id\")"), "DDL 应含主键");
        assertTrue(r.ddl().contains("idx_sys_order"), "DDL 应含索引语句");
        assertTrue(r.ddl().contains("NOT NULL DEFAULT 0 COMMENT '租户ID'"), "DDL 应含租户隔离列");

        // 代码草稿：DO / ServiceImpl / Vue / JS 齐备
        List<GeneratedFileVO> files = r.files();
        assertTrue(files.stream().anyMatch(f -> f.path().endsWith("OrderDO.java")), "应生成 OrderDO.java");
        assertTrue(files.stream().anyMatch(f -> f.path().endsWith("OrderServiceImpl.java")), "应生成 OrderServiceImpl.java");
        assertTrue(files.stream().anyMatch(f -> f.path().endsWith(".vue")), "应生成 Vue 页面");
        assertTrue(files.stream().anyMatch(f -> f.path().endsWith(".js")), "应生成 api.js");
    }

    @Test
    void emptyPromptRejected() {
        assertThrows(AiScaffoldException.class, () -> service().generate("   ", "order"));
    }

    @Test
    void overlongPromptRejectedByCostGate() {
        String prompt = "x".repeat(3000);
        assertThrows(AiScaffoldException.class, () -> service().generate(prompt, "order"));
    }
}
