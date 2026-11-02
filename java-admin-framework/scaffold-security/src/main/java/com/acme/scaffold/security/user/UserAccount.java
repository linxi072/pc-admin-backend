package com.acme.scaffold.security.user;

import java.time.LocalDateTime;

/**
 * 账号聚合的轻量快照，供 security 侧（认证 / 令牌签发 / 状态守卫）使用，
 * 定义于 {@code security.user} 包，避免 security 直接依赖 {@code system.entity.SysUserDO}。
 *
 * <p>仅承载鉴权链路所需的字段；跨模块读取经由 {@link UserAccountDataPort}（system 适配器实现）完成，
 * 满足模块边界（B1/B5 同款防腐层约束）。</p>
 *
 * @param id               用户主键
 * @param username        登录用户名
 * @param displayName     展示名
 * @param tenantId        租户 id（多租户隔离解析来源）
 * @param status          账号状态（ENABLED / DISABLED / LOCKED）
 * @param passwordHash    密文口令哈希
 * @param tokenVersion    Token 版本号（权限变更后递增，令旧凭证失效）
 * @param passwordExpired 是否需改密（1=是）
 * @param mobile          手机号
 * @param email           邮箱
 * @param lockedUntil     锁定解除时间（null 表示未锁定或长期锁定）
 * @param failedLoginCount 连续登录失败计数
 * @param lastLoginAt     最近登录时间
 */
public record UserAccount(Long id, String username, String displayName, Long tenantId, String status,
                          String passwordHash, Integer tokenVersion, Integer passwordExpired,
                          String mobile, String email, LocalDateTime lockedUntil,
                          Integer failedLoginCount, LocalDateTime lastLoginAt) {
}
