package com.acme.scaffold.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 业务指标：登录成功/失败计数、审批耗时等，供 Prometheus/Grafana 观测。
 */
@Component
public class BusinessMetrics {

    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Timer approvalTimer;

    public BusinessMetrics(MeterRegistry registry) {
        this.loginSuccess = Counter.builder("app.login.total")
                .tag("result", "success").description("登录成功次数").register(registry);
        this.loginFailure = Counter.builder("app.login.total")
                .tag("result", "failure").description("登录失败次数").register(registry);
        this.approvalTimer = Timer.builder("app.approval.duration")
                .description("审批处理耗时（毫秒）").register(registry);
    }

    public void recordLoginSuccess() {
        loginSuccess.increment();
    }

    public void recordLoginFailure() {
        loginFailure.increment();
    }

    public void recordApproval(long milliseconds) {
        approvalTimer.record(milliseconds, TimeUnit.MILLISECONDS);
    }
}
