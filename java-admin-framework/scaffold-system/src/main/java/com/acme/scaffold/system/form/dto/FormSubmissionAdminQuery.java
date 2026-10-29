package com.acme.scaffold.system.form.dto;

/**
 * 表单提交管理后台查询入参（M47 跨表单统一提交管理）。
 *
 * <p>承接渲染面的「按单表单查询」升级为「跨表单统一检索」：可按表单编码精确过滤、
 * 提交人模糊匹配、提交值 JSON 关键词检索，并支持排序字段/方向白名单。
 * 空串在本工厂归一为 {@code null}，控制器无需自行判空。</p>
 *
 * @param formCode    表单编码（精确匹配，可空）
 * @param submittedBy 提交人（模糊匹配，可空）
 * @param keyword     提交值 JSON 关键词（模糊匹配，可空）
 * @param sortField   排序字段（白名单：created_at / submitted_by / form_code，缺省 created_at）
 * @param sortDir     排序方向（ASC / DESC，缺省 DESC）
 * @param page        页码（从 1 起，由调用方/服务侧钳制）
 * @param size        每页条数（由调用方/服务侧钳制）
 */
public record FormSubmissionAdminQuery(
        String formCode,
        String submittedBy,
        String keyword,
        String sortField,
        String sortDir,
        int page,
        int size) {

    public static FormSubmissionAdminQuery of(String formCode, String submittedBy, String keyword,
                                              String sortField, String sortDir, int page, int size) {
        return new FormSubmissionAdminQuery(blank(formCode), blank(submittedBy), blank(keyword),
                blank(sortField), blank(sortDir), page, size);
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
