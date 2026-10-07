package com.acme.scaffold.monitor;

import java.math.BigDecimal;

/**
 * 服务器与 JVM 运行状态指标（零依赖采集）。
 * 百分比字段为 null 表示当前 JVM/平台不支持该指标采集，前端应显示 "--" 而非 0。
 */
public class ServerMetrics {

    // ---- CPU ----
    private BigDecimal cpuUsage;
    private Integer cpuCores;
    private Double loadAverage;

    // ---- 内存 ----
    private BigDecimal memoryUsage;
    private BigDecimal systemMemoryUsage;
    private Long usedHeapBytes;
    private Long maxHeapBytes;
    private Long usedMemoryBytes;
    private Long totalMemoryBytes;

    // ---- 磁盘 ----
    private BigDecimal diskUsage;
    private String diskPath;

    // ---- JVM / 运行时 ----
    private String jvmName;
    private String javaVersion;
    private String osName;
    private Long uptimeMillis;
    private Integer threadCount;
    private Integer peakThreadCount;
    private Long loadedClassCount;
    private Long processId;

    private String sampledAt;

    public BigDecimal getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(BigDecimal cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public Integer getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(Integer cpuCores) {
        this.cpuCores = cpuCores;
    }

    public Double getLoadAverage() {
        return loadAverage;
    }

    public void setLoadAverage(Double loadAverage) {
        this.loadAverage = loadAverage;
    }

    public BigDecimal getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(BigDecimal memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public BigDecimal getSystemMemoryUsage() {
        return systemMemoryUsage;
    }

    public void setSystemMemoryUsage(BigDecimal systemMemoryUsage) {
        this.systemMemoryUsage = systemMemoryUsage;
    }

    public Long getUsedHeapBytes() {
        return usedHeapBytes;
    }

    public void setUsedHeapBytes(Long usedHeapBytes) {
        this.usedHeapBytes = usedHeapBytes;
    }

    public Long getMaxHeapBytes() {
        return maxHeapBytes;
    }

    public void setMaxHeapBytes(Long maxHeapBytes) {
        this.maxHeapBytes = maxHeapBytes;
    }

    public Long getUsedMemoryBytes() {
        return usedMemoryBytes;
    }

    public void setUsedMemoryBytes(Long usedMemoryBytes) {
        this.usedMemoryBytes = usedMemoryBytes;
    }

    public Long getTotalMemoryBytes() {
        return totalMemoryBytes;
    }

    public void setTotalMemoryBytes(Long totalMemoryBytes) {
        this.totalMemoryBytes = totalMemoryBytes;
    }

    public BigDecimal getDiskUsage() {
        return diskUsage;
    }

    public void setDiskUsage(BigDecimal diskUsage) {
        this.diskUsage = diskUsage;
    }

    public String getDiskPath() {
        return diskPath;
    }

    public void setDiskPath(String diskPath) {
        this.diskPath = diskPath;
    }

    public String getJvmName() {
        return jvmName;
    }

    public void setJvmName(String jvmName) {
        this.jvmName = jvmName;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public Long getUptimeMillis() {
        return uptimeMillis;
    }

    public void setUptimeMillis(Long uptimeMillis) {
        this.uptimeMillis = uptimeMillis;
    }

    public Integer getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(Integer threadCount) {
        this.threadCount = threadCount;
    }

    public Integer getPeakThreadCount() {
        return peakThreadCount;
    }

    public void setPeakThreadCount(Integer peakThreadCount) {
        this.peakThreadCount = peakThreadCount;
    }

    public Long getLoadedClassCount() {
        return loadedClassCount;
    }

    public void setLoadedClassCount(Long loadedClassCount) {
        this.loadedClassCount = loadedClassCount;
    }

    public Long getProcessId() {
        return processId;
    }

    public void setProcessId(Long processId) {
        this.processId = processId;
    }

    public String getSampledAt() {
        return sampledAt;
    }

    public void setSampledAt(String sampledAt) {
        this.sampledAt = sampledAt;
    }
}
