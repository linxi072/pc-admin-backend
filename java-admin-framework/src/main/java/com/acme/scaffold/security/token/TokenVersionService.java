package com.acme.scaffold.security.token;

import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.entity.SysUserRoleDO;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Token 版本服务：让「权限变更」能立即（最多一个缓存窗口）作废已签发凭证。
 *
 * <p>设计方案 §20 要求「权限变更后旧权限缓存与 Token 版本正确失效」。
 * 此前 {@code sys_user.token_version} 建了列、JWT 也带了 {@code tokenVersion} 声明，
 * 但<b>全链路没有任何一处递增或校验</b>——改掉某人的角色后，他手里的旧 token 依然畅通无阻，
 * 直到自然过期。本服务补齐这条闭环。
 *
 * <p>递增时机（bump）：用户-角色变更、密码重置、用户删除、角色数据权限变更。
 * 校验时机：每个已认证请求（见 {@link TokenVersionVerifier}）。
 */
@Slf4j
@Service
public class TokenVersionService {

    /** 缓存窗口：默认 30 秒，即权限变更最迟 30 秒后全局生效。 */
    private static final long CACHE_TTL_MILLIS = Duration.ofSeconds(30).toMillis();

    private final DSLContext dsl;
    private final TokenVersionCache cache = new TokenVersionCache(CACHE_TTL_MILLIS);

    public TokenVersionService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** 读取用户当前版本；用户不存在（或已逻辑删除）返回 null，调用方据此判定失效。 */
    public Integer currentVersion(Long userId) {
        if (userId == null) {
            return null;
        }
        Integer cached = cache.get(userId);
        if (cached != null) {
            return cached;
        }
        Record1<Integer> row = dsl.select(JooqTables.SYS_USER.field("token_version", Integer.class))
                .from(JooqTables.SYS_USER.table())
                .where(DSL.and(JooqWriters.notDeleted(JooqTables.SYS_USER),
                        JooqTables.SYS_USER.field("id", Long.class).eq(userId)))
                .fetchOne();
        Integer version = row == null ? null : row.value1();
        if (version != null) {
            cache.put(userId, version);
        }
        return version;
    }

    /** 凭证是否仍然有效：库里版本超前于凭证声明即失效。 */
    public boolean isValid(Long userId, Integer claimVersion) {
        return TokenVersionRules.isValid(claimVersion, currentVersion(userId));
    }

    /** 递增指定用户的版本（多对多角色变更、密码重置、删除等场景）。 */
    public void bumpUsers(Collection<Long> userIds) {
        Set<Long> ids = normalize(userIds);
        if (ids.isEmpty()) {
            return;
        }
        int updated = dsl.update(JooqTables.SYS_USER.table())
                .set(JooqTables.SYS_USER.field("token_version", Integer.class),
                        JooqTables.SYS_USER.field("token_version", Integer.class).plus(1))
                .where(DSL.and(JooqWriters.notDeleted(JooqTables.SYS_USER),
                        JooqTables.SYS_USER.field("id", Long.class).in(ids)))
                .execute();
        cache.invalidate(ids);
        log.info("Token 版本递增 userIdCount={} affected={}", ids.size(), updated);
    }

    /**
     * 递增持有指定角色的全部用户版本（角色权限/数据范围变更场景）。
     *
     * <p>纯 N:N 下「角色变更」必须按角色反查用户，不能只改当前操作人。
     */
    public void bumpRoleHolders(Collection<Long> roleIds) {
        Set<Long> roleIdSet = normalize(roleIds);
        if (roleIdSet.isEmpty()) {
            return;
        }
        List<Long> userIds = JooqWriters.fetchList(dsl, JooqTables.SYS_USER_ROLE, SysUserRoleDO.class,
                        JooqTables.SYS_USER_ROLE.field("role_id", Long.class).in(roleIdSet)).stream()
                .map(SysUserRoleDO::getUserId)
                .distinct()
                .collect(Collectors.toList());
        bumpUsers(userIds);
    }

    /** 单个用户递增的便捷方法。 */
    public void bumpUser(Long userId) {
        bumpUsers(userId == null ? List.of() : List.of(userId));
    }

    private Set<Long> normalize(Collection<Long> ids) {
        if (ids == null) {
            return Set.of();
        }
        return ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    }
}
