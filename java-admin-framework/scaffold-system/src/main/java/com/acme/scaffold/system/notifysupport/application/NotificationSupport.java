package com.acme.scaffold.system.notifysupport.application;

import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.Field;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通知相关服务共享静态支撑方法。
 *
 * <p>{@code NotificationQuietHoursService} 与 {@code NotificationPreferenceService} 原本各自内联了一份
 * 完全相同的「地址 → userId 反查」与「按保留列表重建 {@link NotificationMessage}」逻辑；此处收敛为单一事实来源，
 * 两服务保留同名私有/公开方法作为委托壳（调用方零改动）。</p>
 */
public final class NotificationSupport {

    private NotificationSupport() {
    }

    /**
     * 地址 → userId 反查（soft-deleted 用户除外）。
     * 键为归一化地址：email 小写（DB collation 大小写不敏感，存储值可能与原写大小写不一致），mobile 原样 trim。
     */
    public static Map<String, Long> fetchUserIdsByAddress(DSLContext dsl, List<String> addresses, boolean byEmail) {
        SqlTables.TableRef user = SqlTables.SYS_USER;
        String column = byEmail ? "email" : "mobile";
        Field<Long> idField = user.field("id", Long.class);
        Field<String> addrField = user.field(column, String.class);
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
        dsl.select(idField, addrField).from(user.table())
                .where(DSL.and(addrField.in(distinct), SqlWriters.notDeleted(user)))
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

    /** 按保留的接收者 id 列表重建消息（其余字段原样透传）。 */
    public static NotificationMessage withReceivers(NotificationMessage message, List<Long> kept) {
        return new NotificationMessage(message.channel(), message.title(), message.content(),
                message.msgType(), kept, message.roleIds(), message.orgIds(),
                message.webhookUrl(), message.extra(), message.emails(), message.mobiles());
    }

    /** 按保留的地址列表重建消息（email / mobile 二选一由 {@code byEmail} 决定，其余字段原样透传）。 */
    public static NotificationMessage withAddresses(NotificationMessage message, List<String> kept, boolean byEmail) {
        return byEmail
                ? new NotificationMessage(message.channel(), message.title(), message.content(),
                        message.msgType(), message.receiverIds(), message.roleIds(), message.orgIds(),
                        message.webhookUrl(), message.extra(), kept, message.mobiles())
                : new NotificationMessage(message.channel(), message.title(), message.content(),
                        message.msgType(), message.receiverIds(), message.roleIds(), message.orgIds(),
                        message.webhookUrl(), message.extra(), message.emails(), kept);
    }
}
