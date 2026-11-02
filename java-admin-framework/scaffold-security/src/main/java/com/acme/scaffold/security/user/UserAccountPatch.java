package com.acme.scaffold.security.user;

import java.time.LocalDateTime;

/**
 * 账号可写字段补丁（防腐层）。
 *
 * <p>由 {@code security.service.AuthService} 在登录失败累计 / 改密 / 重置后构造，
 * 经 {@link UserAccountDataPort#updateById} 落到 {@code sys_user}；仅非空字段参与更新
 * （由 {@code JooqWriters.updateById} 跳过 null 实现），故本记录所有字段均可空。</p>
 *
 * @param passwordHash      密文口令哈希（改密 / 重置时写入）
 * @param passwordChangedAt 口令变更时间（改密 / 重置时写入）
 * @param passwordExpired   是否需改密（1=是；重置清零为 0）
 * @param failedLoginCount  连续登录失败计数（失败累加 / 成功清零）
 * @param lockedUntil       锁定解除时间（渐进式锁定时写入；清零时置 null）
 * @param tokenVersion      Token 版本号（改密 / 重置时递增）
 * @param lastLoginAt       最近登录时间（登录成功时写入）
 * @param status            账号状态（渐进式锁定置 LOCKED）
 */
public record UserAccountPatch(String passwordHash, LocalDateTime passwordChangedAt, Integer passwordExpired,
                               Integer failedLoginCount, LocalDateTime lockedUntil, Integer tokenVersion,
                               LocalDateTime lastLoginAt, String status) {
}
