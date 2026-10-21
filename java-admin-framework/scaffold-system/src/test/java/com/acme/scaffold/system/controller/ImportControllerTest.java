package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.importer.ImportRow;
import com.acme.scaffold.common.importer.ImportTask;
import com.acme.scaffold.common.importer.ImportValidators;
import com.acme.scaffold.system.importer.config.ImportProperties;
import com.acme.scaffold.system.service.ImportCenterService;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 导入中心控制器测试。
 *
 * <p>覆盖三类关注点：
 * <ul>
 *   <li><b>模板</b>：列举模板表头；下载空白模板默认 XLSX、可切换 CSV（含 UTF-8 BOM）；</li>
 *   <li><b>上传</b>：CSV 预检返回统计；</li>
 *   <li><b>校验</b>：未知格式 / 未知模板 / 空文件 / 不支持的扩展名均给出明确错误状态码。</li>
 * </ul>
 *
 * <p>用 standalone MockMvc + 最小 advice 复刻生产 {@code GlobalExceptionHandler} 对
 * {@link BusinessException} 的状态码映射，使错误分支断言真实反映线上行为。</p>
 */
class ImportControllerTest {

    private static ImportTask demoTask() {
        return new ImportTask() {
            @Override
            public String code() {
                return "demo";
            }

            @Override
            public String displayName() {
                return "演示数据";
            }

            @Override
            public List<String> headers() {
                return List.of("名称", "数量");
            }

            @Override
            public List<String> validate(ImportRow row) {
                // 名称必填 + 数量须为 1~999 整数：数量非法使注入载荷所在行进入失败报告
                return ImportValidators.collect(
                        ImportValidators.required(row.trimmed("名称"), "名称"),
                        ImportValidators.integerRange(row.trimmed("数量"), "数量", 1, 999, false));
            }

            @Override
            public void importRow(ImportRow row) {
                // 控制器测试不关注落库行为
            }
        };
    }

    private MockMvc mvc() {
        return mvc(new ImportProperties());
    }

    private MockMvc mvc(ImportProperties props) {
        ImportCenterService service = new ImportCenterService(List.of(demoTask()), props);
        return MockMvcBuilders.standaloneSetup(new ImportController(service))
                .setControllerAdvice(new TestAdvice())
                .build();
    }

    @Test
    void templates_exposeHeaders() throws Exception {
        mvc().perform(get("/api/system/import/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].code").value("demo"))
                .andExpect(jsonPath("$.data[0].displayName").value("演示数据"))
                .andExpect(jsonPath("$.data[0].headers.length()").value(2))
                .andExpect(jsonPath("$.data[0].headers[0]").value("名称"));
    }

    @Test
    void template_defaultsToXlsx() throws Exception {
        byte[] body = mvc().perform(get("/api/system/import/template").param("code", "demo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString(".xlsx")))
                .andExpect(content().contentTypeCompatibleWith(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals('P', body[0]);
        assertEquals('K', body[1]);
    }

    @Test
    void template_csvCarriesBomAndHeaders() throws Exception {
        byte[] body = mvc().perform(get("/api/system/import/template")
                        .param("code", "demo")
                        .param("format", "CSV"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString(".csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals((byte) 0xEF, body[0]);
        assertEquals((byte) 0xBB, body[1]);
        assertEquals((byte) 0xBF, body[2]);
        String text = new String(body, StandardCharsets.UTF_8);
        assertTrue(text.contains("名称,数量"), "模板应含表头: " + text);
    }

    @Test
    void template_unknownFormatRejected() throws Exception {
        mvc().perform(get("/api/system/import/template")
                        .param("code", "demo")
                        .param("format", "PDF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void template_unknownCodeNotFound() throws Exception {
        mvc().perform(get("/api/system/import/template").param("code", "nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_404"));
    }

    @Test
    void upload_csvDryRunReturnsSummary() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "demo.csv", "text/csv",
                "名称,数量\nA,1\nB,2\n".getBytes(StandardCharsets.UTF_8));

        mvc().perform(multipart("/api/system/import/demo").file(file).param("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.errors.length()").value(0));
    }

    @Test
    void upload_reportsRowErrors() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "demo.csv", "text/csv",
                "名称,数量\n,1\n".getBytes(StandardCharsets.UTF_8));

        mvc().perform(multipart("/api/system/import/demo").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.errors.length()").value(1))
                .andExpect(jsonPath("$.data.errors[0].rowNo").value(1));
    }

    @Test
    void upload_emptyFileRejected() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "demo.csv", "text/csv", new byte[0]);
        mvc().perform(multipart("/api/system/import/demo").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void upload_unsupportedExtensionRejected() throws Exception {
        MockMultipartFile txt = new MockMultipartFile("file", "demo.txt", "text/plain",
                "名称,数量\nA,1\n".getBytes(StandardCharsets.UTF_8));
        mvc().perform(multipart("/api/system/import/demo").file(txt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void upload_errorReportReturnsCsvAttachmentOnFailures() throws Exception {
        // "=cmd" 模拟 CSV 公式注入载荷；数量 x 非法使该行失败；dryRun 预检即可触发报告（不落库）
        MockMultipartFile file = new MockMultipartFile("file", "demo.csv", "text/csv",
                "名称,数量\n=cmd,x\n".getBytes(StandardCharsets.UTF_8));
        byte[] body = mvc().perform(multipart("/api/system/import/demo").file(file)
                        .param("dryRun", "true").param("errorReport", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString(".csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals((byte) 0xEF, body[0]);
        assertEquals((byte) 0xBB, body[1]);
        assertEquals((byte) 0xBF, body[2]);
        String text = new String(body, StandardCharsets.UTF_8);
        assertTrue(text.contains("数据行号,失败原因,名称,数量"), "报告表头: " + text);
        // 公式注入载荷加单引号前缀按文本展示；失败行原始值应还原（x 为行尾列）
        assertTrue(text.contains("'=cmd,x"), "应防公式注入且还原原始值: " + text);
    }

    @Test
    void upload_errorReportFallsBackToJsonWhenAllSucceed() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "demo.csv", "text/csv",
                "名称,数量\nA,1\nB,2\n".getBytes(StandardCharsets.UTF_8));

        mvc().perform(multipart("/api/system/import/demo").file(file)
                        .param("dryRun", "true").param("errorReport", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failureCount").value(0));
    }

    /** 复刻生产全局异常处理器对 BusinessException → HTTP 状态码的映射。 */
    @RestControllerAdvice
    static class TestAdvice {

        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
            return ResponseEntity.status(ex.getErrorCode().httpStatus())
                    .body(Result.error(ex.getErrorCode().code(), ex.getMessage()));
        }
    }
}
