package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqSorts;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.system.dto.AnnouncementQuery;
import com.acme.scaffold.system.dto.AnnouncementView;
import com.acme.scaffold.system.dto.CreateAnnouncementRequest;
import com.acme.scaffold.system.dto.UpdateAnnouncementRequest;
import com.acme.scaffold.system.entity.SysAnnouncementDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 系统公告服务：发布 / 编辑 / 定时生效 / 下线 / 置顶 / 关键词检索。
 *
 * <p><b>状态机</b>：DRAFT(草稿) → PUBLISHED(已发布) → OFFLINE(已下线)。
 * <ul>
 *   <li>发布(publish)：DRAFT 或 OFFLINE 可发布；publishAt 为空表示立即生效，否则到点后由
 *       {@link #publishDueAnnouncements()} 或列表读取时自动可见。</li>
 *   <li>下线(offline)：PUBLISHED → OFFLINE，记录 offlineAt。</li>
 *   <li>编辑(update)：仅 DRAFT / OFFLINE 允许，避免线上公告被静默篡改。</li>
 *   <li>过期：expireAt < now 的 PUBLISHED 公告由定时任务自动置为 OFFLINE。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String OFFLINE = "OFFLINE";

    /** 公告列表可排序字段白名单：客户端字段名 → 数据库列名（白名单外一律忽略）。 */
    private static final Map<String, String> ANNOUNCEMENT_SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "title", "title",
            "status", "status",
            "isTop", "is_top",
            "publishAt", "publish_at",
            "expireAt", "expire_at",
            "createdAt", "created_at");

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * 分页查询公告。排序：置顶优先，其次发布时间倒序。
     * onlyValid=true 时附加「已发布且在有效期内」条件。
     */
    public PageResult<AnnouncementView> list(AnnouncementQuery query) {
        LocalDateTime now = LocalDateTime.now();
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(JooqTables.SYS_ANNOUNCEMENT));

        if (StringUtils.hasText(query.keyword())) {
            String kw = "%" + query.keyword().trim() + "%";
            conds.add(DSL.or(
                    JooqTables.SYS_ANNOUNCEMENT.field("title", String.class).like(kw),
                    JooqTables.SYS_ANNOUNCEMENT.field("content", String.class).like(kw)));
        }
        if (StringUtils.hasText(query.status())) {
            conds.add(JooqTables.SYS_ANNOUNCEMENT.field("status", String.class).eq(query.status()));
        }
        if (Boolean.TRUE.equals(query.onlyValid())) {
            conds.add(JooqTables.SYS_ANNOUNCEMENT.field("status", String.class).eq(PUBLISHED));
            conds.add(DSL.or(JooqTables.SYS_ANNOUNCEMENT.field("publish_at", LocalDateTime.class).isNull(),
                    JooqTables.SYS_ANNOUNCEMENT.field("publish_at", LocalDateTime.class).le(now)));
            conds.add(DSL.or(JooqTables.SYS_ANNOUNCEMENT.field("expire_at", LocalDateTime.class).isNull(),
                    JooqTables.SYS_ANNOUNCEMENT.field("expire_at", LocalDateTime.class).gt(now)));
        }

        var pageQuery = query.toPageQuery();
        PageResult<SysAnnouncementDO> page = JooqWriters.page(dsl, JooqTables.SYS_ANNOUNCEMENT,
                SysAnnouncementDO.class, DSL.and(conds), pageQuery,
                JooqSorts.resolve(JooqTables.SYS_ANNOUNCEMENT, pageQuery, ANNOUNCEMENT_SORT_FIELDS,
                        // 默认：置顶优先(is_top 倒序)，其次发布时间倒序
                        JooqTables.SYS_ANNOUNCEMENT.field("is_top", Integer.class).desc(),
                        JooqTables.SYS_ANNOUNCEMENT.field("publish_at", LocalDateTime.class).desc().nullsLast(),
                        JooqTables.SYS_ANNOUNCEMENT.field("id", Long.class).desc()));

        List<AnnouncementView> views = page.records().stream()
                .map(a -> AnnouncementView.from(a, AnnouncementView.isEffective(a, now)))
                .toList();
        return new PageResult<>(page.page(), page.size(), page.total(), views);
    }

    /** 公告详情；查看时累加浏览次数。 */
    @Transactional
    public AnnouncementView get(Long id) {
        SysAnnouncementDO a = getDO(id);
        // 浏览计数（不影响业务语义，失败不阻断）
        try {
            dsl.update(JooqTables.SYS_ANNOUNCEMENT.table())
                    .set(JooqTables.SYS_ANNOUNCEMENT.field("view_count", Integer.class),
                            JooqTables.SYS_ANNOUNCEMENT.field("view_count", Integer.class).plus(1))
                    .where(JooqTables.SYS_ANNOUNCEMENT.field("id", Long.class).eq(id))
                    .execute();
            a.setViewCount((a.getViewCount() == null ? 0 : a.getViewCount()) + 1);
        } catch (Exception e) {
            log.warn("公告浏览计数累加失败 id={}", id, e);
        }
        return AnnouncementView.from(a, AnnouncementView.isEffective(a, LocalDateTime.now()));
    }

    // ------------------------------------------------------------------
    // 写操作
    // ------------------------------------------------------------------

    /** 新建公告，初始为草稿。 */
    @Transactional
    public Long create(CreateAnnouncementRequest request) {
        validatePeriod(request.publishAt(), request.expireAt());
        SysAnnouncementDO a = new SysAnnouncementDO();
        a.setTitle(request.title().trim());
        a.setContent(request.content());
        a.setStatus(DRAFT);
        a.setIsTop(request.isTop() != null && request.isTop() == 1 ? 1 : 0);
        a.setPublishAt(request.publishAt());
        a.setExpireAt(request.expireAt());
        a.setPublisherId(currentUserId());
        JooqWriters.insert(dsl, JooqTables.SYS_ANNOUNCEMENT, a);
        return a.getId();
    }

    /** 编辑公告；仅草稿/已下线可编辑。 */
    @Transactional
    public void update(Long id, UpdateAnnouncementRequest request) {
        SysAnnouncementDO exist = getDO(id);
        if (PUBLISHED.equals(exist.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "公告已发布，请先下线后再编辑");
        }
        validatePeriod(request.publishAt(), request.expireAt());

        SysAnnouncementDO a = new SysAnnouncementDO();
        if (request.title() != null) {
            a.setTitle(request.title().trim());
        }
        if (request.content() != null) {
            a.setContent(request.content());
        }
        if (request.isTop() != null) {
            a.setIsTop(request.isTop());
        }
        a.setPublishAt(request.publishAt());
        a.setExpireAt(request.expireAt());
        JooqWriters.updateById(dsl, JooqTables.SYS_ANNOUNCEMENT, id, a);
    }

    /**
     * 发布公告：草稿/已下线 → 已发布。
     * publishAt 为空表示立即生效；否则等待到点由定时任务或读取时自动可见。
     */
    @Transactional
    public void publish(Long id, LocalDateTime publishAt, LocalDateTime expireAt) {
        SysAnnouncementDO exist = getDO(id);
        if (PUBLISHED.equals(exist.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "公告已是已发布状态");
        }
        LocalDateTime effectiveAt = publishAt != null ? publishAt : exist.getPublishAt();
        LocalDateTime expire = expireAt != null ? expireAt : exist.getExpireAt();
        validatePeriod(effectiveAt, expire);

        SysAnnouncementDO a = new SysAnnouncementDO();
        a.setStatus(PUBLISHED);
        a.setPublishAt(effectiveAt);
        a.setExpireAt(expire);
        a.setPublishedAt(LocalDateTime.now());
        a.setOfflineAt(null); // 重新发布时清空下线时间
        a.setPublisherId(currentUserId());
        // 重新发布允许直接改置顶
        JooqWriters.updateById(dsl, JooqTables.SYS_ANNOUNCEMENT, id, a);
    }

    /** 下线公告：已发布 → 已下线。 */
    @Transactional
    public void offline(Long id) {
        SysAnnouncementDO exist = getDO(id);
        if (!PUBLISHED.equals(exist.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "仅已发布的公告可以下线");
        }
        SysAnnouncementDO a = new SysAnnouncementDO();
        a.setStatus(OFFLINE);
        a.setOfflineAt(LocalDateTime.now());
        JooqWriters.updateById(dsl, JooqTables.SYS_ANNOUNCEMENT, id, a);
    }

    /** 切换置顶。 */
    @Transactional
    public void toggleTop(Long id) {
        SysAnnouncementDO exist = getDO(id);
        SysAnnouncementDO a = new SysAnnouncementDO();
        a.setIsTop(exist.getIsTop() != null && exist.getIsTop() == 1 ? 0 : 1);
        JooqWriters.updateById(dsl, JooqTables.SYS_ANNOUNCEMENT, id, a);
    }

    /** 删除公告（逻辑删除）。 */
    @Transactional
    public void delete(Long id) {
        getDO(id);
        JooqWriters.delete(dsl, JooqTables.SYS_ANNOUNCEMENT, id, true);
    }

    // ------------------------------------------------------------------
    // 定时任务支持
    // ------------------------------------------------------------------

    /**
     * 扫描到期公告并自动下线（由 JobRunr 周期调用）。
     * 条件：status=PUBLISHED 且 expire_at < now。
     *
     * @return 本次自动下线的条数
     */
    @Transactional
    public int offlineExpired() {
        LocalDateTime now = LocalDateTime.now();
        return dsl.update(JooqTables.SYS_ANNOUNCEMENT.table())
                .set(JooqTables.SYS_ANNOUNCEMENT.field("status", String.class), OFFLINE)
                .set(JooqTables.SYS_ANNOUNCEMENT.field("offline_at", LocalDateTime.class), now)
                .where(JooqTables.SYS_ANNOUNCEMENT.field("status", String.class).eq(PUBLISHED))
                .and(JooqTables.SYS_ANNOUNCEMENT.field("expire_at", LocalDateTime.class).lt(now))
                .execute();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /** 有效期校验：截止必须晚于生效。 */
    private void validatePeriod(LocalDateTime publishAt, LocalDateTime expireAt) {
        if (publishAt != null && expireAt != null && !expireAt.isAfter(publishAt)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "有效期截止时间必须晚于生效时间");
        }
    }

    private SysAnnouncementDO getDO(Long id) {
        SysAnnouncementDO a = JooqWriters.fetchById(dsl, JooqTables.SYS_ANNOUNCEMENT, SysAnnouncementDO.class, id);
        if (a == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "公告不存在");
        }
        return a;
    }

    private Long currentUserId() {
        return securityContextFacade.getCurrentPrincipal().map(p -> p.userId()).orElse(null);
    }
}
