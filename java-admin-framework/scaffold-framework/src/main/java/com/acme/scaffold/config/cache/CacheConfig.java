package com.acme.scaffold.config.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 *       且不要求 VO 实现 {@link java.io.Serializable}；</li>
 *   <li>TTL 与 key 前缀经 {@link CacheProperties} 配置化，未配置时取默认值。</li>
 * </ul>
 *
 * <p><b>类型信息必须显式开启</b>：{@code GenericJackson2JsonRedisSerializer} 直接使用调用方
 * 传入的 {@link ObjectMapper}，而应用共享 mapper 未激活 default typing —— 缓存值以
 * <b>无类型 JSON</b> 落库，反序列化回 {@code LinkedHashMap}（记录类为 final，
 * {@code NON_FINAL} typing 亦不覆盖），{@code @Cacheable} 方法返回
 * {@code List<XxxVO>} 命中缓存后调用方强转即 {@code ClassCastException}
 * （机构树/菜单树等泛型返回值全部命中）。故此处用<b>独立副本</b>激活
 * {@code EVERYTHING} typing（{@code @class} 随值持久化），并以包前缀白名单限定
 * 反序列化目标，防 gadget 链。
 *
 * <p>{@code app.cache.enabled=false} 时整个配置类（含 {@code @EnableCaching}）不注册，
 * 缓存注解整体失效——集成测试用：既避开 Redis 序列化路径，也杜绝
 * {@code @Transactional} 回滚后缓存残留旧数据导致的跨用例脏读。
 *
 * <p>Redis 不可用时上下文仍可启动（Lettuce 连接惰性建立），仅缓存读写失败；
 * 与健康探针的降级策略保持一致。
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "app.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CacheConfig {

    @Bean
    @ConditionalOnMissingBean(name = "cacheManager")
    public StatisticsCacheManager cacheManager(ObjectProvider<RedisConnectionFactory> connectionFactory,
                                               ObjectProvider<ObjectMapper> objectMapper,
                                               CacheProperties properties) {
        RedisConnectionFactory factory = connectionFactory.getObject();
        ObjectMapper appMapper = objectMapper.getIfAvailable();
        ObjectMapper cacheMapper = appMapper == null ? new ObjectMapper() : appMapper.copy();
        // 激活默认类型信息（@class 随值持久化）：复制应用 mapper 配置后独立激活，
        // 避免污染 REST 出口的共享 mapper（那会给所有响应体加上 @class）。
        // 限定可信包前缀：仅本包与 JDK 集合/基础/时间类型允许反序列化。
        cacheMapper.activateDefaultTypingAsProperty(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.acme.scaffold.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.time.")
                        .build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                "@class");
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.getTtl())
                .prefixCacheNameWith(properties.getKeyPrefix())
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheMapper)));
        RedisCacheManager redisCacheManager = RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .build();
        return new StatisticsCacheManager(redisCacheManager);
    }
}
