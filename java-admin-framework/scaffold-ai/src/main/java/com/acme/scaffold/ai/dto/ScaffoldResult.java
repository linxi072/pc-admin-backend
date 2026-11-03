package com.acme.scaffold.ai.dto;

import com.acme.scaffold.codegen.dto.GeneratedFileVO;

import java.util.List;

/**
 * AI 脚手架生成结果：表规格 + 迁移 SQL + 代码草稿 + 是否需要人工复核。
 *
 * @param tableSpec       解析出的表规格
 * @param ddl             迁移 SQL 草稿（引号小写 snake_case 标识符 + 独立索引）
 * @param files           代码草稿文件列表（DO/VO/Query/Controller/Service/ServiceImpl/Vue/api.js）
 * @param needsReview     是否需要人工复核（AI 生成物一律为 true）
 * @param estimatedTokens 提示词估算 token 数（成本门禁留痕）
 * @param provider        实际使用的提供者标识（heuristic / cloud）
 */
public record ScaffoldResult(
        TableSpec tableSpec,
        String ddl,
        List<GeneratedFileVO> files,
        boolean needsReview,
        int estimatedTokens,
        String provider) {
}
