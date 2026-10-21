package com.acme.scaffold.e2e;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.exception.GlobalExceptionHandler;
import com.acme.scaffold.common.i18n.I18nService;
import com.acme.scaffold.common.idempotency.controller.IdempotencyRecordController;
import com.acme.scaffold.common.idempotency.domain.IdempotencyRecordDO;
import com.acme.scaffold.common.idempotency.dto.IdempotencyRecordView;
import com.acme.scaffold.common.idempotency.dto.IdempotencyStatsView;
import com.acme.scaffold.common.idempotency.service.IdempotencyRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 幂等记录管理端到端边界测试（离线可执行切片）。
 *
 * <p>以 standalone MockMvc 装配<b>真实</b> {@link IdempotencyRecordController} 与<b>真实</b>
 * {@link GlobalExceptionHandler}（构造<b>真实</b> {@link I18nService}，读取 {@code i18n/messages*}
 * 资源束），服务层以 Mockito 桩接。覆盖「HTTP 路由 → 控制器编排 → 服务调用 → 统一 {@link Result}
 * 包络 / 异常映射 / 筛选值透传」全链路，在无需数据库执行的前提下对幂等记录查询与清理接口
 * 做契约级验证。</p>
 *
 * <p><b>分层口径</b>：服务层的筛选值归一（非法状态忽略）、时间窗收敛与排序方向硬校验
 * （{@code SortItem.single}）由 {@code IdempotencyRecordRulesTest} 与既有 {@code PageQuery}
 * 单测覆盖；本类只验证 HTTP 契约与异常→包络映射（ID-03 以桩模拟服务层校验抛出，
 * 与 N+1 模块 NP-03 同手法）。</p>
 */
@DisplayName("幂等记录管理端到端边界测试")
class IdempotencyRecordE2eTest {

    private IdempotencyRecordService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(IdempotencyRecordService.class);
        IdempotencyRecordController controller = new IdempotencyRecordController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(i18n()))
                .build();
    }

    private I18nService i18n() {
        ReloadableResourceBundleMessageSource src = new ReloadableResourceBundleMessageSource();
        src.setBasenames("classpath:i18n/messages", "classpath:i18n/validation");
        src.setDefaultEncoding("UTF-8");
        src.setFallbackToSystemLocale(false);
        src.setCacheSeconds(-1);
        return new I18nService(src);
    }

    private static IdempotencyRecordDO sampleRecord() {
        IdempotencyRecordDO d = new IdempotencyRecordDO();
        d.setId(9001L);
        d.setIdempotencyKey("order-2026-1001");
        d.setApiScope("workflow:complete");
        d.setUserId(42L);
        d.setRequestFingerprint("a1b2c3d4e5f6");
        d.setStatus("SUCCESS");
        d.setExpiresAt(LocalDateTime.of(2026, 10, 19, 10, 0));
        d.setCreatedAt(LocalDateTime.of(2026, 10, 19, 9, 0));
        d.setUpdatedAt(LocalDateTime.of(2026, 10, 19, 9, 5));
        return d;
    }

    @Test
    @DisplayName("ID-01 分页查询默认参数 → 200 / code=0 / 包络字段与记录字段正确（含过期计算标记）")
    void page_defaults_ok() throws Exception {
        when(service.page(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResult.of(1, 10, 1,
                        List.of(IdempotencyRecordView.of(sampleRecord(),
                                LocalDateTime.of(2026, 10, 20, 12, 0)))));
        mvc.perform(get("/api/system/idempotency-records/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].idempotencyKey").value("order-2026-1001"))
                .andExpect(jsonPath("$.data.records[0].apiScope").value("workflow:complete"))
                .andExpect(jsonPath("$.data.records[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.records[0].requestFingerprint").value("a1b2c3d4e5f6"))
                .andExpect(jsonPath("$.data.records[0].expired").value(true))
                // 响应快照不在视图内（契约级保证，见 IdempotencyRecordView 注释）
                .andExpect(jsonPath("$.data.records[0].responseSnapshot").doesNotExist());
    }

    @Test
    @DisplayName("ID-02 分页筛选透传 → 200 / 原始筛选值与排序参数原样进入服务层（归一部分在服务层完成）")
    void page_filters_passedThrough() throws Exception {
        when(service.page(eq(2), eq(20), eq("SUCCESS"), eq("workflow:complete"), eq("42"),
                eq("order"), eq(LocalDateTime.of(2026, 10, 19, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 20, 0, 0)), eq("createdAt"), eq("DESC")))
                .thenReturn(PageResult.empty(2, 20));
        mvc.perform(get("/api/system/idempotency-records/page")
                        .param("page", "2")
                        .param("size", "20")
                        .param("status", "SUCCESS")
                        .param("apiScope", "workflow:complete")
                        .param("userId", "42")
                        .param("keyword", "order")
                        .param("startTime", "2026-10-19T00:00:00")
                        .param("endTime", "2026-10-20T00:00:00")
                        .param("sortField", "createdAt")
                        .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records").isArray());
        verify(service).page(2, 20, "SUCCESS", "workflow:complete", "42",
                "order", LocalDateTime.of(2026, 10, 19, 0, 0),
                LocalDateTime.of(2026, 10, 20, 0, 0), "createdAt", "DESC");
    }

    @Test
    @DisplayName("ID-03 排序方向非法 → 400 / COMMON_001（服务层输入校验，异常经 handler 映射）")
    void page_invalidSortDirection() throws Exception {
        // 真实校验位于服务层 PageQuery.of → SortItem.single（抛 BusinessException(VALIDATION_ERROR)），
        // 此处以桩模拟该抛出，验证 GlobalExceptionHandler 的 400/COMMON_001 映射不回归
        when(service.page(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(),
                eq("SIDEWAYS")))
                .thenThrow(BusinessException.badRequest("排序方向仅支持 ASC/DESC，当前为：SIDEWAYS"));
        mvc.perform(get("/api/system/idempotency-records/page").param("sortDirection", "SIDEWAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("ID-04 统计摘要 → 200 / code=0 / 三态分布与占比字段正确")
    void summary_ok() throws Exception {
        when(service.summary(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IdempotencyStatsView(100, 3, 90, 7, 12, 0.03,
                        LocalDateTime.of(2026, 10, 20, 11, 59)));
        mvc.perform(get("/api/system/idempotency-records/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(100))
                .andExpect(jsonPath("$.data.processing").value(3))
                .andExpect(jsonPath("$.data.success").value(90))
                .andExpect(jsonPath("$.data.failed").value(7))
                .andExpect(jsonPath("$.data.expiredPending").value(12))
                .andExpect(jsonPath("$.data.processingShare").value(0.03));
    }

    @Test
    @DisplayName("ID-05 手动清理过期记录 → 200 / code=0 / 返回删除条数")
    void purge_ok() throws Exception {
        when(service.purgeExpired()).thenReturn(7);
        mvc.perform(post("/api/system/idempotency-records/purge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(7));
        verify(service).purgeExpired();
    }

    @Test
    @DisplayName("ID-06 非法状态筛选值 → 200 容错（原样透传由服务层归一忽略，绝不 500）")
    void page_invalidStatusFilter_isTolerated() throws Exception {
        when(service.page(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResult.empty(1, 10));
        mvc.perform(get("/api/system/idempotency-records/page").param("status", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records").isArray());
        // 容错 posture 守护：控制器原样透传，归一/忽略发生在服务层；
        // 若未来误加 @Validated 严格校验（曾导致 ConstraintViolationException 穿透 500），本用例即回归红灯
        verify(service).page(1, 10, "UNKNOWN", null, null, null, null, null, null, null);
    }
}
