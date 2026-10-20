package com.acme.scaffold.common.idempotency.dto;

import com.acme.scaffold.common.idempotency.domain.IdempotencyRecordDO;
import com.acme.scaffold.common.idempotency.rules.IdempotencyRecordRules;

import java.time.LocalDateTime;

/**
 * 幂等记录视图（幂等记录管理页数据源，对应 {@code sys_idempotency_record} 表）。
 *
 * <p><b>为什么不暴露 {@code response_snapshot}</b>：该列保存首次执行的完整响应体
 * （最大 4000 字符），可能包含业务数据与 PII。运维排障需要的是「哪条 key 卡住/
 * 失败、错在哪」，不需要重放响应原文——重放由幂等切面自动完成。
 * 查询面按最小必要原则裁剪，从契约上消除越权读取面。</p>
 *
 * <p><b>{@code expired} 为计算字段</b>：{@code expires_at} 早于查询时刻即视为已过期
 * （清理任务的删除候选），前端可据此直接标红，无需二次比较时间。</p>
 *
 * @param id                 记录 ID
 * @param idempotencyKey     幂等键（客户端 {@code Idempotency-Key} 头原值）
 * @param apiScope           接口域（{@code @Idempotent} 声明的 scope）
 * @param userId             触发用户 ID（匿名/系统触发为 null）
 * @param requestFingerprint 请求指纹（同 key 不同指纹 → 409 冲突）
 * @param status             状态：PROCESSING / SUCCESS / FAILED
 * @param errorCode          业务失败错误码（SUCCESS 为 null）
 * @param errorMessage       业务失败原因（SUCCESS 为 null）
 * @param expiresAt          过期时间（此后同 key 重试视为全新操作）
 * @param createdAt          登记时间
 * @param updatedAt          最近状态流转时间
 * @param expired            是否已过期（待清理）
 */
public record IdempotencyRecordView(
        Long id,
        String idempotencyKey,
        String apiScope,
        Long userId,
        String requestFingerprint,
        String status,
        String errorCode,
        String errorMessage,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean expired) {

    /**
     * 由持久化对象构造视图；{@code expired} 按传入的 {@code now} 计算（由调用方固定
     * 查询时刻，避免逐行调用系统时钟导致同页数据口径不一致）。
     *
     * @param d   持久化对象（null 返回 null）
     * @param now 查询时刻
     */
    public static IdempotencyRecordView of(IdempotencyRecordDO d, LocalDateTime now) {
        if (d == null) {
            return null;
        }
        return new IdempotencyRecordView(
                d.getId(),
                d.getIdempotencyKey(),
                d.getApiScope(),
                d.getUserId(),
                d.getRequestFingerprint(),
                d.getStatus(),
                d.getErrorCode(),
                d.getErrorMessage(),
                d.getExpiresAt(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                IdempotencyRecordRules.isExpired(d.getExpiresAt(), now));
    }
}
