package com.acme.scaffold.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DnsResolvers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置：默认仅用于缓存、限流与短期状态。权限与令牌的最终事实源仍是数据库。
 * <p>
 * 默认情况下 Lettuce 使用 Netty 的异步 DNS 解析器（{@code DnsNameResolver}）。在受限网络
 * 或容器环境中，该解析器即便对 {@code 127.0.0.1} 这类字面量地址也可能解析失败
 * （表现为 {@code Unable to connect to 127.0.0.1/<unresolved>}），导致 Redis 健康检查降级。
 * 此处显式指定 {@link DnsResolvers#JVM_DEFAULT}（基于 {@code InetAddress} 的 JVM 原生解析器），
 * 对任意主机名与 IP 字面量均能正确解析，从而在所有环境下保持 Redis 健康探针可用。
 * <p>
 * Spring Boot 的 {@code LettuceConnectionConfiguration} 会按类型查找 {@link ClientResources}
 * Bean 并用于构建 {@code LettuceConnectionFactory}，无需额外装配。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public ClientResources clientResources() {
        return ClientResources.builder()
                .dnsResolver(DnsResolvers.JVM_DEFAULT)
                .build();
    }
}
