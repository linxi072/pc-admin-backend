package com.acme.scaffold.common.idempotency;

/**
 * 幂等切面命中既有记录后的动作。由 {@link IdempotencyRules#decide} 产出，供切面分支执行。
 */
public enum IdempotencyAction {

    /** 放行：无记录，或上一条记录为 FAILED（允许用同 key 重试）。 */
    PROCEED,

    /** 重放：命中已成功记录，直接返回首次响应快照。 */
    REPLAY,

    /** 冲突：同一 key 的请求仍在处理中。 */
    CONFLICT,

    /** 指纹不一致：同一 key 换了请求内容，属客户端误用。 */
    MISMATCH
}
