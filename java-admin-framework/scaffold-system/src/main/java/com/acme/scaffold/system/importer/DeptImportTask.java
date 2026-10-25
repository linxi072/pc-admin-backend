package com.acme.scaffold.system.importer;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.importer.ImportRow;
import com.acme.scaffold.common.importer.ImportTask;
import com.acme.scaffold.common.importer.ImportValidators;
import com.acme.scaffold.common.tree.TreeBuilder;
import com.acme.scaffold.system.dto.CreateOrgRequest;
import com.acme.scaffold.system.dto.OrgTreeVO;
import com.acme.scaffold.system.service.OrgService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门导入模板（M26 组织数据导入两件套之一）。
 *
 * <p>与 {@link DictDataImportTask} 对称：声明一个 {@link ImportTask} Bean 即可被导入中心自动发现。
 * 落库复用既有 {@link OrgService#create(CreateOrgRequest)}，自动继承其：</p>
 * <ul>
 *   <li>事务边界（每行独立事务，单行失败不影响其余行）；</li>
 *   <li>业务规则（编码租户内唯一，冲突时抛错被计为该行失败；机构类型缺省 DEPARTMENT、状态缺省 ACTIVE）；</li>
 *   <li>缓存驱逐（写入后 org-tree 缓存整驱，导入后部门树即刻刷新）；</li>
 *   <li>ancestors 物化路径计算（由服务层按 parentId 完成，导入方只需给出上级编码）。</li>
 * </ul>
 *
 * <p><b>父级解析</b>：上级部门按<b>编码</b>（而非 ID）填写——编码是用户可见的稳定标识，
 * 且导出模板（{@code DeptExportTask}）即含编码列，可「先导出、改完再导入」闭环。
 * 每行导入时经 {@link OrgService#tree()}（分键缓存，热点读取）内存反查编码→ID；
 * 顶级部门的上级编码留空。编码不存在时抛业务异常计入该行失败（不影响其余行）。</p>
 *
 * <p><b>顺序依赖</b>：子行若先于父行出现，父级尚不存在会导入失败——请按「上级在前」排序，
 * 或分批导入（这也是扁平淡结构的固有代价，错误明细会明示缺失的上级编码）。</p>
 *
 * <p>列宽上限与 {@code sys_org} 表结构一致：编码 64 / 名称 128。</p>
 */
@Component
@RequiredArgsConstructor
public class DeptImportTask implements ImportTask {

    /** 模板编码（接口路由用）。 */
    public static final String CODE = "depts";

    private static final String COL_PARENT_CODE = "上级部门编码";
    private static final String COL_CODE = "部门编码";
    private static final String COL_NAME = "部门名称";
    private static final String COL_SORT = "排序";

    private static final long SORT_MIN = 0L;
    private static final long SORT_MAX = 999_999L;

    private final OrgService orgService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String displayName() {
        return "部门";
    }

    @Override
    public List<String> headers() {
        return List.of(COL_PARENT_CODE, COL_CODE, COL_NAME, COL_SORT);
    }

    /**
     * 纯校验：必填 + 长度 + 排序范围。一次收集全部字段错误，便于用户一轮改完。
     *
     * <p>上级部门编码留空合法（顶级部门）；「上级是否存在」属库层解析，按既有约定
     * 留给正式导入时返回（预检为纯函数，不查库）。</p>
     */
    @Override
    public List<String> validate(ImportRow row) {
        return ImportValidators.collect(
                ImportValidators.maxLength(row.value(COL_PARENT_CODE), COL_PARENT_CODE, 64),
                ImportValidators.required(row.trimmed(COL_CODE), COL_CODE),
                ImportValidators.maxLength(row.value(COL_CODE), COL_CODE, 64),
                ImportValidators.required(row.trimmed(COL_NAME), COL_NAME),
                ImportValidators.maxLength(row.value(COL_NAME), COL_NAME, 128),
                ImportValidators.integerRange(row.trimmed(COL_SORT), COL_SORT, SORT_MIN, SORT_MAX, true));
    }

    @Override
    public void importRow(ImportRow row) {
        orgService.create(new CreateOrgRequest(
                resolveParentId(row.trimmed(COL_PARENT_CODE)),
                row.trimmed(COL_CODE),
                row.trimmed(COL_NAME),
                null,
                parseSort(row.trimmed(COL_SORT)),
                null,
                null));
    }

    /** 上级编码 → parentId：留空为顶级（0L）；不存在时抛业务异常（计入该行失败）。 */
    private Long resolveParentId(String parentCode) {
        if (parentCode.isEmpty()) {
            return 0L;
        }
        Long parentId = idByCode().get(parentCode);
        if (parentId == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "上级部门编码不存在：" + parentCode);
        }
        return parentId;
    }

    /** 全树（数据权限可见范围）编码 → ID 映射；树为分键缓存热点读，逐行调用命中缓存。 */
    private Map<String, Long> idByCode() {
        List<OrgTreeVO> flat = TreeBuilder.flatten(orgService.tree(), OrgTreeVO::children);
        Map<String, Long> map = new HashMap<>();
        for (OrgTreeVO node : flat) {
            map.put(node.orgCode(), node.id());
        }
        return map;
    }

    /** 排序为空时交由服务层取默认值 0。 */
    private static Integer parseSort(String raw) {
        return raw.isBlank() ? null : Integer.valueOf(raw);
    }
}
