package com.acme.scaffold.system.service;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.BindContactRequest;
import com.acme.scaffold.system.dto.DeviceView;
import com.acme.scaffold.system.dto.PreferenceView;
import com.acme.scaffold.system.dto.ProfileView;
import com.acme.scaffold.system.dto.UpdatePreferenceRequest;
import com.acme.scaffold.system.dto.UpdateProfileRequest;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.entity.SysUserOrgDO;
import com.acme.scaffold.system.entity.SysUserRoleDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 个人中心 / 账号设置应用服务（C2 重构：设备 / 改密子域已抽离至
 * {@link ProfileDeviceService} / {@link ProfilePasswordService}）。
 *
 * <p><b>边界约定</b>：本服务只操作「当前登录用户本人」，一律从
 * {@link SecurityContextFacade} 取 userId，<b>不接收前端传入的用户 ID</b>，
 * 从根本上杜绝越权改他人资料的可能。本类保留「资料维护」与「偏好设置」两组职责；
 * 登录设备与自助改密分别委托给对应的子域服务，保持公开方法签名不变。
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;
    private final ProfileDeviceService profileDeviceService;
    private final ProfilePasswordService profilePasswordService;

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    // ==================== 资料 ====================

    public ProfileView getProfile() {
        SysUserDO user = loadCurrentUser();
        return toProfileView(user);
    }

    /**
     * 修改本人资料。
     * <p>手机/邮箱允许传空串表示「解绑」，非空值须通过格式校验与唯一性预检。
     */
    @Transactional
    public ProfileView updateProfile(UpdateProfileRequest request) {
        SysUserDO current = loadCurrentUser();

        SysUserDO patch = new SysUserDO();
        patch.setDisplayName(request.displayName().trim());
        patch.setAvatarUrl(StringUtils.hasText(request.avatarUrl()) ? request.avatarUrl().trim() : null);

        Set<String> nullColumns = new HashSet<>();
        if (request.mobile() != null) {
            String mobile = normalize(request.mobile());
            if (!mobile.isEmpty()) {
                assertMobileFormat(mobile);
                assertContactAvailable("mobile", mobile, current.getId());
            }
            patch.setMobile(mobile);
            if (mobile.isEmpty()) {
                nullColumns.add("mobile");
            }
        }
        if (request.email() != null) {
            String email = normalize(request.email()).toLowerCase();
            if (!email.isEmpty()) {
                assertEmailFormat(email);
                assertContactAvailable("email", email, current.getId());
            }
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
     * <p>绑定走短信/邮件验证码校验（当前演示环境未接网关，故仅校验格式与唯一性）。
     */
    @Transactional
    public ProfileView bindContact(BindContactRequest request) {
        SysUserDO current = loadCurrentUser();
        boolean isMobile = "MOBILE".equals(request.channel());
        String contact = normalize(request.contact());

        if (!contact.isEmpty()) {
            if (isMobile) {
                assertMobileFormat(contact);
            } else {
                assertEmailFormat(contact);
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
        JooqWriters.updateByIdWithNulls(dsl, JooqTables.SYS_USER, current.getId(), patch, nullColumns);
        return getProfile();
    }

    /**
     * 上传头像：仅保存服务端生成的相对路径。
     */
    @Transactional
    public ProfileView updateAvatar(String avatarUrl) {
        SysUserDO current = loadCurrentUser();
        SysUserDO patch = new SysUserDO();
        boolean hasAvatar = StringUtils.hasText(avatarUrl);
        patch.setAvatarUrl(hasAvatar ? avatarUrl.trim() : null);
        JooqWriters.updateByIdWithNulls(dsl, JooqTables.SYS_USER, current.getId(), patch,
                hasAvatar ? Set.of() : Set.of("avatar_url"));
        return getProfile();
    }

    // ==================== 密码（委托 ProfilePasswordService） ====================

    /** 自助改密：委托 {@link ProfilePasswordService}（校验原密码 / 强度 / 复用 / 失效旧令牌）。 */
    @Transactional
    public void changePassword(com.acme.scaffold.system.dto.ChangePasswordRequest request) {
        profilePasswordService.changePassword(request);
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

    // ==================== 登录设备（委托 ProfileDeviceService） ====================

    public List<DeviceView> listDevices() {
        return profileDeviceService.listDevices();
    }

    public void revokeDevice(String sessionId) {
        profileDeviceService.revokeDevice(sessionId);
    }

    public int logoutOtherDevices() {
        return profileDeviceService.logoutOtherDevices();
    }

    // ==================== 内部方法 ====================

    private SysUserDO loadCurrentUser() {
        Long userId = securityContextFacade.requireCurrentPrincipal().userId();
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
        org.jooq.Condition cond = JooqTables.SYS_USER.field(column, String.class).eq(value)
                .and(JooqWriters.notDeleted(JooqTables.SYS_USER));
        if (selfId != null) {
            cond = cond.and(JooqTables.SYS_USER.field("id", Long.class).ne(selfId));
        }
        if (JooqWriters.count(dsl, JooqTables.SYS_USER, cond) > 0) {
            throw BusinessException.conflict("MOBILE".equalsIgnoreCase(column) || "mobile".equals(column)
                    ? "该手机号已被其他账号绑定" : "该邮箱已被其他账号绑定");
        }
    }

    private void assertMobileFormat(String mobile) {
        if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            throw BusinessException.badRequest("手机号格式不正确");
        }
    }

    private void assertEmailFormat(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw BusinessException.badRequest("邮箱格式不正确");
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
                loadRoleNames(u.getId()),
                loadOrgNames(u.getId()),
                u.getStatus(),
                u.getLastLoginAt(),
                u.getPasswordChangedAt(),
                u.getCreatedAt());
    }

    /** 取用户全部角色名称（多对多，sys_user_role 关联表）。 */
    private List<String> loadRoleNames(Long userId) {
        List<Long> roleIds = JooqWriters.fetchList(dsl, JooqTables.SYS_USER_ROLE, SysUserRoleDO.class,
                        JooqTables.SYS_USER_ROLE.field("user_id", Long.class).eq(userId)).stream()
                .map(SysUserRoleDO::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                        DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ROLE),
                                JooqTables.SYS_ROLE.field("id", Long.class).in(roleIds)))
                .stream().map(SysRoleDO::getRoleName).toList();
    }

    /** 取用户全部部门名称（多对多，sys_user_org 关联表）。 */
    private List<String> loadOrgNames(Long userId) {
        List<Long> orgIds = JooqWriters.fetchList(dsl, JooqTables.SYS_USER_ORG, SysUserOrgDO.class,
                        JooqTables.SYS_USER_ORG.field("user_id", Long.class).eq(userId)).stream()
                .map(SysUserOrgDO::getOrgId).toList();
        if (orgIds.isEmpty()) {
            return List.of();
        }
        return JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                        DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ORG),
                                JooqTables.SYS_ORG.field("id", Long.class).in(orgIds)))
                .stream().map(SysOrgDO::getOrgName).toList();
    }
}
