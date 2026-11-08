package com.acme.scaffold.ai.port;

import com.acme.scaffold.ai.dto.ColumnSpec;
import com.acme.scaffold.ai.dto.TableSpec;
import com.acme.scaffold.codegen.service.JavaTypeMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 离线启发式大模型提供者（默认）：基于关键词的确定性表结构推断，无外部依赖，可过双门禁。
 *
 * <p>解析策略：
 * <ol>
 *   <li>定位「表」字，向前截取实体中文名（剥离请/生成/创建/一个 等引导词），映射为英文表名；</li>
 *   <li>扫描已知字段关键词（金额/状态/名称/数量…）补齐业务列，并补标准主键与审计列；</li>
 *   <li>列类型经 {@link JavaTypeMapper} 统一映射为 Java 类型，与 codegen 渲染对齐。</li>
 * </ol>
 * 启发式仅覆盖常见场景，复杂语义仍由人工复核（结果标记 {@code needsReview=true}）。
 */
@Component
public class HeuristicLlmProvider implements LlmProvider {

    /** 中文实体名 → 英文表名（去 sys_ 前缀；未命中回退 gen_ + 清洗）。 */
    private static final Map<String, String> ENTITY_EN = Map.ofEntries(
            Map.entry("订单", "order"),
            Map.entry("用户", "user"),
            Map.entry("商品", "product"),
            Map.entry("产品", "product"),
            Map.entry("客户", "customer"),
            Map.entry("文章", "article"),
            Map.entry("分类", "category"),
            Map.entry("评论", "comment"),
            Map.entry("角色", "role"),
            Map.entry("菜单", "menu"),
            Map.entry("日志", "log"),
            Map.entry("配置", "config"),
            Map.entry("文件", "file"),
            Map.entry("消息", "message"),
            Map.entry("图片", "image"),
            Map.entry("供应商", "supplier"),
            Map.entry("仓库", "warehouse"),
            Map.entry("库存", "stock"),
            Map.entry("支付", "payment"),
            Map.entry("退款", "refund"),
            Map.entry("地址", "address"),
            Map.entry("购物车", "cart"),
            Map.entry("部门", "dept"),
            Map.entry("岗位", "post"),
            Map.entry("字典", "dict"));

    /** 字段关键词 → (列名, JDBC 类型, 注释)。命中任一关键词即补该列（去重）。 */
    private static final List<FieldRule> FIELD_RULES = List.of(
            new FieldRule(List.of("金额", "价格", "总价", "单价", "费用", "价钱"), "amount", "decimal", "金额"),
            new FieldRule(List.of("状态", "审批状态", "审核状态"), "status", "varchar", "状态"),
            new FieldRule(List.of("名称", "名字", "姓名", "标题", "用户名"), "name", "varchar", "名称"),
            new FieldRule(List.of("描述", "备注", "说明", "简介"), "description", "varchar", "描述"),
            new FieldRule(List.of("数量", "个数", "库存数量"), "quantity", "int", "数量"),
            new FieldRule(List.of("手机号", "手机", "电话"), "phone", "varchar", "手机号"),
            new FieldRule(List.of("邮箱", "邮件"), "email", "varchar", "邮箱"),
            new FieldRule(List.of("地址"), "address", "varchar", "地址"),
            new FieldRule(List.of("图片", "头像", "封面"), "image_url", "varchar", "图片地址"),
            new FieldRule(List.of("内容", "正文"), "content", "text", "内容"),
            new FieldRule(List.of("分类", "类型"), "category", "varchar", "分类"),
            new FieldRule(List.of("编码", "编号", "单号"), "code", "varchar", "编码"));

    /** 引导动词/量词前缀（解析实体名时剥离）。 */
    private static final List<String> LEAD_PREFIXES = List.of(
            "请", "帮", "我", "生成", "创建", "新建", "设计", "一张", "一个", "的", "需要", "想", "构建");

    private record FieldRule(List<String> keywords, String column, String jdbc, String comment) {
    }

    @Override
    public boolean isDefault() {
        return true;
    }

    @Override
    public String name() {
        return "heuristic";
    }

    @Override
    public TableSpec parse(String prompt) {
        String entityCn = extractEntity(prompt);
        String en = ENTITY_EN.getOrDefault(entityCn, "gen_" + sanitize(entityCn));
        String tableName = "sys_" + en;
        String comment = entityCn.isBlank() ? en : entityCn;

        Map<String, ColumnSpec> cols = new LinkedHashMap<>();
        cols.put("id", pk("id", "BIGINT", "主键"));
        for (FieldRule rule : FIELD_RULES) {
            for (String kw : rule.keywords()) {
                if (prompt != null && prompt.contains(kw)) {
                    cols.putIfAbsent(rule.column(), biz(rule.column(), rule.jdbc(), rule.comment()));
                    break;
                }
            }
        }
        cols.putIfAbsent("tenant_id", biz("tenant_id", "bigint", "租户ID"));
        cols.putIfAbsent("create_time", biz("create_time", "datetime", "创建时间"));
        cols.putIfAbsent("update_time", biz("update_time", "datetime", "更新时间"));
        cols.putIfAbsent("deleted", deleted());

        return new TableSpec(tableName, comment, new ArrayList<>(cols.values()));
    }

    private static String extractEntity(String prompt) {
        if (prompt == null) {
            return "";
        }
        int idx = prompt.indexOf('表');
        if (idx <= 0) {
            return "";
        }
        int start = Math.max(0, idx - 4);
        String cand = prompt.substring(start, idx).trim();
        for (String p : LEAD_PREFIXES) {
            while (cand.startsWith(p)) {
                cand = cand.substring(p.length());
            }
        }
        return cand;
    }

    private static String sanitize(String s) {
        if (s == null || s.isBlank()) {
            return "biz";
        }
        String cleaned = s.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return cleaned.isEmpty() ? "biz" : cleaned;
    }

    private static ColumnSpec pk(String name, String jdbc, String comment) {
        return new ColumnSpec(name, jdbc.toLowerCase(), JavaTypeMapper.toJavaType(jdbc, false),
                JavaTypeMapper.toJavaField(name), comment, true, false, true);
    }

    private static ColumnSpec biz(String name, String jdbc, String comment) {
        return new ColumnSpec(name, jdbc.toLowerCase(), JavaTypeMapper.toJavaType(jdbc, false),
                JavaTypeMapper.toJavaField(name), comment, false, true, false);
    }

    private static ColumnSpec deleted() {
        return new ColumnSpec("deleted", "tinyint", JavaTypeMapper.toJavaType("tinyint", true),
                "deleted", "逻辑删除", false, false, false);
    }
}
