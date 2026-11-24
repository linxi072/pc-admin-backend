package com.acme.scaffold.system.export.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.export.ExportFormat;
import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.common.export.XlsxSheet;
import com.acme.scaffold.system.export.config.ExportProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 导出中心单测（E5）：PDF 三格式分流、多段模板降级与格式清单。 */
class ExportCenterServiceTest {

    @Test
    void shouldListCsvXlsxPdfForSingleTableTemplate() {
        ExportCenterService service = new ExportCenterService(List.of(new SampleTask()), new ExportProperties());
        var templates = service.listTemplates();
        assertEquals(1, templates.size());
        assertEquals(List.of("CSV", "XLSX", "PDF"), templates.get(0).formats());
    }

    @Test
    void shouldGeneratePdfBytesForPdfFormat() {
        ExportCenterService service = new ExportCenterService(List.of(new SampleTask()), new ExportProperties());
        var file = service.download(Map.of("code", "sample", "format", "pdf"));
        assertEquals(ExportFormat.PDF, file.format());
        String pdf = new String(file.body(), StandardCharsets.ISO_8859_1);
        assertTrue(pdf.startsWith("%PDF-1.4"));
        assertTrue(pdf.contains("<FEFF59D3540D")); // “姓名” 的 UCS2-BE
    }

    @Test
    void shouldRejectPdfForMultiSectionTemplateWithoutOverride() {
        ExportTask multiSection = new ExportTask() {
            @Override public String code() { return "multi"; }
            @Override public String displayName() { return "多段"; }
            @Override public String fileName() { return "multi"; }
            @Override public List<String> headers() { return List.of(); }
            @Override public List<List<String>> rows(Map<String, String> params) { return List.of(); }
            @Override public List<XlsxSheet> xlsxSheets(Map<String, String> params) {
                return List.of(new XlsxSheet("汇总", List.of("列"), List.of(List.of("值"))));
            }
            @Override public boolean supportsXlsx() { return true; }
        };
        ExportCenterService service = new ExportCenterService(List.of(multiSection), new ExportProperties());
        var templates = service.listTemplates();
        assertEquals(List.of("CSV", "XLSX"), templates.get(0).formats());
        assertThrows(RuntimeException.class,
                () -> service.download(Map.of("code", "multi", "format", "PDF")));
    }

    private static final class SampleTask implements ExportTask {
        @Override public String code() { return "sample"; }
        @Override public String displayName() { return "样例"; }
        @Override public String fileName() { return "sample"; }
        @Override public List<String> headers() { return List.of("姓名", "年龄"); }
        @Override public List<List<String>> rows(Map<String, String> params) {
            return List.of(List.of("张三", "30"));
        }
    }
}
