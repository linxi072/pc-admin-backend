package com.acme.scaffold.system.export;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.system.dto.UserQuery;
import com.acme.scaffold.system.dto.UserView;
import com.acme.scaffold.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户列表导出（导出中心内置模板，M26 组织数据导出四件套之一）。
 *
 * <p>与 {@link LoginLogExportTask} 对称：实现 {@link ExportTask} 契约即被导出中心自动发现，
 * <b>无需新增接口、迁移或前端页面</b>。筛选参数（username/status/orgId/roleId）由导出中心透传，
 * 缺省导出全部（与用户管理列表页默认口径一致）；角色 / 部门 / 岗位为多值，以「、」拼接为单格。</p>
 *
 * <p><b>数据权限</b>：复用 {@link UserService#list(UserQuery)}，其上的 {@code @DataScope} 切面
 * 在导出请求线程内同样生效——导出结果与调用者在列表页可见范围严格一致，不会因导出通道越权。</p>
 *
 * <p><b>翻页取数</b>：单页 200 行循环拉取，总行数超 {@value #MAX_ROWS} 时截断并告警
 * （与 {@link #PAGE_SIZE} 一起约束内存与导出耗时）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserExportTask implements ExportTask {

    /** 单次导出取数上限（超出截断并告警，避免大导出拖垮请求线程）。 */
    private static final int MAX_ROWS = 10_000;

    /** 翻页大小：兼顾查询次数与单页内存。 */
    private static final int PAGE_SIZE = 200;

    private final UserService userService;

    @Override
    public String code() {
        return "users";
    }

    @Override
    public String displayName() {
        return "用户列表";
    }

    @Override
    public String fileName() {
        return "user";
    }

    @Override
    public List<String> headers() {
        return List.of("用户ID", "用户名", "姓名", "手机号", "邮箱", "状态", "角色", "部门", "岗位", "创建时间");
    }

    @Override
    public List<List<String>> rows(Map<String, String> params) {
        List<UserView> all = fetchAll(params);
        return all.stream().map(v -> List.of(
                str(v.id()),
                str(v.username()),
                str(v.displayName()),
                str(v.mobile()),
                str(v.email()),
                str(v.status()),
                join(v.roleNames()),
                join(v.deptNames()),
                join(v.postNames()),
                str(v.createdAt())
        )).toList();
    }

    /** 翻页拉取（首页定总量，超上限截断告警）。 */
    private List<UserView> fetchAll(Map<String, String> params) {
        PageResult<UserView> first = userService.list(query(params, 1));
        if (first.total() > MAX_ROWS) {
            log.warn("用户导出：匹配 {} 行超出单次上限 {} 行，已截断", first.total(), MAX_ROWS);
        }
        List<UserView> all = new ArrayList<>(first.records());
        long expected = Math.min(first.total(), MAX_ROWS);
        for (int page = 2; all.size() < expected; page++) {
            PageResult<UserView> result = userService.list(query(params, page));
            if (result.records().isEmpty()) {
                break; // 防御：理论上不会发生（并发删除可致空洞），避免死循环
            }
            all.addAll(result.records());
        }
        return all.size() > MAX_ROWS ? new ArrayList<>(all.subList(0, MAX_ROWS)) : all;
    }

    /** 构造查询：筛选参数透传，orgId / roleId 非法值忽略（视作不过滤）。 */
    private static UserQuery query(Map<String, String> params, int page) {
        return new UserQuery(page, PAGE_SIZE, params.get("username"), params.get("status"),
                parseLong(params.get("orgId")), parseLong(params.get("roleId")), null, null);
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

    /** 多值集合拼接通栏（角色 / 部门 / 岗位），空集合输出空串。 */
    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join("、", values);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
