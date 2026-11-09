package com.acme.scaffold.system.user.application;
import com.acme.scaffold.mybatis.dsl.FetchSupport;

import com.acme.scaffold.common.api.PagingRules;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.event.EventPublisher;
import com.acme.scaffold.common.event.domain.UserDisabledEvent;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.sensitive.SensitiveMasker;
import com.acme.scaffold.common.validation.DomainValidators;
import com.acme.scaffold.common.validation.RecordStatus;
import com.acme.scaffold.common.sensitive.SensitiveType;
import com.acme.scaffold.mybatis.dsl.SqlSorts;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.common.api.dto.KeysetPageQuery;
import com.acme.scaffold.common.api.dto.KeysetPageResult;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.mybatis.dsl.KeysetPager;
import com.acme.scaffold.security.permission.ColumnField;
import com.acme.scaffold.security.permission.ColumnPermission;
import com.acme.scaffold.security.permission.ColumnPermissionFieldView;
import com.acme.scaffold.security.permission.ColumnPermissionResourceRegistry;
import com.acme.scaffold.security.permission.ColumnPermissionRules;
import com.acme.scaffold.security.permission.ColumnStrategy;
import com.acme.scaffold.security.permission.DataScope;
import com.acme.scaffold.security.permission.DataScopeConditions;
import com.acme.scaffold.security.password.PasswordHistoryService;
import com.acme.scaffold.security.password.PasswordPolicy;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.token.service.TokenVersionService;
import com.acme.scaffold.system.user.dto.CreateUserRequest;
import com.acme.scaffold.system.user.dto.UpdateUserRequest;
import com.acme.scaffold.system.user.dto.UserQuery;
import com.acme.scaffold.system.user.dto.UserView;
import com.acme.scaffold.system.org.domain.model.SysOrgDO;
import com.acme.scaffold.system.role.domain.model.SysRoleDO;
import com.acme.scaffold.system.user.domain.model.SysUserDO;
import com.acme.scaffold.system.user.domain.model.SysUserOrgDO;
import com.acme.scaffold.system.user.domain.model.SysUserRoleDO;
import com.acme.scaffold.system.role.application.RoleColumnPermissionService;
import lombok.RequiredArgsConstructor;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.Field;
import com.acme.scaffold.mybatis.dsl.DSL;
import org.springframework.beans.factory.annotation.Value;
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
import com.acme.scaffold.system.menu.application.MenuService;
import com.acme.scaffold.system.post.application.PostService;

/**
 * 用户应用服务：CRUD、多角色/多部门绑定、密码重置，并在列表查询中演示数据权限扩展点。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ（动态 DSL）。
 * <p><b>约束（多对多）</b>：用户可绑定多个角色（sys_user_role）、归属多个部门（sys_user_org），
 * 主角色 / 主部门由关联表 is_primary 标记。sys_user 本表不再保留 role_id / org_id 单值列。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final SqlTables.TableRef USER_ROLE = SqlTables.SYS_USER_ROLE;
    private static final SqlTables.TableRef USER_ORG = SqlTables.SYS_USER_ORG;
    private static final SqlTables.TableRef USER_POST = SqlTables.SYS_USER_POST;
    private static final SqlTables.TableRef USER = SqlTables.SYS_USER;

    /** 用户列表可排序字段白名单：客户端字段名 → 数据库列名（白名单外一律忽略）。 */
    private static final Map<String, String> USER_SORT_FIELDS = SqlSorts.whitelist(
            "id", "id",
            "username", "username",
            "displayName", "display_name",
            "status", "status",
            "createdAt", "created_at");

    /** jOOQ 生成层桥接：与手写 DSL 的 {@link SqlTables#SYS_ROLE} 等价，列集一致。 */
    private static final com.acme.scaffold.mybatis.dsl.SqlTables.TableRef ROLE =
            SqlTables.SYS_ROLE;

    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;
    private final TokenVersionService tokenVersionService;
    private final PasswordHistoryService passwordHistoryService;
    private final PostService postService;
    private final RoleColumnPermissionService roleColumnPermissionService;
    private final ColumnPermissionResourceRegistry columnRegistry;
    private final SecurityContextFacade securityContextFacade;
    private final EventPublisher eventPublisher;

    /**
     * 字段级（列级）数据权限运行时脱敏总开关，默认关闭（同 M19 enforce 默认关闭策略）：
     * 关闭时不产生任何脱敏行为、不触发额外查询，零线上回归；开启后按角色分配的字段权限码
     * 对 {@code system:user} 的 mobile/email 等敏感字段施加脱敏/隐藏。
     */
    @Value("${column-permission.enforce:false}")
    private boolean columnPermissionEnforce;

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
        user.setStatus(RecordStatus.DEFAULT);
        user.setPasswordChangedAt(LocalDateTime.now());
        // 设计文档 §5.3：首次登录强制改密（password_expired=1），新用户必须先改密再进入系统
        user.setPasswordExpired(1);
        SqlWriters.insert(dsl, USER, user);

        // 记录初始口令到密码历史，纳入「禁止复用最近 5 次」安全基线
        passwordHistoryService.record(user.getId(), encodedPassword);

        assignRoles(user.getId(), request.roleIds(), request.primaryRoleId());
        assignOrgs(user.getId(), request.deptIds(), request.primaryDeptId());
        // 岗位为可选兼任：postIds 为空/缺省即不绑定（replaceUserPosts 内部归一）
        postService.replaceUserPosts(user.getId(), request.postIds());
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
        boolean disabling = false;
        String previousStatus = null;
        if (request.status() != null) {
            // 状态白名单：登录链路只识别 ACTIVE/DISABLED（锁定态由登录失败计数驱动，不经此入口），
            // 其余字符串落库即永久数据噪声；复用 F1 通用校验器作白名单校验（行为不变）
            DomainValidators.requireIn(request.status(), new String[]{"ACTIVE", "DISABLED"},
                    "用户状态仅支持 ACTIVE / DISABLED");
            previousStatus = user.getStatus();
            disabling = !"DISABLED".equals(previousStatus) && "DISABLED".equals(request.status());
            user.setStatus(request.status());
            permissionChanged = true;
        }
        SqlWriters.updateById(dsl, USER, id, user);

        // 跨域副作用解耦（ROADMAP R2）：用户被禁用时发布领域事件，由 security/notification 等订阅方响应
        // （踢下线 / 发通知）。system 不再直连下游；事件总线错误隔离，订阅方故障不影响本事务提交。
        if (disabling) {
            publishUserDisabledEvent(id, user.getUsername(), previousStatus);
        }

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
        // 岗位传入时整体替换；空列表表示清空（岗位允许为空，与角色/部门的「至少一个」语义区分）
        if (request.postIds() != null) {
            postService.replaceUserPosts(id, request.postIds());
        }
        if (permissionChanged) {
            tokenVersionService.bumpUser(id);
        }
    }

    /**
     * 发布「用户被禁用」领域事件（ROADMAP R2 跨域副作用示范）。
     *
     * <p>仅当状态由非 DISABLED 转为 DISABLED 时触发。发布方法本身经事件总线错误隔离，
     * 订阅方（security 踢下线 / notification 发通知）故障不会回滚本事务或影响其他订阅者。</p>
     *
     * @param userId         被禁用用户主键
     * @param username       用户名
     * @param previousStatus 禁用前状态（区分「首次禁用」与「状态回切」）
     */
    private void publishUserDisabledEvent(Long userId, String username, String previousStatus) {
        String operator = null;
        try {
            operator = securityContextFacade.requireCurrentPrincipal().username();
        } catch (Exception ignored) {
            // 非认证上下文（如内部调度）无操作人，留空
        }
        eventPublisher.publish(new UserDisabledEvent(null, "system", userId, username,
                previousStatus, operator, List.of()));
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
        SqlWriters.updateById(dsl, USER, id, user);
        // 记录本次重置后的口令到密码历史
        passwordHistoryService.record(id, encodedPassword);
        // 统一走 TokenVersionService 递增：既写库又失效缓存，避免与「仅改内存」的写法分叉
        tokenVersionService.bumpUser(id);
    }

    /**
     * 物理删除用户（硬删除）。删除前先清理 sys_user_role / sys_user_org / sys_user_post 关联行，避免孤儿数据。
     *
     * <p>缓存：该用户的菜单树缓存项随用户一并失效（按用户精确驱逐）。
     */
    @CacheEvict(cacheNames = MenuService.USER_MENUS_CACHE, key = "#id")
    @Transactional
    public void delete(Long id) {
        get(id); // 校验存在性，不存在抛 NOT_FOUND
        SqlWriters.deleteByColumn(dsl, USER_ROLE, "user_id", id);
        SqlWriters.deleteByColumn(dsl, USER_ORG, "user_id", id);
        SqlWriters.deleteByColumn(dsl, USER_POST, "user_id", id);
        SqlWriters.delete(dsl, USER, id, false);
        // 用户已不存在：递增影响 0 行，但会失效缓存，使该用户残留的凭证在下一次请求即被判失效
        tokenVersionService.bumpUser(id);
    }

    /**
     * 查询单个用户详情（含角色 / 机构 / 岗位聚合）。
     *
     * <p><b>数据权限</b>：与 {@link #list(UserQuery)} 同口径施加 {@link DataScope}。
     * 此前本方法直接按主键取数、不经过数据权限，范围受限（SELF / DEPT 等）的账号
     * 只要猜到 ID 就能读到任意用户详情——列表接口受限而详情接口不设防等于没设防。</p>
     *
     * <p>越权与「不存在」统一抛出 {@code NOT_FOUND}：不区分两种失败，
     * 避免攻击者据响应差异枚举出真实存在的用户 ID。</p>
     */
    @DataScope(resourceCode = UserQuery.RESOURCE_CODE, name = "用户管理")
    @ColumnPermission(resourceCode = UserQuery.RESOURCE_CODE, name = "用户管理",
            fields = {
                @ColumnField(name = "mobile", label = "手机号", strategy = ColumnStrategy.MASK,
                        sensitiveType = SensitiveType.MOBILE),
                @ColumnField(name = "email", label = "邮箱", strategy = ColumnStrategy.MASK,
                        sensitiveType = SensitiveType.EMAIL)
            })
    public UserView getView(Long id) {
        SysUserDO user = getScoped(id);
        return toView(user, loadUserRoles(List.of(user)), loadUserOrgs(List.of(user)),
                postService.loadPostNamesByUserIds(List.of(user.getId())), currentGrantedColumnCodes());
    }

    /**
     * 按数据权限取单条用户：未删除 + 主键匹配 + 数据范围条件三者同时成立才返回。
     *
     * <p>与 {@link #get(Long)}（写路径用的纯主键取数）刻意分开——写路径的越权应由
     * 各自的业务校验负责，避免改动面扩散到更新 / 删除 / 重置密码等既有行为。</p>
     */
    private SysUserDO getScoped(Long id) {
        SysUserDO user = SqlWriters.fetchOne(dsl, USER, SysUserDO.class,
                DSL.and(
                        SqlWriters.notDeleted(USER),
                        USER.field("id", Long.class).eq(id),
                        DataScopeConditions.of(USER, "org_id", "id")));
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    @DataScope(resourceCode = UserQuery.RESOURCE_CODE, name = "用户管理")
    @ColumnPermission(resourceCode = UserQuery.RESOURCE_CODE, name = "用户管理",
            fields = {
                @ColumnField(name = "mobile", label = "手机号", strategy = ColumnStrategy.MASK,
                        sensitiveType = SensitiveType.MOBILE),
                @ColumnField(name = "email", label = "邮箱", strategy = ColumnStrategy.MASK,
                        sensitiveType = SensitiveType.EMAIL)
            })
    public PageResult<UserView> list(UserQuery query) {
        // 深翻页护栏：OFFSET 分页在偏移过大时随偏移线性退化，超过上限强制要求改用游标分页接口。
        // 阈值与文案统一由 common/api/PagingRules 提供，避免与站内信/前端 keyset 阈值漂移。
        if (PagingRules.exceedsOffset(query.page(), query.size())) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    PagingRules.offsetLimitMessage("用户列表", "/api/system/users/page-keyset"));
        }
        List<Condition> conds = buildUserListConds(query);
        var pageQuery = query.toPageQuery();
        PageResult<SysUserDO> page = SqlWriters.page(dsl, USER, SysUserDO.class,
                DSL.and(conds), pageQuery,
                SqlSorts.resolve(USER, pageQuery, USER_SORT_FIELDS,
                        USER.field("id", Long.class).desc()));
        return new PageResult<>(page.page(), page.size(), page.total(), toUserViews(page.records()));
    }

    /** 构造用户列表查询条件（含数据权限），供 OFFSET 与 keyset 两种分页复用。 */
    private List<Condition> buildUserListConds(UserQuery query) {
        List<Condition> conds = new ArrayList<>();
        conds.add(SqlWriters.notDeleted(USER));
        if (StringUtils.hasText(query.username())) {
            conds.add(DSL.or(
                    USER.field("username", String.class).like("%" + query.username() + "%"),
                    USER.field("display_name", String.class).like("%" + query.username() + "%")));
        }
        if (StringUtils.hasText(query.status())) {
            conds.add(USER.field("status", String.class).eq(query.status()));
        }
        // 按单一角色 / 部门筛选：改走关联表子查询（多对多）
        if (query.roleId() != null) {
            conds.add(USER.field("id", Long.class).in(
                    dsl.select(USER_ROLE.field("user_id", Long.class))
                            .from(USER_ROLE.table())
                            .where(USER_ROLE.field("role_id", Long.class).eq(query.roleId()))));
        }
        if (query.orgId() != null) {
            conds.add(USER.field("id", Long.class).in(
                    dsl.select(USER_ORG.field("user_id", Long.class))
                            .from(USER_ORG.table())
                            .where(USER_ORG.field("org_id", Long.class).eq(query.orgId()))));
        }
        // 数据权限：SELF -> id = 当前用户；DEPT/DEPT_AND_CHILD/CUSTOM -> 主部门所在机构集合；ALL -> 不加条件。
        // sys_user 已无 org_id 单列，DataScopeConditions 内部改走 sys_user_org 关联表。
        conds.add(DataScopeConditions.of(USER, "org_id", "id"));
        return conds;
    }

    /**
     * 用户列表游标（keyset）分页：深翻页性能稳定，不随偏移退化，替代 {@link #list} 的 OFFSET 路径。
     *
     * <p>排序键服务端固定为 {@code (created_at DESC, id DESC)}（含唯一 tie-breaker id），与 orderBy 完全一致
     * 以保证 seek 正确；游标不透明编码（{@link com.acme.scaffold.common.api.CursorCodec}），客户端不可伪造排序。
     * 不返回 total（keyset 不廉价计算总数）。筛选口径与 {@link #list} 一致。</p>
     */
    @ColumnPermission(resourceCode = UserQuery.RESOURCE_CODE, name = "用户管理",
            fields = {
                @ColumnField(name = "mobile", label = "手机号", strategy = ColumnStrategy.MASK,
                        sensitiveType = SensitiveType.MOBILE),
                @ColumnField(name = "email", label = "邮箱", strategy = ColumnStrategy.MASK,
                        sensitiveType = SensitiveType.EMAIL)
            })
    public KeysetPageResult<UserView> listKeyset(int size, String cursor,
            String username, String status, Long orgId, Long roleId) {
        UserQuery query = new UserQuery(1, size, username, status, orgId, roleId, null, null);
        List<Condition> conds = buildUserListConds(query);
        KeysetPageQuery q = new KeysetPageQuery(size, cursor == null ? "" : cursor);
        Field<?>[] keyFields = new Field<?>[]{
                USER.field("created_at", LocalDateTime.class),
                USER.field("id", Long.class)
        };
        boolean[] ascending = {false, false}; // created_at DESC, id DESC
        KeysetPageResult<SysUserDO> raw = KeysetPager.query(dsl, USER, SysUserDO.class,
                DSL.and(conds), q,
                u -> new Object[]{u.getCreatedAt(), u.getId()}, keyFields, ascending);
        return KeysetPageResult.of(raw.size(), toUserViews(raw.records()), raw.nextCursor());
    }

    /** 批量映射用户 DO → 视图（含角色 / 部门 / 岗位与字段级脱敏），供两种分页复用。 */
    private List<UserView> toUserViews(List<SysUserDO> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        Map<Long, List<RoleInfo>> roleMap = loadUserRoles(users);
        Map<Long, List<OrgInfo>> orgMap = loadUserOrgs(users);
        Map<Long, Map<Long, String>> postMap = postService.loadPostNamesByUserIds(
                users.stream().map(SysUserDO::getId).toList());
        Set<String> granted = currentGrantedColumnCodes();
        return users.stream()
                .map(u -> toView(u, roleMap, orgMap, postMap, granted))
                .collect(Collectors.toList());
    }

    /** 校验角色存在且未删除，返回角色ID（用于绑定前校验）。 */
    private Long requireRole(Long roleId) {
        if (roleId == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "角色不能为空");
        }
        SysRoleDO role = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, ROLE, SysRoleDO.class, roleId), "角色不存在");
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
        SysOrgDO org = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, SqlTables.SYS_ORG, SysOrgDO.class, orgId), "部门不存在");
    }

    /** 整体替换用户角色关联：删除旧关联后按新集合写入，primaryRoleId 标记主角色（为空取首位）。 */
    private void assignRoles(Long userId, List<Long> roleIds, Long primaryRoleId) {
        SqlWriters.deleteByColumn(dsl, USER_ROLE, "user_id", userId);
        Long primary = roleIds.isEmpty() ? null : (primaryRoleId != null ? primaryRoleId : roleIds.get(0));
        for (Long roleId : roleIds) {
            SysUserRoleDO ur = new SysUserRoleDO();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            ur.setIsPrimary(Objects.equals(roleId, primary) ? 1 : 0);
            SqlWriters.insert(dsl, USER_ROLE, ur);
        }
    }

    /** 整体替换用户部门关联：删除旧关联后按新集合写入，primaryDeptId 标记主部门（为空取首位）。 */
    private void assignOrgs(Long userId, List<Long> deptIds, Long primaryDeptId) {
        SqlWriters.deleteByColumn(dsl, USER_ORG, "user_id", userId);
        Long primary = deptIds.isEmpty() ? null : (primaryDeptId != null ? primaryDeptId : deptIds.get(0));
        for (Long deptId : deptIds) {
            SysUserOrgDO uo = new SysUserOrgDO();
            uo.setUserId(userId);
            uo.setOrgId(deptId);
            uo.setIsPrimary(Objects.equals(deptId, primary) ? 1 : 0);
            SqlWriters.insert(dsl, USER_ORG, uo);
        }
    }

    private UserView toView(SysUserDO user, Map<Long, List<RoleInfo>> roleMap, Map<Long, List<OrgInfo>> orgMap,
                            Map<Long, Map<Long, String>> postMap, Set<String> grantedColumnCodes) {
        List<RoleInfo> roles = roleMap.getOrDefault(user.getId(), List.of());
        List<OrgInfo> orgs = orgMap.getOrDefault(user.getId(), List.of());
        Map<Long, String> posts = postMap.getOrDefault(user.getId(), Map.of());
        // 字段级（列级）数据权限：构造前脱敏（UserView 为 record，反射式 applier 不可达）。
        // enforce 默认关闭 → grantedColumnCodes 为空、applyColumnMask 原样返回，零线上回归。
        String mobile = applyColumnMask(user.getMobile(), UserQuery.RESOURCE_CODE, "mobile", grantedColumnCodes);
        String email = applyColumnMask(user.getEmail(), UserQuery.RESOURCE_CODE, "email", grantedColumnCodes);
        return UserView.from(
                user.getId(), user.getUsername(), user.getDisplayName(), mobile, email,
                roles.stream().map(RoleInfo::id).toList(),
                roles.stream().map(RoleInfo::name).toList(),
                roles.stream().map(RoleInfo::code).toList(),
                orgs.stream().map(OrgInfo::id).toList(),
                orgs.stream().map(OrgInfo::name).toList(),
                List.copyOf(posts.keySet()),
                List.copyOf(posts.values()),
                user.getStatus(), user.getCreatedAt());
    }

    /**
     * 按字段级数据权限对单字段脱敏/隐藏（构造前脱敏，record 安全）。
     *
     * <p>当前主体拥有该字段明文权限码（{@code resourceCode:column:field}）时原样返回；
     * 否则按 {@code @ColumnPermission} 声明驱动 {@link SensitiveMasker}（未声明敏感类型回落通用掩码），
     * 空值直接返回。开关关闭或缺失主体时由调用方传入空集合，这里等同不脱敏。</p>
     */
    private String applyColumnMask(String value, String resourceCode, String field, Set<String> grantedColumnCodes) {
        if (!columnPermissionEnforce || value == null) {
            return value;
        }
        if (grantedColumnCodes.contains(ColumnPermissionRules.columnPermissionCode(resourceCode, field))) {
            return value;
        }
        SensitiveType type = columnRegistry.fieldsOf(resourceCode).stream()
                .filter(f -> f.field().equals(field))
                .map(ColumnPermissionFieldView::sensitiveType)
                .findFirst().orElse(null);
        return SensitiveMasker.mask(value, type);
    }

    /**
     * 解析当前登录主体所属角色的字段权限码集合（多角色取并集）。
     * 开关关闭或无主体时返回空集，避免无谓查询。
     */
    private Set<String> currentGrantedColumnCodes() {
        if (!columnPermissionEnforce) {
            return Set.of();
        }
        return securityContextFacade.getCurrentPrincipal()
                .map(p -> {
                    if (p.userId() == null) {
                        return Set.<String>of();
                    }
                    Set<Long> roleIds = SqlWriters.fetchList(dsl, USER_ROLE, SysUserRoleDO.class,
                                    USER_ROLE.field("user_id", Long.class).eq(p.userId())).stream()
                            .map(SysUserRoleDO::getRoleId).collect(Collectors.toSet());
                    return roleColumnPermissionService.grantedCodes(roleIds);
                })
                .orElse(Set.of());
    }

    /** 批量装载用户角色信息（id/名称/编码），避免列表查询 N+1。 */
    private Map<Long, List<RoleInfo>> loadUserRoles(List<SysUserDO> users) {
        Set<Long> userIds = users.stream().map(SysUserDO::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Long>> userRoleIds = SqlWriters.fetchList(dsl, USER_ROLE, SysUserRoleDO.class,
                        USER_ROLE.field("user_id", Long.class).in(userIds)).stream()
                .collect(Collectors.groupingBy(SysUserRoleDO::getUserId,
                        Collectors.mapping(SysUserRoleDO::getRoleId, Collectors.toList())));
        Set<Long> roleIds = userRoleIds.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, RoleInfo> roleCache = new HashMap<>();
        if (!roleIds.isEmpty()) {
            SqlWriters.fetchList(dsl, ROLE, SysRoleDO.class,
                            DSL.and(SqlWriters.notDeleted(ROLE),
                                    ROLE.field("id", Long.class).in(roleIds)))
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
        Map<Long, List<Long>> userOrgIds = SqlWriters.fetchList(dsl, USER_ORG, SysUserOrgDO.class,
                        USER_ORG.field("user_id", Long.class).in(userIds)).stream()
                .collect(Collectors.groupingBy(SysUserOrgDO::getUserId,
                        Collectors.mapping(SysUserOrgDO::getOrgId, Collectors.toList())));
        Set<Long> orgIds = userOrgIds.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, OrgInfo> orgCache = new HashMap<>();
        if (!orgIds.isEmpty()) {
            SqlWriters.fetchList(dsl, SqlTables.SYS_ORG, SysOrgDO.class,
                            DSL.and(SqlWriters.notDeleted(SqlTables.SYS_ORG),
                                    SqlTables.SYS_ORG.field("id", Long.class).in(orgIds)))
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
        return SqlWriters.count(dsl, USER,
                DSL.and(SqlWriters.notDeleted(USER),
                        USER.field("username", String.class).eq(username))) > 0;
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
        Condition cond = USER.field(column, String.class).eq(value.trim())
                .and(SqlWriters.notDeleted(USER));
        if (selfId != null) {
            cond = cond.and(USER.field("id", Long.class).ne(selfId));
        }
        if (SqlWriters.count(dsl, USER, cond) > 0) {
            throw BusinessException.conflict("mobile".equals(column)
                    ? "该手机号已被其他账号绑定" : "该邮箱已被其他账号绑定");
        }
    }

    private SysUserDO get(Long id) {
        SysUserDO user = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, USER, SysUserDO.class, id), "用户不存在");
        return user;
    }

    /** 角色聚合信息（id / 名称 / 编码）。 */
    private record RoleInfo(Long id, String name, String code) {
    }

    /** 部门聚合信息（id / 名称）。 */
    private record OrgInfo(Long id, String name) {
    }
}
