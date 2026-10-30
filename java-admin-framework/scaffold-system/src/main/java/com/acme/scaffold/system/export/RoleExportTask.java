package com.acme.scaffold.system.export;

import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.system.dto.RoleView;
import com.acme.scaffold.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 角色列表导出（导出中心内置模板，M26 组织数据导出四件套之一）。
 *
 * <p>与 {@link LoginLogExportTask} 对称：实现 {@link ExportTask} 契约即被导出中心自动发现。
 * 角色数量通常有限（百级），{@link RoleService#list()} 一次全量返回（按 sort_no 升序），
 * 无需翻页；仅保留 {@value #MAX_ROWS} 截断护栏防止异常数据量。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleExportTask implements ExportTask {

    /** 单次导出取数上限（异常数据量护栏）。 */
    private static final int MAX_ROWS = 10_000;

    private final RoleService roleService;

    @Override
    public String code() {
        return "roles";
    }

    @Override
    public String displayName() {
        return "角色列表";
    }

    @Override
    public String fileName() {
        return "role";
    }

    @Override
    public List<String> headers() {
        return List.of("角色ID", "角色编码", "角色名称", "角色类型", "排序", "状态");
    }

    @Override
    public List<List<String>> rows(Map<String, String> params) {
        List<RoleView> roles = roleService.list();
        if (roles.size() > MAX_ROWS) {
            log.warn("角色导出：共 {} 行超出单次上限 {} 行，已截断", roles.size(), MAX_ROWS);
            roles = new ArrayList<>(roles.subList(0, MAX_ROWS));
        }
        return roles.stream().map(v -> List.of(
                str(v.id()),
                str(v.roleCode()),
                str(v.roleName()),
                str(v.roleType()),
                str(v.sortNo()),
                str(v.status())
        )).toList();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
