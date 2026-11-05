package com.acme.scaffold.config.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 缓存异常处理器（P1-3 防雪崩）：仅记录日志并降级，不向上抛出异常，避免缓存层故障穿透到业务事务。
 *
 * <p>降级语义：
 * <ul>
 *   <li>读取失败 ⇒ 视为缓存未命中，回落数据源（由调用方重新加载）；</li>
 *   <li>写入/淘汰/清空失败 ⇒ 静默丢弃，不阻断主流程（缓存最终由 TTL 自然收敛）。</li>
 * </ul>
 *
 * <p>Spring 6.1 的 {@code AbstractCacheManager} 尚未提供 {@code setCacheErrorHandler}（6.2 才引入），
 * 故在 {@link StatisticsCache} 装饰层捕获异常并路由至此处理器，做到版本无关、不依赖具体 Cache 实现。</p>
 */
@Slf4j
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    /** 共享单例：所有缓存装饰器复用同一实例，避免重复创建。 */
    public static final LoggingCacheErrorHandler INSTANCE = new LoggingCacheErrorHandler();

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("缓存读取失败，降级为数据源（cache={}, key={}）: {}",
                cache != null ? cache.getName() : "?", key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("缓存写入失败，静默丢弃（cache={}, key={}）: {}",
                cache != null ? cache.getName() : "?", key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("缓存淘汰失败，忽略（cache={}, key={}）: {}",
                cache != null ? cache.getName() : "?", key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("缓存清空失败，忽略（cache={}）: {}",
                cache != null ? cache.getName() : "?", exception.getMessage());
    }
}
