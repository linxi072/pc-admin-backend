package com.acme.scaffold.security.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * 基于 Redis 的分布式固定窗口限流器：多实例共享同一计数，按 key 维度（IP / 用户 / 全局）
 * 跨实例协同限流。
 *
 * <p>实现要点：
 * <ul>
 *   <li><b>原子计数</b>：窗口起点对齐到 {@code windowSeconds} 整数倍毫秒，将
 *       {@code prefix:key:windowStart} 作为 Redis 键；通过 Lua 脚本原子执行
 *       {@code incr + 首次访问设置 pexpire(windowMillis)}，避免「读-改-写」竞态；</li>
 *   <li><b>自动过期</b>：每个窗口键 TTL 恰为窗口长度，无需后台清理，进入下一窗口自然重新计数；</li>
 *   <li><b>fail-open</b>：Redis 连接失败 / 超时（{@link DataAccessException}）或脚本返回空时，
 *       自动回落到本地 {@link InMemoryRateLimiter}，保证限流不成为可用性瓶颈
 *       （代价是该窗口在多实例下计数不共享）；</li>
 *   <li><b>零新依赖</b>：复用 Spring Boot {@code RedisAutoConfiguration} 提供的
 *       {@link StringRedisTemplate}（项目 {@code scaffold-framework} 已引入
 *       {@code spring-boot-starter-data-redis}），仅本模块将其纳入编译 classpath。</li>
 * </ul>
 */
@Slf4j
public class RedisRateLimiter implements RateLimiter {

    /**
     * 固定窗口原子计数器（Lua）：自增并返回当前计数；首次访问时按窗口长度设置 TTL。
     * KEYS[1] = 窗口键；ARGV[1] = 窗口长度（毫秒）。
     */
    private static final String FIXED_WINDOW_LUA =
            "local n = redis.call('incr', KEYS[1])\n"
                    + "if n == 1 then redis.call('pexpire', KEYS[1], ARGV[1]) end\n"
                    + "return n";

    private static final RedisScript<Long> FIXED_WINDOW_SCRIPT = new DefaultRedisScript<>(FIXED_WINDOW_LUA, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimiter fallback;
    private final RateLimitProperties properties;

    public RedisRateLimiter(StringRedisTemplate redisTemplate, RateLimiter fallback, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.fallback = fallback;
        this.properties = properties;
    }

    @Override
    public boolean tryAcquire(String key, int max, int windowSeconds) {
        return tryAcquire(key, max, windowSeconds, System.currentTimeMillis());
    }

    @Override
    public boolean tryAcquire(String key, int max, int windowSeconds, long nowMillis) {
        long windowMillis = (long) windowSeconds * 1000L;
        long windowStart = nowMillis - (nowMillis % windowMillis);
        String redisKey = properties.getKeyPrefix() + ":" + key + ":" + windowStart;
        try {
            // 注意：redisTemplate 是 StringRedisTemplate，其 args 序列化器为 StringRedisSerializer，
            // 仅接受 String。windowMillis 虽为 Long，但 Lua 脚本把它当作字符串数字（pexpire 的毫秒数）
            // 使用，故此处转成 String 传入，避免 DefaultScriptExecutor 用 StringRedisSerializer
            // 序列化 Long 触发 ClassCastException（java.lang.Long cannot be cast to java.lang.String）。
            Long count = redisTemplate.execute(FIXED_WINDOW_SCRIPT, List.of(redisKey), String.valueOf(windowMillis));
            if (count == null) {
                log.warn("Redis 限流计数返回空（key={}），回落本地计数", redisKey);
                return fallback.tryAcquire(key, max, windowSeconds, nowMillis);
            }
            return count <= max;
        } catch (DataAccessException ex) {
            // Redis 不可用：fail-open 回落本地计数，避免限流自身阻断业务
            log.warn("Redis 不可用，限流回落本地计数（key={}）: {}", redisKey, ex.toString());
            return fallback.tryAcquire(key, max, windowSeconds, nowMillis);
        }
    }

    /**
     * 当前某 key 的窗口计数（观测用）。Redis 路径按当前时间窗口键读取；
     * 读取失败（或 Redis 不可用）时回落本地兜底计数。
     */
    @Override
    public int currentCount(String key) {
        long nowMillis = System.currentTimeMillis();
        long windowMillis = 60_000L;
        long windowStart = nowMillis - (nowMillis % windowMillis);
        String redisKey = properties.getKeyPrefix() + ":" + key + ":" + windowStart;
        try {
            String raw = redisTemplate.opsForValue().get(redisKey);
            return raw == null ? 0 : Integer.parseInt(raw);
        } catch (DataAccessException ex) {
            log.debug("Redis 读取限流计数失败（key={}），回落本地计数", redisKey);
            return fallback.currentCount(key);
        }
    }
}
