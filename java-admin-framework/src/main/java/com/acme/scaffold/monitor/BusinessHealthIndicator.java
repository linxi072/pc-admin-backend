package com.acme.scaffold.monitor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 业务健康探针：返回应用标识与版本，作为 /actuator/health 自定义健康项。
 */
@Component("business")
public class BusinessHealthIndicator implements HealthIndicator {

    @Value("${spring.application.name}")
    private String appName;

    @Override
    public Health health() {
        return Health.up()
                .withDetail("application", appName)
                .withDetail("scaffold", "ready")
                .build();
    }
}
