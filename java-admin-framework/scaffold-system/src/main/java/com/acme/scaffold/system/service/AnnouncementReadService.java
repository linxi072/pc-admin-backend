package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.system.dto.AnnouncementReadStatsView;
import com.acme.scaffold.system.dto.AnnouncementReceiptQuery;
import com.acme.scaffold.system.dto.AnnouncementReceiptView;
import com.acme.scaffold.system.dto.AnnouncementView;
import com.acme.scaffold.system.dto.SendMessageRequest;
import com.acme.scaffold.system.entity.SysAnnouncementDO;
import com.acme.scaffold.system.entity.SysAnnouncementReadDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.rules.AnnouncementReadRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 公告已读回执中心：标记已读、我的未读、回执分页、已读统计、催办未读。
 *
 * <p><b>模型</b>：懒插入（lazy-insert）——回执行（{@code sys_announcement_read}）仅在用户
 * 确认已读时落行，与站内信「发送即 eager 全员投递」互为补充：
 * <ul>
 *   <li>公告受众是全体活跃用户而阅读者通常远小于受众，eager 模型会让发布动作写放大；</li>
 *   <li>未读名单用反连接推导：活跃用户集合 − 已读回执集合（{@link #myUnread} /
 *       {@link #unreadUserIds}），分母用发布时快照 {@code audience_count}；</li>
 *   <li>幂等锚点是唯一键 {@code uk_ann_user}：先查后插 + 唯一键兜底并发，重复标记
 *       不产生第二行、不重复计数。</li>
 * </ul>
 * <p>催办（{@link #remindUnread}）复用 {@link MessageService#doSend} 落库并实时推送，
 * 不复制一套投递逻辑；上限 {@value AnnouncementReadRules#MAX_REMIND_LIMIT} 人。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementReadService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final String ACTIVE = "ACTIVE";

    /** markAllRead 保护性上限：实际场景未读公告远不会达到，仅防止极端情况下遍历过宽。 */
    private static final int MAX_MARK_ALL = 1000;

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;
    private final MessageService messageService;

    // ------------------------------------------------------------------
    // 写操作
    // ------------------------------------------------------------------

    /**
     * 标记公告已读（幂等）。
     *
     * <p>懒插入回执 + {@code read_count} +1；重复标记直接返回（已存在回执行），
     * 并发下唯一键 {@code uk_ann_user} 拒绝的插入按已读处理——另一事务的回执与计数
     * 在同一事务内原子完成，不会出现「有回执没计数」。
     *
     * <p>仅已发布公告可标记：草稿/已下线对普通用户不可见，其「已读」没有业务意义。
     */
    @Transactional
    public void markRead(Long announcementId) {
        Long userId = requireCurrentUserId();
        SysAnnouncementDO a = JooqWriters.fetchById(dsl, JooqTables.SYS_ANNOUNCEMENT,
                SysAnnouncementDO.class, announcementId);
        if (a == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "公告不存在");
        }
        if (!PUBLISHED.equals(a.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "公告未发布，无需确认已读");
        }
        markReadInternal(announcementId, userId);
    }

    /**
     * 标记单条公告已读（幂等，内部实现）。
     *
     * <p>抽取自 {@link #markRead} 与 {@link #markAllRead} 共用：先查后插 + 唯一键兜底并发，
     * 并发下唯一键 {@code uk_ann_user} 拒绝的插入按已读处理；插入成功后再累加 {@code read_count}。
     * 调用方须保证公告已发布且有效（{@link #markRead} 已校验；{@link #markAllRead} 仅遍历
     * {@link #myUnreadConditions} 过滤出的有效未读公告，天然满足）。
     *
     * @return 本次实际新增的已读回执数（0 表示已读/冲突，1 表示首次标记）
     */
    private int markReadInternal(Long announcementId, Long userId) {
        SysAnnouncementReadDO exist = JooqWriters.fetchOne(dsl, JooqTables.SYS_ANNOUNCEMENT_READ,
                SysAnnouncementReadDO.class,
                DSL.and(JooqTables.SYS_ANNOUNCEMENT_READ.field("announcement_id", Long.class).eq(announcementId),
                        JooqTables.SYS_ANNOUNCEMENT_READ.field("user_id", Long.class).eq(userId)));
        if (exist != null) {
            return 0; // 已读，幂等返回
        }

        SysAnnouncementReadDO r = new SysAnnouncementReadDO();
        r.setAnnouncementId(announcementId);
        r.setUserId(userId);
        try {
            JooqWriters.insert(dsl, JooqTables.SYS_ANNOUNCEMENT_READ, r);
        } catch (DataIntegrityViolationException e) {
            // 并发/重放：应用层查重与插入之间的时间窗由唯一键裁决，冲突即视为已读
            log.debug("公告已读回执并发插入被唯一键拒绝，按幂等处理 announcementId={} userId={}",
                    announcementId, userId);
            return 0;
        }

        dsl.update(JooqTables.SYS_ANNOUNCEMENT.table())
                .set(JooqTables.SYS_ANNOUNCEMENT.field("read_count", Integer.class),
                        JooqTables.SYS_ANNOUNCEMENT.field("read_count", Integer.class).plus(1))
                .where(JooqTables.SYS_ANNOUNCEMENT.field("id", Long.class).eq(announcementId))
                .execute();
        return 1;
    }

    /**
     * 我的未读公告总数（反连接推导，与 {@link #myUnread} 同源）。
     * 供顶栏统一铃铛/通知中心聚合未读角标，无需分页。
     */
    public long countMyUnread() {
        Long userId = requireCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        return JooqWriters.count(dsl, JooqTables.SYS_ANNOUNCEMENT,
                DSL.and(myUnreadConditions(userId, now)));
    }

    /**
     * 一键全部已读（仅作用于当前用户的有效未读公告）。
     *
     * <p>遍历 {@link #myUnreadConditions} 过滤出的未读公告逐条调用 {@link #markReadInternal}，
     * 复用幂等语义与 {@code read_count} 累加；返回实际标记的条数。
     * 上限 {@value #MAX_MARK_ALL} 条（实际场景远不会达到，仅作保护性护栏）。
     */
    @Transactional
    public int markAllRead() {
        Long userId = requireCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        List<SysAnnouncementDO> unread = JooqWriters.fetchTop(dsl, JooqTables.SYS_ANNOUNCEMENT,
                SysAnnouncementDO.class, DSL.and(myUnreadConditions(userId, now)),
                MAX_MARK_ALL, defaultOrder(JooqTables.SYS_ANNOUNCEMENT));
        int marked = 0;
        for (SysAnnouncementDO a : unread) {
            marked += markReadInternal(a.getId(), userId);
        }
        return marked;
    }

    /**
     * 催办未读用户：给尚未阅读的用户发一条站内信（复用投递模型 + 实时推送）。
     *
     * @param limit 本次催办人数上限；为空取默认，越界收敛到 {@code [1, MAX_REMIND_LIMIT]}
     * @return 实际催办（投递）人数
     */
    @Transactional
    public int remindUnread(Long announcementId, Integer limit) {
        SysAnnouncementDO a = JooqWriters.fetchById(dsl, JooqTables.SYS_ANNOUNCEMENT,
                SysAnnouncementDO.class, announcementId);
        if (a == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "公告不存在");
        }
        if (!AnnouncementReadRules.isRemindable(a.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "仅已发布的公告可以催办");
        }
        List<Long> unread = unreadUserIds(announcementId,
                AnnouncementReadRules.resolveRemindLimit(limit));
        if (unread.isEmpty()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "当前没有未读用户，无需催办");
        }
        // 走 doSend 而非 send：催办由管理员触发，没有 HTTP 幂等键，不应经过幂等切面
        SendMessageRequest req = new SendMessageRequest(
                "公告催办：" + a.getTitle(),
                "您有一条公告尚未阅读：《" + a.getTitle() + "》，请及时查看。",
                "NOTICE", unread, null, null);
        int sent = messageService.doSend(req);
        log.info("公告催办完成 announcementId={} 催办人数={}", announcementId, sent);
        return sent;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * 我的未读公告分页（反连接推导：当前有效公告中排除我已读的）。
     * 排序与公告列表一致：置顶优先，其次发布时间倒序。
     */
    public PageResult<AnnouncementView> myUnread(int page, int size) {
        Long userId = requireCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        var ann = JooqTables.SYS_ANNOUNCEMENT;

        var q = PageQuery.of(page, size);
        PageResult<SysAnnouncementDO> pageResult = JooqWriters.page(dsl, ann, SysAnnouncementDO.class,
                DSL.and(myUnreadConditions(userId, now)), q, defaultOrder(ann));
        List<AnnouncementView> views = pageResult.records().stream()
                .map(a -> AnnouncementView.from(a, AnnouncementView.isEffective(a, now)))
                .toList();
        return new PageResult<>(pageResult.page(), pageResult.size(), pageResult.total(), views);
    }

    /** 公告已读回执分页（谁在何时已读），批量装载阅读人显示名防 N+1。 */
    public PageResult<AnnouncementReceiptView> pageReceipts(AnnouncementReceiptQuery query) {
        var rd = JooqTables.SYS_ANNOUNCEMENT_READ;
        var usr = JooqTables.SYS_USER;

        List<Condition> conds = new ArrayList<>();
        conds.add(rd.field("announcement_id", Long.class).eq(query.announcementId()));
        if (StringUtils.hasText(query.keyword())) {
            // 阅读人关键字走子查询限定 user_id 集合：保持主表单表分页，免 join
            String kw = "%" + query.keyword().trim() + "%";
            conds.add(rd.field("user_id", Long.class).in(
                    dsl.select(usr.field("id", Long.class)).from(usr.table())
                            .where(DSL.or(usr.field("display_name", String.class).like(kw),
                                    usr.field("username", String.class).like(kw)))));
        }

        var q = query.toPageQuery();
        PageResult<SysAnnouncementReadDO> pageResult = JooqWriters.page(dsl, rd,
                SysAnnouncementReadDO.class, DSL.and(conds), q,
                rd.field("read_at", LocalDateTime.class).desc(),
                rd.field("id", Long.class).desc());

        Set<Long> userIds = pageResult.records().stream()
                .map(SysAnnouncementReadDO::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = loadDisplayNames(userIds);

        List<AnnouncementReceiptView> views = pageResult.records().stream()
                .map(r -> new AnnouncementReceiptView(r.getId(), r.getAnnouncementId(), r.getUserId(),
                        names.get(r.getUserId()), r.getReadAt()))
                .toList();
        return new PageResult<>(pageResult.page(), pageResult.size(), pageResult.total(), views);
    }

    /** 单条公告已读统计（分子分母与推导值一次齐备）。 */
    public AnnouncementReadStatsView stats(Long announcementId) {
        SysAnnouncementDO a = JooqWriters.fetchById(dsl, JooqTables.SYS_ANNOUNCEMENT,
                SysAnnouncementDO.class, announcementId);
        if (a == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "公告不存在");
        }
        return AnnouncementReadStatsView.of(a);
    }

    /** 已读统计分页（公告管理页内嵌「已读进度」列的数据源），按置顶/发布时间倒序。 */
    public PageResult<AnnouncementReadStatsView> listStats(int page, int size, String keyword) {
        var ann = JooqTables.SYS_ANNOUNCEMENT;
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(ann));
        if (StringUtils.hasText(keyword)) {
            conds.add(ann.field("title", String.class).like("%" + keyword.trim() + "%"));
        }
        var q = PageQuery.of(page, size);
        PageResult<SysAnnouncementDO> pageResult = JooqWriters.page(dsl, ann, SysAnnouncementDO.class,
                DSL.and(conds), q, defaultOrder(ann));
        List<AnnouncementReadStatsView> views = pageResult.records().stream()
                .map(AnnouncementReadStatsView::of)
                .toList();
        return new PageResult<>(pageResult.page(), pageResult.size(), pageResult.total(), views);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /** 公告默认排序：置顶优先，其次发布时间倒序（与公告列表口径一致）。 */
    private OrderField<?>[] defaultOrder(JooqTables.TableRef ann) {
        return new OrderField[]{
                ann.field("is_top", Integer.class).desc(),
                ann.field("publish_at", LocalDateTime.class).desc().nullsLast(),
                ann.field("id", Long.class).desc()};
    }

    /**
     * 我的未读公告条件（反连接推导）：已发布且在有效期内，且不存在我的回执行。
     * 子查询内两侧列均加表名限定——两表都有 id 列，无限定时 MySQL 会按内层作用域把
     * id 解析成回执表的 id，关联必错。被 {@link #myUnread} / {@link #countMyUnread} /
     * {@link #markAllRead} 共用，保证三者未读口径完全一致。
     */
    private List<Condition> myUnreadConditions(Long userId, LocalDateTime now) {
        var ann = JooqTables.SYS_ANNOUNCEMENT;
        var rd = JooqTables.SYS_ANNOUNCEMENT_READ;
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(ann));
        conds.add(ann.field("status", String.class).eq(PUBLISHED));
        conds.add(DSL.or(ann.field("publish_at", LocalDateTime.class).isNull(),
                ann.field("publish_at", LocalDateTime.class).le(now)));
        conds.add(DSL.or(ann.field("expire_at", LocalDateTime.class).isNull(),
                ann.field("expire_at", LocalDateTime.class).gt(now)));
        conds.add(DSL.notExists(
                dsl.selectOne().from(rd.table())
                        .where(qualified(rd.name(), "announcement_id")
                                        .eq(qualified(ann.name(), "id")),
                                qualified(rd.name(), "user_id").eq(userId))));
        return conds;
    }

    /**
     * 未读用户 ID 列表：活跃用户 − 已读回执（反连接），按 limit 截断。
     * 子查询内引用外表列必须加表名限定（两表都有 id 列，无限定必解析错）。
     */
    private List<Long> unreadUserIds(Long announcementId, int limit) {
        var usr = JooqTables.SYS_USER;
        var rd = JooqTables.SYS_ANNOUNCEMENT_READ;
        return JooqWriters.fetchTop(dsl, usr, SysUserDO.class,
                DSL.and(JooqWriters.notDeleted(usr),
                        usr.field("status", String.class).eq(ACTIVE),
                        DSL.notExists(dsl.selectOne().from(rd.table())
                                .where(qualified(rd.name(), "announcement_id").eq(announcementId))
                                .and(qualified(rd.name(), "user_id")
                                        .eq(qualified(usr.name(), "id"))))),
                limit,
                usr.field("id", Long.class).asc()).stream()
                .map(SysUserDO::getId).collect(Collectors.toList());
    }

    /** 批量装载用户显示名：userId → displayName（用户被删时缺项，视图降级展示 userId）。 */
    private Map<Long, String> loadDisplayNames(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return JooqWriters.fetchList(dsl, JooqTables.SYS_USER, SysUserDO.class,
                        JooqTables.SYS_USER.field("id", Long.class).in(userIds)).stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(SysUserDO::getId, SysUserDO::getDisplayName, (a, b) -> a));
    }

    /**
     * 跨表关联字段：子查询内引用他表列必须加表名限定。
     * {@code TableRef.field} 产出的是无限定列名，直接放进子查询会被 MySQL 按
     * 内层作用域解析（两表同名列时必然关联错），故此处显式限定。
     */
    private static Field<Long> qualified(String table, String column) {
        return DSL.field(DSL.name(table, column), Long.class);
    }

    private Long currentUserId() {
        return securityContextFacade.getCurrentPrincipal().map(p -> p.userId()).orElse(null);
    }

    private Long requireCurrentUserId() {
        Long userId = currentUserId();
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "未获取到当前登录用户");
        }
        return userId;
    }
}
