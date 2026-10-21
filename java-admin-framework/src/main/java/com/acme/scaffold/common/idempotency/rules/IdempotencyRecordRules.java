package com.acme.scaffold.common.idempotency.rules;

import com.acme.scaffold.common.idempotency.IdempotencyStatus;

import java.time.LocalDateTime;

/**
 * 幂等记录查询纯函数规则：筛选值归一、时间窗收敛、过期判定。
 *
 * <p>无副作用、不依赖 Spring 上下文与数据库，可直接离线单测；供
 * {@link com.acme.scaffold.common.idempotency.service.IdempotencyRecordService}
 * （查询条件构造）与 {@link com.acme.scaffold.common.idempotency.dto.IdempotencyRecordView}
 * （过期标记计算）复用，保证「查什么」与「标什么」口径同源。</p>
 *
 * <p><b>容错查询 posture</b>：与登录日志 / 短信记录页一致——非法筛选值
 * <b>忽略而非报错</b>（返回 {@code null} 表示不附加该条件）。管理页是排障工具，
 * 操作员粘贴一段可疑 key 片段就该能搜，不该先被参数校验弹回。
 * 唯一的硬校验是排序方向（{@code PageQuery} → {@code SortItem}），
 * 因为静默降级成默认排序会让前端误以为排序已生效。</p>
 */
public final class IdempotencyRecordRules {

    /** 默认查询时间窗（天）：仅看最近一段，防止无条件全表扫描。 */
    public static final int DEFAULT_WINDOW_DAYS = 7;

    /** api_scope 列宽（V16 DDL：VARCHAR(128)），筛选值超宽截断而非报错。 */
    public static final int MAX_SCOPE_LEN = 128;

    /** 关键字检索长度上限：key/scope 均 128、error_code 64，取 64 已覆盖实际排障输入。 */
    public static final int MAX_KEYWORD_LEN = 64;

    private IdempotencyRecordRules() {
    }

    /**
     * 状态筛选值归一：仅接受 {@link IdempotencyStatus} 三个枚举名（大小写不敏感），
     * 其余（含空）返回 {@code null}——调用方据此忽略该筛选条件。
     */
    public static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String s = status.trim().toUpperCase();
        for (IdempotencyStatus value : IdempotencyStatus.values()) {
            if (value.name().equals(s)) {
                return s;
            }
        }
        return null;
    }

    /**
     * 接口域筛选值归一：trim 后按列宽截断；空值返回 {@code null}（忽略筛选）。
     */
    public static String normalizeScope(String apiScope) {
        if (apiScope == null || apiScope.isBlank()) {
            return null;
        }
        return truncate(apiScope.trim(), MAX_SCOPE_LEN);
    }

    /**
     * 用户 ID 筛选值归一：trim 后解析 Long；非数字、负数或空返回 {@code null}
     * （忽略筛选）。控制器以 String 承接正是为了避免类型转换失败穿透成 500——
     * 与管理页「粘贴即搜」的容错 posture 一致。
     */
    public static Long normalizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(userId.trim());
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 关键字归一为 SQL LIKE 模式（{@code %kw%}）：trim 后按上限截断；
     * 空值返回 {@code null}。不含通配符转义——与管理页既有检索口径一致
     * （操作员主动输入 {@code %} 时按字面匹配是常见排障手法）。
     */
    public static String wrapKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + truncate(keyword.trim(), MAX_KEYWORD_LEN) + "%";
    }

    /**
     * 时间窗收敛（与短信记录页同口径）：
     * <ul>
     *   <li>{@code end} 为空取 {@code now}；{@code start} 为空取 {@code end - 7 天}；</li>
     *   <li>{@code start} 晚于 {@code end} 时钳为 {@code end}（区间为空但不报错）。</li>
     * </ul>
     *
     * @return 收敛后的 {@code [begin, end]} 闭区间
     */
    public static Window clampWindow(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        LocalDateTime endAt = end == null ? now : end;
        LocalDateTime beginAt = start == null ? endAt.minusDays(DEFAULT_WINDOW_DAYS) : start;
        if (beginAt.isAfter(endAt)) {
            beginAt = endAt;
        }
        return new Window(beginAt, endAt);
    }

    /**
     * 过期判定：{@code expiresAt} 非空且早于 {@code now} 即为已过期
     * （清理任务与手动清理的删除候选）。null 不过期（防御脏数据）。
     */
    public static boolean isExpired(LocalDateTime expiresAt, LocalDateTime now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    /** 通用截断：null 原样返回，超长截取前 {@code max} 字符（不追加省略号，用于定宽列与筛选值）。 */
    public static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        if (max <= 0) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** 收敛后的时间窗（闭区间）。 */
    public record Window(LocalDateTime begin, LocalDateTime end) {
    }
}
