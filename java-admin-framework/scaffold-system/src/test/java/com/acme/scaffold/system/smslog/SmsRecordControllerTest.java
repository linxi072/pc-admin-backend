package com.acme.scaffold.system.smslog;

import com.acme.scaffold.common.api.dto.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SmsRecordController} 契约测试：分页/摘要两类端点的参数默认值、
 * 响应结构与字段口径（脱敏列名、状态码位）。
 *
 * <p>standalone MockMvc + mock Service（不触达 DB）；权限注解
 * （{@code system:sms-record:read}）由 Spring Security 测试另行覆盖，
 * 此处固化「接口形状」——前后端联调的静态契约。
 */
class SmsRecordControllerTest {

    private final SmsRecordService service = mock(SmsRecordService.class);

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new SmsRecordController(service)).build();
    }

    private static SmsRecordView view() {
        return new SmsRecordView(1L, "1234567890", "138****8000", 1,
                "【Acme】您的验证码是 123456", "LOG", "SUCCESS", null,
                1L, "管理员", "trace-1", LocalDateTime.of(2026, 10, 19, 10, 0));
    }

    @Test
    void page_returnsRecordsWithMaskedColumns() throws Exception {
        when(service.page(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResult.of(1, 10, 1, List.of(view())));

        mvc().perform(get("/api/system/sms-records/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].mobilesMasked").value("138****8000"))
                .andExpect(jsonPath("$.data.records[0].mobileCount").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.records[0].provider").value("LOG"))
                // 不提供「原文」字段：接口形状即安全边界
                .andExpect(jsonPath("$.data.records[0].mobiles").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].content").doesNotExist());
    }

    @Test
    void page_defaultsToOneAndTen() throws Exception {
        when(service.page(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResult.empty(1, 10));

        mvc().perform(get("/api/system/sms-records/page")).andExpect(status().isOk());

        ArgumentCaptor<Integer> page = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);
        verify(service).page(page.capture(), size.capture(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull());
        assertEquals(1, page.getValue());
        assertEquals(10, size.getValue());
    }

    @Test
    void page_passesFiltersThrough() throws Exception {
        when(service.page(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResult.empty(1, 10));

        mvc().perform(get("/api/system/sms-records/page")
                        .param("status", "FAILED")
                        .param("provider", "http")
                        .param("keyword", "gateway")
                        .param("sortField", "sentAt")
                        .param("sortDirection", "DESC"))
                .andExpect(status().isOk());

        // 控制器原样透传，归一（大小写/白名单）由 Service 规则层负责
        verify(service).page(eq(1), eq(10), eq("FAILED"), eq("http"), eq("gateway"),
                isNull(), isNull(), eq("sentAt"), eq("DESC"));
    }

    @Test
    void summary_returnsAggregates() throws Exception {
        // 注意：Map.of 不允许 null 值，摘要含 lastSentAt=null 的常态，须用可变 Map
        Map<String, Object> sum = new java.util.LinkedHashMap<>();
        sum.put("total", 10L);
        sum.put("success", 7L);
        sum.put("failure", 3L);
        sum.put("failureRate", 0.3);
        sum.put("lastSentAt", null);
        when(service.summary(any(), any(), any(), any(), any())).thenReturn(sum);

        mvc().perform(get("/api/system/sms-records/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(10))
                .andExpect(jsonPath("$.data.failure").value(3));

        verify(service).summary(isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void page_sizeAboveMaxIsPassedThroughForClamping() throws Exception {
        // 钳制发生在 Service 的 PageQuery（已由 PageQueryTest 覆盖）；
        // 此处固化控制器不做二次钳制、原样透传的职责边界
        when(service.page(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResult.empty(1, 100));

        mvc().perform(get("/api/system/sms-records/page").param("size", "999"))
                .andExpect(status().isOk());

        ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);
        verify(service).page(anyInt(), size.capture(), any(), any(), any(), any(), any(), any(), any());
        assertEquals(999, size.getValue());
    }

    @Test
    void page_sortDirectionCaptured() throws Exception {
        when(service.page(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResult.empty(1, 10));

        mvc().perform(get("/api/system/sms-records/page").param("sortDirection", "ASC"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> dir = ArgumentCaptor.forClass(String.class);
        // sortField 缺省为 null，anyString() 不匹配 null（Mockito 2+ 语义），须用 any()
        verify(service).page(anyInt(), anyInt(), any(), any(), any(), any(), any(),
                any(), dir.capture());
        assertEquals("ASC", dir.getValue());
    }
}
