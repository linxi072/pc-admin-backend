package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.system.export.config.ExportProperties;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 导出中心控制器测试。
 *
 * <p>覆盖三类关注点：
 * <ul>
 *   <li><b>向后兼容</b>：缺省 {@code format} 仍返回 CSV（旧前端零改动可用）；</li>
 *   <li><b>新增 XLSX</b>：返回 ZIP/OOXML 字节与正确 content-type / 文件名后缀；</li>
 *   <li><b>校验</b>：未知格式 / 未知模板 / 多段结构模板 / 超行数上限均给出明确错误。</li>
 * </ul>
 *
 * <p>用 standalone MockMvc + 最小 advice 复刻生产 {@code GlobalExceptionHandler} 对
 * {@link BusinessException} 的状态码映射，使错误分支断言真实反映线上行为（无 advice 时异常会以
 * ServletException 冒泡，无法验证状态码）。</p>
 */
class ExportControllerTest {

    /** 单表结构模板（headers 非空 → 支持 CSV + XLSX）。 */
    private static ExportTask filesTask() {
        return new ExportTask() {
            @Override
            public String code() {
                return "files";
            }

            @Override
            public String displayName() {
                return "文件管理列表";
            }

            @Override
            public String fileName() {
                return "file-list";
            }

            @Override
            public List<String> headers() {
                return List.of("ID", "文件名");
            }

            @Override
            public List<List<String>> rows(Map<String, String> params) {
                return List.of(List.of("1", "a.txt"), List.of("2", "b,c.csv"));
            }
        };
    }

    /** 多段结构模板（headers 为空、覆写 csv()）→ 仅支持 CSV。 */
    private static ExportTask reportTask() {
        return new ExportTask() {
            @Override
            public String code() {
                return "report";
            }

            @Override
            public String displayName() {
                return "报表管理导出";
            }

            @Override
            public String fileName() {
                return "report-export";
            }

            @Override
            public List<String> headers() {
                return List.of();
            }

            @Override
            public List<List<String>> rows(Map<String, String> params) {
                return List.of();
            }

            @Override
            public byte[] csv(Map<String, String> params) {
                return "report-segment".getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    private MockMvc mvc(ExportProperties props, ExportTask... tasks) {
        ExportController controller = new ExportController(List.of(tasks), props);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TestAdvice())
                .build();
    }

    private MockMvc mvc() {
        return mvc(new ExportProperties(), filesTask(), reportTask());
    }

    @Test
    void templates_exposeSupportedFormatsPerTask() throws Exception {
        mvc().perform(get("/api/system/export/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                // 单表结构：CSV + XLSX
                .andExpect(jsonPath("$.data[0].code").value("files"))
                .andExpect(jsonPath("$.data[0].fileName").value("file-list"))
                .andExpect(jsonPath("$.data[0].formats.length()").value(2))
                .andExpect(jsonPath("$.data[0].formats[0]").value("CSV"))
                .andExpect(jsonPath("$.data[0].formats[1]").value("XLSX"))
                // 多段结构：仅 CSV
                .andExpect(jsonPath("$.data[1].code").value("report"))
                .andExpect(jsonPath("$.data[1].formats.length()").value(1))
                .andExpect(jsonPath("$.data[1].formats[0]").value("CSV"));
    }

    @Test
    void download_withoutFormat_stillReturnsCsv() throws Exception {
        byte[] body = mvc().perform(get("/api/system/export/download").param("code", "files"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString("file-list.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsByteArray();

        // UTF-8 BOM 前缀（Excel 中文不乱码）
        assertEquals((byte) 0xEF, body[0]);
        assertEquals((byte) 0xBB, body[1]);
        assertEquals((byte) 0xBF, body[2]);
        String text = new String(body, StandardCharsets.UTF_8);
        assertTrue(text.contains("ID,文件名"), "应含中文表头: " + text);
        assertTrue(text.contains("\"b,c.csv\""), "含逗号字段应按 RFC4180 加引号: " + text);
    }

    @Test
    void download_xlsx_returnsZipWithXlsxContentType() throws Exception {
        byte[] body = mvc().perform(get("/api/system/export/download")
                        .param("code", "files")
                        .param("format", "XLSX"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString("file-list.xlsx")))
                .andExpect(content().contentTypeCompatibleWith(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();

        // ZIP 魔术字节（PK\x03\x04）
        assertEquals('P', body[0]);
        assertEquals('K', body[1]);
    }

    @Test
    void download_multiSegmentTemplate_xlsxRejected() throws Exception {
        mvc().perform(get("/api/system/export/download")
                        .param("code", "report")
                        .param("format", "XLSX"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void download_unknownFormat_rejected() throws Exception {
        mvc().perform(get("/api/system/export/download")
                        .param("code", "files")
                        .param("format", "PDF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void download_unknownTemplate_notFound() throws Exception {
        mvc().perform(get("/api/system/export/download").param("code", "nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_404"));
    }

    @Test
    void download_rowsExceedingLimit_rejected() throws Exception {
        ExportProperties props = new ExportProperties();
        props.setMaxRows(1); // filesTask 有 2 行数据 → 超限
        mvc(props, filesTask()).perform(get("/api/system/export/download")
                        .param("code", "files")
                        .param("format", "XLSX"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void exportProperties_rejectsNonPositiveLimit() {
        ExportProperties props = new ExportProperties();
        props.setMaxRows(0);
        assertEquals(1, props.getMaxRows(), "上限下限应为 1，避免非法配置使任何导出都被拒绝");
        props.setMaxRows(-5);
        assertEquals(1, props.getMaxRows());
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
