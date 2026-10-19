package com.acme.scaffold.perf;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.export.XlsxDataExporter;
import com.acme.scaffold.common.importer.CsvDataReader;
import com.acme.scaffold.common.importer.TabularData;
import com.acme.scaffold.common.sensitive.SensitiveMasker;
import com.acme.scaffold.common.sensitive.SensitiveType;
import com.acme.scaffold.common.tree.TreeBuilder;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.job.domain.JobIntervalUnit;
import com.acme.scaffold.job.domain.JobNextFire;
import com.acme.scaffold.job.domain.JobTriggerType;
import com.acme.scaffold.workflow.bpmn.BpmnWorkflowBuilder;
import com.acme.scaffold.workflow.bpmn.WorkflowDesignValidator;
import com.acme.scaffold.workflow.dto.design.WorkflowEdgeDTO;
import com.acme.scaffold.workflow.dto.design.WorkflowNodeDTO;
import com.acme.scaffold.workflow.model.WorkflowNodeType;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 可量化微基准（纯函数 / 无 IO 层）：对核心热点路径测量吞吐并给出 ops/ms 指标，
 * 同时以宽松上界充当性能回归门禁（留 10 倍以上余量，避免 CI 抖动误报）。
 *
 * <p>覆盖：排序白名单解析（含 SQL 渲染）、菜单/机构树构建、敏感数据脱敏、
 * XLSX 写出、CSV 解析、BPMN 构建与设计校验、cron 下次触发计算。</p>
 *
 * <p>边界说明：沙箱无 MySQL，数据库层（查询/N+1）无法在本基准中量化，
 * 相应结论见测试报告中的静态性能审查章节。</p>
 */
class PerformanceBenchmarkTest {

    private record BenchNode(Long id, Long parentId, String name) {
    }

    private record BenchVO(Long id, String name, List<BenchVO> children) {
    }

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ------------------------------------------------------------------
    // 1. JooqSorts 排序白名单解析 + SQL 渲染
    // ------------------------------------------------------------------

    @Test
    @DisplayName("基准：JooqSorts.resolve 白名单解析吞吐（含 SQL 渲染）")
    void benchJooqSortsResolve() {
        DSLContext ctx = DSL.using(SQLDialect.MYSQL);
        Map<String, String> whitelist = JooqSorts.whitelist(
                "id", "id", "username", "username", "createdAt", "created_at");
        OrderField<?> fallback = JooqTables.SYS_USER.field("id", Object.class).desc();

        // 预热（JIT）
        for (int i = 0; i < 5_000; i++) {
            render(ctx, JooqSorts.resolve(JooqTables.SYS_USER,
                    PageQuery.of(1, 10, "username", "DESC"), whitelist, fallback));
        }
        int rounds = 20_000;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            render(ctx, JooqSorts.resolve(JooqTables.SYS_USER,
                    PageQuery.of(1, 10, i % 3 == 0 ? "username" : "createdAt", "DESC"),
                    whitelist, fallback));
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        double opsPerMs = rounds / (double) Math.max(ms, 1);
        report("JooqSorts.resolve+render", rounds, ms, opsPerMs);
        assertTrue(ms < 10_000, "20k 次解析+渲染耗时 " + ms + "ms，超出回归门禁");
    }

    private static String render(DSLContext ctx, OrderField<?>[] order) {
        return ctx.renderInlined(ctx.selectOne().from(JooqTables.SYS_USER.table()).orderBy(order))
                .toString();
    }

    // ------------------------------------------------------------------
    // 2. 树构建（菜单/机构树同款 TreeBuilder）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("基准：TreeBuilder 千节点树构建吞吐")
    void benchTreeBuild() {
        // 1000 节点：50 个顶层 × 每层 20 分支 × 深度 1（宽而浅，贴近菜单树）
        List<BenchNode> flat = new ArrayList<>();
        long id = 1;
        for (int root = 0; root < 50; root++) {
            Long rootId = id++;
            flat.add(new BenchNode(rootId, 0L, "root-" + root));
            for (int child = 0; child < 19; child++) {
                flat.add(new BenchNode(id++, rootId, "c-" + child));
            }
        }
        Function<BenchNode, Long> idOf = BenchNode::id;
        Function<BenchNode, Long> parentIdOf = BenchNode::parentId;
        Predicate<BenchNode> rootPredicate = n -> n.parentId() == 0L;
        BiFunction<BenchNode, List<BenchVO>, BenchVO> mapper =
                (n, children) -> new BenchVO(n.id(), n.name(), children);

        for (int i = 0; i < 50; i++) {
            TreeBuilder.build(flat, idOf, parentIdOf, rootPredicate, mapper);
        }
        int rounds = 500;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            List<BenchVO> tree = TreeBuilder.build(flat, idOf, parentIdOf, rootPredicate, mapper);
            assertEquals(50, tree.size());
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        double opsPerMs = rounds / (double) Math.max(ms, 1);
        report("TreeBuilder.build(1000节点)", rounds, ms, opsPerMs);
        assertTrue(ms < 10_000, "500 次千节点树构建耗时 " + ms + "ms，超出回归门禁");
    }

    // ------------------------------------------------------------------
    // 3. 敏感数据脱敏
    // ------------------------------------------------------------------

    @Test
    @DisplayName("基准：SensitiveMasker.mask 混合类型吞吐")
    void benchSensitiveMasker() {
        SensitiveType[] types = {SensitiveType.MOBILE, SensitiveType.ID_CARD, SensitiveType.EMAIL,
                SensitiveType.BANK_CARD, SensitiveType.NAME, SensitiveType.ADDRESS, SensitiveType.IP};
        String[] values = {"13800138000", "110101199003078516", "user@example.com",
                "6222021234567890123", "张三丰", "北京市海淀区中关村大街1号", "192.168.1.100"};

        int rounds = 100_000;
        for (int i = 0; i < 10_000; i++) {
            SensitiveMasker.mask(values[i % values.length], types[i % types.length]);
        }
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            SensitiveMasker.mask(values[i % values.length], types[i % types.length]);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        double opsPerMs = rounds / (double) Math.max(ms, 1);
        report("SensitiveMasker.mask(混合)", rounds, ms, opsPerMs);
        assertTrue(ms < 10_000, "10 万次脱敏耗时 " + ms + "ms，超出回归门禁");
    }

    // ------------------------------------------------------------------
    // 4. XLSX 写出（零依赖手写 OOXML）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("基准：XlsxDataExporter.export 五千行写出吞吐")
    void benchXlsxExport() {
        List<String> headers = List.of("id", "name", "dept", "mobile", "email", "status", "created", "remark");
        List<List<String>> rows = new ArrayList<>();
        for (int r = 0; r < 5_000; r++) {
            rows.add(List.of(String.valueOf(r), "用户" + r, "研发部", "1380013800" + (r % 10),
                    "u" + r + "@example.com", "ACTIVE", "2026-10-19 10:00:00", "备注" + r));
        }
        for (int i = 0; i < 2; i++) {
            XlsxDataExporter.export(headers, rows);
        }
        int rounds = 5;
        long totalBytes = 0;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            byte[] bytes = XlsxDataExporter.export(headers, rows);
            totalBytes += bytes.length;
            assertNotNull(bytes);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        double opsPerMs = rounds / (double) Math.max(ms, 1);
        double mbPerSec = totalBytes / 1024.0 / 1024.0 / (ms / 1000.0);
        report("XlsxDataExporter.export(5000行)", rounds, ms, opsPerMs);
        System.out.printf("    └─ 产出 %.2f MB，写出速率 %.2f MB/s%n",
                totalBytes / 1024.0 / 1024.0, mbPerSec);
        assertTrue(ms < 30_000, "5 次五千行 XLSX 写出耗时 " + ms + "ms，超出回归门禁");
    }

    // ------------------------------------------------------------------
    // 5. CSV 解析（RFC4180 状态机）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("基准：CsvDataReader.read 五千行解析吞吐")
    void benchCsvRead() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write("id,name,remark\n".getBytes(StandardCharsets.UTF_8));
        for (int r = 0; r < 5_000; r++) {
            // 混入引号/逗号/换行，压测状态机
            bos.write((r + ",\"用户, " + r + "\",\"第一行\n第二行 \"\"引号\"\"\"\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        byte[] csv = bos.toByteArray();
        for (int i = 0; i < 3; i++) {
            CsvDataReader.read(csv);
        }
        int rounds = 10;
        long start = System.nanoTime();
        int lastRows = 0;
        for (int i = 0; i < rounds; i++) {
            TabularData data = CsvDataReader.read(csv);
            lastRows = data.rows().size();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        double opsPerMs = rounds / (double) Math.max(ms, 1);
        double mbPerSec = csv.length / 1024.0 / 1024.0 / (ms / 1000.0);
        report("CsvDataReader.read(~" + csv.length / 1024 + "KB)", rounds, ms, opsPerMs);
        System.out.printf("    └─ 每轮解析 %d 行，吞吐 %.2f MB/s%n", lastRows, mbPerSec);
        assertEquals(5_000, lastRows);
        assertTrue(ms < 20_000, "10 轮五千行 CSV 解析耗时 " + ms + "ms，超出回归门禁");
    }

    // ------------------------------------------------------------------
    // 6. BPMN 构建 + 设计校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("基准：BpmnWorkflowBuilder.build 三十节点设计与校验+构建吞吐")
    void benchBpmnBuild() {
        List<WorkflowNodeDTO> nodes = new ArrayList<>();
        List<WorkflowEdgeDTO> edges = new ArrayList<>();
        nodes.add(node("n0", WorkflowNodeType.START, null));
        for (int i = 1; i <= 30; i++) {
            nodes.add(new WorkflowNodeDTO("n" + i, WorkflowNodeType.APPROVAL, "审批" + i,
                    "ANY", null, "USER", "1", null, null, null, "END", null, null, null));
            edges.add(edge("e" + (i - 1), "n" + (i - 1), "n" + i));
        }
        nodes.add(node("n31", WorkflowNodeType.END, null));
        edges.add(edge("e30", "n30", "n31"));

        for (int i = 0; i < 20; i++) {
            BpmnWorkflowBuilder.build("bench", "基准流程", nodes, edges);
        }
        int rounds = 100;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            String xml = BpmnWorkflowBuilder.build("bench", "基准流程", nodes, edges);
            assertTrue(xml.contains("<definitions"), "生成的 BPMN 不含 definitions 根元素");
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        double opsPerMs = rounds / (double) Math.max(ms, 1);
        report("BpmnWorkflowBuilder.build(32节点)", rounds, ms, opsPerMs);
        assertTrue(ms < 20_000, "100 次 32 节点 BPMN 构建耗时 " + ms + "ms，超出回归门禁");

        // 纯校验（不含构建）单独计量
        start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            WorkflowDesignValidator.validate("bench", "基准流程", nodes, edges);
        }
        long vms = (System.nanoTime() - start) / 1_000_000;
        report("WorkflowDesignValidator.validate(32节点)", rounds, vms, rounds / (double) Math.max(vms, 1));
    }

    // ------------------------------------------------------------------
    // 7. cron 下次触发计算
    // ------------------------------------------------------------------

    @Test
    @DisplayName("基准：JobNextFire.compute CRON 解析吞吐")
    void benchCronNextFire() {
        Instant now = Instant.parse("2026-10-19T02:00:00Z");
        for (int i = 0; i < 2_000; i++) {
            JobNextFire.compute(JobTriggerType.CRON, "0 0/5 * * * ?", null, null,
                    null, null, now.plusSeconds(i), ZoneOffset.UTC);
        }
        int rounds = 10_000;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            JobNextFire.compute(JobTriggerType.CRON, "0 0/5 * * * ?", null, null,
                    null, null, now.plusSeconds(i), ZoneOffset.UTC);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        double opsPerMs = rounds / (double) Math.max(ms, 1);
        report("JobNextFire.compute(CRON)", rounds, ms, opsPerMs);
        assertTrue(ms < 10_000, "1 万次 cron 下次触发计算耗时 " + ms + "ms，超出回归门禁");

        // SIMPLE 与 ONE_TIME 路径（纯算术，标称指标）
        start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            JobNextFire.compute(JobTriggerType.SIMPLE, null, 5, JobIntervalUnit.MINUTES,
                    null, null, now, ZoneOffset.UTC);
        }
        long sms = (System.nanoTime() - start) / 1_000_000;
        report("JobNextFire.compute(SIMPLE)", rounds, sms, rounds / (double) Math.max(sms, 1));
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private static void report(String name, long rounds, long ms, double opsPerMs) {
        System.out.printf("【基准】%-42s 轮次=%-7d 耗时=%-6dms 吞吐=%.1f ops/ms%n",
                name, rounds, ms, opsPerMs);
    }

    private static WorkflowNodeDTO node(String id, WorkflowNodeType type, String rejectPolicy) {
        return new WorkflowNodeDTO(id, type, id, null, null, null, null,
                null, null, null, rejectPolicy, null, null, null);
    }

    private static WorkflowEdgeDTO edge(String id, String from, String to) {
        return new WorkflowEdgeDTO(id, from, to, null, null);
    }
}
