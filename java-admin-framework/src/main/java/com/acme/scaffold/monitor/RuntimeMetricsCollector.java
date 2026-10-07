package com.acme.scaffold.monitor;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 运行时指标采集器（<b>零第三方依赖</b>）。
 *
 * <p>本项目要求离线可构建，不引入 OSHI 等外部库，因此直接使用 JDK  Management API：
 * <ul>
 *   <li>CPU：{@link OperatingSystemMXBean#getSystemCpuLoad()}（需强转 com.sun.management），
 *       取两次采样间隔计算均值；不可用时返回 null 而非 0，避免"假装有数据"。</li>
 *   <li>JVM 堆：{@link MemoryMXBean}；物理内存：{@link OperatingSystemMXBean#getTotalMemorySize()}。</li>
 *   <li>磁盘：{@link java.io.File#getTotalSpace()} 取当前工作目录所在卷。</li>
 *   <li>运行时信息：{@link RuntimeMXBean} 提供 JVM 版本、启动时间、线程数、类加载数。</li>
 * </ul>
 */
public class RuntimeMetricsCollector {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OperatingSystemMXBean os;
    private final MemoryMXBean memory;
    private final RuntimeMXBean runtime;
    private final ThreadMXBean threads;
    private final File diskPath;

    private volatile double lastCpuLoad = -1;

    public RuntimeMetricsCollector() {
        this.os = ManagementFactory.getOperatingSystemMXBean();
        this.memory = ManagementFactory.getMemoryMXBean();
        this.runtime = ManagementFactory.getRuntimeMXBean();
        this.threads = ManagementFactory.getThreadMXBean();
        String userDir = System.getProperty("user.dir", ".");
        this.diskPath = new File(userDir);
    }

    /** 服务器与运行状态快照。 */
    public ServerMetrics snapshot() {
        ServerMetrics m = new ServerMetrics();

        m.setCpuUsage(percent(cpuUsage()));
        m.setCpuCores(Runtime.getRuntime().availableProcessors());
        m.setLoadAverage(os.getSystemLoadAverage());
        m.setSystemMemoryUsage(percent(systemMemoryUsage()));
        m.setDiskUsage(percent(diskUsage()));
        m.setDiskPath(diskPath.getAbsolutePath());

        long heapUsed = memory.getHeapMemoryUsage().getUsed();
        long heapMax = memory.getHeapMemoryUsage().getMax();
        m.setMemoryUsage(percent(heapMax > 0 ? (double) heapUsed / heapMax * 100 : -1));
        m.setUsedHeapBytes(heapUsed);
        m.setMaxHeapBytes(heapMax);
        m.setUsedMemoryBytes(totalMemorySize() - freeMemorySize());
        m.setTotalMemoryBytes(totalMemorySize());

        m.setJvmName(System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.version") + ")");
        m.setJavaVersion(System.getProperty("java.version"));
        m.setOsName(os.getName() + " / " + os.getArch());
        m.setUptimeMillis(runtime.getUptime());
        m.setThreadCount(threads.getThreadCount());
        m.setPeakThreadCount(threads.getPeakThreadCount());
        m.setLoadedClassCount((long) ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
        m.setProcessId(ProcessHandle.current().pid());
        m.setSampledAt(LocalDateTime.now().format(FMT));
        return m;
    }

    /**
     * 系统 CPU 使用率(%)。取两次采样（间隔 200ms）均值，降低瞬时抖动。
     */
    private double cpuUsage() {
        double a = readCpuLoad();
        if (a < 0) {
            return -1;
        }
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        double b = readCpuLoad();
        double result = (a + b) / 2;
        lastCpuLoad = result;
        return result;
    }

    /** 强转 com.sun.management.OperatingSystemMXBean 才能拿到 CPU 负载；非 HotSpot 返回 -1。 */
    private double readCpuLoad() {
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            double load = sun.getSystemCpuLoad();
            return load < 0 ? -1 : load;
        }
        return -1;
    }

    /** 物理内存使用率(%)。 */
    private double systemMemoryUsage() {
        long total = totalMemorySize();
        if (total <= 0) {
            return -1;
        }
        return (total - freeMemorySize()) / (double) total * 100;
    }

    /** 物理内存总量：需 com.sun.management 扩展接口，不可用时返回 0。 */
    private long totalMemorySize() {
        return os instanceof com.sun.management.OperatingSystemMXBean sun ? sun.getTotalMemorySize() : 0L;
    }

    /** 物理内存空闲量：需 com.sun.management 扩展接口，不可用时返回 0。 */
    private long freeMemorySize() {
        return os instanceof com.sun.management.OperatingSystemMXBean sun ? sun.getFreeMemorySize() : 0L;
    }

    /** 当前工作目录所在磁盘卷使用率(%)。 */
    private double diskUsage() {
        long total = diskPath.getTotalSpace();
        if (total <= 0) {
            return -1;
        }
        long usable = diskPath.getUsableSpace();
        return (total - usable) / (double) total * 100;
    }

    /** 保留最近一次 CPU 采样，便于定时任务写入历史表。 */
    public double lastCpuLoad() {
        return lastCpuLoad;
    }

    /** 百分比规整为 2 位小数；-1 表示不可用，返回 null 以便前端区分"无数据"。 */
    private static BigDecimal percent(double value) {
        if (value < 0) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
