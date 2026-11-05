package com.acme.scaffold.cms.service;

import com.acme.scaffold.cms.dto.ArticleCreateRequest;
import com.acme.scaffold.cms.dto.ArticleUpdateRequest;
import com.acme.scaffold.cms.dto.ArticleView;
import com.acme.scaffold.cms.dto.CategoryCreateRequest;
import com.acme.scaffold.cms.event.ArticlePublishedEvent;
import com.acme.scaffold.cms.support.CmsH2;
import com.acme.scaffold.cms.support.RecordingEventPublisher;
import com.acme.scaffold.cms.support.StubSecurityContextFacade;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文章发布状态机集成测试（H2）：覆盖 create / update / publish / offline 四条「转入发布态」路径的
 * 幂等性与副作用一致性。
 *
 * <p>回归目标：① 重复 publish 不得刷新 publishedAt、不得重复广播 ArticlePublishedEvent；
 * ② 下线后重新发布必须清除 offline_at（updateById 跳过 null 的列需用 updateByIdWithNulls 显式置空）；
 * ③ 直接以 PUBLISHED 创建、或经 update 转入 PUBLISHED，也应补齐发布时间并广播恰好一次事件。</p>
 *
 * <p>命名为 *Test 而非 *IT：本仓 *IT 由 failsafe 在 verify 阶段执行且本地默认跳过，
 * 采用 surefire 覆盖的命名才能保证双门禁（mvn -o clean test）真正执行这些断言。</p>
 */
class CmsArticlePublishFlowTest {

    private DSLContext dsl;
    private CmsCategoryService categoryService;
    private CmsArticleService articleService;
    private RecordingEventPublisher publisher;
    private long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        dsl = CmsH2.newContext();
        categoryService = new CmsCategoryService(dsl, new StubSecurityContextFacade());
        publisher = new RecordingEventPublisher();
        articleService = new CmsArticleService(dsl, new StubSecurityContextFacade(), publisher);
        categoryId = newCategory();
    }

    private long newCategory() {
        CategoryCreateRequest req = new CategoryCreateRequest();
        req.setCategoryCode("cat");
        req.setCategoryName("栏目");
        return categoryService.create(req);
    }

    private long createArticle(String title, String status) {
        ArticleCreateRequest req = new ArticleCreateRequest();
        req.setCategoryId(categoryId);
        req.setTitle(title);
        req.setStatus(status);
        return articleService.create(req);
    }

    private ArticleUpdateRequest updateReq(long id, String title, String status) {
        ArticleUpdateRequest req = new ArticleUpdateRequest();
        req.setId(id);
        req.setCategoryId(categoryId);
        req.setTitle(title);
        req.setStatus(status);
        return req;
    }

    @Test
    @DisplayName("publish 幂等：重复发布不刷新 publishedAt、不重复广播事件")
    void publish_isIdempotent() {
        long id = createArticle("幂等文章", "DRAFT");

        articleService.publish(id);
        ArticleView first = articleService.getById(id);
        assertEquals("PUBLISHED", first.getStatus());
        assertNotNull(first.getPublishedAt());
        assertEquals(1, publisher.publishedEvents().size());

        articleService.publish(id);
        articleService.publish(id);
        ArticleView after = articleService.getById(id);

        assertEquals("PUBLISHED", after.getStatus());
        assertEquals(first.getPublishedAt(), after.getPublishedAt(), "重复发布不得刷新发布时间");
        assertEquals(1, publisher.publishedEvents().size(), "重复发布不得重复广播事件");
    }

    @Test
    @DisplayName("offline 幂等：重复下线不刷新 offlineAt")
    void offline_isIdempotent() {
        long id = createArticle("下线文章", "DRAFT");
        articleService.publish(id);
        articleService.offline(id);
        LocalDateTime first = articleService.getById(id).getOfflineAt();
        assertNotNull(first);

        articleService.offline(id);
        assertEquals(first, articleService.getById(id).getOfflineAt());
        assertEquals(1, publisher.publishedEvents().size(), "下线不应广播发布事件");
    }

    @Test
    @DisplayName("下线后重新发布：清除 offline_at、刷新 published_at、再广播一次")
    void republishAfterOffline_clearsOfflineAtAndEmitsOnce() {
        long id = createArticle("复发布文章", "DRAFT");
        articleService.publish(id);
        LocalDateTime firstPublishedAt = articleService.getById(id).getPublishedAt();

        articleService.offline(id);
        assertNotNull(articleService.getById(id).getOfflineAt());

        articleService.publish(id);
        ArticleView after = articleService.getById(id);

        assertEquals("PUBLISHED", after.getStatus());
        assertNull(after.getOfflineAt(), "重新发布必须清空下线时间");
        assertNotNull(after.getPublishedAt());
        assertFalse(after.getPublishedAt().isBefore(firstPublishedAt), "重新发布应刷新发布时间");
        assertEquals(2, publisher.publishedEvents().size(), "每次真实状态转换广播恰好一次");
        ArticlePublishedEvent ev = (ArticlePublishedEvent) publisher.publishedEvents().get(1);
        assertEquals(id, ev.getArticleId());
        assertEquals(categoryId, ev.getCategoryId());
    }

    @Test
    @DisplayName("直接以 PUBLISHED 创建：补齐 publishedAt 并广播一次事件")
    void createPublished_emitsEventOnce() {
        long id = createArticle("直接发布", "PUBLISHED");

        ArticleView v = articleService.getById(id);
        assertEquals("PUBLISHED", v.getStatus());
        assertNotNull(v.getPublishedAt(), "直接发布也必须记录发布时间");
        assertEquals(1, publisher.publishedEvents().size());
    }

    @Test
    @DisplayName("update 转入 PUBLISHED：补齐 publishedAt 并广播一次；保持 PUBLISHED 的编辑不再广播")
    void updateTransitionToPublished_emitsOnce() {
        long id = createArticle("编辑发布", "DRAFT");

        articleService.update(updateReq(id, "编辑发布", "PUBLISHED"));
        ArticleView v = articleService.getById(id);
        assertEquals("PUBLISHED", v.getStatus());
        assertNotNull(v.getPublishedAt());
        assertEquals(1, publisher.publishedEvents().size());

        articleService.update(updateReq(id, "改标题", "PUBLISHED"));
        assertEquals(1, publisher.publishedEvents().size(), "非状态转换的编辑不得重复广播");
    }

    @Test
    @DisplayName("update 由 OFFLINE 转回 PUBLISHED：清空 offline_at 并广播一次")
    void updateFromOfflineToPublished_clearsOfflineAt() {
        long id = createArticle("编辑复发布", "DRAFT");
        articleService.publish(id);
        articleService.offline(id);
        assertNotNull(articleService.getById(id).getOfflineAt());

        articleService.update(updateReq(id, "编辑复发布", "PUBLISHED"));

        assertEquals("PUBLISHED", articleService.getById(id).getStatus());
        assertNull(articleService.getById(id).getOfflineAt());
        assertEquals(2, publisher.publishedEvents().size());
    }

    @Test
    @DisplayName("完整状态机 happy path：DRAFT → PUBLISHED → OFFLINE → PUBLISHED")
    void fullStateMachine() {
        long id = createArticle("全流程", "DRAFT");
        assertEquals("DRAFT", articleService.getById(id).getStatus());

        articleService.publish(id);
        assertEquals("PUBLISHED", articleService.getById(id).getStatus());

        articleService.offline(id);
        assertEquals("OFFLINE", articleService.getById(id).getStatus());

        articleService.publish(id);
        ArticleView v = articleService.getById(id);
        assertEquals("PUBLISHED", v.getStatus());
        assertNull(v.getOfflineAt());
        assertTrue(v.getPublishedAt() != null);
        assertEquals(2, publisher.publishedEvents().size());
    }
}
