package com.acme.scaffold.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局 JSON 序列化约定（D3 修复）。
 *
 * <p>将 {@code Long} 装箱类型统一序列化为字符串，避免超过 JS 安全整数（2^53）的雪花 ID
 * 在「后端返回数字 → 前端 JSON.parse 失真 → 回传失真 ID」链路中精度丢失
 * （典型表现：CMS 文章创建的「所属栏目不存在」误报）。</p>
 *
 * <p>仅作用于 {@link Long} 装箱类型；基础类型 {@code long}（如分页计数 total/page/size）
 * 保持数值，不影响前端数值运算与契约中明确定义为数字的字段。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringSerializer() {
        return builder -> builder.serializerByType(Long.class, ToStringSerializer.instance);
    }
}
