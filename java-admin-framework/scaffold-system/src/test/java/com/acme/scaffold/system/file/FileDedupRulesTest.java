package com.acme.scaffold.system.file;

import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileDedupRules} 纯函数单测：哈希计算与 JDK MessageDigest 交叉验证、
 * 哈希格式校验、ID 去重保序、引用计数删除保护边界。离线可跑。
 */
class FileDedupRulesTest {

    @Test
    @DisplayName("sha256Hex 与 JDK MessageDigest 已知向量一致")
    void sha256Hex_knownVectors() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                FileDedupRules.sha256Hex(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                FileDedupRules.sha256Hex(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    @DisplayName("sha256Hex 与 MessageDigest 随机内容交叉验证（跨缓冲区边界）")
    void sha256Hex_matchesMessageDigest() throws Exception {
        byte[] data = new byte[100_000]; // 远超 8192 缓冲区，覆盖多次 read 累加
        new Random(42).nextBytes(data);
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : expected) {
            sb.append(String.format("%02x", b));
        }
        assertEquals(sb.toString(), FileDedupRules.sha256Hex(new ByteArrayInputStream(data)));
    }

    @Test
    @DisplayName("normalizeHash 合法 64 位十六进制统一转小写")
    void normalizeHash_validLowercases() {
        String upper = "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789";
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                FileDedupRules.normalizeHash(upper));
    }

    @Test
    @DisplayName("normalizeHash 非法格式抛 FILE_HASH_INVALID")
    void normalizeHash_invalidThrows() {
        assertHashInvalid(null);
        assertHashInvalid("");
        assertHashInvalid("   ");
        assertHashInvalid("abc");                 // 过短
        assertHashInvalid("a".repeat(63));        // 63 位
        assertHashInvalid("a".repeat(65));        // 65 位
        assertHashInvalid("g".repeat(64));        // 非十六进制字符
    }

    @Test
    @DisplayName("distinctIds 保序去重、过滤 null、null 入参安全")
    void distinctIds_dedupesPreservingOrder() {
        assertEquals(List.of(3L, 1L, 2L),
                FileDedupRules.distinctIds(Arrays.asList(3L, 1L, 3L, null, 2L, 1L)));
        assertTrue(FileDedupRules.distinctIds(null).isEmpty());
        assertTrue(FileDedupRules.distinctIds(List.of()).isEmpty());
    }

    @Test
    @DisplayName("shouldDeletePhysical 边界：引用数 <=1 才删物理文件")
    void shouldDeletePhysical_boundary() {
        assertTrue(FileDedupRules.shouldDeletePhysical(0));
        assertTrue(FileDedupRules.shouldDeletePhysical(1));
        assertFalse(FileDedupRules.shouldDeletePhysical(2));
        assertFalse(FileDedupRules.shouldDeletePhysical(Long.MAX_VALUE));
    }

    private static void assertHashInvalid(String hash) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> FileDedupRules.normalizeHash(hash));
        assertEquals("FILE_005", ex.getErrorCode().code());
    }
}
