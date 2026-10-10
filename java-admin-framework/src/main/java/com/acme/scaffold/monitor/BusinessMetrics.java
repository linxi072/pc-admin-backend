package com.acme.scaffold.monitor;

import com.acme.scaffold.observability.MetricsRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 业务指标：登录成功/失败计数、审批耗时等，经 {@link MetricsRegistry} 桥接至 Micrometer / Prometheus / Grafana。
 */
@Component
public class BusinessMetrics {

    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Timer approvalTimer;

    public BusinessMetrics(MetricsRegistry metrics) {
        this.loginSuccess = metrics.counter("app.login.total", "登录成功次数", "result", "success");
        this.loginFailure = metrics.counter("app.login.total", "登录失败次数", "result", "failure");
        this.approvalTimer = metrics.timer("app.approval.duration", "审批处理耗时（毫秒）");
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
