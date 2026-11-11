package com.acme.scaffold.system.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 文件下载响应共享构造方法。
 *
 * <p>{@code ExportController} 与 {@code ImportController} 原本各自内联了一份完全相同的「字节 →
 * 带 Content-Disposition 的附件响应」逻辑；此处收敛为单一事实来源，格式相关的扩展名与媒体类型由调用方透传。</p>
 */
public final class FileResponseSupport {

    private FileResponseSupport() {
    }

    /** 构造以 {@code fileName.extension} 为附件名、指定媒体类型的响应体。 */
    public static ResponseEntity<byte[]> fileResponse(byte[] body, String fileName, String extension, String contentType) {
        String fullName = fileName + "." + extension;
        String encoded = URLEncoder.encode(fullName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(body.length)
                .body(body);
    }
}
