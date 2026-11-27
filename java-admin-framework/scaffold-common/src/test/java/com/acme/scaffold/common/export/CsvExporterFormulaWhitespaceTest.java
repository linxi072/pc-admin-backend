package com.acme.scaffold.common.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CSV 公式注入前导空白绕过回归：与前端 csv.js 保持同口径。 */
class CsvExporterFormulaWhitespaceTest {

    @Test
    void leadingSpaceBeforeFormulaShouldBeNeutralized() {
        assertEquals("'  =cmd", CsvExporter.neutralizeFormula("  =cmd"));
        assertEquals("'   @evil", CsvExporter.neutralizeFormula("   @evil"));
    }

    @Test
    void leadingWhitespaceVariantsShouldBeNeutralized() {
        for (String ws : List.of(" ", "\t", "\r", "\n", "\u00A0", "\u3000")) {
            assertEquals("'" + ws + "=cmd", CsvExporter.neutralizeFormula(ws + "=cmd"),
                    "前导空白 " + escape(ws) + " 不应绕过公式防护");
        }
    }

    @Test
    void leadingSpacesWithNumericShouldStayNumber() {
        assertEquals("  -5", CsvExporter.neutralizeFormula("  -5"));
        // Tab / CR 本身即危险首字符，数值例外不适用于它们，沿用改造前口径。
        assertEquals("'\t+3.14", CsvExporter.neutralizeFormula("\t+3.14"));
    }

    @Test
    void pureSpacesShouldStayUnchangedButTabStillNeutralized() {
        assertEquals("   ", CsvExporter.neutralizeFormula("   "));
        assertEquals("'   \t", CsvExporter.neutralizeFormula("   \t"));
    }

    @Test
    void writeShouldNeutralizeLeadingSpaceFormulaInCells() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        CsvExporter.write(out, List.of("备注"), List.of(List.of("  @evil")));

        String csv = out.toString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("'  @evil"), "写出端也必须中和前导空白后的公式: " + csv);
    }

    private static String escape(String ws) {
        return ws.replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }
}
