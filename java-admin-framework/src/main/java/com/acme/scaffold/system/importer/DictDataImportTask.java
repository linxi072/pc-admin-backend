package com.acme.scaffold.system.importer;

import com.acme.scaffold.common.importer.ImportRow;
import com.acme.scaffold.common.importer.ImportTask;
import com.acme.scaffold.common.importer.ImportValidators;
import com.acme.scaffold.system.dto.CreateDictDataRequest;
import com.acme.scaffold.system.service.DictDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 字典数据导入模板（试点实现）。
 *
 * <p>与 {@code system.export.FileListExportTask} 对称：声明一个 {@link ImportTask} Bean 即可被
 * 导入中心自动发现，无需新增上传接口。</p>
 *
 * <p>落库复用既有 {@link DictDataService#create(CreateDictDataRequest)}，因此自动继承其：
 * <ul>
 *   <li>事务边界（每行独立事务，单行失败不影响其余行）；</li>
 *   <li>业务规则（同一字典类型下字典值唯一，重复时抛冲突，被导入中心捕获为该行错误）；</li>
 *   <li>缓存驱逐（写入后 {@code dict-data} 缓存整驱，导入后前端即刻可见新字典项）。</li>
 * </ul>
 *
 * <p>列宽上限与 {@code sys_dict_data} 表结构一致：类型码 64 / 标签 100 / 值 100 / 备注 255。</p>
 */
@Component
@RequiredArgsConstructor
public class DictDataImportTask implements ImportTask {

    /** 模板编码（接口路由用）。 */
    public static final String CODE = "dict-data";

    private static final String COL_TYPE = "字典类型";
    private static final String COL_LABEL = "字典标签";
    private static final String COL_VALUE = "字典值";
    private static final String COL_SORT = "排序";
    private static final String COL_STATUS = "状态";
    private static final String COL_REMARK = "备注";

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final long SORT_MIN = 0L;
    private static final long SORT_MAX = 999_999L;

    private final DictDataService dictDataService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String displayName() {
        return "字典数据";
    }

    @Override
    public List<String> headers() {
        return List.of(COL_TYPE, COL_LABEL, COL_VALUE, COL_SORT, COL_STATUS, COL_REMARK);
    }

    /**
     * 纯校验：必填 + 长度 + 取值。一次收集全部字段错误，便于用户一轮改完。
     *
     * <p>不含「同类型下字典值重复」判断——该冲突由 {@link DictDataService#create} 在正式导入时返回，
     * 预检模式下无法感知（需查库，且与「纯函数校验」定位冲突）。</p>
     */
    @Override
    public List<String> validate(ImportRow row) {
        return ImportValidators.collect(
                ImportValidators.required(row.trimmed(COL_TYPE), COL_TYPE),
                ImportValidators.maxLength(row.value(COL_TYPE), COL_TYPE, 64),
                ImportValidators.required(row.trimmed(COL_LABEL), COL_LABEL),
                ImportValidators.maxLength(row.value(COL_LABEL), COL_LABEL, 100),
                ImportValidators.required(row.trimmed(COL_VALUE), COL_VALUE),
                ImportValidators.maxLength(row.value(COL_VALUE), COL_VALUE, 100),
                ImportValidators.integerRange(row.trimmed(COL_SORT), COL_SORT, SORT_MIN, SORT_MAX, true),
                ImportValidators.oneOf(row.trimmed(COL_STATUS), COL_STATUS, true, STATUS_ACTIVE, STATUS_INACTIVE),
                ImportValidators.maxLength(row.value(COL_REMARK), COL_REMARK, 255));
    }

    @Override
    public void importRow(ImportRow row) {
        dictDataService.create(new CreateDictDataRequest(
                row.trimmed(COL_TYPE),
                row.trimmed(COL_LABEL),
                row.trimmed(COL_VALUE),
                parseSort(row.trimmed(COL_SORT)),
                blankToNull(row.trimmed(COL_STATUS)),
                blankToNull(row.trimmed(COL_REMARK))));
    }

    /** 排序为空时交由服务层取默认值 0。 */
    private static Integer parseSort(String raw) {
        return raw.isBlank() ? null : Integer.valueOf(raw);
    }

    private static String blankToNull(String raw) {
        return raw.isBlank() ? null : raw;
    }
}
