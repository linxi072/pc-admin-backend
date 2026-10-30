package com.acme.scaffold.security.password;

import java.time.LocalDateTime;

/**
 * 密码历史记录的轻量快照，供纯函数规则排序/裁剪使用。
 *
 * <p>定义于 {@code security.password} 包，避免 security 侧为裁剪逻辑而直连
 * {@code system.entity.SysPasswordHistoryDO}（模块边界 B1/B5 同款防腐层约束）。</p>
 *
 * @param id         历史记录主键
 * @param createdAt  记录创建时间（裁剪时按降序保留最近 N 条）
 */
public record PasswordHistoryRecord(Long id, LocalDateTime createdAt) {
}
