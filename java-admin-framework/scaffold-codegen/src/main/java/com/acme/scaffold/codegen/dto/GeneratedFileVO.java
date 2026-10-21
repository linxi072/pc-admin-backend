package com.acme.scaffold.codegen.dto;

/**
 * 单个生成文件（预览与 zip 下载的统一载体）。
 *
 * @param path     建议落盘相对路径（如 src/main/java/com/acme/.../UserController.java）
 * @param language 语言标签（JAVA / VUE / JS / SQL，前端预览着色用）
 * @param content  文件内容
 */
public record GeneratedFileVO(String path, String language, String content) {
}
