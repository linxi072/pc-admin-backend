package com.acme.scaffold.system.file;

import com.acme.scaffold.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 上传文件校验：扩展名白名单 + 关键类型魔数二次校验。
 *
 * <p>仅靠扩展名可被伪造（如把脚本改名成 .png），故对图片与 PDF 再读文件头做真实类型判定；
 * Office / 压缩包 / 文本等仅依赖扩展名白名单放行，避免过度拦截合法业务文档。
 */
public final class FileValidator {

    private FileValidator() {
    }

    /**
     * 从原始文件名解析小写扩展名；无扩展名或不在白名单内抛 {@link FileErrorCode#FILE_TYPE_NOT_ALLOWED}。
     */
    public static String resolveExtension(String originalFilename, Set<String> allowed) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            throw BusinessException.of(FileErrorCode.FILE_TYPE_NOT_ALLOWED, "文件名缺少扩展名");
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!allowed.contains(ext)) {
            throw BusinessException.of(FileErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "不支持的文件类型 ." + ext + "，允许：" + String.join("/", allowed));
        }
        return ext;
    }

    /**
     * 魔数二次校验，杜绝「改扩展名伪装」。仅校验图片与 PDF，其余类型按扩展名放行。
     */
    public static void verifyContent(String ext, InputStream in) {
        byte[] header = new byte[8];
        int read;
        try {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw BusinessException.of(FileErrorCode.FILE_CONTENT_MISMATCH, "文件读取失败");
        }
        if (read < 4) {
            throw BusinessException.of(FileErrorCode.FILE_CONTENT_MISMATCH, "文件内容为空或已损坏");
        }
        boolean ok = switch (ext) {
            case "jpg", "jpeg" -> (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8;
            case "png" -> (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
            case "gif" -> "GIF8".equalsIgnoreCase(new String(header, 0, 4));
            case "webp" -> header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F';
            case "pdf" -> "%PDF".equalsIgnoreCase(new String(header, 0, 4));
            default -> true;
        };
        if (!ok) {
            throw BusinessException.of(FileErrorCode.FILE_CONTENT_MISMATCH, "文件内容与扩展名不符");
        }
    }
}
