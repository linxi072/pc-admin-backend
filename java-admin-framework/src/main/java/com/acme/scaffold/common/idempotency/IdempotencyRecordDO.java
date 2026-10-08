package com.acme.scaffold.common.idempotency;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等记录持久化对象。列与 {@code V16__idempotency_record.sql} 严格一致。
 *
 * <p>该表<b>无 deleted / version 列</b>：记录到期即物理删除，不做逻辑删除，
 * 也不参与乐观锁（并发由唯一索引裁决，而非版本号）。
 */
@Data
public class IdempotencyRecordDO {

    private Long id;

    private Long tenantId = 0L;

    private String idempotencyKey;

    private String apiScope;

    private Long userId;

    private String requestFingerprint;

    private String status;

    private String responseSnapshot;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
