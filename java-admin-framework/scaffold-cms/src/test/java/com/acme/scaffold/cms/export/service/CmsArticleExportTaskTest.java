package com.acme.scaffold.cms.export.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.cms.article.domain.ArticleView;
import com.acme.scaffold.cms.article.service.CmsArticleService;
import com.acme.scaffold.cms.category.domain.CategoryView;
import com.acme.scaffold.cms.category.service.CmsCategoryService;
import com.acme.scaffold.common.api.domain.PageResult;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** CMS 文章导出任务单测（C3）：契约、字段映射、三格式字节产物。 */
class CmsArticleExportTaskTest {

    private CmsArticleService articleService;
    private CmsCategoryService categoryService;
    private CmsArticleExportTask task;

    @BeforeEach
    void setUp() {
        articleService = mock(CmsArticleService.class);
        categoryService = mock(CmsCategoryService.class);
        task = new CmsArticleExportTask(articleService, categoryService);
    }

    private void stubOneArticle() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 24, 10, 0);
        ArticleView v = new ArticleView(101L, 7L, "标题A", "摘要", "alice", null,
                "PUBLISHED", 42L, t, t, t);
        when(categoryService.list()).thenReturn(List.of(
                new CategoryView(7L, null, "news", "新闻", null, 0, "ACTIVE", t, t)));
        when(articleService.page(anyInt(), anyInt(), any(), any(), any(), isNull(), isNull()))
                .thenReturn(PageResult.of(1, 200, 1, List.of(v)));
    }

    @Test
    void shouldExposeContract() {
        assertEquals("cms-article", task.code());
        assertEquals("CMS 文章列表", task.displayName());
        assertEquals("cms-article", task.fileName());
        assertEquals(8, task.headers().size());
        assertTrue(task.supportsXlsx());
        assertTrue(task.supportsPdf());
    }

    @Test
    void shouldMapRowsWithCategoryName() {
        stubOneArticle();
        List<List<String>> rows = task.rows(Map.of());
        assertEquals(1, rows.size());
        List<String> row = rows.get(0);
        assertEquals("101", row.get(0));
        assertEquals("标题A", row.get(1));
        assertEquals("新闻", row.get(2));
        assertEquals("PUBLISHED", row.get(3));
        assertEquals("alice", row.get(4));
        assertEquals("42", row.get(5));
    }

    @Test
    void shouldPassFiltersThrough() {
        stubOneArticle();
        task.rows(Map.of("categoryId", "7", "title", "关键", "status", "PUBLISHED"));
        org.mockito.Mockito.verify(articleService).page(eq(1), anyInt(), eq(7L),
                eq("关键"), eq("PUBLISHED"), isNull(), isNull());
    }

    @Test
    void shouldGenerateCsvWithBomAndHeader() {
        stubOneArticle();
        byte[] bytes = task.csv(Map.of());
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("\uFEFF"));
        assertTrue(text.contains("文章ID"));
        assertTrue(text.contains("新闻"));
        assertTrue(text.contains("标题A"));
    }

    @Test
    void shouldGeneratePdfBytes() {
        stubOneArticle();
        byte[] bytes = task.pdf(Map.of());
        assertTrue(bytes.length > 100);
        assertTrue(new String(bytes, 0, 8, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
    }

    @Test
    void shouldGenerateXlsxBytes() {
        stubOneArticle();
        byte[] bytes = com.acme.scaffold.common.export.XlsxDataExporter.export(
                task.headers(), task.rows(Map.of()));
        assertTrue(bytes.length > 100);
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
    }

    @Test
    void shouldFallbackCategoryIdWhenUnknown() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 24, 10, 0);
        ArticleView v = new ArticleView(1L, 999L, "无栏目", null, null, null,
                "DRAFT", 0L, null, t, t);
        when(categoryService.list()).thenReturn(List.of());
        when(articleService.page(anyInt(), anyInt(), any(), any(), any(), isNull(), isNull()))
                .thenReturn(PageResult.of(1, 200, 1, List.of(v)));
        List<List<String>> rows = task.rows(null);
        assertEquals("999", rows.get(0).get(2));
        assertEquals("", rows.get(0).get(4)); // 作者 null 映射为空串占位

    }
}
