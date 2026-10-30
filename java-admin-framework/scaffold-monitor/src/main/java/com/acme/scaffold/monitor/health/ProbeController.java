package com.acme.scaffold.monitor.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康探针端点（根路径，k8s/容器编排风格）：
 * <ul>
 *   <li>{@code GET /livez}：存活探针，仅依赖 JVM，永不直接依赖外部服务；进程存活即返回 200。</li>
 *   <li>{@code GET /readyz}：就绪探针，检查数据库等必需依赖；不可用时返回 503，由编排层摘除流量。</li>
 * </ul>
 * 两者均在 {@code SecurityConfig} 中 permitAll（探针不应要求鉴权）。
 */
@RestController
public class ProbeController {

    private final ReadinessService readinessService;

    public ProbeController(ReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @GetMapping("/livez")
    public ResponseEntity<ProbeResult> livez() {
        ProbeResult result = LivenessProbe.check();
        return ResponseEntity.status(toHttpStatus(result)).body(result);
    }

    @GetMapping("/readyz")
    public ResponseEntity<ProbeResult> readyz() {
        ProbeResult result = readinessService.check();
        return ResponseEntity.status(toHttpStatus(result)).body(result);
    }

    private static int toHttpStatus(ProbeResult result) {
        return result.status() == ProbeStatus.UP ? 200 : 503;
    }
}
