package com.acme.scaffold.monitor.health;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 就绪探针（readiness）：检查提供服务所必需的依赖是否就绪。当前必需依赖为数据库；
 * 可降级的缓存（如 Redis）即使故障也不应摘除实例（见《方案》§10.3）。
 */
@Service
public class ReadinessService {

    private final DatabaseProbe databaseProbe;

    public ReadinessService(DatabaseProbe databaseProbe) {
        this.databaseProbe = databaseProbe;
    }

    public ProbeResult check() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean dbUp = databaseProbe.isAvailable();
        details.put("database", dbUp ? "up" : "down");
        if (!dbUp) {
            return ProbeResult.down("database unavailable", details);
        }
        return ProbeResult.up(details);
    }
}
