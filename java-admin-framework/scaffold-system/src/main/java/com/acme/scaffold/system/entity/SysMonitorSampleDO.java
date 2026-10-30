package com.acme.scaffold.system.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 系统监控采样记录（由 JobRunr 周期任务写入，供趋势查询）。
 */
@Data
public class SysMonitorSampleDO {

    private Long id;

    private Long tenantId = 0L;
    /** CPU 使用率(%) */
    private BigDecimal cpuUsage;
    /** JVM 堆内存使用率(%) */
    private BigDecimal memoryUsage;
    /** 物理内存使用率(%) */
    private BigDecimal systemMemoryUsage;
    /** 磁盘使用率(%) */
    private BigDecimal diskUsage;

    private Long usedHeapBytes;
    private Long maxHeapBytes;
    private Long usedMemoryBytes;
    private Long totalMemoryBytes;

    private Integer onlineUsers = 0;
    private Integer activeSessions = 0;
    private Integer threadCount = 0;

    private LocalDateTime sampledAt;
}
