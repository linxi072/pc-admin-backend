package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.audit.AuditLogService;
import com.acme.scaffold.common.audit.AuditProperties;
import com.acme.scaffold.common.context.SecurityContextFacade;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审计日志控制器测试（M20 深化：留存策略端点）。
 *
 * <p>留存端点为纯配置读取（{@code AuditProperties} + {@code AuditRetentionRules}），无数据库访问；
 * 用 standalone MockMvc 直接驱动控制器逻辑，{@code AuditLogService}/{@code SecurityContextFacade}
 * 以 mock 注入（留存端点不回调二者，仅满足构造函数约束）。
 * 关注点：留存天数为正时返回天数与 cutoff；≤0 时 cutoff 为 null（禁用自动清理）。</p>
 */
class AuditLogControllerTest {

    private MockMvc mvc(int retentionDays) {
        AuditLogService auditLogService = mock(AuditLogService.class);
        SecurityContextFacade facade = mock(SecurityContextFacade.class);
        AuditProperties props = new AuditProperties();
        props.setRetentionDays(retentionDays);
        return MockMvcBuilders.standaloneSetup(
                new AuditLogController(auditLogService, facade, props)).build();
    }

    @Test
    void retention_returnsConfiguredDaysAndCutoff() throws Exception {
        mvc(90).perform(get("/api/system/audit-logs/retention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.retentionDays").value(90))
                .andExpect(jsonPath("$.data.cutoff", notNullValue()));
    }

    @Test
    void retention_disabledWhenDaysNotPositive() throws Exception {
        mvc(0).perform(get("/api/system/audit-logs/retention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.retentionDays").value(0))
                .andExpect(jsonPath("$.data.cutoff", nullValue()));
    }

    @Test
    void retention_negativeDaysTreatedAsDisabled() throws Exception {
        mvc(-7).perform(get("/api/system/audit-logs/retention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retentionDays").value(-7))
                .andExpect(jsonPath("$.data.cutoff", nullValue()));
    }
}
