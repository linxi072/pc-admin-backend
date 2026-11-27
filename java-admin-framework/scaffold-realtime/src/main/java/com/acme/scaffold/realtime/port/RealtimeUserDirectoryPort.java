package com.acme.scaffold.realtime.port;

import java.util.Set;

/**
 * 实时推送管理的用户可见范围端口。
 *
 * <p>realtime 仅依赖 common，不能直接查 system 的用户表或引入 security 的数据权限切面；
 * 由 system 模块以适配器注入当前租户内可见的用户 ID 集合，避免实时管理接口成为
 * 跨租户枚举与推送的旁路。</p>
 */
public interface RealtimeUserDirectoryPort {

    /** 给定租户内可管理的活跃用户 ID 集合；实现方负责租户隔离。 */
    Set<Long> userIdsInTenant(long tenantId);
}
