package com.acme.scaffold.system.service;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooqgen.Tables;
import com.acme.scaffold.security.token.service.RefreshTokenService;
import com.acme.scaffold.security.entity.SysRefreshTokenDO;
import com.acme.scaffold.system.dto.DeviceView;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人中心「登录设备」子域（从 {@link ProfileService} 抽离，C2 重构）。
 *
 * <p>职责：基于 {@code sys_refresh_token} 投影当前用户的活跃会话为设备列表，
 * 并提供单设备下线 / 退出其他设备能力。依赖与行为与抽离前完全一致，
 * {@link ProfileService} 仅做委托，公开方法签名不变。
 */
@Service
@RequiredArgsConstructor
public class ProfileDeviceService {

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;
    private final RefreshTokenService refreshTokenService;

    private static final JooqTables.TableRef RT =
            JooqTables.toTableRef(Tables.SYS_REFRESH_TOKEN);

    private static final java.time.format.DateTimeFormatter UA_TIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 我的登录设备：一条未过期的 refresh_token 记录 = 一次登录会话。
     * <p><b>关键点</b>：客户端标识 clientId 只是「客户端类型」（web/app 等），
     * 同一浏览器多次登录会共享同一个 clientId，<b>不能</b>用它区分设备或判断「当前设备」。
     * 因此这里以 sessionId（一次登录唯一）作为设备标识。
     */
    public List<DeviceView> listDevices() {
        Long userId = currentUserId();
        String currentClientId = securityContextFacade.getCurrentPrincipal()
                .map(CurrentPrincipal::clientId).orElse(null);

        List<SysRefreshTokenDO> active = JooqWriters.fetchList(dsl, RT,
                SysRefreshTokenDO.class,
                RT.field("user_id", Long.class).eq(userId)
                        .and(RT.field("revoked_at", Timestamp.class).isNull())
                        .and(RT.field("expires_at", Timestamp.class)
                                .gt(Timestamp.valueOf(LocalDateTime.now()))));

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

    /** 下线单个设备（按 sessionId 精确命中）；当前会话禁止自下线。 */
    @org.springframework.transaction.annotation.Transactional
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
    @org.springframework.transaction.annotation.Transactional
    public int logoutOtherDevices() {
        Long userId = currentUserId();
        String currentSessionId = resolveCurrentSessionId(listDevicesSessions(), currentClientId());
        if (!StringUtils.hasText(currentSessionId)) {
            return 0;
        }
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

    private Long currentUserId() {
        return securityContextFacade.requireCurrentPrincipal().userId();
    }

    /** 当前用户的全部活跃会话。 */
    private List<SysRefreshTokenDO> listDevicesSessions() {
        Long userId = currentUserId();
        return JooqWriters.fetchList(dsl, RT,
                SysRefreshTokenDO.class,
                RT.field("user_id", Long.class).eq(userId)
                        .and(RT.field("revoked_at", Timestamp.class).isNull())
                        .and(RT.field("expires_at", Timestamp.class)
                                .gt(Timestamp.valueOf(LocalDateTime.now()))));
    }

    /**
     * 推断当前会话：同一 clientId 下最近活跃的一条。
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

    /** 由 deviceId / User-Agent 生成可读的设备名称。 */
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
