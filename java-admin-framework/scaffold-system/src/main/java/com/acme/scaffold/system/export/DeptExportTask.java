package com.acme.scaffold.system.export;

import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.common.tree.TreeBuilder;
import com.acme.scaffold.system.dto.OrgTreeVO;
import com.acme.scaffold.system.service.OrgService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门列表导出（导出中心内置模板，M26 组织数据导出四件套之一）。
 *
 * <p>与 {@link LoginLogExportTask} 对称：实现 {@link ExportTask} 契约即被导出中心自动发现。
 * 部门为树形结构，导出为<b>深度优先扁平表</b>（父行先于子行，与前端树展开顺序一致），
 * 「上级部门」列按 id→名称映射回显，顶级部门显「（顶级）」。</p>
 *
 * <p><b>数据权限</b>：复用 {@link OrgService#tree()}，其 {@code @DataScope} 切面在导出请求线程内
 * 同样生效——导出范围与调用者在部门树可见范围一致（该树按权限签名分键缓存，导出不写缓存）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeptExportTask implements ExportTask {

    /** 单次导出取数上限（部门总量通常远小于此，属异常数据量护栏）。 */
    private static final int MAX_ROWS = 10_000;

    /** 顶级部门在「上级部门」列的占位（与前端部门树顶级展示口径一致）。 */
    private static final String TOP_LEVEL_LABEL = "（顶级）";

    private final OrgService orgService;

    @Override
    public String code() {
        return "depts";
    }

    @Override
    public String displayName() {
        return "部门列表";
    }

    @Override
    public String fileName() {
        return "dept";
    }

    @Override
    public List<String> headers() {
        return List.of("部门ID", "部门编码", "部门名称", "上级部门", "负责人", "排序", "状态");
    }

    @Override
    public List<List<String>> rows(Map<String, String> params) {
        List<OrgTreeVO> flat = TreeBuilder.flatten(orgService.tree(), OrgTreeVO::children);
        if (flat.size() > MAX_ROWS) {
            log.warn("部门导出：共 {} 行超出单次上限 {} 行，已截断", flat.size(), MAX_ROWS);
            flat = new ArrayList<>(flat.subList(0, MAX_ROWS));
        }
        // id → 名称映射（回显上级部门；数据权限过滤掉父节点时该行升为顶级，映射缺失按顶级处理）
        Map<Long, String> nameById = new HashMap<>();
        for (OrgTreeVO node : flat) {
            nameById.put(node.id(), node.orgName());
        }
        return flat.stream().map(node -> List.of(
                str(node.id()),
                str(node.orgCode()),
                str(node.orgName()),
                parentName(node, nameById),
                str(node.leaderUserName()),
                str(node.sortNo()),
                str(node.status())
        )).toList();
    }

    /** 上级部门名：无父（parentId 为 null 或 0）或父节点不在可见集合内 → 顶级占位。 */
    private static String parentName(OrgTreeVO node, Map<Long, String> nameById) {
        Long parentId = node.parentId();
        if (parentId == null || parentId == 0L) {
            return TOP_LEVEL_LABEL;
        }
        String name = nameById.get(parentId);
        return name == null ? TOP_LEVEL_LABEL : name;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
