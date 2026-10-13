package com.acme.scaffold.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 参数配置视图。id 为 18 位雪花 ID，超出 JS Number.MAX_SAFE_INTEGER，序列化为字符串防精度丢失。
 */
public record ConfigVO(@JsonSerialize(using = ToStringSerializer.class) Long id,
                       String configKey, String configName, String configValue,
                       String configType, String remark, String status) {
}
