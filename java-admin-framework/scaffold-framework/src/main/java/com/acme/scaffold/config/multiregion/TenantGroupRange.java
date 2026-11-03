package com.acme.scaffold.config.multiregion;

/**
 * 租户组区间 → 部署单元映射（按租户 ID 闭区间 [from, to] 批量归属）。
 *
 * <p>用于「大租户组整体物理隔离」：将一段连续租户 ID 划给某个部署单元（具名数据源）。
 * 解析优先级低于显式 {@code tenantToUnit}，高于默认单元。</p>
 */
public class TenantGroupRange {

    private long from;
    private long to;
    private String unit;

    public TenantGroupRange() {
    }

    public TenantGroupRange(long from, long to, String unit) {
        this.from = from;
        this.to = to;
        this.unit = unit;
    }

    /** 租户 ID 是否落入本区间（含端点）。 */
    public boolean contains(long tenantId) {
        return tenantId >= from && tenantId <= to;
    }

    public long getFrom() {
        return from;
    }

    public void setFrom(long from) {
        this.from = from;
    }

    public long getTo() {
        return to;
    }

    public void setTo(long to) {
        this.to = to;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}
