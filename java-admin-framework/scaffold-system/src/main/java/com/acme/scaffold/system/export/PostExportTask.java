package com.acme.scaffold.system.export;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.system.dto.PostQuery;
import com.acme.scaffold.system.dto.PostView;
import com.acme.scaffold.system.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 岗位列表导出（导出中心内置模板，M26 组织数据导出四件套之一）。
 *
 * <p>与 {@link LoginLogExportTask} 对称：实现 {@link ExportTask} 契约即被导出中心自动发现。
 * 筛选参数（keyword/status）由导出中心透传，缺省导出全部（与岗位管理列表页默认口径一致）。
 * 翻页取数策略与 {@link UserExportTask} 相同：单页 200 行循环，超 {@value #MAX_ROWS} 截断告警。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostExportTask implements ExportTask {

    /** 单次导出取数上限（超出截断并告警，避免大导出拖垮请求线程）。 */
    private static final int MAX_ROWS = 10_000;

    /** 翻页大小：兼顾查询次数与单页内存。 */
    private static final int PAGE_SIZE = 200;

    private final PostService postService;

    @Override
    public String code() {
        return "posts";
    }

    @Override
    public String displayName() {
        return "岗位列表";
    }

    @Override
    public String fileName() {
        return "post";
    }

    @Override
    public List<String> headers() {
        return List.of("岗位ID", "岗位编码", "岗位名称", "排序", "状态", "备注");
    }

    @Override
    public List<List<String>> rows(Map<String, String> params) {
        List<PostView> all = fetchAll(params);
        return all.stream().map(v -> List.of(
                str(v.id()),
                str(v.postCode()),
                str(v.postName()),
                str(v.postSort()),
                str(v.status()),
                str(v.remark())
        )).toList();
    }

    /** 翻页拉取（首页定总量，超上限截断告警）。 */
    private List<PostView> fetchAll(Map<String, String> params) {
        PageResult<PostView> first = postService.list(query(params, 1));
        if (first.total() > MAX_ROWS) {
            log.warn("岗位导出：匹配 {} 行超出单次上限 {} 行，已截断", first.total(), MAX_ROWS);
        }
        List<PostView> all = new ArrayList<>(first.records());
        long expected = Math.min(first.total(), MAX_ROWS);
        for (int page = 2; all.size() < expected; page++) {
            PageResult<PostView> result = postService.list(query(params, page));
            if (result.records().isEmpty()) {
                break; // 防御：理论上不会发生（并发删除可致空洞），避免死循环
            }
            all.addAll(result.records());
        }
        return all.size() > MAX_ROWS ? new ArrayList<>(all.subList(0, MAX_ROWS)) : all;
    }

    /** 构造查询：筛选参数透传（服务端已做 trim 与空值忽略）。 */
    private static PostQuery query(Map<String, String> params, int page) {
        return new PostQuery(page, PAGE_SIZE, params.get("keyword"), params.get("status"), null, null);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
