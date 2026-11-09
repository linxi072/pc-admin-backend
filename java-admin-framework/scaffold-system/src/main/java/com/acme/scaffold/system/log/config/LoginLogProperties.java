package com.acme.scaffold.system.log.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录日志模块配置（登录日志留存清理，对应 operations §27.6「后续方向」）。
 *
 * <p><b>留存天数</b>：{@code app.login-log.retention-days}（默认 365），由 {@code LoginLogRetentionJob}
 * 按 cron 清理发生时间早于截止点的记录；≤0 表示不清理。审计日志留存配置见
 * {@code AuditProperties}（{@code app.audit.retention-days}），二者独立、互不影响。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.login-log")
public class LoginLogProperties {

    /** 登录日志留存天数（默认 365）；≤0 表示不清理。 */
    private int retentionDays = 365;
}
