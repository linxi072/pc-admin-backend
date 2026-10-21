package com.acme.scaffold.config.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 缓存管理装配：开启 Spring Cache 注解（{@code @Cacheable}/{@code @CacheEvict}），
 * 并以 Redis 为默认实现提供带统计能力的 {@link StatisticsCacheManager}。
 *
 * <p>选型说明：
 * <ul>
 *   <li>不引入 Caffeine 等二级缓存：框架基线以「Redis 单实现 + 进程内统计」满足
 *       「命中率查看 + 清除」诉求，避免离线仓库无构件与多级一致性复杂度；</li>
 *   <li>值序列化用 {@link GenericJackson2JsonRedisSerializer}（与
 *       {@code config.redis.RedisConfig} 的 RedisTemplate 一致），跨版本兼容优于 JDK 序列化，
 *       且不要求 VO 实现 {@code Serializable}；</li>
 *   <li>TTL 与 key 前缀经 {@link CacheProperties} 配置化，未配置时取默认值。</li>
 * </ul>
 *
 * <p>Redis 不可用时上下文仍可启动（Lettuce 连接惰性建立），仅缓存读写失败；
 * 与健康探针的降级策略保持一致。
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
@ConditionalOnClass(RedisConnectionFactory.class)
public class CacheConfig {

    @Bean
    @ConditionalOnMissingBean(name = "cacheManager")
    public StatisticsCacheManager cacheManager(ObjectProvider<RedisConnectionFactory> connectionFactory,
                                               ObjectProvider<ObjectMapper> objectMapper,
                                               CacheProperties properties) {
        RedisConnectionFactory factory = connectionFactory.getObject();
        ObjectMapper mapper = objectMapper.getObject();
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.getTtl())
                .prefixCacheNameWith(properties.getKeyPrefix())
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));
        RedisCacheManager redisCacheManager = RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .build();
        return new StatisticsCacheManager(redisCacheManager);
    }
}
