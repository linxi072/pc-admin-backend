package com.acme.scaffold.security.online;

import java.time.LocalDateTime;

/**
 * 当前生效的登录限制快照（防腐层）。
 *
 * <p>供 security 侧（认证链路拦截）使用，定义于 {@code security.online}，
 * 避免 security 直接依赖 {@code system.entity.SysLoginRestrictionDO}。</p>
 *
 * @param expireAt 限制解除时间（null 表示长期限制，需联系管理员）
 */
public record LoginRestriction(LocalDateTime expireAt) {
}
