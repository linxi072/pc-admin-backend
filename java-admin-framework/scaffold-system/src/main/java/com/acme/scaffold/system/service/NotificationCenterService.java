package com.acme.scaffold.system.service;

import com.acme.scaffold.system.dto.NotificationItemView;
import com.acme.scaffold.system.dto.NotificationSummaryView;
import com.acme.scaffold.system.rules.NotificationCenterRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 通知中心聚合服务：贯穿全站的统一未读入口。
 *
 * <p><b>设计</b>：只读聚合 + 一处写操作，不引入新表、不新增迁移。
 * 聚合两类既有未读来源（复用 M7 公告回执中心与站内信模型，零重复实现）：
 * <ul>
 *   <li>公告未读：{@link AnnouncementReadService#countMyUnread()}/{@link AnnouncementReadService#myUnread}
 *       （懒插入反连接推导，需已发布且在有效期内）；</li>
 *   <li>站内信未读：{@link MessageService#unreadCount()}/{@link MessageService#pageMine}
 *       （eager 投递回执，is_read=0 即未读）。</li>
 * </ul>
 * 合并/排序/截断/上限钳制等纯逻辑下沉到 {@link NotificationCenterRules}，本服务只做编排与映射。
 *
 * <p><b>权限</b>：接口为登录用户自身动作（查看自己的未读、标记自己已读），仅登录即可，
 * 不挂权限码——与既有的 {@code /api/system/messages/unread-count}、{@code /mine/unread}
 * 同构，保证所有登录用户（含非 SUPER_ADMIN）都能用顶栏铃铛。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCenterService {

    private final AnnouncementReadService announcementReadService;
    private final MessageService messageService;

    /** 未读汇总：公告未读 + 站内信未读 + 合计。供顶栏铃铛角标。 */
    public NotificationSummaryView summary() {
        long announcementUnread = announcementReadService.countMyUnread();
        long messageUnread = messageService.unreadCount();
        return new NotificationSummaryView(announcementUnread, messageUnread,
                NotificationCenterRules.totalUnread(announcementUnread, messageUnread));
    }

    /**
     * 近期未读条目：取各来源前 limit 条未读，合并按时间倒序截断。
     *
     * @param limit 期望条数；{@code null} 取默认，越界经 {@link NotificationCenterRules#clampRecentLimit} 收敛
     */
    public List<NotificationItemView> recent(Integer limit) {
        int n = NotificationCenterRules.clampRecentLimit(limit);

        List<NotificationItemView> announcements = announcementReadService.myUnread(1, n).records().stream()
                .map(a -> new NotificationItemView(
                        "ANNOUNCEMENT",
                        a.id(),
                        a.title(),
                        truncate(a.content(), 80),
                        "/system/notice?tab=announcement&id=" + a.id(),
                        a.publishAt() != null ? a.publishAt() : a.createdAt()))
                .toList();

        List<NotificationItemView> messages = messageService.pageMine(1, n, true).records().stream()
                .map(m -> new NotificationItemView(
                        "MESSAGE",
                        m.messageId(),
                        m.title(),
                        truncate(m.content(), 80),
                        "/system/notice?tab=message&id=" + m.messageId(),
                        m.sentAt()))
                .toList();

        return NotificationCenterRules.mergeAndSortRecent(announcements, messages, NotificationItemView::occurredAt, n);
    }

    /**
     * 一键全部已读（公告 + 站内信两类来源）。返回各自标记数与合计，供前端提示。
     * 委托既有服务，复用其幂等语义与计数逻辑。
     */
    @Transactional
    public Map<String, Integer> markAllRead() {
        int announcementMarked = announcementReadService.markAllRead();
        int messageMarked = messageService.markAllRead();
        return Map.of(
                "announcementMarked", announcementMarked,
                "messageMarked", messageMarked,
                "marked", announcementMarked + messageMarked);
    }

    /** 摘要截断：去多余空白、超长截断并补省略号；null 安全。 */
    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String compact = s.replaceAll("\\s+", " ").trim();
        if (compact.length() <= max) {
            return compact;
        }
        return compact.substring(0, max) + "…";
    }
}
