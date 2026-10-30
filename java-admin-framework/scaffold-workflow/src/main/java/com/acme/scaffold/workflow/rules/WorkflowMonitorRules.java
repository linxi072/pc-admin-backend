package com.acme.scaffold.workflow.rules;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 流程监控纯函数规则（M23）：超时判定、停留/超时时长换算、催办防骚扰间隔、监控筛选归一。
 *
 * <p>与 {@link ClaimRules} 同范式：全部为无副作用静态函数，不触引擎与 DB，
 * 由 {@code WorkflowMonitorService} 编排调用，单测直接固化语义。</p>
 */
public final class WorkflowMonitorRules {

    /** 实例状态白名单（与 wf_instance_ext.status 落库值一致）。 */
    public static final Set<String> INSTANCE_STATUSES = Set.of("RUNNING", "APPROVED", "REJECTED");

    /** 监控实例状态筛选的「全部」取值（大小写不敏感）。 */
    public static final String STATUS_ALL = "ALL";

    /** 关键字长度上限（与列表页关键字输入框约定一致，防超长 LIKE）。 */
    public static final int MAX_KEYWORD_LENGTH = 64;

    private WorkflowMonitorRules() {
    }

    /**
     * 超时判定：已配置到期时间且已过点。
     *
     * @param dueAt 到期时间（null 表示该任务未设期限，永不超时）
     * @param now   当前时刻
     */
    public static boolean isOverdue(LocalDateTime dueAt, LocalDateTime now) {
        return dueAt != null && now != null && dueAt.isBefore(now);
    }

    /** 超时期分钟数（未到期或未设期限返回 0，不返回负数）。 */
    public static long overdueMinutes(LocalDateTime dueAt, LocalDateTime now) {
        if (dueAt == null || now == null) {
            return 0L;
        }
        long minutes = Duration.between(dueAt, now).toMinutes();
        return Math.max(minutes, 0L);
    }

    /** 停留分钟数（startedAt 为空或晚于 now 时返回 0，不返回负数）。 */
    public static long stayedMinutes(LocalDateTime startedAt, LocalDateTime now) {
        if (startedAt == null || now == null) {
            return 0L;
        }
        long minutes = Duration.between(startedAt, now).toMinutes();
        return Math.max(minutes, 0L);
    }

    /**
     * 催办防骚扰判定：同一任务距上次催办须满 {@code minIntervalMinutes} 分钟才允许再次催办。
     *
     * <p>语义：从未催办（{@code lastRemindAt == null}）或间隔配置为非正数（关闭限流）时放行；
     * 否则按「实际经过分钟数 &gt;= 间隔」判定（取整分钟，边界等于间隔即放行）。</p>
     *
     * @param lastRemindAt       上次催办时刻（null 表示从未催办）
     * @param now                当前时刻
     * @param minIntervalMinutes 最小催办间隔（分钟，&lt;=0 表示不限制）
     */
    public static boolean canRemind(LocalDateTime lastRemindAt, LocalDateTime now, int minIntervalMinutes) {
        if (lastRemindAt == null || now == null || minIntervalMinutes <= 0) {
            return true;
        }
        return Duration.between(lastRemindAt, now).toMinutes() >= minIntervalMinutes;
    }

    /**
     * 归一实例状态筛选：白名单内返回规范大写名；{@code ALL}/空按「全部」回落 null；
     * 非法值同样回落 null（按全部处理，避免 400 惊扰监控页轮询）。
     */
    public static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        if (STATUS_ALL.equals(s)) {
            return null;
        }
        return INSTANCE_STATUSES.contains(s) ? s : null;
    }

    /**
     * 归一关键字筛选：去首尾空白，空串回落 null，超长截断到 {@link #MAX_KEYWORD_LENGTH}。
     * 截断而非拒绝：监控页作为高频轮询面，输入超长属误操作，截断后按前缀匹配更符合直觉。
     */
    public static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String k = keyword.trim();
        return k.length() > MAX_KEYWORD_LENGTH ? k.substring(0, MAX_KEYWORD_LENGTH) : k;
    }

    /**
     * 批量解析用户名（供监控列表展示）：入参为用户 id 列表（允许 null 元素），
     * 返回 id -&gt; 用户名的映射（查不到者跳过，由调用方回落显示 id）。
     *
     * <p>说明：单次 IN 查询装载，避免列表逐行解析用户名造成 N+1（与 M4 既有
     * {@code resolveUsername} 单条版对应，监控页批量场景专用）。</p>
     */
    public static List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }
}
