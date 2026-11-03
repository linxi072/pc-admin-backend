package com.acme.scaffold.ai.dto;

/**
 * AI 脚手架生成请求：自然语言提示词 + 可选模块名。
 *
 * @param prompt      自然语言描述（如「订单表含金额/状态」）
 * @param moduleName  模块名（可选，仅小写字母/数字/连字符；为空时由表名推导）
 */
public record ScaffoldRequest(String prompt, String moduleName) {
}
