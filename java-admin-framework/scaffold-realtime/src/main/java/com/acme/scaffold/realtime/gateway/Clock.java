package com.acme.scaffold.realtime.gateway;

/**
 * 时钟端口（可测试性基础设施）。
 *
 * <p>热路径全部时间判断（心跳、限流、TTL、排空超时）经此端口取时间，
 * 单测用 {@link MutableClock} 精确驱动，杜绝 sleep 与时间竞态。
 */
public interface Clock {

    long nowMillis();

    /** 生产时钟。 */
    final class System implements Clock {
        private static final System INSTANCE = new System();

        private System() {
        }

        public static System instance() {
            return INSTANCE;
        }

        @Override
        public long nowMillis() {
            return java.lang.System.currentTimeMillis();
        }
    }
}
