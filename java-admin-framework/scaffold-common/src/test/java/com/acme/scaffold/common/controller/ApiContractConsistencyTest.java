package com.acme.scaffold.common.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.exception.GlobalExceptionHandler;
import com.acme.scaffold.common.i18n.I18nService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 前后端联调统一契约一致性测试（后端侧）。
 *
 * <p>以 standalone MockMvc 装配<b>真实</b> {@link GlobalExceptionHandler} 与<b>真实</b> {@link I18nService}
 * （读取 {@code i18n/messages*} 资源束），并以内存版 {@link DemoItemController} 复刻「HTTP → 校验 → 业务 →
 * 统一包络」的端到端链路。所有断言的<b>唯一事实来源</b>是仓库根级契约
 * {@code java-admin-framework/scripts/api-contract.json}（与前端 {@code result.test.js} 共用同一份），
 * 从构造上杜绝前后端契约漂移。</p>
 *
 * <p>覆盖维度（对应需求「接口连通性 / 数据交互正确性 / 正常流程 / 边界值 / 异常场景 / 错误码处理 / 前后端一致性」）：</p>
 * <ul>
 *   <li>正常流程：create → get → list 数据往返一致，包络字段齐备；</li>
 *   <li>边界值：分页 size/page 钳制、name 空/缺省、name 超长、保留字业务拒绝、唯一键冲突、查询不存在；</li>
 *   <li>异常场景与错误码：9 个通用错误码经真实 handler 映射后 HTTP 状态 / code / 双语文案与契约逐项一致；</li>
 *   <li>契约一致性：错误码表与 {@link CommonErrorCode} 枚举双向一致，且资源束双语文案与契约一致。</li>
 * </ul>
 */
class ApiContractConsistencyTest {

    private static final String ZH = "zh-CN";
    private static final String EN = "en-US";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DemoItemService service;
    private JsonNode contract;
    private Map<String, JsonNode> errByCode;

    @BeforeEach
    void setup() throws IOException {
        service = new DemoItemService();
        contract = loadContract();
        errByCode = new HashMap<>();
        for (JsonNode e : contract.get("errorCodes")) {
            errByCode.put(e.get("code").asText(), e);
        }
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    // ------------------------------------------------------------------
    // 契约加载：从模块 cwd 向上查找仓库根级 api-contract.json（与前端同一份）
    // ------------------------------------------------------------------

    private JsonNode loadContract() throws IOException {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && dir.getParent() != null) {
            Path cand = dir.resolve("java-admin-framework/scripts/api-contract.json");
            if (Files.exists(cand)) {
                return MAPPER.readTree(Files.readString(cand));
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "未找到 api-contract.json（从 cwd=" + Paths.get("").toAbsolutePath() + " 向上查找失败）");
    }

    // ------------------------------------------------------------------
    // 基础设施装配（与 ConnectivityControllerTest 同范式）
    // ------------------------------------------------------------------

    private I18nService i18n() {
        ReloadableResourceBundleMessageSource src = new ReloadableResourceBundleMessageSource();
        src.setBasenames("classpath:i18n/messages", "classpath:i18n/validation");
        src.setDefaultEncoding("UTF-8");
        src.setFallbackToSystemLocale(false);
        src.setCacheSeconds(-1);
        return new I18nService(src);
    }

    private GlobalExceptionHandler handler() {
        return new GlobalExceptionHandler(i18n());
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new DemoItemController(service), new DiagnosticController())
                .setControllerAdvice(handler())
                .build();
    }

    private JsonNode read(MvcResult r) throws IOException {
        return MAPPER.readTree(new String(r.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    private JsonNode findScenario(String name) {
        for (JsonNode s : contract.get("exceptionScenarios")) {
            if (name.equals(s.get("scenario").asText())) return s;
        }
        return null;
    }

    /** 断言任一响应的统一包络字段齐备（code/message/data/traceId/timestamp）。 */
    private void assertEnvelope(MvcResult r, String expectedCode, int expectedHttp) throws IOException {
        MockHttpServletResponse resp = r.getResponse();
        JsonNode node = MAPPER.readTree(new String(resp.getContentAsByteArray(), StandardCharsets.UTF_8));
        assertEquals(expectedCode, node.get("code").asText(), "code 与契约一致");
        assertEquals(expectedHttp, resp.getStatus(), "HTTP 状态与契约一致");
        assertTrue(node.has("message"), "message 字段存在");
        assertTrue(node.has("data"), "data 字段存在");
        assertTrue(node.has("traceId"), "traceId 字段存在");
        assertFalse(node.get("traceId").asText().isEmpty(), "traceId 非空");
        assertTrue(node.has("timestamp"), "timestamp 字段存在");
        assertFalse(node.get("timestamp").asText().isEmpty(), "timestamp 非空");
    }

    // ==================================================================
    // 1) 正常流程：create → get → list 数据往返
    // ==================================================================

    @Test
    @DisplayName("正常流程：create 返回 201 + 成功包络，data 字段完整且 id 为字符串")
    void normalFlow_create_returnsEnvelope() throws Exception {
        mvc().perform(post("/api/contract/demo-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"item-1\",\"remark\":\"r1\"}")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("item-1"))
                .andExpect(jsonPath("$.data.remark").value("r1"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("正常流程：create → get 按 id 取回，数据一致")
    void normalFlow_createThenGet_roundTrip() throws Exception {
        MockMvc mvc = mvc();
        MvcResult c = mvc.perform(post("/api/contract/demo-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"item-1\"}")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH))
                .andExpect(status().isCreated()).andReturn();
        String id = read(c).get("data").get("id").asText();
        assertTrue(id != null && !id.isEmpty(), "返回 id 非空字符串（前端精度安全）");

        mvc.perform(get("/api/contract/demo-items/" + id).header(HttpHeaders.ACCEPT_LANGUAGE, ZH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.name").value("item-1"));
    }

    @Test
    @DisplayName("正常流程：list 返回 records/total/page/size，且 total 反映真实数量")
    void normalFlow_list_pagingShape() throws Exception {
        MockMvc mvc = mvc();
        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/api/contract/demo-items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"item-" + i + "\"}"))
                    .andExpect(status().isCreated());
        }
        MvcResult list = mvc.perform(get("/api/contract/demo-items")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH)).andReturn();
        JsonNode data = read(list).get("data");
        assertEquals(3, data.get("total").asInt(), "total=3");
        assertEquals(3, data.get("records").size(), "records 含 3 条");
        assertEquals(1, data.get("page").asInt(), "默认 page=1");
        assertEquals(10, data.get("size").asInt(), "默认 size=10");
    }

    // ==================================================================
    // 2) 边界值
    // ==================================================================

    @Test
    @DisplayName("边界值：分页 size/page 钳制（size=0→1, size=999→100, page=0→1）")
    void boundary_pagingClamp() throws Exception {
        MockMvc mvc = mvc();
        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/api/contract/demo-items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"b" + i + "\"}"))
                    .andExpect(status().isCreated());
        }

        JsonNode r1 = read(mvc.perform(get("/api/contract/demo-items")
                .param("page", "0").param("size", "0")).andReturn());
        assertEquals(1, r1.get("data").get("size").asInt(), "size<1 钳制为 1");
        assertEquals(1, r1.get("data").get("page").asInt(), "page<1 钳制为 1");
        assertEquals(1, r1.get("data").get("records").size(), "仅返回 1 条");

        JsonNode r2 = read(mvc.perform(get("/api/contract/demo-items")
                .param("size", "999")).andReturn());
        assertEquals(100, r2.get("data").get("size").asInt(), "size>100 钳制为上限 100");
        assertEquals(3, r2.get("data").get("records").size(), "返回全部 3 条（<=100）");
    }

    @Test
    @DisplayName("边界值：name 为空 → 400 COMMON_001，data 为字段错误 Map 且含 name")
    void boundary_emptyName_400() throws Exception {
        MvcResult r = mvc().perform(post("/api/contract/demo-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH)).andReturn();
        assertEnvelope(r, "COMMON_001", 400);
        assertTrue(read(r).get("data").has("name"), "data 含 name 字段错误");
    }

    @Test
    @DisplayName("边界值：name 缺省（无该字段）→ 400 COMMON_001")
    void boundary_missingName_400() throws Exception {
        MvcResult r = mvc().perform(post("/api/contract/demo-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH)).andReturn();
        assertEnvelope(r, "COMMON_001", 400);
    }

    @Test
    @DisplayName("边界值：name 超长(65) → 400 COMMON_001")
    void boundary_longName_400() throws Exception {
        String longName = "a".repeat(65);
        MvcResult r = mvc().perform(post("/api/contract/demo-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longName + "\"}")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH)).andReturn();
        assertEnvelope(r, "COMMON_001", 400);
    }

    @Test
    @DisplayName("边界值：name=admin 命中业务保留规则 → 422 COMMON_422")
    void boundary_reservedName_422() throws Exception {
        MvcResult r = mvc().perform(post("/api/contract/demo-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"admin\"}")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH)).andReturn();
        assertEnvelope(r, "COMMON_422", 422);
    }

    @Test
    @DisplayName("边界值：查询不存在 id → 404 COMMON_404")
    void boundary_notFound_404() throws Exception {
        MvcResult r = mvc().perform(get("/api/contract/demo-items/9999")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH)).andReturn();
        assertEnvelope(r, "COMMON_404", 404);
    }

    @Test
    @DisplayName("边界值：唯一键冲突（同名二次创建）→ 409 COMMON_409")
    void boundary_duplicate_409() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(post("/api/contract/demo-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"dup\"}"))
                .andExpect(status().isCreated());
        MvcResult r = mvc.perform(post("/api/contract/demo-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"dup\"}")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, ZH)).andReturn();
        assertEnvelope(r, "COMMON_409", 409);
    }

    // ==================================================================
    // 3) 异常场景与错误码（经真实 handler 映射）
    // ==================================================================

    @Test
    @DisplayName("异常场景：除限流外全部错误码，HTTP 状态/code/双语文案均与契约一致")
    void exceptionScenarios_matchContract() throws Exception {
        MockMvc mvc = mvc();
        for (JsonNode sc : contract.get("exceptionScenarios")) {
            String scenario = sc.get("scenario").asText();
            if ("rateLimited".equals(scenario)) continue; // 限流由切面直接返回，无专用 handler，单独校验
            String uri = "/api/contract/diag/" + scenario;
            int http = sc.get("httpStatus").asInt();
            String code = sc.get("code").asText();
            JsonNode ec = errByCode.get(code);

            MvcResult zhR = mvc.perform(get(uri).header(HttpHeaders.ACCEPT_LANGUAGE, ZH)).andReturn();
            assertEnvelope(zhR, code, http);
            assertEquals(ec.get("messageZh").asText(), read(zhR).get("message").asText(),
                    scenario + " zh 文案与契约一致");

            MvcResult enR = mvc.perform(get(uri).header(HttpHeaders.ACCEPT_LANGUAGE, EN)).andReturn();
            assertEnvelope(enR, code, http);
            assertEquals(ec.get("messageEn").asText(), read(enR).get("message").asText(),
                    scenario + " en 文案与契约一致");
        }
    }

    @Test
    @DisplayName("错误码 COMMON_429：枚举与资源束与契约一致（限流由切面直接返回，不走 MockMvc 异常链路）")
    void rateLimited_contractConsistent() {
        JsonNode sc = findScenario("rateLimited");
        String code = sc.get("code").asText();
        JsonNode ec = errByCode.get(code);

        CommonErrorCode e429 = null;
        for (CommonErrorCode e : CommonErrorCode.values()) {
            if (e.code().equals(code)) e429 = e;
        }
        assertNotNull(e429, "枚举必须含 COMMON_429");
        assertEquals(429, e429.httpStatus(), "枚举 HTTP 状态=429");
        assertEquals(ec.get("httpStatus").asInt(), e429.httpStatus(), "契约 HTTP 状态一致");
        assertEquals(ec.get("messageZh").asText(),
                i18n().getMessage("error." + code, Locale.SIMPLIFIED_CHINESE), "zh 文案一致");
        assertEquals(ec.get("messageEn").asText(),
                i18n().getMessage("error." + code, Locale.US), "en 文案一致");
    }

    // ==================================================================
    // 4) 契约一致性（前后端单一事实来源）
    // ==================================================================

    @Test
    @DisplayName("契约 successCodes 必须含字符串 \"0\"（前端须同时兼容数字 0）")
    void contract_successCodes_containStringZero() {
        boolean hasStringZero = false;
        for (JsonNode s : contract.get("successCodes")) {
            if (s.isTextual() && "0".equals(s.asText())) hasStringZero = true;
        }
        assertTrue(hasStringZero, "契约 successCodes 必须含字符串 \"0\"");
    }

    @Test
    @DisplayName("契约错误码表与 CommonErrorCode 枚举双向一致，且资源束双语文案与契约一致")
    void contract_errorCodes_matchEnumAndBundles() {
        Set<String> enumCodes = new HashSet<>();
        for (CommonErrorCode e : CommonErrorCode.values()) {
            enumCodes.add(e.code());
            JsonNode ec = errByCode.get(e.code());
            assertNotNull(ec, "契约必须包含枚举错误码 " + e.code());
            assertEquals(e.httpStatus(), ec.get("httpStatus").asInt(), "HTTP 状态一致: " + e.code());
            assertEquals(ec.get("messageZh").asText(),
                    i18n().getMessage("error." + e.code(), Locale.SIMPLIFIED_CHINESE), "zh 文案一致: " + e.code());
            assertEquals(ec.get("messageEn").asText(),
                    i18n().getMessage("error." + e.code(), Locale.US), "en 文案一致: " + e.code());
        }
        // 反向：契约中每个错误码都必须能在枚举找到对应
        for (JsonNode ec : contract.get("errorCodes")) {
            assertTrue(enumCodes.contains(ec.get("code").asText()),
                    "枚举必须包含契约错误码 " + ec.get("code").asText());
        }
    }

    // ==================================================================
    // 测试内资源（不进入生产装配）
    // ==================================================================

    record DemoItemCreateRequest(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 255) String remark) {
    }

    record DemoItem(String id, String name, String remark, Instant createdAt) {
    }

    record DemoItemView(String id, String name, String remark, Instant createdAt) {
    }

    record DemoItemPage(List<DemoItemView> records, long total, int page, int size) {
    }

    static class DemoItemService {
        private final Map<String, DemoItem> store = new HashMap<>();
        private long seq = 0;

        DemoItemView create(DemoItemCreateRequest req) {
            if ("admin".equals(req.name())) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR);
            }
            for (DemoItem d : store.values()) {
                if (d.name().equals(req.name())) {
                    throw new org.springframework.dao.DataIntegrityViolationException("duplicate name");
                }
            }
            String id = String.valueOf(++seq);
            DemoItem item = new DemoItem(id, req.name(), req.remark(), Instant.now());
            store.put(id, item);
            return new DemoItemView(id, item.name(), item.remark(), item.createdAt());
        }

        DemoItemView get(String id) throws NoResourceFoundException {
            DemoItem d = store.get(id);
            if (d == null) {
                throw new NoResourceFoundException(HttpMethod.GET, "/api/contract/demo-items/" + id);
            }
            return new DemoItemView(d.id(), d.name(), d.remark(), d.createdAt());
        }

        DemoItemPage list(int page, int size) {
            int p = Math.max(1, page);
            int s = Math.max(1, Math.min(100, size));
            List<DemoItemView> all = new ArrayList<>();
            for (DemoItem d : store.values()) {
                all.add(new DemoItemView(d.id(), d.name(), d.remark(), d.createdAt()));
            }
            int from = Math.min((p - 1) * s, all.size());
            int to = Math.min(from + s, all.size());
            return new DemoItemPage(all.subList(from, to), all.size(), p, s);
        }
    }

    @RestController
    @RequestMapping("/api/contract/demo-items")
    static class DemoItemController {
        private final DemoItemService service;

        DemoItemController(DemoItemService service) {
            this.service = service;
        }

        @PostMapping
        ResponseEntity<Result<DemoItemView>> create(@Valid @RequestBody DemoItemCreateRequest req) {
            return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(service.create(req)));
        }

        @GetMapping("/{id}")
        Result<DemoItemView> get(@PathVariable String id) throws NoResourceFoundException {
            return Result.success(service.get(id));
        }

        @GetMapping
        Result<DemoItemPage> list(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int size) {
            return Result.success(service.list(page, size));
        }
    }

    @RestController
    @RequestMapping("/api/contract/diag")
    static class DiagnosticController {
        @GetMapping("/validation")
        void validation() throws Exception {
            org.springframework.validation.BeanPropertyBindingResult br =
                    new org.springframework.validation.BeanPropertyBindingResult(new Object(), "payload");
            br.addError(new org.springframework.validation.FieldError("payload", "username", "must not be blank"));
            throw new org.springframework.web.bind.MethodArgumentNotValidException(null, br);
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new BadCredentialsException("no auth");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/notFound")
        void notFound() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/api/contract/diag/notFound");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new org.springframework.dao.DataIntegrityViolationException("dup");
        }

        @GetMapping("/business")
        void business() {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR);
        }

        @GetMapping("/internal")
        void internal() {
            throw new RuntimeException("boom");
        }

        @GetMapping("/timeout")
        void timeout() throws Exception {
            throw new TimeoutException("slow downstream");
        }
    }
}
