package com.acme.scaffold.common.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/** XLSX 读取安全回归：高压缩比 ZIP bomb 必须在解析前被有界解压拒绝。 */
class XlsxDataReaderSafetyTest {

    private static final int CHUNK_SIZE = 8192;

    @Test
    void highlyCompressedEntryShouldBeRejectedBeforeParsing() throws IOException {
        byte[] bomb = zipRepeated(Map.of("xl/worksheets/sheet1.xml", 4200));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> XlsxDataReader.read(bomb));

        assertTrue(ex.getMessage().contains("解压后内容过大"));
    }

    @Test
    void cumulativeUncompressedSizeShouldBeBounded() throws IOException {
        Map<String, Integer> entries = new LinkedHashMap<>();
        entries.put("xl/worksheets/sheet1.xml", 2200);
        entries.put("xl/sharedStrings.xml", 2200);
        byte[] bomb = zipRepeated(entries);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> XlsxDataReader.read(bomb));

        assertTrue(ex.getMessage().contains("解压后内容过大"));
    }

    @Test
    void normalWorkbookShouldStillParse() throws IOException {
        String sheet = """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet><sheetData>
                  <row r="1"><c r="A1" t="inlineStr"><is><t>name</t></is></c></row>
                  <row r="2"><c r="A2" t="inlineStr"><is><t>Alice</t></is></c></row>
                </sheetData></worksheet>
                """;
        byte[] xlsx = zipText(Map.of("xl/worksheets/sheet1.xml", sheet));

        TabularData data = XlsxDataReader.read(xlsx);

        assertEquals(List.of("name"), data.headers());
        assertEquals(1, data.rowCount());
        assertEquals("Alice", data.rows().get(0).get(0));
    }

    private static byte[] zipText(Map<String, String> entries) throws IOException {
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        entries.forEach((name, text) -> bytes.put(name, text.getBytes(StandardCharsets.UTF_8)));
        return zip(bytes);
    }

    private static byte[] zipRepeated(Map<String, Integer> chunksPerEntry) throws IOException {
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        byte[] chunk = new byte[CHUNK_SIZE];
        Arrays.fill(chunk, (byte) 'A');
        chunksPerEntry.forEach((name, chunks) -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int i = 0; i < chunks; i++) {
                out.write(chunk, 0, chunk.length);
            }
            bytes.put(name, out.toByteArray());
        });
        return zip(bytes);
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(compressed)) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return compressed.toByteArray();
    }
}
