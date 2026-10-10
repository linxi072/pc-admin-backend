package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 密码历史记录：落地设计文档 §5.3「保留最近 5 次密码哈希，禁止重复使用」安全基线。
 * 对应 {@code sys_password_history} 表（见 V2__security.sql）。
 *
 * <p>该表无 {@code deleted} / {@code version} 列，删除为物理删除；
 * 写入由 {@link com.acme.scaffold.jooq.JooqWriters} 自动回填 id / tenant_id / created_at。
 */
@Data
public class SysPasswordHistoryDO {

    private Long id;

    private Long tenantId = 0L;
    private Long userId;
    private String passwordHash;
    private LocalDateTime createdAt;
}
