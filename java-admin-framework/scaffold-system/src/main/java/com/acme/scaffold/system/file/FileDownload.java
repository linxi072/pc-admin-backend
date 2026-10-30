package com.acme.scaffold.system.file;

import org.springframework.core.io.Resource;

/**
 * 下载结果：文件资源 + 原始文件名 + 内容类型，供 Controller 组装响应头。
 */
public record FileDownload(Resource resource, String originalName, String contentType) {
}
