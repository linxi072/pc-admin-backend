package com.acme.scaffold.system.export.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.ExportFormat;
import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.common.export.XlsxSheet;
import com.acme.scaffold.system.export.config.ExportProperties;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportCenterServiceTest {

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

    /** 多段结构模板（覆写 xlsxSheets）→ CSV + XLSX 双格式。 */
    private static ExportTask reportXlsxTask() {
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

            @Override
            public boolean supportsXlsx() {
                return true;
            }

            @Override
            public List<XlsxSheet> xlsxSheets(Map<String, String> params) {
                return List.of(
                        new XlsxSheet("汇总", List.of("指标", "数值"), List.of(List.of("访问量", "100"))),
                        new XlsxSheet("趋势", List.of("时间"), List.of(List.of("10-15"))));
            }
        };
    }

    @Test
    void listTemplates_exposesSupportedFormats() {
        ExportCenterService service = new ExportCenterService(List.of(filesTask(), reportTask()), new ExportProperties());
        var list = service.listTemplates();
        assertEquals(2, list.size());
        assertEquals(List.of("CSV", "XLSX"), list.get(0).formats());
        assertEquals(List.of("CSV"), list.get(1).formats());
    }

    @Test
    void download_csv_defaultWhenFormatMissing() {
        ExportCenterService service = new ExportCenterService(List.of(filesTask()), new ExportProperties());
        var file = service.download(Map.of("code", "files"));
        assertEquals("file-list", file.fileName());
        assertEquals(ExportFormat.CSV, file.format());

        // UTF-8 BOM 前缀（Excel 中文不乱码）
        byte[] body = file.body();
        assertEquals((byte) 0xEF, body[0]);
        assertEquals((byte) 0xBB, body[1]);
        assertEquals((byte) 0xBF, body[2]);
        String text = new String(body, StandardCharsets.UTF_8);
        assertTrue(text.contains("ID,文件名"), "应含中文表头: " + text);
        assertTrue(text.contains("\"b,c.csv\""), "含逗号字段应按 RFC4180 加引号: " + text);
    }

    @Test
    void download_xlsx_singleSheetProducesZip() {
        ExportCenterService service = new ExportCenterService(List.of(filesTask()), new ExportProperties());
        var file = service.download(Map.of("code", "files", "format", "XLSX"));
        assertEquals(ExportFormat.XLSX, file.format());
        byte[] body = file.body();
        assertEquals('P', body[0]);
        assertEquals('K', body[1]);
    }

    @Test
    void download_unknownTemplate_notFound() {
        ExportCenterService service = new ExportCenterService(List.of(filesTask()), new ExportProperties());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.download(Map.of("code", "nope")));
        assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void download_unknownFormat_badRequest() {
        ExportCenterService service = new ExportCenterService(List.of(filesTask()), new ExportProperties());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.download(Map.of("code", "files", "format", "PDF")));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void download_multiSegmentXlsxUnsupported_rejected() {
        ExportCenterService service = new ExportCenterService(List.of(reportTask()), new ExportProperties());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.download(Map.of("code", "report", "format", "XLSX")));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void download_rowsExceedingLimit_rejected() {
        ExportProperties props = new ExportProperties();
        props.setMaxRows(1); // filesTask 有 2 行数据 → 超限
        ExportCenterService service = new ExportCenterService(List.of(filesTask()), props);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.download(Map.of("code", "files", "format", "XLSX")));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void download_multiSheetRowsExceedingLimit_rejected() {
        ExportProperties props = new ExportProperties();
        props.setMaxRows(1); // reportXlsxTask 两张表共 2 行数据 → 超限
        ExportCenterService service = new ExportCenterService(List.of(reportXlsxTask()), props);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.download(Map.of("code", "report", "format", "XLSX")));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }
}
