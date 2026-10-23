package com.acme.scaffold.common.audit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 审计模块配置（M20 增强）。
 *
 * <p><b>读操作审计开关</b>：{@code app.audit.audit-reads} 默认 {@code false}——
 * 读审计默认关闭，仅当业务需要「留痕谁读取了敏感数据」时显式开启，避免读流量放大审计量。
 * <b>采样率</b>：{@code app.audit.read-sample-rate} ∈ [0,1]，1=全量采样，0=不采样（与开关关闭等价）。
 * <b>留存天数</b>：{@code app.audit.retention-days}（默认 365），到期由 {@code AuditRetentionJob} 清理；≤0 表示不清理。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.audit")
public class AuditProperties {

    /** 是否开启读操作审计（默认关闭）。 */
    private boolean auditReads = false;

    /** 读操作审计采样率 [0,1]，默认 0.1（十分之一）。 */
    private double readSampleRate = 0.1;

    /** 审计日志留存天数（默认 365）；≤0 表示不清理。 */
    private int retentionDays = 365;
}
