package com.acme.scaffold.common.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 格式解析测试：覆盖大小写、前后空格、非法值。
 */
class ExportFormatTest {

    @Test
    void parsesCaseInsensitively() {
        assertEquals(ExportFormat.CSV, ExportFormat.from("csv"));
        assertEquals(ExportFormat.CSV, ExportFormat.from("CSV"));
        assertEquals(ExportFormat.XLSX, ExportFormat.from("xlsx"));
    }

    @Test
    void trimsAndRejectsInvalid() {
        assertEquals(ExportFormat.XLSX, ExportFormat.from("  XLSX  "));
        assertNull(ExportFormat.from("PDF"));
        assertNull(ExportFormat.from(null));
    }

    @Test
    void exposesExtensionAndContentType() {
        assertEquals("csv", ExportFormat.CSV.extension());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                ExportFormat.XLSX.contentType());
    }
}
