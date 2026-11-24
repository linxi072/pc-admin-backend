package com.acme.scaffold.cms.export.service;

import com.acme.scaffold.cms.article.domain.ArticleView;
import com.acme.scaffold.cms.article.service.CmsArticleService;
import com.acme.scaffold.cms.category.domain.CategoryView;
import com.acme.scaffold.cms.category.service.CmsCategoryService;
import com.acme.scaffold.common.api.domain.PageResult;
import com.acme.scaffold.common.export.ExportTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CMS 文章列表导出（C3）：实现 {@link ExportTask} 契约即被导出中心自动发现，
 * 无需新增接口或前端页面；CSV/XLSX/PDF 三格式由接口默认方法提供。
 *
 * <p><b>取数复用</b>：翻页调用 {@link CmsArticleService#page}，与文章管理列表同口径
 * （逻辑删除自动排除、状态/栏目/标题筛选透传）；栏目列经 {@link CmsCategoryService#list()}
 * 构建 id→名称映射，未知栏目回退为 id 字符串，避免逐行查询。</p>
 *
 * <p><b>取数上限</b>：单页 {@value #PAGE_SIZE} 行循环拉取，总行数超 {@value #MAX_ROWS} 截断并告警，
 * 约束导出内存与耗时。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CmsArticleExportTask implements ExportTask {

    /** 单次导出取数上限。 */
    private static final int MAX_ROWS = 10_000;

    /** 翻页大小（需不大于服务端 MAX_PAGE_SIZE，200 为导出中心惯例）。 */
    private static final int PAGE_SIZE = 200;

    private final CmsArticleService articleService;
    private final CmsCategoryService categoryService;

    @Override
    public String code() {
        return "cms-article";
    }

    @Override
    public String displayName() {
        return "CMS 文章列表";
    }

    @Override
    public String fileName() {
        return "cms-article";
    }

    @Override
    public List<String> headers() {
        return List.of("文章ID", "标题", "栏目", "状态", "作者", "浏览数", "发布时间", "创建时间");
    }

    @Override
    public List<List<String>> rows(Map<String, String> params) {
        Map<String, String> p = params == null ? Map.of() : params;
        Map<Long, String> categoryNames = categoryNames();
        return fetchAll(p).stream().map(v -> List.of(
                str(v.getId()),
                str(v.getTitle()),
                categoryNames.getOrDefault(v.getCategoryId(), str(v.getCategoryId())),
                str(v.getStatus()),
                str(v.getAuthor()),
                str(v.getViewCount()),
                str(v.getPublishedAt()),
                str(v.getCreatedAt())
        )).toList();
    }

    /** 栏目 id→名称映射（一次性拉取，避免逐行查询）。 */
    private Map<Long, String> categoryNames() {
        Map<Long, String> names = new HashMap<>();
        try {
            for (CategoryView c : categoryService.list()) {
                if (c.getId() != null) {
                    names.put(c.getId(), c.getCategoryName());
                }
            }
        } catch (RuntimeException e) {
            log.warn("CMS 文章导出：栏目名称映射构建失败，栏目列回退为 id", e);
        }
        return names;
    }

    /** 翻页拉取文章（首页定总量，超上限截断告警）。 */
    private List<ArticleView> fetchAll(Map<String, String> p) {
        Long categoryId = parseLong(p.get("categoryId"));
        String title = blankToNull(p.get("title"));
        String status = blankToNull(p.get("status"));
        PageResult<ArticleView> first = articleService.page(1, PAGE_SIZE, categoryId, title, status, null, null);
        if (first.total() > MAX_ROWS) {
            log.warn("CMS 文章导出：匹配 {} 行超出单次上限 {} 行，已截断", first.total(), MAX_ROWS);
        }
        List<ArticleView> all = new ArrayList<>(first.records());
        long expected = Math.min(first.total(), MAX_ROWS);
        for (int page = 2; all.size() < expected; page++) {
            PageResult<ArticleView> result =
                    articleService.page(page, PAGE_SIZE, categoryId, title, status, null, null);
            if (result.records().isEmpty()) {
                break; // 防御：并发删除可致空洞，避免死循环
            }
            all.addAll(result.records());
        }
        return all.size() > MAX_ROWS ? new ArrayList<>(all.subList(0, MAX_ROWS)) : all;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
