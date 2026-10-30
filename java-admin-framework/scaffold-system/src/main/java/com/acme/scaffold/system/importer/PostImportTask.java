package com.acme.scaffold.system.importer;

import com.acme.scaffold.common.importer.ImportRow;
import com.acme.scaffold.common.importer.ImportTask;
import com.acme.scaffold.common.importer.ImportValidators;
import com.acme.scaffold.system.dto.CreatePostRequest;
import com.acme.scaffold.system.rules.PostRules;
import com.acme.scaffold.system.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 岗位导入模板（M26 组织数据导入两件套之一）。
 *
 * <p>与 {@link DictDataImportTask} 对称：声明一个 {@link ImportTask} Bean 即可被导入中心自动发现，
 * 无需新增上传接口。落库复用既有 {@link PostService#create(CreatePostRequest)}，自动继承其：</p>
 * <ul>
 *   <li>事务边界（每行独立事务，单行失败不影响其余行）；</li>
 *   <li>业务规则（编码租户内唯一、仅允许字母/数字/下划线/连字符，冲突时抛错被计为该行失败）；</li>
 *   <li>默认值语义（状态固定 ACTIVE、排序缺省 0）。</li>
 * </ul>
 *
 * <p><b>列设计</b>：仅 4 列（编码/名称/排序/备注），<b>不含状态列</b>——{@code CreatePostRequest}
 * 无 status 字段且 create 固定写入 ACTIVE，模板放一列永不生效的状态只会误导用户
 * （导入后一律启用，需要停用请到岗位管理页操作）。</p>
 *
 * <p>列宽上限与 {@code sys_post} 表结构一致：编码 64 / 名称 100 / 备注 512。</p>
 */
@Component
@RequiredArgsConstructor
public class PostImportTask implements ImportTask {

    /** 模板编码（接口路由用）。 */
    public static final String CODE = "posts";

    private static final String COL_CODE = "岗位编码";
    private static final String COL_NAME = "岗位名称";
    private static final String COL_SORT = "排序";
    private static final String COL_REMARK = "备注";

    private static final long SORT_MIN = 0L;
    private static final long SORT_MAX = 999_999L;

    private final PostService postService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String displayName() {
        return "岗位";
    }

    @Override
    public List<String> headers() {
        return List.of(COL_CODE, COL_NAME, COL_SORT, COL_REMARK);
    }

    /**
     * 纯校验：必填 + 长度 + 编码形态。一次收集全部字段错误，便于用户一轮改完。
     *
     * <p>编码形态提前校验（而非只靠 {@link PostService#create}）：形态错误是确定性规则，
     * 预检阶段即可暴露；「编码已存在」属库层冲突，仍按既有约定留给正式导入时返回。</p>
     */
    @Override
    public List<String> validate(ImportRow row) {
        return ImportValidators.collect(
                ImportValidators.required(row.trimmed(COL_CODE), COL_CODE),
                ImportValidators.maxLength(row.value(COL_CODE), COL_CODE, 64),
                codeFormat(row.trimmed(COL_CODE)),
                ImportValidators.required(row.trimmed(COL_NAME), COL_NAME),
                ImportValidators.maxLength(row.value(COL_NAME), COL_NAME, 100),
                ImportValidators.integerRange(row.trimmed(COL_SORT), COL_SORT, SORT_MIN, SORT_MAX, true),
                ImportValidators.maxLength(row.value(COL_REMARK), COL_REMARK, 512));
    }

    @Override
    public void importRow(ImportRow row) {
        postService.create(new CreatePostRequest(
                row.trimmed(COL_CODE),
                row.trimmed(COL_NAME),
                parseSort(row.trimmed(COL_SORT)),
                blankToNull(row.trimmed(COL_REMARK))));
    }

    /** 编码形态校验（复用 PostRules 纯函数；空值由 required 负责，此处不重复报错）。 */
    private static String codeFormat(String code) {
        if (code.isEmpty()) {
            return null;
        }
        return PostRules.isValidCode(code) ? null : "「" + COL_CODE + "」仅允许字母、数字、下划线、连字符";
    }

    /** 排序为空时交由服务层取默认值 0。 */
    private static Integer parseSort(String raw) {
        return raw.isBlank() ? null : Integer.valueOf(raw);
    }

    private static String blankToNull(String raw) {
        return raw.isBlank() ? null : raw;
    }
}
