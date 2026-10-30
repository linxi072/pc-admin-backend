package com.acme.scaffold.monitor.health;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 存活探针（liveness）：仅检查 JVM 与应用主循环是否存活，<b>不依赖任何外部依赖</b>
 * （数据库、缓存、第三方服务）。进程能执行到此即视为存活；仅当可用堆逼近 0（即将 OOM）时
 * 才标记 DOWN，以触发容器重启。设计为无 Spring 依赖的纯函数，便于离线单测。
 */
public final class LivenessProbe {

    private static final long CRITICAL_FREE_MEMORY_BYTES = 1L << 20; // 1 MiB

    private LivenessProbe() {
    }

    public static ProbeResult check() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("jvm", "alive");
        details.put("availableProcessors", runtime.availableProcessors());
        details.put("uptimeMillis", ManagementFactory.getRuntimeMXBean().getUptime());
        long freeMemory = runtime.freeMemory();
        details.put("freeMemoryBytes", freeMemory);
        if (freeMemory < CRITICAL_FREE_MEMORY_BYTES) {
            return ProbeResult.down("jvm free memory critically low", details);
        }
        return ProbeResult.up(details);
    }
}
