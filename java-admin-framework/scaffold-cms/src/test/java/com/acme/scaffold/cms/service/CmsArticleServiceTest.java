package com.acme.scaffold.cms.service;

import com.acme.scaffold.cms.dto.ArticleCreateRequest;
import com.acme.scaffold.cms.dto.ArticleView;
import com.acme.scaffold.cms.dto.CategoryCreateRequest;
import com.acme.scaffold.cms.event.ArticlePublishedEvent;
import com.acme.scaffold.cms.support.CmsH2;
import com.acme.scaffold.cms.support.RecordingEventPublisher;
import com.acme.scaffold.cms.support.StubSecurityContextFacade;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.exception.BusinessException;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文章服务集成测试：H2 承载 DDL，直接实例化 Service；用 {@link RecordingEventPublisher}
 * 断言「发布事件」经 R2 事件总线广播。
 */
class CmsArticleServiceTest {

    private DSLContext dsl;
    private CmsCategoryService categoryService;
    private CmsArticleService articleService;
    private RecordingEventPublisher publisher;

    @BeforeEach
    void setUp() throws Exception {
        dsl = CmsH2.newContext();
        categoryService = new CmsCategoryService(dsl, new StubSecurityContextFacade());
        publisher = new RecordingEventPublisher();
        articleService = new CmsArticleService(dsl, new StubSecurityContextFacade(), publisher);
    }

    private long newCategory() {
        CategoryCreateRequest req = new CategoryCreateRequest();
        req.setCategoryCode("cat");
        req.setCategoryName("栏目");
        return categoryService.create(req);
    }

    private long createArticle(long catId, String title, String status) {
        ArticleCreateRequest req = new ArticleCreateRequest();
        req.setCategoryId(catId);
        req.setTitle(title);
        req.setStatus(status);
        return articleService.create(req);
    }

    @Test
    void createDefaultsToDraft() {
        long cat = newCategory();
        long id = createArticle(cat, "标题1", "DRAFT");
        ArticleView v = articleService.getById(id);
        assertEquals("DRAFT", v.getStatus());
        assertEquals("标题1", v.getTitle());
    }

    @Test
    void createMissingCategoryFails() {
        ArticleCreateRequest req = new ArticleCreateRequest();
        req.setCategoryId(999L);
        req.setTitle("x");
        assertThrows(BusinessException.class, () -> articleService.create(req));
    }

    @Test
    void publishSetsStatusAndEmitsEvent() {
        long cat = newCategory();
        long id = createArticle(cat, "发布文章", "DRAFT");
        articleService.publish(id);
        ArticleView v = articleService.getById(id);
        assertEquals("PUBLISHED", v.getStatus());
        assertNotNull(v.getPublishedAt());
        assertEquals(1, publisher.publishedEvents().size());
        assertTrue(publisher.publishedEvents().get(0) instanceof ArticlePublishedEvent);
        ArticlePublishedEvent ev = (ArticlePublishedEvent) publisher.publishedEvents().get(0);
        assertEquals(id, ev.getArticleId());
    }

    @Test
    void offlineSetsStatus() {
        long cat = newCategory();
        long id = createArticle(cat, "下线文章", "PUBLISHED");
        articleService.offline(id);
        ArticleView v = articleService.getById(id);
        assertEquals("OFFLINE", v.getStatus());
        assertNotNull(v.getOfflineAt());
    }

    @Test
    void viewCountIncrements() {
        long cat = newCategory();
        long id = createArticle(cat, "计数", "DRAFT");
        articleService.incrementViewCount(id);
        articleService.incrementViewCount(id);
        assertEquals(2L, articleService.getById(id).getViewCount());
    }

    @Test
    void pageFiltersByStatusAndTitle() {
        long cat = newCategory();
        createArticle(cat, "苹果新闻", "DRAFT");
        createArticle(cat, "香蕉新闻", "PUBLISHED");
        createArticle(cat, "橙子新闻", "DRAFT");
        PageResult<ArticleView> draft = articleService.page(1, 20, null, null, "DRAFT", "createdAt", "desc");
        assertEquals(2L, draft.total());
        PageResult<ArticleView> byTitle = articleService.page(1, 20, null, "香蕉", null, "createdAt", "desc");
        assertEquals(1L, byTitle.total());
    }
}
