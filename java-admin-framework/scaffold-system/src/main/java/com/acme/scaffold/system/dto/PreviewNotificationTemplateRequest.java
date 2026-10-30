package com.acme.scaffold.system.dto;

import java.util.Map;

/**
 * 通知模板渲染预览请求：传入变量名 → 取值映射，渲染正文。
 *
 * <p>变量映射可为空（返回原样含 {@code ${var}} 的内容）；缺失变量在响应中单独列出，
 * 便于前端提示「还有哪些变量未填」。</p>
 *
 * @param variables 变量名 → 取值，如 {@code {"orderNo": "NO2026", "amount": "￥99.00"}}；可为 null/空
 */
public record PreviewNotificationTemplateRequest(Map<String, String> variables) {
}
