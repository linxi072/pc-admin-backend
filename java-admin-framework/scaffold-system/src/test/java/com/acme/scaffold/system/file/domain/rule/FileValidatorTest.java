package com.acme.scaffold.system.file.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.acme.scaffold.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 文件上传校验纯规则单测（TEST-COVERAGE-PLAN 第二批 / T3 system 主链路）。
 * 锁定扩展名白名单与关键类型魔数二次校验契约（防改扩展名伪装）。
 */
class FileValidatorTest {

    private static final Set<String> ALLOWED = Set.of("png", "jpg", "jpeg", "gif", "webp", "pdf", "txt", "docx");

    @Test
    void resolveExtensionShouldLowercaseAndRejectInvalid() {
        assertEquals("png", FileValidator.resolveExtension("avatar.PNG", ALLOWED));
        assertEquals("docx", FileValidator.resolveExtension("report.DOCX", ALLOWED));
        assertThrows(BusinessException.class, () -> FileValidator.resolveExtension("noextension", ALLOWED));
        assertThrows(BusinessException.class, () -> FileValidator.resolveExtension("  ", ALLOWED));
        assertThrows(BusinessException.class, () -> FileValidator.resolveExtension("script.exe", ALLOWED));
    }

    @Test
    void verifyContentShouldAcceptMatchingMagicNumbers() {
        assertMagicOk("png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4});
        assertMagicOk("jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3, 4, 5, 6});
        assertMagicOk("jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3, 4, 5, 6});
        assertMagicOk("gif", "GIF89a!!".getBytes(StandardCharsets.US_ASCII));
        assertMagicOk("webp", "RIFFxxxx".getBytes(StandardCharsets.US_ASCII));
        assertMagicOk("pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void verifyContentShouldRejectMismatchAndTruncated() {
        assertThrows(BusinessException.class, () -> FileValidator.verifyContent("png",
                new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2})));
        assertThrows(BusinessException.class, () -> FileValidator.verifyContent("pdf",
                new ByteArrayInputStream(new byte[]{(byte) 0x89, 'P', 'N', 'G'})));
        assertThrows(BusinessException.class, () -> FileValidator.verifyContent("png",
                new ByteArrayInputStream(new byte[]{1, 2, 3})), "不足 4 字节应判损坏");
    }

    @Test
    void verifyContentShouldPassThroughUnknownExtension() {
        // 非图片/PDF 类型按扩展名白名单放行，不做魔数校验
        assertMagicOk("txt", "hello!!".getBytes(StandardCharsets.US_ASCII));
        assertMagicOk("docx", new byte[]{0, 1, 2, 3});
    }

    private static void assertMagicOk(String ext, byte[] header) {
        FileValidator.verifyContent(ext, new ByteArrayInputStream(header));
    }
}
