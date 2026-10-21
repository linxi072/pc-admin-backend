package com.acme.scaffold.job.dto;

/**
 * 可选处理器选项（供前端「处理器」下拉）。
 *
 * @param key  处理器标识（taskKey）
 * @param name 展示名称
 */
public record JobHandlerOptionVO(String key, String name) {
}
