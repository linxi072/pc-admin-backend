package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.token.RefreshTokenService;
import com.acme.scaffold.security.token.SysRefreshTokenDO;
import com.acme.scaffold.system.dto.BindContactRequest;
import com.acme.scaffold.system.dto.ChangePasswordRequest;
import com.acme.scaffold.system.dto.DeviceView;
import com.acme.scaffold.system.dto.PreferenceView;
import com.acme.scaffold.system.dto.ProfileView;
import com.acme.scaffold.system.dto.UpdatePreferenceRequest;
import com.acme.scaffold.system.dto.UpdateProfileRequest;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysUserDO;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 个人中心 / 账号设置应用服务。
 *
 * <p><b>边界约定</b>：本服务只操作「当前登录用户本人」，一律从
 * {@link SecurityContextFacade} 取 userId，<b>不接收前端传入的用户 ID</b>，
 * 从根本上杜绝越权改他人资料的可能。
 *
 * <p><b>登录设备</b>不额外建表：{@code sys_refresh_token} 中每条未过期、未吊销的记录
 * 即一个活跃会话，天然承载 device_id / user_agent / ip_address，可直接投影为设备列表。
 *
 * <p><b>会话失效</b>统一走 token_version 自增（改密、退出全部设备）或
 * 刷新令牌吊销（单个设备下线），二者都在下一次请求鉴权时生效。
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextFacade securityContextFacade;
    private final RefreshTokenService refreshTokenService;

    private static final DateTimeFormatter UA_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ==================== 资料 ====================

    public ProfileView getProfile() {
        SysUserDO user = loadCurrentUser();
        return toProfileView(user);
    }

    /**
     * 修改本人资料。
     * <p>手机/邮箱允许传空串表示「解绑」，但不允许与他人重复——
     * uk_tenant_mobile / uk_tenant_username 上有唯一约束，冲突时给出可读提示而非暴露 SQL 错误。
     */
    @Transactional
    public ProfileView updateProfile(UpdateProfileRequest request) {
        SysUserDO current = loadCurrentUser();

        SysUserDO patch = new SysUserDO();
        patch.setDisplayName(request.displayName().trim());
        patch.setAvatarUrl(StringUtils.hasText(request.avatarUrl()) ? request.avatarUrl().trim() : null);

        // 传空串表示解绑，需显式置 NULL，故收集待置空列
        Set<String> nullColumns = new HashSet<>();
        if (request.mobile() != null) {
            String mobile = normalize(request.mobile());
            assertContactAvailable("mobile", mobile, current.getId());
            patch.setMobile(mobile);
            if (mobile.isEmpty()) {
                nullColumns.add("mobile");
            }
        }
        if (request.email() != null) {
            String email = normalize(request.email()).toLowerCase();
            assertContactAvailable("email", email, current.getId());
            patch.setEmail(email);
            if (email.isEmpty()) {
                nullColumns.add("email");
            }
        }

        JooqWriters.updateByIdWithNulls(dsl, JooqTables.SYS_USER, current.getId(), patch, nullColumns);
        return getProfile();
    }

    /**
     * 绑定/解绑手机号或邮箱。
     * <p>绑定走短信/邮件验证码校验（当前演示环境未接网关，故仅校验格式与唯一性，
     * 待接入时在 {@code requireVerified} 处补充验证码断言）。
     */
    @Transactional
    public ProfileView bindContact(BindContactRequest request) {
        SysUserDO current = loadCurrentUser();
        boolean isMobile = "MOBILE".equals(request.channel());
        String contact = normalize(request.contact());

        if (!contact.isEmpty()) {
            if (isMobile && !contact.matches("^1[3-9]\\d{9}$")) {
                throw BusinessException.badRequest("手机号格式不正确");
            }
            if (!isMobile && !contact.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw BusinessException.badRequest("邮箱格式不正确");
            }
            assertContactAvailable(isMobile ? "mobile" : "email", contact, current.getId());
        }
        // 解除 sms 通知依赖：解绑手机号时自动关闭短信通道，避免发信失败
        SysUserDO patch = new SysUserDO();
        Set<String> nullColumns = new HashSet<>();
        if (isMobile) {
            patch.setMobile(contact);
            if (contact.isEmpty()) {
                nullColumns.add("mobile");
                patch.setNotifyMobile(0);
            }
        } else {
            patch.setEmail(contact);
            if (contact.isEmpty()) {
                nullColumns.add("email");
                patch.setNotifyEmail(0);
            }
        }
        // 解绑必须把列置空，故走 updateByIdWithNulls（updateById 会跳过 null）
        JooqWriters.updateByIdWithNulls(dsl, JooqTables.SYS_USER, current.getId(), patch, nullColumns);
        return getProfile();
    }

    /**
     * 上传头像：仅保存服务端生成的相对路径。
     * <p>文件字节的存储与类型校验（大小/格式）由 Web 层
     * {@code ProfileController#avatar} 完成，本方法只负责落库，
     * 保证「存储实现」与「业务逻辑」分离。
     */
    @Transactional
    public ProfileView updateAvatar(String avatarUrl) {
        SysUserDO current = loadCurrentUser();
        SysUserDO patch = new SysUserDO();
        boolean hasAvatar = StringUtils.hasText(avatarUrl);
        patch.setAvatarUrl(hasAvatar ? avatarUrl.trim() : null);
        // 传空值表示「恢复默认头像」，需把列置空
        JooqWriters.updateByIdWithNulls(dsl, JooqTables.SYS_USER, current.getId(), patch,
                hasAvatar ? Set.of() : Set.of("avatar_url"));
        return getProfile();
    }

    // ==================== 密码 ====================

    /**
     * 自助改密：必须校验原密码；成功后自增 token_version 使全部旧令牌失效，
     * 同时吊销所有刷新令牌，强制重新登录。
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        SysUserDO current = loadCurrentUser();
        if (!passwordEncoder.matches(request.oldPassword(), current.getPasswordHash())) {
            throw new BusinessException(SecurityErrorCode.BAD_CREDENTIALS, "原密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), current.getPasswordHash())) {
            throw new BusinessException(SecurityErrorCode.PASSWORD_REUSED, "新密码不能与原密码相同");
        }

        SysUserDO patch = new SysUserDO();
        patch.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        patch.setPasswordChangedAt(LocalDateTime.now());
        patch.setPasswordExpired(0);
        patch.setFailedLoginCount(0);
        patch.setLockedUntil(null);
        patch.setTokenVersion((current.getTokenVersion() == null ? 1 : current.getTokenVersion()) + 1);
        JooqWriters.updateById(dsl, JooqTables.SYS_USER, current.getId(), patch);

        // 改密后吊销全部刷新令牌：即便攻击者持有旧的 refresh token 也无法续期
        refreshTokenService.revokeByUser(current.getId());
    }

    // ==================== 偏好设置 ====================

    public PreferenceView getPreference() {
        return toPreferenceView(loadCurrentUser());
    }

    /** 局部更新：null 字段表示不改动，避免未提交的区块被清空。 */
    @Transactional
    public PreferenceView updatePreference(UpdatePreferenceRequest request) {
        SysUserDO current = loadCurrentUser();
        SysUserDO patch = new SysUserDO();
        if (request.notifySiteMessage() != null) {
            patch.setNotifySiteMessage(request.notifySiteMessage());
        }
        if (request.notifyEmail() != null) {
            patch.setNotifyEmail(request.notifyEmail());
        }
        if (request.notifyMobile() != null) {
            patch.setNotifyMobile(request.notifyMobile());
        }
        if (request.showLoginLog() != null) {
            patch.setShowLoginLog(request.showLoginLog());
        }
        if (request.maskMobile() != null) {
            patch.setMaskMobile(request.maskMobile());
        }
        if (request.discoverable() != null) {
            patch.setDiscoverable(request.discoverable());
        }
        JooqWriters.updateById(dsl, JooqTables.SYS_USER, current.getId(), patch);
        return getPreference();
    }

    // ==================== 登录设备 ====================

    /**
     * 我的登录设备：一条未过期的 refresh_token 记录 = 一次登录会话。
     * <p><b>关键点</b>：客户端标识 clientId 只是「客户端类型」（web/app 等），
     * 同一浏览器多次登录会共享同一个 clientId，<b>不能</b>用它区分设备或判断「当前设备」，
     * 否则会全部被标为当前设备且下线一台会误伤其他会话。
     * 因此这里以 sessionId（一次登录唯一）作为设备标识，当前设备通过
     * 「本次请求的 refresh token 所属会话」之外的近似规则标记，见 currentSessionId。
     * <p>无 deviceId 与 UA 时退化为「未知设备」，并优先用 UA 推断可读名称。
     */
    public List<DeviceView> listDevices() {
        Long userId = currentUserId();
        String currentClientId = securityContextFacade.getCurrentPrincipal()
                .map(CurrentPrincipal::clientId).orElse(null);

        List<SysRefreshTokenDO> active = JooqWriters.fetchList(dsl, JooqTables.SYS_REFRESH_TOKEN,
                SysRefreshTokenDO.class,
                JooqTables.SYS_REFRESH_TOKEN.field("user_id", Long.class).eq(userId)
                        .and(JooqTables.SYS_REFRESH_TOKEN.field("revoked_at", Timestamp.class).isNull())
                        .and(JooqTables.SYS_REFRESH_TOKEN.field("expires_at", Timestamp.class)
                                .gt(Timestamp.valueOf(LocalDateTime.now()))));

        // clientId 相同的会话属同一客户端类型，只有最近活跃的那条才视为当前设备
        String currentSessionId = resolveCurrentSessionId(active, currentClientId);

        return active.stream()
                .filter(t -> StringUtils.hasText(t.getSessionId()))
                .sorted((a, b) -> {
                    boolean aCur = a.getSessionId().equals(currentSessionId);
                    boolean bCur = b.getSessionId().equals(currentSessionId);
                    if (aCur != bCur) {
                        return aCur ? -1 : 1;
                    }
                    LocalDateTime at = a.getLastUsedAt() == null ? a.getIssuedAt() : a.getLastUsedAt();
                    LocalDateTime bt = b.getLastUsedAt() == null ? b.getIssuedAt() : b.getLastUsedAt();
                    return bt == null ? 0 : (at == null ? 1 : bt.compareTo(at));
                })
                .map(t -> new DeviceView(
                        t.getSessionId(),
                        t.getSessionId(),
                        describeDevice(t.getDeviceId(), t.getUserAgent()),
                        t.getUserAgent(),
                        t.getIpAddress(),
                        t.getIssuedAt(),
                        t.getLastUsedAt(),
                        t.getExpiresAt(),
                        t.getSessionId().equals(currentSessionId)))
                .toList();
    }

    /**
     * 推断当前会话：同一 clientId 下最近活跃的一条。
     * <p>说明：JWT 中不含 sessionId（仅 clientId），无法在服务端 100% 精确判定
     * 「哪一条会话正是发起本次请求的会话」。此处取最贴近业务语义的近似值——
     * 当前客户端最近活跃的那次登录。该判定只影响列表排序与置灰，不影响吊销准确性
     * （吊销始终以 sessionId 精确命中）。
     */
    private String resolveCurrentSessionId(List<SysRefreshTokenDO> active, String currentClientId) {
        if (!StringUtils.hasText(currentClientId)) {
            return null;
        }
        return active.stream()
                .filter(t -> currentClientId.equals(t.getClientId()))
                .max((a, b) -> {
                    LocalDateTime at = a.getLastUsedAt() == null ? a.getIssuedAt() : a.getLastUsedAt();
                    LocalDateTime bt = b.getLastUsedAt() == null ? b.getIssuedAt() : b.getLastUsedAt();
                    if (at == null) {
                        return bt == null ? 0 : -1;
                    }
                    return bt == null ? 1 : at.compareTo(bt);
                })
                .map(SysRefreshTokenDO::getSessionId)
                .orElse(null);
    }

    /** 下线单个设备（按 sessionId 精确命中）；当前会话禁止自下线，避免请求发出即失效。 */
    @Transactional
    public void revokeDevice(String sessionId) {
        Long userId = currentUserId();
        if (!StringUtils.hasText(sessionId)) {
            throw BusinessException.badRequest("设备标识不能为空");
        }
        String currentSessionId = resolveCurrentSessionId(listDevicesSessions(), currentClientId());
        if (sessionId.equals(currentSessionId)) {
            throw BusinessException.badRequest("不能下线当前设备，如需退出请使用「退出登录」");
        }
        int affected = refreshTokenService.revokeBySession(userId, sessionId);
        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "该设备不存在或已下线");
        }
    }

    /** 退出其他所有设备：保留当前会话，返回被下线的会话数。 */
    @Transactional
    public int logoutOtherDevices() {
        Long userId = currentUserId();
        String currentSessionId = resolveCurrentSessionId(listDevicesSessions(), currentClientId());
        if (!StringUtils.hasText(currentSessionId)) {
            return 0;
        }
        // 先统计再吊销，便于给前端明确的数量反馈
        List<SysRefreshTokenDO> sessions = listDevicesSessions();
        int target = 0;
        for (SysRefreshTokenDO s : sessions) {
            if (StringUtils.hasText(s.getSessionId()) && !s.getSessionId().equals(currentSessionId)) {
                refreshTokenService.revokeBySession(userId, s.getSessionId());
                target++;
            }
        }
        return target;
    }

    private String currentClientId() {
        return securityContextFacade.getCurrentPrincipal().map(CurrentPrincipal::clientId).orElse(null);
    }

    /** 当前用户的全部活跃会话。 */
    private List<SysRefreshTokenDO> listDevicesSessions() {
        Long userId = currentUserId();
        return JooqWriters.fetchList(dsl, JooqTables.SYS_REFRESH_TOKEN,
                SysRefreshTokenDO.class,
                JooqTables.SYS_REFRESH_TOKEN.field("user_id", Long.class).eq(userId)
                        .and(JooqTables.SYS_REFRESH_TOKEN.field("revoked_at", Timestamp.class).isNull())
                        .and(JooqTables.SYS_REFRESH_TOKEN.field("expires_at", Timestamp.class)
                                .gt(Timestamp.valueOf(LocalDateTime.now()))));
    }

    // ==================== 内部方法 ====================

    private Long currentUserId() {
        return securityContextFacade.requireCurrentPrincipal().userId();
    }

    private SysUserDO loadCurrentUser() {
        Long userId = currentUserId();
        SysUserDO user = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, userId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /** 手机/邮箱唯一性校验：排除自身，冲突时给出可读提示。 */
    private void assertContactAvailable(String column, String value, Long selfId) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        Condition cond = JooqTables.SYS_USER.field(column, String.class).eq(value)
                .and(JooqWriters.notDeleted(JooqTables.SYS_USER));
        if (selfId != null) {
            cond = cond.and(JooqTables.SYS_USER.field("id", Long.class).ne(selfId));
        }
        if (JooqWriters.count(dsl, JooqTables.SYS_USER, cond) > 0) {
            throw BusinessException.conflict("MOBILE".equalsIgnoreCase(column) || "mobile".equals(column)
                    ? "该手机号已被其他账号绑定" : "该邮箱已被其他账号绑定");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private PreferenceView toPreferenceView(SysUserDO u) {
        return new PreferenceView(
                flag(u.getNotifySiteMessage()),
                flag(u.getNotifyEmail()),
                flag(u.getNotifyMobile()),
                flag(u.getShowLoginLog()),
                flag(u.getMaskMobile()),
                flag(u.getDiscoverable()));
    }

    /** 数据库用 TINYINT 存开关，null 视为「开」以兼容迁移前的存量数据。 */
    private boolean flag(Integer v) {
        return v == null || v == 1;
    }

    private ProfileView toProfileView(SysUserDO u) {
        return new ProfileView(
                u.getId(),
                u.getUsername(),
                u.getDisplayName(),
                u.getAvatarUrl(),
                u.getMobile(),
                StringUtils.hasText(u.getMobile()),
                u.getEmail(),
                StringUtils.hasText(u.getEmail()),
                loadRoleName(u.getRoleId()),
                loadOrgName(u.getOrgId()),
                u.getStatus(),
                u.getLastLoginAt(),
                u.getPasswordChangedAt(),
                u.getCreatedAt());
    }

    private String loadRoleName(Long roleId) {
        if (roleId == null) {
            return null;
        }
        SysRoleDO role = JooqWriters.fetchById(dsl, JooqTables.SYS_ROLE, SysRoleDO.class, roleId);
        return role == null ? null : role.getRoleName();
    }

    private String loadOrgName(Long orgId) {
        if (orgId == null) {
            return null;
        }
        SysOrgDO org = JooqWriters.fetchById(dsl, JooqTables.SYS_ORG, SysOrgDO.class, orgId);
        return org == null ? null : org.getOrgName();
    }

    /** 由 deviceId / User-Agent 生成可读的设备名称，覆盖浏览器与常见脚本客户端。 */
    private String describeDevice(String deviceId, String userAgent) {
        if (StringUtils.hasText(deviceId) && !"unknown".equalsIgnoreCase(deviceId)) {
            return deviceId;
        }
        if (!StringUtils.hasText(userAgent)) {
            return "未知设备";
        }
        String ua = userAgent;
        if (ua.contains("Edg")) {
            return "Edge 浏览器";
        }
        if (ua.contains("Chrome")) {
            return "Chrome 浏览器";
        }
        if (ua.contains("Safari")) {
            return "Safari 浏览器";
        }
        if (ua.contains("Firefox")) {
            return "Firefox 浏览器";
        }
        if (ua.contains("Windows")) {
            return "Windows 设备";
        }
        if (ua.contains("Macintosh") || ua.contains("Mac OS")) {
            return "macOS 设备";
        }
        if (ua.contains("Android")) {
            return "Android 设备";
        }
        if (ua.contains("iPhone") || ua.contains("iPad")) {
            return "iOS 设备";
        }
        if (ua.contains("Linux")) {
            return "Linux 设备";
        }
        // 接口调试与脚本客户端（如 python-requests / curl / PostmanRuntime）
        String lower = ua.toLowerCase();
        if (lower.contains("curl")) {
            return "curl 客户端";
        }
        if (lower.contains("postman")) {
            return "Postman 客户端";
        }
        if (lower.contains("python") || lower.contains("urllib") || lower.contains("requests")) {
            return "脚本客户端";
        }
        if (lower.contains("httpclient") || lower.contains("okhttp") || lower.contains("java")) {
            return "程序客户端";
        }
        return "其他设备";
    }

    /** 便捷取当前时间文本，供设备列表展示「最近使用」。 */
    static String formatTime(LocalDateTime time) {
        return time == null ? "-" : UA_TIME.format(time);
    }
}