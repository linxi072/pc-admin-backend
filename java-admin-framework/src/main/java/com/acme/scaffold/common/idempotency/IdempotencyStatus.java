package com.acme.scaffold.common.idempotency;

/**
 * 幂等记录状态机。
 *
 * <pre>
 *   （无记录） --acquire--> PROCESSING --success--> SUCCESS
 *                              |                      |
 *                              |--business fail------> FAILED --acquire--> PROCESSING（允许重试）
 *                              |
 *                              '--timeout/expired-----> （被清理任务删除）
 * </pre>
 *
 * <p>为什么需要 PROCESSING 而不直接落 SUCCESS：审批类接口可能耗时较长，
 * 同一 key 的第二次请求必须被挡住（409），否则会产生并发重复审批。
 */
public enum IdempotencyStatus {

    /** 正在处理：同 key 重复请求返回 409。 */
    PROCESSING,

    /** 已成功：同 key 重复请求重放首次响应。 */
    SUCCESS,

    /** 业务失败：允许用同 key 重试（重新进入 PROCESSING）。 */
    FAILED;

    public static IdempotencyStatus safeOf(String value) {
        if (value == null || value.isBlank()) {
            return PROCESSING;
        }
        try {
            return IdempotencyStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PROCESSING;
        }
    }
}
