package com.acme.scaffold.system.form.dto;

/**
 * 单表单提交计数（M47 跨表单统计）。
 *
 * @param formCode  表单编码
 * @param formName  表单名称（缺失时回退为编码）
 * @param count     该表单下的提交总数
 */
public record FormSubmissionFormCount(String formCode, String formName, long count) {
}
