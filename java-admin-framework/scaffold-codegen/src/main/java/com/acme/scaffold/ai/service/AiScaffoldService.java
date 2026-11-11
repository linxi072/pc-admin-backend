package com.acme.scaffold.ai.service;

import com.acme.scaffold.ai.AiScaffoldException;
import com.acme.scaffold.ai.config.AiProperties;
import com.acme.scaffold.ai.dto.ScaffoldResult;
import com.acme.scaffold.ai.dto.TableSpec;
import com.acme.scaffold.ai.port.CloudLlmProvider;
import com.acme.scaffold.ai.port.HeuristicLlmProvider;
import com.acme.scaffold.ai.port.LlmProvider;
import com.acme.scaffold.codegen.config.CodeGenProperties;
import com.acme.scaffold.codegen.dto.ColumnMetaVO;
import com.acme.scaffold.codegen.dto.GenOption;
import com.acme.scaffold.codegen.dto.GeneratedFileVO;
import com.acme.scaffold.codegen.dto.TableMetaVO;
import com.acme.scaffold.codegen.service.CodeTemplates;
import com.acme.scaffold.codegen.service.JavaTypeMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;

import org.springframework.stereotype.Service;

/**
 * AI 脚手架编排服务：提示词成本门禁 → 表规格解析 → 迁移 SQL + 代码草稿，并标记需人工复核。
 *
 * <p>编排步骤：
 * <ol>
 *   <li>参数校验与成本门禁（空提示词 / 超长提示词直接拒生成）；</li>
 *   <li>选择大模型提供者（云可用则优选，否则离线启发式）；</li>
 *   <li>表规格 → 迁移 SQL（{@link DdlRenderer}）；</li>
 *   <li>表规格 → 代码草稿（复用 codegen 的 {@link CodeTemplates#renderAll} 渲染实体/服务/Vue）。</li>
 * </ol>
 * 生成的 SQL 与代码均为草稿，结果 {@code needsReview=true}，须人工复核后落地。
 */
@Service
public class AiScaffoldService {

    private final HeuristicLlmProvider heuristic;
    private final ObjectProvider<CloudLlmProvider> cloudProvider;
    private final AiProperties props;

    public AiScaffoldService(HeuristicLlmProvider heuristic,
                             ObjectProvider<CloudLlmProvider> cloudProvider,
                             AiProperties props) {
        this.heuristic = heuristic;
        this.cloudProvider = cloudProvider;
        this.props = props;
    }

    /**
     * 根据自然语言提示词生成脚手架草稿。
     *
     * @param prompt      自然语言描述
     * @param moduleName  模块名（可选；为空时由表名推导）
     * @return 含表规格 / 迁移 SQL / 代码草稿的生成结果（needsReview=true）
     * @throws AiScaffoldException 提示词为空或超过成本门禁
     */
    public ScaffoldResult generate(String prompt, String moduleName) {
        if (prompt == null || prompt.isBlank()) {
            throw new AiScaffoldException("提示词不能为空");
        }
        int estimatedTokens = estimateTokens(prompt);
        if (estimatedTokens > props.getMaxPromptTokens()) {
            throw new AiScaffoldException(
                    "提示词估算 token 数 " + estimatedTokens + " 超过成本门禁 " + props.getMaxPromptTokens());
        }

        LlmProvider provider = cloudProvider.getIfAvailable();
        if (provider == null) {
            provider = heuristic;
        }

        TableSpec spec = provider.parse(prompt);
        String ddl = DdlRenderer.render(spec);
        List<GeneratedFileVO> files = renderCode(spec, moduleName);

        return new ScaffoldResult(spec, ddl, files, true, estimatedTokens, provider.name());
    }

    private List<GeneratedFileVO> renderCode(TableSpec spec, String moduleName) {
        String module = (moduleName == null || moduleName.isBlank())
                ? deriveModule(spec.tableName()) : moduleName.trim();
        List<ColumnMetaVO> cols = spec.columns().stream().map(c -> new ColumnMetaVO(
                c.columnName(), c.dataType(), c.javaType(), c.javaField(),
                c.comment(), c.primaryKey(), c.nullable(), c.autoIncrement())).toList();
        TableMetaVO meta = new TableMetaVO(spec.tableName(),
                JavaTypeMapper.toClassName(spec.tableName()), spec.comment(), cols);

        CodeGenProperties cgp = new CodeGenProperties();
        cgp.setBasePackage("com.acme.scaffold");
        cgp.setAuthor(props.getAuthor());
        cgp.setWebPath(props.getWebPath());

        GenOption option = new GenOption(spec.tableName(), module, spec.comment(),
                null, props.getAuthor(), true);
        return CodeTemplates.renderAll(meta, option, cgp);
    }

    private static String deriveModule(String tableName) {
        String t = tableName.trim().toLowerCase();
        if (t.startsWith("sys_")) {
            t = t.substring(4);
        }
        return t.isEmpty() ? "gen" : t;
    }

    /** 粗略估算：离线场景下按字符数近似（中文约 1 字/token）。 */
    private static int estimateTokens(String prompt) {
        return prompt.length();
    }
}
