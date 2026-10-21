package com.acme.scaffold.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 字典类型视图。id 为 18 位雪花 ID，超出 JS Number.MAX_SAFE_INTEGER，序列化为字符串防精度丢失。
 */
public record DictTypeVO(@JsonSerialize(using = ToStringSerializer.class) Long id,
                         String dictCode, String dictName, String status, Integer sortNo, String remark) {
}
