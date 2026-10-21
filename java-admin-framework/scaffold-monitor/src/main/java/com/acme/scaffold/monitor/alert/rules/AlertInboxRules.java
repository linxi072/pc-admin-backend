package com.acme.scaffold.monitor.alert.rules;

import com.acme.scaffold.monitor.alert.AlertInboxEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 告警收件箱查询纯函数规则：筛选值归一、读取上限钳制、汇总统计。
 *
 * <p>无副作用、不依赖 Spring 上下文，可直接离线单测；供
 * {@link com.acme.scaffold.monitor.alert.service.AlertInboxService}（编排）复用，
 * 保证「怎么筛」「怎么汇总」口径单一来源。</p>
 *
 * <p><b>容错查询 posture</b>：与登录日志 / 短信记录 / 幂等记录页一致——非法筛选值
 * <b>忽略而非报错</b>（返回 {@code null} 表示不附加该条件）。告警管理页是排障工具，
 * 操作员从告警平台复制一段 severity 粘贴进来就该能搜，不该先被参数校验弹回。
 * status 例外：白名单外的值一律忽略（等同不筛选），绝不静默映射成别的状态。</p>
 */
public final class AlertInboxRules {

    /** 告警状态白名单（Alertmanager webhook v3 语义，大小写不敏感）。 */
    public static final String STATUS_FIRING = "firing";
    public static final String STATUS_RESOLVED = "resolved";

    /** 读取条数缺省值：收件箱容量默认 200，100 条一屏覆盖多数复盘场景。 */
    public static final int DEFAULT_LIMIT = 100;

    /** severity 归一后长度上限：label 值超宽截断而非报错（防御畸形推送）。 */
    public static final int MAX_SEVERITY_LEN = 32;

    /** summary 视图截断长度：annotations.summary 可能很长，视图只保留头部可读片段。 */
    public static final int MAX_SUMMARY_LEN = 200;

    /** severity 缺失时的汇总桶名：畸形推送不在汇总里消失，保证「总数 = 各桶之和」。 */
    public static final String UNKNOWN_SEVERITY = "unknown";

    private AlertInboxRules() {
    }

    /**
     * 状态筛选值归一：仅接受 {@value #STATUS_FIRING} / {@value #STATUS_RESOLVED}
     * （大小写不敏感），其余（含空）返回 {@code null}——调用方据此忽略该筛选条件。
     */
    public static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String s = status.trim().toLowerCase();
        return STATUS_FIRING.equals(s) || STATUS_RESOLVED.equals(s) ? s : null;
    }

    /**
     * severity 筛选值归一：trim + 小写后按 {@value #MAX_SEVERITY_LEN} 截断；
     * 空值返回 {@code null}（忽略筛选）。severity 是自由 label（critical / warning /
     * info / page ……），不做白名单——白名单会把自定义等级误杀为「不筛选」。
     */
    public static String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return null;
        }
        return truncate(severity.trim().toLowerCase(), MAX_SEVERITY_LEN);
    }

    /**
     * 读取条数钳制：{@code null} 取 {@value #DEFAULT_LIMIT}；小于 1 钳为 1；
     * 超过 {@code maxAllowed} 钳为 {@code maxAllowed}（收件箱至多容纳容量条，
     * 读取上限高于容量没有意义，按容量自适应即可）。
     */
    public static int normalizeLimit(Integer raw, int maxAllowed) {
        int max = Math.max(1, maxAllowed);
        if (raw == null) {
            return Math.min(DEFAULT_LIMIT, max);
        }
        return Math.min(Math.max(1, raw), max);
    }

    /**
     * 纯筛选：按归一后的 status / severity 精确匹配（大小写不敏感），
     * 保持入参顺序（收件箱本身最新在前）。任一条件为 {@code null} 表示不参与筛选。
     */
    public static List<AlertInboxEntry> filter(List<AlertInboxEntry> entries, String status, String severity) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        String st = normalizeStatus(status);
        String sv = normalizeSeverity(severity);
        if (st == null && sv == null) {
            return new ArrayList<>(entries);
        }
        List<AlertInboxEntry> out = new ArrayList<>(entries.size());
        for (AlertInboxEntry e : entries) {
            if (st != null && !st.equalsIgnoreCase(e.getStatus())) {
                continue;
            }
            if (sv != null && !sv.equalsIgnoreCase(e.getSeverity())) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    /**
     * 收件箱汇总（纯函数）：总数 / firing / resolved 分布 / severity 分布 /
     * 最新与最旧留存时间（epoch 毫秒，空箱为 0）。
     *
     * <p>severity 分布按计数降序、计数相同按键名字母序输出（{@link LinkedHashMap}
     * 保序），保证 JSON 序列化稳定，前端可直接渲染Top-N 而不必再排序。
     * severity 为 {@code null} 或空的条目归入 {@value #UNKNOWN_SEVERITY} 桶——
     * 畸形推送不应在汇总里消失，否则「总数 ≠ 各桶之和」会误导排障。</p>
     */
    public static Summary summarize(List<AlertInboxEntry> entries, int capacity) {
        List<AlertInboxEntry> safe = entries == null ? List.of() : entries;
        int total = safe.size();
        int firing = 0;
        int resolved = 0;
        long mostRecent = 0L;
        long oldest = 0L;
        Map<String, Integer> bySeverity = new TreeMap<>();
        for (AlertInboxEntry e : safe) {
            if (STATUS_FIRING.equalsIgnoreCase(e.getStatus())) {
                firing++;
            } else if (STATUS_RESOLVED.equalsIgnoreCase(e.getStatus())) {
                resolved++;
            }
            String key = e.getSeverity() == null || e.getSeverity().isBlank()
                    ? UNKNOWN_SEVERITY : e.getSeverity().trim().toLowerCase();
            bySeverity.merge(key, 1, Integer::sum);
            long millis = toEpochMilli(e.getReceivedAt());
            if (millis > 0) {
                mostRecent = Math.max(mostRecent, millis);
                oldest = oldest == 0 ? millis : Math.min(oldest, millis);
            }
        }
        return new Summary(capacity, total, firing, resolved, sortByCountDesc(bySeverity), mostRecent, oldest);
    }

    /** 通用截断：null 原样返回，超长截取前 {@code max} 字符（不追加省略号，用于视图展示）。 */
    public static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        if (max <= 0) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static long toEpochMilli(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }

    /** 计数降序重排（计数相同按键名字母序，TreeMap 迭代序天然满足）。 */
    private static Map<String, Integer> sortByCountDesc(Map<String, Integer> bySeverity) {
        List<Map.Entry<String, Integer>> pairs = new ArrayList<>(bySeverity.entrySet());
        pairs.sort((a, b) -> {
            int cmp = b.getValue().compareTo(a.getValue());
            return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
        });
        Map<String, Integer> sorted = new LinkedHashMap<>(pairs.size());
        for (Map.Entry<String, Integer> p : pairs) {
            sorted.put(p.getKey(), p.getValue());
        }
        return sorted;
    }

    /**
     * 收件箱汇总结果（纯数据，由服务层映射为
     * {@link com.acme.scaffold.monitor.dto.AlertInboxStatsView} 视图）。
     */
    public record Summary(int capacity, int total, int firing, int resolved,
                          Map<String, Integer> severityCounts, long mostRecentEpochMillis,
                          long oldestRetainedEpochMillis) {
    }
}
