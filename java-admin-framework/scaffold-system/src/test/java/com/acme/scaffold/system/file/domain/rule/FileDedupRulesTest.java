package com.acme.scaffold.system.file.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 文件秒传去重纯规则单测（TEST-COVERAGE-PLAN 第二批 / T3 system 主链路）。
 * 锁定 SHA-256 摘要、哈希格式校验、批量去重保序与物理删除引用计数契约。
 */
class FileDedupRulesTest {

    @Test
    void sha256HexShouldMatchKnownDigest() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        // echo -n hello | sha256sum
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                FileDedupRules.sha256Hex(new ByteArrayInputStream(content)));
    }

    @Test
    void sha256HexOfEmptyContentShouldBeKnownConstant() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                FileDedupRules.sha256Hex(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void normalizeHashShouldLowercaseValidAndRejectInvalid() {
        String upper = "A".repeat(64);
        assertEquals("a".repeat(64), FileDedupRules.normalizeHash(upper));
        assertEquals("0".repeat(64), FileDedupRules.normalizeHash("0".repeat(64)));
        assertThrows(BusinessException.class, () -> FileDedupRules.normalizeHash(null));
        assertThrows(BusinessException.class, () -> FileDedupRules.normalizeHash(""));
        assertThrows(BusinessException.class, () -> FileDedupRules.normalizeHash("abc"));
        assertThrows(BusinessException.class, () -> FileDedupRules.normalizeHash("g".repeat(64)),
                "非十六进制字符应拒绝");
    }

    @Test
    void distinctIdsShouldFilterNullAndKeepFirstOccurrenceOrder() {
        assertEquals(List.of(), FileDedupRules.distinctIds(null));
        assertEquals(List.of(), FileDedupRules.distinctIds(List.of()));
        assertEquals(List.of(3L, 1L, 2L),
                FileDedupRules.distinctIds(Arrays.asList(3L, null, 1L, 3L, 2L, null)));
    }

    @Test
    void shouldDeletePhysicalOnlyWhenRefCountAtMostOne() {
        assertTrue(FileDedupRules.shouldDeletePhysical(0));
        assertTrue(FileDedupRules.shouldDeletePhysical(1));
        assertFalse(FileDedupRules.shouldDeletePhysical(2), "仍被其他记录引用时不删物理文件");
        assertFalse(FileDedupRules.shouldDeletePhysical(100));
    }
}
