package com.acme.scaffold.system.importer;

import com.acme.scaffold.common.importer.ImportRow;
import com.acme.scaffold.common.importer.ImportValidators;
import com.acme.scaffold.common.importer.MultiSegmentImportTask;
import com.acme.scaffold.common.importer.SegmentSpec;
import com.acme.scaffold.system.dto.CreateDictDataRequest;
import com.acme.scaffold.system.dto.CreateDictTypeRequest;
import com.acme.scaffold.system.service.DictDataService;
import com.acme.scaffold.system.service.DictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 字典完整包导入模板（多段结构试点实现）：一个 XLSX 工作簿含两张工作表——
 * 「字典类型」与「字典数据」，分别对应 {@link DictTypeService} 与 {@link DictDataService}。
 *
 * <p>与导出侧多 Sheet（{@code XlsxDataExporter.exportWorkbook} + {@code XlsxSheet}）形成对称闭环：
 * 同一份字典包可先导出为多 Sheet 报表，再原样回导。多段导入仅接受 XLSX（多工作表是 XLSX 原生能力）。</p>
 *
 * <p>落库复用既有服务，因此自动继承其：事务边界（逐行独立事务）、业务规则（类型编码 / 数据值唯一性冲突
 * 由服务返回并计入该行失败明细）、缓存驱逐（写入后字典缓存整驱）。校验规则与单段
 * {@code DictDataImportTask} / 类型录入保持一致。</p>
 */
@Component
@RequiredArgsConstructor
public class DictFullImportTask implements MultiSegmentImportTask {

    /** 模板编码（接口路由用）。 */
    public static final String CODE = "dict-full";

    /** 段名：字典类型工作表。 */
    public static final String SEGMENT_TYPE = "字典类型";

    /** 段名：字典数据工作表。 */
    public static final String SEGMENT_DATA = "字典数据";

    private static final String T_CODE = "字典编码";
    private static final String T_NAME = "字典名称";
    private static final String T_STATUS = "状态";
    private static final String T_SORT = "排序";
    private static final String T_REMARK = "备注";

    private static final String D_TYPE = "字典类型";
    private static final String D_LABEL = "字典标签";
    private static final String D_VALUE = "字典值";
    private static final String D_SORT = "排序";
    private static final String D_STATUS = "状态";
    private static final String D_REMARK = "备注";

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final long SORT_MIN = 0L;
    private static final long SORT_MAX = 999_999L;

    private final DictTypeService dictTypeService;
    private final DictDataService dictDataService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String displayName() {
        return "字典完整包（类型+数据）";
    }

    @Override
    public List<SegmentSpec> segments() {
        return List.of(typeSegment(), dataSegment());
    }

    /** 段一：字典类型（映射 {@link DictTypeService#create}）。 */
    private SegmentSpec typeSegment() {
        return new SegmentSpec() {
            @Override
            public String name() {
                return SEGMENT_TYPE;
            }

            @Override
            public List<String> headers() {
                return List.of(T_CODE, T_NAME, T_STATUS, T_SORT, T_REMARK);
            }

            @Override
            public List<String> validate(ImportRow row) {
                return ImportValidators.collect(
                        ImportValidators.required(row.trimmed(T_CODE), T_CODE),
                        ImportValidators.maxLength(row.value(T_CODE), T_CODE, 64),
                        ImportValidators.required(row.trimmed(T_NAME), T_NAME),
                        ImportValidators.maxLength(row.value(T_NAME), T_NAME, 100),
                        ImportValidators.oneOf(row.trimmed(T_STATUS), T_STATUS, true, STATUS_ACTIVE, STATUS_INACTIVE),
                        ImportValidators.integerRange(row.trimmed(T_SORT), T_SORT, SORT_MIN, SORT_MAX, true),
                        ImportValidators.maxLength(row.value(T_REMARK), T_REMARK, 255));
            }

            @Override
            public void importRow(ImportRow row) {
                dictTypeService.create(new CreateDictTypeRequest(
                        row.trimmed(T_CODE),
                        row.trimmed(T_NAME),
                        blankToNull(row.trimmed(T_STATUS)),
                        parseSort(row.trimmed(T_SORT)),
                        blankToNull(row.trimmed(T_REMARK))));
            }
        };
    }

    /** 段二：字典数据（映射 {@link DictDataService#create}，校验规则与单段 {@code DictDataImportTask} 一致）。 */
    private SegmentSpec dataSegment() {
        return new SegmentSpec() {
            @Override
            public String name() {
                return SEGMENT_DATA;
            }

            @Override
            public List<String> headers() {
                return List.of(D_TYPE, D_LABEL, D_VALUE, D_SORT, D_STATUS, D_REMARK);
            }

            @Override
            public List<String> validate(ImportRow row) {
                return ImportValidators.collect(
                        ImportValidators.required(row.trimmed(D_TYPE), D_TYPE),
                        ImportValidators.maxLength(row.value(D_TYPE), D_TYPE, 64),
                        ImportValidators.required(row.trimmed(D_LABEL), D_LABEL),
                        ImportValidators.maxLength(row.value(D_LABEL), D_LABEL, 100),
                        ImportValidators.required(row.trimmed(D_VALUE), D_VALUE),
                        ImportValidators.maxLength(row.value(D_VALUE), D_VALUE, 100),
                        ImportValidators.integerRange(row.trimmed(D_SORT), D_SORT, SORT_MIN, SORT_MAX, true),
                        ImportValidators.oneOf(row.trimmed(D_STATUS), D_STATUS, true, STATUS_ACTIVE, STATUS_INACTIVE),
                        ImportValidators.maxLength(row.value(D_REMARK), D_REMARK, 255));
            }

            @Override
            public void importRow(ImportRow row) {
                dictDataService.create(new CreateDictDataRequest(
                        row.trimmed(D_TYPE),
                        row.trimmed(D_LABEL),
                        row.trimmed(D_VALUE),
                        parseSort(row.trimmed(D_SORT)),
                        blankToNull(row.trimmed(D_STATUS)),
                        blankToNull(row.trimmed(D_REMARK))));
            }
        };
    }

    /** 排序为空时交由服务层取默认值 0。 */
    private static Integer parseSort(String raw) {
        return raw.isBlank() ? null : Integer.valueOf(raw);
    }

    private static String blankToNull(String raw) {
        return raw.isBlank() ? null : raw;
    }
}
