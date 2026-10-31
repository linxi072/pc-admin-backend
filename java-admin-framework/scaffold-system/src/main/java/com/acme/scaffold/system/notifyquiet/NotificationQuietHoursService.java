package com.acme.scaffold.system.notifyquiet;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooqgen.Tables;
import com.acme.scaffold.system.notifyquiet.domain.SysNotificationQuietHoursDO;
import com.acme.scaffold.system.notifyquiet.dto.UpdateQuietHoursRequest;
import com.acme.scaffold.system.notifyquiet.rules.NotificationQuietHoursRules;
import com.acme.scaffold.system.notifyquiet.rules.NotificationQuietHoursRules.QuietSetting;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 免打扰时段应用服务（V44 免打扰中心）：「个人中心 → 免打扰」自服务页数据源，
 * 兼作 {@code NotificationService.dispatch} 发送前的<b>时间维总闸</b>。
 *
 * <p><b>自服务边界</b>：与 {@code ProfileService} / {@code NotificationPreferenceService} 同范式——
 * 只操作「当前登录用户本人」，一律从 {@link SecurityContextFacade} 取 userId，
 * 不接收前端传入的用户 ID。</p>
 *
 * <p><b>懒行模型</b>：无行 = 无免打扰（与 M13 偏好「无行默认开通」互为镜像）；
 * 关停免打扰保留配置行（{@code enabled=0}），删行语义由前端「恢复默认」不提供
 * （免打扰只有一组时间窗，关闭开关即语义还原）。</p>
 *
 * <p><b>过滤口径</b>（{@link #applyQuietHours}，供 dispatch 挂点调用，先于 M13 偏好过滤）：
 * <ul>
 *   <li>IN_APP / WEBSOCKET：按 {@code receiverIds} 逐用户判定；</li>
 *   <li>EMAIL / SMS：按 {@code emails} / {@code mobiles} 反查 sys_user 后逐用户判定，
 *       无匹配用户的外部地址默认放行（不因注册用户的作息抑制外部收件人）；</li>
 *   <li>广播（IN_APP 仅传 roleIds/orgIds、WEBSOCKET 无定向接收者）与 WEBHOOK 不过滤——
 *       受众规模不定且无个人作息语义，属文档化限制；</li>
 *   <li>定向接收者全部处于免打扰时返回 {@code null}，由调用方记 {@code SKIPPED}
 *       （原因 {@link NotificationQuietHoursRules#REASON_QUIET_HOURS}）并跳过投递。</li>
 * </ul>
 * 定向接收者规模有限（大批量受众走角色/机构广播，不过滤），故反查与行查询均按入参列表
 * 单次 IN 查询，不做分页。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class NotificationQuietHoursService {
    private static final JooqTables.TableRef USER = JooqTables.toTableRef(Tables.SYS_USER);

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;

    /** 桥接自代码生成层 {@code Tables.SYS_NOTIFICATION_QUIET_HOURS} 的类型安全表引用（B18）。 */
    private static final JooqTables.TableRef QUIET_HOURS =
            JooqTables.toTableRef(Tables.SYS_NOTIFICATION_QUIET_HOURS);

    // ------------------------------------------------------------------
    // 自服务：查询与更新
    // ------------------------------------------------------------------

    /** 我的免打扰配置（无行返回默认视图：未启用 + 22:00–08:00 + 告警穿透）。 */
    public NotificationQuietHoursView mine() {
        Long userId = currentUserId();
        SysNotificationQuietHoursDO row = fetchRow(userId);
        return row == null ? NotificationQuietHoursView.defaults()
                : NotificationQuietHoursView.of(row, isQuietNow(row));
    }

    /**
     * 更新我的免打扰（全量快照：一组起止时间，无局部更新语义）。
     *
     * <p>先全额校验再落库（与 M13 同范式）：时间格式 {@code HH:mm}、起止不可相同，
     * 避免把非法窗口写进库里污染发送链路判定。写入走「查现存 → 更新或插入」
     * （本仓动态 DSL 无 upsert API），唯一键 {@code uk_user} 兜底并发。</p>
     *
     * @return 更新后的配置视图（与 {@link #mine()} 同口径，省一次前端回查）
     */
    @Transactional
    public NotificationQuietHoursView update(UpdateQuietHoursRequest request) {
        Long userId = currentUserId();
        LocalTime start = requireTime(request.startTime(), "开始时间");
        LocalTime end = requireTime(request.endTime(), "结束时间");
        if (start.equals(end)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "免打扰开始时间与结束时间不能相同");
        }
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        boolean breakthrough = !Boolean.FALSE.equals(request.alertBreakthrough());

        SysNotificationQuietHoursDO exist = fetchRow(userId);
        if (exist != null) {
            SysNotificationQuietHoursDO patch = new SysNotificationQuietHoursDO();
            patch.setEnabled(enabled ? 1 : 0);
            patch.setStartTime(start);
            patch.setEndTime(end);
            patch.setAlertBreakthrough(breakthrough ? 1 : 0);
            JooqWriters.updateById(dsl, QUIET_HOURS,
                    exist.getId(), patch);
        } else {
            SysNotificationQuietHoursDO row = new SysNotificationQuietHoursDO();
            row.setUserId(userId);
            row.setEnabled(enabled ? 1 : 0);
            row.setStartTime(start);
            row.setEndTime(end);
            row.setAlertBreakthrough(breakthrough ? 1 : 0);
            JooqWriters.insert(dsl, QUIET_HOURS, row);
        }
        return mine();
    }

    // ------------------------------------------------------------------
    // 发送前过滤（NotificationService.dispatch 挂点）
    // ------------------------------------------------------------------

    /**
     * 按免打扰时段过滤消息（时间维总闸，先于 {@code NotificationPreferenceService} 生效）。
     *
     * @return 过滤后的消息（无人被抑制时原样返回同一实例）；全部定向接收者均处于
     *         免打扰时返回 {@code null}——调用方应记一条 {@code SKIPPED} 发送记录
     *         （原因 {@link NotificationQuietHoursRules#REASON_QUIET_HOURS}）并跳过投递
     */
    public NotificationMessage applyQuietHours(NotificationMessage message) {
        Channel channel = message.channel();
        switch (channel) {
            case IN_APP:
                return applyByReceivers(message, true);
            case WEBSOCKET:
                // 空 receiverIds 即「广播」语义：全抑制时绝不能退化成向全部在线用户广播
                return applyByReceivers(message, false);
            case EMAIL:
                return applyByAddresses(message, true);
            case SMS:
                return applyByAddresses(message, false);
            default:
                // WEBHOOK：地址属外部系统，无个人作息语义，不过滤（文档化限制）
                return message;
        }
    }

    /** 按 userId 列表过滤（IN_APP / WEBSOCKET）；广播（空 receiverIds）不过滤。 */
    private NotificationMessage applyByReceivers(NotificationMessage message, boolean hasBroadcastFallback) {
        List<Long> ids = message.receiverIds();
        if (ids == null || ids.isEmpty()) {
            return message;
        }
        Map<Long, QuietSetting> settings = fetchSettings(ids);
        List<Long> kept = NotificationQuietHoursRules.filterReceivers(
                message.msgType(), LocalTime.now(), ids, settings);
        if (kept.isEmpty()) {
            // 定向接收者全部处于免打扰：IN_APP 若仍带角色/机构广播面则继续（走批量投递），否则整体跳过
            boolean hasBroadcast = hasBroadcastFallback
                    && (hasElements(message.roleIds()) || hasElements(message.orgIds()));
            return hasBroadcast ? withReceivers(message, kept) : null;
        }
        return kept.size() == ids.size() ? message : withReceivers(message, kept);
    }

    /** 按地址列表过滤（EMAIL / SMS）：地址反查 sys_user 后逐用户判定，外部地址默认放行。 */
    private NotificationMessage applyByAddresses(NotificationMessage message, boolean byEmail) {
        List<String> addresses = byEmail ? message.emails() : message.mobiles();
        if (addresses == null || addresses.isEmpty()) {
            // 无接收方：原样放行，由适配器按既有语义报错（保持 FAILED 留痕，不伪装成 SKIPPED）
            return message;
        }
        Map<String, Long> addressUserIds = fetchAddressUserIds(addresses, byEmail);
        if (addressUserIds.isEmpty()) {
            // 全部为外部地址（无法映射到任何注册用户）：无个人作息语义，原样放行
            return message;
        }
        Map<Long, QuietSetting> settings = fetchSettings(new ArrayList<>(addressUserIds.values()));
        LocalTime now = LocalTime.now();
        List<String> kept = new ArrayList<>(addresses.size());
        for (String address : addresses) {
            if (address == null || address.isBlank()) {
                kept.add(address);
                continue;
            }
            String key = byEmail ? address.trim().toLowerCase(Locale.ROOT) : address.trim();
            Long userId = addressUserIds.get(key);
            if (userId == null
                    || !NotificationQuietHoursRules.isSuppressed(message.msgType(), now,
                            settings.get(userId))) {
                kept.add(address);
            }
        }
        if (kept.isEmpty()) {
            return null;
        }
        return kept.size() == addresses.size() ? message : withAddresses(message, kept, byEmail);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private SysNotificationQuietHoursDO fetchRow(Long userId) {
        return JooqWriters.fetchOne(dsl, QUIET_HOURS,
                SysNotificationQuietHoursDO.class,
                Tables.SYS_NOTIFICATION_QUIET_HOURS.USER_ID.eq(userId));
    }

    /** 批量取免打扰配置：userId → QuietSetting。absent = 无行（无免打扰）。 */
    private Map<Long, QuietSetting> fetchSettings(List<Long> userIds) {
        Map<Long, QuietSetting> out = new HashMap<>(userIds.size() * 2);
        if (userIds.isEmpty()) {
            return out;
        }
        List<SysNotificationQuietHoursDO> rows = JooqWriters.fetchList(dsl,
                QUIET_HOURS, SysNotificationQuietHoursDO.class,
                Tables.SYS_NOTIFICATION_QUIET_HOURS.USER_ID.in(userIds));
        for (SysNotificationQuietHoursDO row : rows) {
            if (row.getUserId() != null) {
                out.put(row.getUserId(), new QuietSetting(
                        row.getEnabled() != null && row.getEnabled() != 0,
                        row.getStartTime(), row.getEndTime(),
                        row.getAlertBreakthrough() == null || row.getAlertBreakthrough() != 0));
            }
        }
        return out;
    }

    /**
     * 地址 → userId 反查（soft-deleted 用户除外）。
     * 键为归一化地址：email 小写（DB collation 大小写不敏感，存储值可能与原写大小写不一致），
     * mobile 原样 trim。与 {@code NotificationPreferenceService} 同名私有方法口径一致
     * （两处挂点共享同一反查语义，暂不抽公共层：偏好与免打扰的过滤结论相互独立）。
     */
    private Map<String, Long> fetchAddressUserIds(List<String> addresses, boolean byEmail) {
        String column = byEmail ? "email" : "mobile";
        Field<Long> idField = USER.field("id", Long.class);
        Field<String> addrField = USER.field(column, String.class);
        List<String> distinct = new ArrayList<>();
        for (String address : addresses) {
            if (address != null && !address.isBlank() && !distinct.contains(address.trim())) {
                distinct.add(address.trim());
            }
        }
        Map<String, Long> out = new HashMap<>(distinct.size() * 2);
        if (distinct.isEmpty()) {
            return out;
        }
        dsl.select(idField, addrField).from(USER.table())
                .where(DSL.and(addrField.in(distinct), JooqWriters.notDeleted(USER)))
                .fetch()
                .forEach(r -> {
                    String addr = r.get(addrField);
                    if (addr != null && !addr.isBlank()) {
                        out.put(byEmail ? addr.trim().toLowerCase(Locale.ROOT) : addr.trim(),
                                r.get(idField));
                    }
                });
        return out;
    }

    /** 当前是否正处于免打扰中（仅视图展示用；发送链路以 {@code LocalTime.now()} 实时判定）。 */
    private static boolean isQuietNow(SysNotificationQuietHoursDO row) {
        return row.getEnabled() != null && row.getEnabled() != 0
                && NotificationQuietHoursRules.isQuiet(LocalTime.now(),
                        row.getStartTime(), row.getEndTime());
    }

    private static LocalTime requireTime(String value, String label) {
        LocalTime time = NotificationQuietHoursRules.parseTime(value);
        if (time == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "免打扰" + label + "格式应为 HH:mm: " + value);
        }
        return time;
    }

    private Long currentUserId() {
        return securityContextFacade.requireCurrentPrincipal().userId();
    }

    private static boolean hasElements(List<?> list) {
        return list != null && !list.isEmpty();
    }

    private static NotificationMessage withReceivers(NotificationMessage message, List<Long> kept) {
        return new NotificationMessage(message.channel(), message.title(), message.content(),
                message.msgType(), kept, message.roleIds(), message.orgIds(),
                message.webhookUrl(), message.extra(), message.emails(), message.mobiles());
    }

    private static NotificationMessage withAddresses(NotificationMessage message, List<String> kept,
                                                     boolean byEmail) {
        return byEmail
                ? new NotificationMessage(message.channel(), message.title(), message.content(),
                        message.msgType(), message.receiverIds(), message.roleIds(), message.orgIds(),
                        message.webhookUrl(), message.extra(), kept, message.mobiles())
                : new NotificationMessage(message.channel(), message.title(), message.content(),
                        message.msgType(), message.receiverIds(), message.roleIds(), message.orgIds(),
                        message.webhookUrl(), message.extra(), message.emails(), kept);
    }
}
