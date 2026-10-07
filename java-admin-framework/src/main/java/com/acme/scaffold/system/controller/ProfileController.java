package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.system.dto.BindContactRequest;
import com.acme.scaffold.system.dto.ChangePasswordRequest;
import com.acme.scaffold.system.dto.DeviceView;
import com.acme.scaffold.system.dto.PreferenceView;
import com.acme.scaffold.system.dto.ProfileView;
import com.acme.scaffold.system.dto.UpdatePreferenceRequest;
import com.acme.scaffold.system.dto.UpdateProfileRequest;
import com.acme.scaffold.system.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 个人中心 / 账号设置接口。
 *
 * <p><b>鉴权约定</b>：本组接口<b>不加</b> {@code @PreAuthorize} 权限码——
 * 任何已登录用户都应能访问自己的资料；访问对象由服务端从安全上下文取，
 * 接口签名中<b>不存在 userId 参数</b>，因此不存在越权可能。
 *
 * <p>路径前缀刻意使用 {@code /api/profile}（而非 {@code /api/system/profile}）：
 * 前者表达「我的」这一非管理域语义，避免与 /api/system/users 的管理员批量操作混淆。
 */
@Slf4j
@Tag(name = "个人中心")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 仅允许常见图片格式，按扩展名白名单 + 图片魔数双重校验。 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private static final String AVATAR_URL_PREFIX = "/uploads/avatar/";

    private final ProfileService profileService;
    private final SecurityContextFacade securityContextFacade;

    @Value("${app.upload.avatar-dir:./uploads/avatar}")
    private String avatarDir;

    @Value("${app.upload.max-avatar-bytes:2097152}")
    private long maxAvatarBytes;

    // ==================== 资料 ====================

    @Operation(summary = "获取我的资料")
    @GetMapping
    public Result<ProfileView> profile() {
        return Result.success(profileService.getProfile());
    }

    @Operation(summary = "修改我的资料")
    @PutMapping
    @AuditOperation(module = "profile", type = "UPDATE", name = "修改个人资料")
    public Result<ProfileView> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(profileService.updateProfile(request));
    }

    @Operation(summary = "绑定/解绑手机号或邮箱")
    @PutMapping("/contact")
    @AuditOperation(module = "profile", type = "UPDATE", name = "绑定或解绑联系方式")
    public Result<ProfileView> bindContact(@Valid @RequestBody BindContactRequest request) {
        return Result.success(profileService.bindContact(request));
    }

    @Operation(summary = "上传头像")
    @PostMapping("/avatar")
    @AuditOperation(module = "profile", type = "UPDATE", name = "上传头像")
    public Result<ProfileView> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择要上传的头像文件");
        }
        if (file.getSize() > maxAvatarBytes) {
            throw BusinessException.badRequest("头像文件不能超过 " + (maxAvatarBytes / 1024 / 1024) + "MB");
        }
        String ext = resolveExtension(file);
        validateImageContent(file, ext);

        Long userId = securityContextFacade.requireCurrentPrincipal().userId();
        // 文件名完全由服务端生成，杜绝路径穿越与覆盖攻击
        String filename = userId + "_" + LocalDateTime.now().format(FILE_TS) + "_"
                + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
        try {
            Path dir = Paths.get(avatarDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(dir)) {
                throw BusinessException.badRequest("非法的文件名");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("头像上传失败 userId={}", userId, e);
            throw new BusinessException(com.acme.scaffold.common.error.CommonErrorCode.INTERNAL_ERROR,
                    "头像上传失败，请稍后重试");
        }
        return Result.success(profileService.updateAvatar(AVATAR_URL_PREFIX + filename));
    }

    // ==================== 密码 ====================

    @Operation(summary = "修改我的密码")
    @PostMapping("/password")
    @AuditOperation(module = "profile", type = "UPDATE", name = "修改密码")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(request);
        return Result.success();
    }

    // ==================== 偏好设置 ====================

    @Operation(summary = "获取通知与隐私偏好")
    @GetMapping("/preference")
    public Result<PreferenceView> preference() {
        return Result.success(profileService.getPreference());
    }

    @Operation(summary = "更新通知与隐私偏好")
    @PutMapping("/preference")
    @AuditOperation(module = "profile", type = "UPDATE", name = "更新偏好设置")
    public Result<PreferenceView> updatePreference(@Valid @RequestBody UpdatePreferenceRequest request) {
        return Result.success(profileService.updatePreference(request));
    }

    // ==================== 登录设备 ====================

    @Operation(summary = "我的登录设备")
    @GetMapping("/devices")
    public Result<List<DeviceView>> devices() {
        return Result.success(profileService.listDevices());
    }

    @Operation(summary = "下线指定设备")
    @DeleteMapping("/devices/{sessionId}")
    @AuditOperation(module = "profile", type = "DELETE", name = "下线登录设备")
    public Result<Void> revokeDevice(@PathVariable String sessionId) {
        profileService.revokeDevice(sessionId);
        return Result.success();
    }

    @Operation(summary = "退出其他所有设备")
    @PostMapping("/devices/logout-others")
    @AuditOperation(module = "profile", type = "DELETE", name = "退出其他设备")
    public Result<Integer> logoutOtherDevices() {
        return Result.success(profileService.logoutOtherDevices());
    }

    // ==================== 头像校验 ====================

    /** 取并校验扩展名，拒绝白名单外的后缀（如 jsp/html，避免被当作可执行内容存储）。 */
    private String resolveExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (!StringUtils.hasText(name) || !name.contains(".")) {
            throw BusinessException.badRequest("头像文件格式不支持");
        }
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXT.contains(ext)) {
            throw BusinessException.badRequest("头像仅支持 " + String.join("/", ALLOWED_EXT) + " 格式");
        }
        return ext;
    }

    /**
     * 按魔数二次确认文件确实是图片。
     * <p>仅靠扩展名可被伪造（如把脚本改名成 .png），因此读取文件头做真实类型判定。
     */
    private void validateImageContent(MultipartFile file, String ext) {
        byte[] header = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(header, 0, header.length);
            if (read < 4) {
                throw BusinessException.badRequest("头像文件内容为空或已损坏");
            }
        } catch (IOException e) {
            throw BusinessException.badRequest("头像文件读取失败");
        }
        boolean jpeg = (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8;
        boolean png = (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
        boolean gif = "GIF8".equalsIgnoreCase(new String(header, 0, 4));
        boolean webp = header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F';

        boolean valid = switch (ext) {
            case "jpg", "jpeg" -> jpeg;
            case "png" -> png;
            case "gif" -> gif;
            case "webp" -> webp;
            default -> false;
        };
        if (!valid) {
            throw BusinessException.badRequest("头像文件内容与扩展名不符，仅支持真实的图片文件");
        }
    }
}