package com.acme.scaffold.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 岗位下拉选项（用户表单等多选场景使用，仅返回启用岗位）。
 *
 * @param id       岗位 ID（雪花 ID，序列化为字符串防 JS 精度丢失）
 * @param postName 岗位名称
 */
public record PostOptionVO(@JsonSerialize(using = ToStringSerializer.class) Long id,
                           String postName) {
}
