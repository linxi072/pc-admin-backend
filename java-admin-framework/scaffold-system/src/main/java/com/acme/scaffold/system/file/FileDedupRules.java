package com.acme.scaffold.system.file;

import com.acme.scaffold.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 文件秒传去重纯函数集合（V50）。
 *
 * <p>三项能力共享本类：
 * <ul>
 *   <li><b>秒传</b>：{@link #sha256Hex(InputStream)} 计算内容 SHA-256 十六进制，作为
 *       {@code sys_file.content_hash} 去重锚点；命中存量记录则复用物理文件不落盘；</li>
 *   <li><b>预检</b>：{@link #normalizeHash(String)} 校验前端上传前回传的哈希格式
 *       （64 位十六进制），非法请求直接 400，不进入存储层；</li>
 *   <li><b>删除保护</b>：{@link #shouldDeletePhysical(int)} 按引用计数决定是否删除物理文件，
 *       {@link #distinctIds(Collection)} 为批量删除去重（保序）。</li>
 * </ul>
 *
 * <p>全部方法无副作用、不触库，异常路径统一抛 {@link BusinessException}（带 {@link FileErrorCode}），
 * 与 {@link FileValidator} 风格一致。
 */
public final class FileDedupRules {

    /** SHA-256 十六进制长度（32 字节 × 2）。 */
    private static final int HASH_LENGTH = 64;

    private static final int BUFFER_SIZE = 8192;

    private FileDedupRules() {
    }

    /**
     * 计算输入流内容的 SHA-256 十六进制摘要（小写）。
     *
     * <p>流读取失败（客户端断连、磁盘错误）抛 {@link FileErrorCode#FILE_STORE_FAILED}；
     * 空内容返回空串的 SHA-256（{@code e3b0c442...}），由调用方按业务规则处理。
     */
    public static String sha256Hex(InputStream in) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制实现，理论上不可达
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
        try (DigestInputStream dis = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (dis.read(buffer) != -1) {
                // 读入即经 DigestInputStream 更新摘要，无需额外处理
            }
        } catch (IOException e) {
            throw BusinessException.of(FileErrorCode.FILE_STORE_FAILED, "文件读取失败，无法计算内容摘要");
        }
        return toHex(digest.digest());
    }

    /**
     * 校验并规范化内容哈希：必须为 64 位十六进制字符，统一转小写后返回；
     * 空值或非法格式抛 {@link FileErrorCode#FILE_HASH_INVALID}。
     */
    public static String normalizeHash(String hash) {
        if (!StringUtils.hasText(hash) || hash.length() != HASH_LENGTH || !isHex(hash)) {
            throw BusinessException.of(FileErrorCode.FILE_HASH_INVALID, "内容哈希必须为 64 位十六进制字符串");
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    /**
     * 批量删除 ID 去重（保持首次出现顺序），过滤 null 元素；入参为 null 时返回空列表。
     */
    public static List<Long> distinctIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                seen.add(id);
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * 是否可删除物理文件：同一 {@code stored_name} 的未删记录数（含自身）&le; 1 时才删物理文件，
     * 否则仅逻辑删记录，避免误删仍被其他记录引用的物理文件。
     */
    public static boolean shouldDeletePhysical(long refCount) {
        return refCount <= 1;
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'f';
            boolean upper = c >= 'A' && c <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
