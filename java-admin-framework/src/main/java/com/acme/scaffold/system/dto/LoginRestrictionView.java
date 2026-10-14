package com.acme.scaffold.system.dto;

import com.acme.scaffold.security.online.RestrictionRules;
import com.acme.scaffold.system.entity.SysLoginRestrictionDO;

import java.time.LocalDateTime;

/**
 * 登录限制记录视图（操作日志的结构化沉淀，与 sys_operation_log 审计互为补充）。
 */
public record LoginRestrictionView(
        Long id,
        Long userId,
        String username,
        String displayName,
        String reason,
        Integer durationMinutes,
        String restrictedByName,
        LocalDateTime restrictedAt,
        LocalDateTime expireAt,
        LocalDateTime releasedAt,
        String releaseType,
        String status) {

    /** 是否长期限制（无到期时间）。 */
    public boolean permanent() {
        return expireAt == null;
    }

    /** 当前是否仍生效（口径与登录链路一致：到期未扫到同样视为不生效）。 */
    public boolean active() {
        return RestrictionRules.isActive(toDO(), LocalDateTime.now());
    }

    private SysLoginRestrictionDO toDO() {
        SysLoginRestrictionDO d = new SysLoginRestrictionDO();
        d.setStatus(status);
        d.setExpireAt(expireAt);
        return d;
    }
}
