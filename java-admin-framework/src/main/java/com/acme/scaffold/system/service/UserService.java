package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.permission.DataScope;
import com.acme.scaffold.security.permission.DataScopeConditions;
import com.acme.scaffold.security.password.PasswordHistoryService;
import com.acme.scaffold.security.password.PasswordPolicy;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.token.service.TokenVersionService;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.dto.UpdateUserRequest;
import com.acme.scaffold.system.dto.UserQuery;
import com.acme.scaffold.system.dto.UserView;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.entity.SysUserOrgDO;
import com.acme.scaffold.system.entity.SysUserRoleDO;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户应用服务：CRUD、多角色/多部门绑定、密码重置，并在列表查询中演示数据权限扩展点。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ（动态 DSL）。
 * <p><b>约束（多对多）</b>：用户可绑定多个角色（sys_user_role）、归属多个部门（sys_user_org），
 * 主角色 / 主部门由关联表 is_primary 标记。sys_user 本表不再保留 role_id / org_id 单值列。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    /** 用户列表可排序字段白名单：客户端字段名 → 数据库列名（白名单外一律忽略）。 */
    private static final Map<String, String> USER_SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "username", "username",
            "displayName", "display_name",
            "status", "status",
            "createdAt", "created_at");

    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;
    private final TokenVersionService tokenVersionService;
    private final PasswordHistoryService passwordHistoryService;

    @Transactional
    public Long create(CreateUserRequest request) {
        if (existsByUsername(request.username())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "用户名已存在");
        }
        if (request.roleIds() == null || request.roleIds().isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "至少绑定一个角色");
        }
        if (request.deptIds() == null || request.deptIds().isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "至少归属一个部门");
        }
        // 密码强度策略：创建用户即要求合规（弱口令 / 复杂度不足直接拒绝）
        PasswordPolicy.validate(request.password());
        // 校验角色 / 部门存在且可用（逐个走 require* 校验）
        request.roleIds().forEach(this::requireRole);
        request.deptIds().forEach(this::requireOrg);
        // 手机 / 邮箱唯一性预校验：与 ProfileService.bindContact/updateProfile 同一契约。
        // 手机有 uk_tenant_mobile 兜底（冲突时 409 但提示不可读），邮箱无唯一索引，
        // 不预校验会静默落入库，与个人中心「该邮箱已被其他账号绑定」的承诺相悖。
        assertContactAvailable("mobile", request.mobile(), null);
        assertContactAvailable("email", request.email(), null);

        String encodedPassword = passwordEncoder.encode(request.password());
        SysUserDO user = new SysUserDO();
        user.setUsername(request.username());
        user.setPasswordHash(encodedPassword);
        user.setDisplayName(request.displayName());
        user.setMobile(request.mobile());
        user.setEmail(request.email());
        user.setStatus("ACTIVE");
        user.setPasswordChangedAt(LocalDateTime.now());
        // 设计文档 §5.3：首次登录强制改密（password_expired=1），新用户必须先改密再进入系统
        user.setPasswordExpired(1);
        JooqWriters.insert(dsl, JooqTables.SYS_USER, user);

        // 记录初始口令到密码历史，纳入「禁止复用最近 5 次」安全基线
        passwordHistoryService.record(user.getId(), encodedPassword);

        assignRoles(user.getId(), request.roleIds(), request.primaryRoleId());
        assignOrgs(user.getId(), request.deptIds(), request.primaryDeptId());
        return user.getId();
    }

    /**
     * 更新用户。角色整体替换（roleIds 非 null）时该用户的菜单树随之变化，
     * 按用户精确驱逐 {@value MenuService#USER_MENUS_CACHE} 中该用户的缓存项。
     */
    @CacheEvict(cacheNames = MenuService.USER_MENUS_CACHE, key = "#id")
    @Transactional
    public void update(Long id, UpdateUserRequest request) {
        SysUserDO user = get(id);
        // 角色或状态变化会影响该用户实际权限，需在事务内递增 token 版本使旧凭证失效
        boolean permissionChanged = false;
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.mobile() != null) {
            // 唯一性预校验（排除自身）：手机有 DB 唯一索引兜底但提示不可读，邮箱无索引
            assertContactAvailable("mobile", request.mobile(), id);
            user.setMobile(request.mobile());
        }
        if (request.email() != null) {
            assertContactAvailable("email", request.email(), id);
            user.setEmail(request.email());
        }
        if (request.status() != null) {
            // 状态白名单：登录链路只识别 ACTIVE/DISABLED（外加锁定态由登录失败计数驱动），
            // 任意字符串可落库会成为永久数据噪声，故仅接受这两个值
            if (!"ACTIVE".equals(request.status()) && !"DISABLED".equals(request.status())) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "用户状态仅支持 ACTIVE / DISABLED");
            }
            user.setStatus(request.status());
            permissionChanged = true;
        }
        JooqWriters.updateById(dsl, JooqTables.SYS_USER, id, user);

        // 角色 / 部门传入时整体替换原有关联
        if (request.roleIds() != null) {
            if (request.roleIds().isEmpty()) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "至少保留一个角色");
            }
            request.roleIds().forEach(this::requireRole);
            assignRoles(id, request.roleIds(), request.primaryRoleId());
            permissionChanged = true;
        }
        if (request.deptIds() != null) {
            if (request.deptIds().isEmpty()) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "至少保留一个部门");
            }
            request.deptIds().forEach(this::requireOrg);
            assignOrgs(id, request.deptIds(), request.primaryDeptId());
        }
        if (permissionChanged) {
            tokenVersionService.bumpUser(id);
        }
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        SysUserDO user = get(id);
        PasswordPolicy.validate(newPassword);
        // 设计文档 §5.3：禁止复用最近 5 次密码
        if (passwordHistoryService.isReused(id, newPassword)) {
            throw new BusinessException(SecurityErrorCode.PASSWORD_REUSED, "不能与最近使用的密码相同");
        }
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPasswordHash(encodedPassword);
        user.setPasswordChangedAt(LocalDateTime.now());
        // 重置即赋予可用口令：清除「需改密」标记，用户下次登录直接用新密码进入系统。
        // 语义区分：UserService.create 的初始口令仍置 password_expired=1（首次登录必须由本人更换），
        // 而管理员重置是「已知会告知用户的新口令」，若再强制改密一次会把用户拦在登录页外
        // （登录被 423 拦截且无自助改密入口时形成死锁），故此处显式清零。
        user.setPasswordExpired(0);
        JooqWriters.updateById(dsl, JooqTables.SYS_USER, id, user);
        // 记录本次重置后的口令到密码历史
        passwordHistoryService.record(id, encodedPassword);
        // 统一走 TokenVersionService 递增：既写库又失效缓存，避免与「仅改内存」的写法分叉
        tokenVersionService.bumpUser(id);
    }

    /**
     * 物理删除用户（硬删除）。删除前先清理 sys_user_role / sys_user_org 关联行，避免孤儿数据。
     *
     * <p>缓存：该用户的菜单树缓存项随用户一并失效（按用户精确驱逐）。
     */
    @CacheEvict(cacheNames = MenuService.USER_MENUS_CACHE, key = "#id")
    @Transactional
    public void delete(Long id) {
        get(id); // 校验存在性，不存在抛 NOT_FOUND
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_USER_ROLE, "user_id", id);
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_USER_ORG, "user_id", id);
        JooqWriters.delete(dsl, JooqTables.SYS_USER, id, false);
        // 用户已不存在：递增影响 0 行，但会失效缓存，使该用户残留的凭证在下一次请求即被判失效
        tokenVersionService.bumpUser(id);
    }

    public UserView getView(Long id) {
        SysUserDO user = get(id);
        return toView(user, loadUserRoles(List.of(user)), loadUserOrgs(List.of(user)));
    }

    @DataScope(resourceCode = UserQuery.RESOURCE_CODE)
    public PageResult<UserView> list(UserQuery query) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(JooqTables.SYS_USER));
        if (StringUtils.hasText(query.username())) {
            conds.add(DSL.or(
                    JooqTables.SYS_USER.field("username", String.class).like("%" + query.username() + "%"),
                    JooqTables.SYS_USER.field("display_name", String.class).like("%" + query.username() + "%")));
        }
        if (StringUtils.hasText(query.status())) {
            conds.add(JooqTables.SYS_USER.field("status", String.class).eq(query.status()));
        }
        // 按单一角色 / 部门筛选：改走关联表子查询（多对多）
        if (query.roleId() != null) {
            conds.add(JooqTables.SYS_USER.field("id", Long.class).in(
                    dsl.select(JooqTables.SYS_USER_ROLE.field("user_id", Long.class))
                            .from(JooqTables.SYS_USER_ROLE.table())
                            .where(JooqTables.SYS_USER_ROLE.field("role_id", Long.class).eq(query.roleId()))));
        }
        if (query.orgId() != null) {
            conds.add(JooqTables.SYS_USER.field("id", Long.class).in(
                    dsl.select(JooqTables.SYS_USER_ORG.field("user_id", Long.class))
                            .from(JooqTables.SYS_USER_ORG.table())
                            .where(JooqTables.SYS_USER_ORG.field("org_id", Long.class).eq(query.orgId()))));
        }
        // 数据权限：SELF -> id = 当前用户；DEPT/DEPT_AND_CHILD/CUSTOM -> 主部门所在机构集合；ALL -> 不加条件。
        // sys_user 已无 org_id 单列，DataScopeConditions 内部改走 sys_user_org 关联表。
        conds.add(DataScopeConditions.of(JooqTables.SYS_USER, "org_id", "id"));

        var pageQuery = query.toPageQuery();
        PageResult<SysUserDO> page = JooqWriters.page(dsl, JooqTables.SYS_USER, SysUserDO.class,
                DSL.and(conds), pageQuery,
                JooqSorts.resolve(JooqTables.SYS_USER, pageQuery, USER_SORT_FIELDS,
                        JooqTables.SYS_USER.field("id", Long.class).desc()));

        Map<Long, List<RoleInfo>> roleMap = loadUserRoles(page.records());
        Map<Long, List<OrgInfo>> orgMap = loadUserOrgs(page.records());
        List<UserView> records = page.records().stream()
                .map(u -> toView(u, roleMap, orgMap))
                .collect(Collectors.toList());
        return new PageResult<>(page.page(), page.size(), page.total(), records);
    }

    /** 校验角色存在且未删除，返回角色ID（用于绑定前校验）。 */
    private Long requireRole(Long roleId) {
        if (roleId == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "角色不能为空");
        }
        SysRoleDO role = JooqWriters.fetchById(dsl, JooqTables.SYS_ROLE, SysRoleDO.class, roleId);
        if (role == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "角色不存在");
        }
        if (!"ACTIVE".equals(role.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "角色已停用，无法绑定");
        }
        return roleId;
    }

    /** 校验部门存在且未删除（部门为必选绑定，故不允许 null）。 */
    private void requireOrg(Long orgId) {
        if (orgId == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "部门不能为空");
        }
        SysOrgDO org = JooqWriters.fetchById(dsl, JooqTables.SYS_ORG, SysOrgDO.class, orgId);
        if (org == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "部门不存在");
        }
    }

    /** 整体替换用户角色关联：删除旧关联后按新集合写入，primaryRoleId 标记主角色（为空取首位）。 */
    private void assignRoles(Long userId, List<Long> roleIds, Long primaryRoleId) {
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_USER_ROLE, "user_id", userId);
        Long primary = roleIds.isEmpty() ? null : (primaryRoleId != null ? primaryRoleId : roleIds.get(0));
        for (Long roleId : roleIds) {
            SysUserRoleDO ur = new SysUserRoleDO();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            ur.setIsPrimary(Objects.equals(roleId, primary) ? 1 : 0);
            JooqWriters.insert(dsl, JooqTables.SYS_USER_ROLE, ur);
        }
    }

    /** 整体替换用户部门关联：删除旧关联后按新集合写入，primaryDeptId 标记主部门（为空取首位）。 */
    private void assignOrgs(Long userId, List<Long> deptIds, Long primaryDeptId) {
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_USER_ORG, "user_id", userId);
        Long primary = deptIds.isEmpty() ? null : (primaryDeptId != null ? primaryDeptId : deptIds.get(0));
        for (Long deptId : deptIds) {
            SysUserOrgDO uo = new SysUserOrgDO();
            uo.setUserId(userId);
            uo.setOrgId(deptId);
            uo.setIsPrimary(Objects.equals(deptId, primary) ? 1 : 0);
            JooqWriters.insert(dsl, JooqTables.SYS_USER_ORG, uo);
        }
    }

    private UserView toView(SysUserDO user, Map<Long, List<RoleInfo>> roleMap, Map<Long, List<OrgInfo>> orgMap) {
        List<RoleInfo> roles = roleMap.getOrDefault(user.getId(), List.of());
        List<OrgInfo> orgs = orgMap.getOrDefault(user.getId(), List.of());
        return UserView.from(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getMobile(), user.getEmail(),
                roles.stream().map(RoleInfo::id).toList(),
                roles.stream().map(RoleInfo::name).toList(),
                roles.stream().map(RoleInfo::code).toList(),
                orgs.stream().map(OrgInfo::id).toList(),
                orgs.stream().map(OrgInfo::name).toList(),
                user.getStatus(), user.getCreatedAt());
    }

    /** 批量装载用户角色信息（id/名称/编码），避免列表查询 N+1。 */
    private Map<Long, List<RoleInfo>> loadUserRoles(List<SysUserDO> users) {
        Set<Long> userIds = users.stream().map(SysUserDO::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Long>> userRoleIds = JooqWriters.fetchList(dsl, JooqTables.SYS_USER_ROLE, SysUserRoleDO.class,
                        JooqTables.SYS_USER_ROLE.field("user_id", Long.class).in(userIds)).stream()
                .collect(Collectors.groupingBy(SysUserRoleDO::getUserId,
                        Collectors.mapping(SysUserRoleDO::getRoleId, Collectors.toList())));
        Set<Long> roleIds = userRoleIds.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, RoleInfo> roleCache = new HashMap<>();
        if (!roleIds.isEmpty()) {
            JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                            DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ROLE),
                                    JooqTables.SYS_ROLE.field("id", Long.class).in(roleIds)))
                    .forEach(r -> roleCache.put(r.getId(), new RoleInfo(r.getId(), r.getRoleName(), r.getRoleCode())));
        }
        Map<Long, List<RoleInfo>> result = new HashMap<>();
        for (Long uid : userIds) {
            List<Long> rids = userRoleIds.getOrDefault(uid, List.of());
            result.put(uid, rids.stream().map(roleCache::get).filter(Objects::nonNull).collect(Collectors.toList()));
        }
        return result;
    }

    /** 批量装载用户部门信息（id/名称），避免列表查询 N+1。 */
    private Map<Long, List<OrgInfo>> loadUserOrgs(List<SysUserDO> users) {
        Set<Long> userIds = users.stream().map(SysUserDO::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Long>> userOrgIds = JooqWriters.fetchList(dsl, JooqTables.SYS_USER_ORG, SysUserOrgDO.class,
                        JooqTables.SYS_USER_ORG.field("user_id", Long.class).in(userIds)).stream()
                .collect(Collectors.groupingBy(SysUserOrgDO::getUserId,
                        Collectors.mapping(SysUserOrgDO::getOrgId, Collectors.toList())));
        Set<Long> orgIds = userOrgIds.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, OrgInfo> orgCache = new HashMap<>();
        if (!orgIds.isEmpty()) {
            JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                            DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ORG),
                                    JooqTables.SYS_ORG.field("id", Long.class).in(orgIds)))
                    .forEach(o -> orgCache.put(o.getId(), new OrgInfo(o.getId(), o.getOrgName())));
        }
        Map<Long, List<OrgInfo>> result = new HashMap<>();
        for (Long uid : userIds) {
            List<Long> oids = userOrgIds.getOrDefault(uid, List.of());
            result.put(uid, oids.stream().map(orgCache::get).filter(Objects::nonNull).collect(Collectors.toList()));
        }
        return result;
    }

    private boolean existsByUsername(String username) {
        return JooqWriters.count(dsl, JooqTables.SYS_USER,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_USER),
                        JooqTables.SYS_USER.field("username", String.class).eq(username))) > 0;
    }

    /**
     * 手机 / 邮箱唯一性预校验：排除自身（selfId 为空表示新建），冲突时给出可读提示。
     *
     * <p>与 {@code ProfileService.assertContactAvailable} 同一契约同一命名——两端都写
     * 用户表，唯一性承诺必须一致，不能「个人中心拦、后台管理放」。空白值跳过（表示不绑定）。
     */
    private void assertContactAvailable(String column, String value, Long selfId) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        Condition cond = JooqTables.SYS_USER.field(column, String.class).eq(value.trim())
                .and(JooqWriters.notDeleted(JooqTables.SYS_USER));
        if (selfId != null) {
            cond = cond.and(JooqTables.SYS_USER.field("id", Long.class).ne(selfId));
        }
        if (JooqWriters.count(dsl, JooqTables.SYS_USER, cond) > 0) {
            throw BusinessException.conflict("mobile".equals(column)
                    ? "该手机号已被其他账号绑定" : "该邮箱已被其他账号绑定");
        }
    }

    private SysUserDO get(Long id) {
        SysUserDO user = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, id);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /** 角色聚合信息（id / 名称 / 编码）。 */
    private record RoleInfo(Long id, String name, String code) {
    }

    /** 部门聚合信息（id / 名称）。 */
    private record OrgInfo(Long id, String name) {
    }
}
