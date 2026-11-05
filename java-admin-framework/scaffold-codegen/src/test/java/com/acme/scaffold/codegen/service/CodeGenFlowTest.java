package com.acme.scaffold.codegen.service;

import com.acme.scaffold.codegen.config.CodeGenProperties;
import com.acme.scaffold.codegen.dto.ColumnMetaVO;
import com.acme.scaffold.codegen.dto.GenOption;
import com.acme.scaffold.codegen.dto.GeneratedFileVO;
import com.acme.scaffold.codegen.dto.TableMetaVO;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 代码生成器独立集成测试（Docker-free，surefire 可执行）。
 *
 * <p>通过 jOOQ {@link MockConnection} 在内存中模拟 MySQL {@code information_schema} 结果集，
 * 驱动<b>真实</b>的 {@link TableSchemaService}（原生 SQL → 列元数据 → Java 类型映射）、
 * {@link CodeGenService}（预览 / 打包）与 {@link CodeTemplates}（模板渲染）全链路。
 * 不依赖真实数据库：生产 SQL 使用 MySQL 专有的 {@code TABLE_COMMENT} / {@code DATABASE()}，
 * H2 并不兼容，故以返回结果集的方式隔离「数据库」这一层，其余均为生产对象真实协作。</p>
 */
class CodeGenFlowTest {

    // 列名须与 TableSchemaService 原生 SQL 的投影一致：服务层用 r.get("NAME", type) 按名取值
    private static final Field<String> F_TABLE_NAME = DSL.field(DSL.name("TABLE_NAME"), String.class);
    private static final Field<String> F_TABLE_COMMENT = DSL.field(DSL.name("TABLE_COMMENT"), String.class);
    private static final Field<String> F_COLUMN_NAME = DSL.field(DSL.name("COLUMN_NAME"), String.class);
    private static final Field<String> F_DATA_TYPE = DSL.field(DSL.name("DATA_TYPE"), String.class);
    private static final Field<String> F_COLUMN_TYPE = DSL.field(DSL.name("COLUMN_TYPE"), String.class);
    private static final Field<String> F_COLUMN_COMMENT = DSL.field(DSL.name("COLUMN_COMMENT"), String.class);
    private static final Field<String> F_COLUMN_KEY = DSL.field(DSL.name("COLUMN_KEY"), String.class);
    private static final Field<String> F_IS_NULLABLE = DSL.field(DSL.name("IS_NULLABLE"), String.class);
    private static final Field<String> F_EXTRA = DSL.field(DSL.name("EXTRA"), String.class);

    private static final Field<?>[] COLUMN_FIELDS = {
            F_COLUMN_NAME, F_DATA_TYPE, F_COLUMN_TYPE, F_COLUMN_COMMENT,
            F_COLUMN_KEY, F_IS_NULLABLE, F_EXTRA
    };

    /** 仅用于构造内存结果集，不执行 SQL。 */
    private static final DSLContext HELPER = DSL.using(SQLDialect.MYSQL);

    /** 模拟当前库的 information_schema，并记录服务层实际渲染出的 SQL。 */
    private static final class SchemaData implements MockDataProvider {

        boolean tableExists = true;
        boolean columnsEmpty = false;
        final List<String> executedSql = new ArrayList<>();

        @Override
        public MockResult[] execute(MockExecuteContext ctx) {
            String sql = ctx.sql() == null ? "" : ctx.sql();
            executedSql.add(sql);
            if (sql.contains("TABLE_COMMENT")) {
                return one(commentRows());
            }
            if (sql.contains("information_schema.COLUMNS")) {
                return one(columnRows());
            }
            if (sql.contains("information_schema.TABLES")) {
                return one(tableRows());
            }
            return new MockResult[]{new MockResult(0)};
        }

        private Result<Record> commentRows() {
            // 数组形式走 newResult(Field<?>...) 重载，返回 Result<Record>；
            // 单字段重载会返回 Result<Record1<T>>，与 MockResult 所需的 Result<Record> 不兼容。
            Result<Record> rs = HELPER.newResult(new Field<?>[]{F_TABLE_COMMENT});
            if (tableExists) {
                Record r = HELPER.newRecord(new Field<?>[]{F_TABLE_COMMENT});
                r.set(F_TABLE_COMMENT, "系统用户");
                rs.add(r);
            }
            return rs;
        }

        private Result<Record> tableRows() {
            Result<Record> rs = HELPER.newResult(new Field<?>[]{F_TABLE_NAME});
            for (String name : List.of("sys_role", "sys_user")) {
                Record r = HELPER.newRecord(new Field<?>[]{F_TABLE_NAME});
                r.set(F_TABLE_NAME, name);
                rs.add(r);
            }
            return rs;
        }

        private Result<Record> columnRows() {
            Result<Record> rs = HELPER.newResult(COLUMN_FIELDS);
            if (columnsEmpty) {
                return rs;
            }
            addColumn(rs, "id", "bigint", "bigint(20)", "主键", "PRI", "NO", "auto_increment");
            addColumn(rs, "user_name", "varchar", "varchar(64)", "用户名", "", "NO", "");
            addColumn(rs, "amount", "decimal", "decimal(18,2)", "余额", "", "YES", "");
            addColumn(rs, "enabled", "tinyint", "tinyint(1)", "是否启用（中文注释）", "", "NO", "");
            addColumn(rs, "created_at", "datetime", "datetime", "创建时间", "", "YES", "");
            return rs;
        }

        private static void addColumn(Result<Record> rs, String name, String dataType, String columnType,
                                      String comment, String key, String nullable, String extra) {
            Record r = HELPER.newRecord(COLUMN_FIELDS);
            r.set(F_COLUMN_NAME, name);
            r.set(F_DATA_TYPE, dataType);
            r.set(F_COLUMN_TYPE, columnType);
            r.set(F_COLUMN_COMMENT, comment);
            r.set(F_COLUMN_KEY, key);
            r.set(F_IS_NULLABLE, nullable);
            r.set(F_EXTRA, extra);
            rs.add(r);
        }

        private static MockResult[] one(Result<Record> rs) {
            return new MockResult[]{new MockResult(rs.size(), rs)};
        }
    }

    private static TableSchemaService schema(SchemaData data) {
        Connection conn = new MockConnection(data);
        return new TableSchemaService(DSL.using(conn, SQLDialect.MYSQL));
    }

    private static CodeGenService codeGen(SchemaData data) {
        return new CodeGenService(schema(data), new CodeGenProperties());
    }

    private static GenOption option() {
        return new GenOption("sys_user", "user", "用户管理", null, "zhangsan", true);
    }

    private static ColumnMetaVO column(TableMetaVO meta, String columnName) {
        return meta.columns().stream()
                .filter(c -> columnName.equals(c.columnName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少列: " + columnName));
    }

    // ---------------------------------------------------------------- 元数据层

    @Test
    void schemaMapping_mapsMysqlMetadataToJavaMeta() {
        SchemaData data = new SchemaData();
        TableMetaVO meta = schema(data).tableMeta("sys_user");

        assertNotNull(meta);
        assertEquals("sys_user", meta.tableName());
        assertEquals("User", meta.className(), "表名前缀应被剥离并转驼峰");
        assertEquals("系统用户", meta.comment());
        assertEquals(5, meta.columns().size());

        ColumnMetaVO id = column(meta, "id");
        assertEquals("Long", id.javaType());
        assertTrue(id.primaryKey(), "PRI 应映射为主键");
        assertFalse(id.nullable(), "NO 应为非空");
        assertTrue(id.autoIncrement(), "EXTRA 含 auto_increment 应识别");

        assertEquals("String", column(meta, "user_name").javaType());
        assertEquals("userName", column(meta, "user_name").javaField(), "下划线应转驼峰");
        assertEquals("BigDecimal", column(meta, "amount").javaType());
        assertTrue(column(meta, "amount").nullable(), "YES 应为可空");
        assertEquals("Boolean", column(meta, "enabled").javaType(), "tinyint(1) 应映射 Boolean");
        assertEquals("LocalDateTime", column(meta, "created_at").javaType());
        assertEquals("createdAt", column(meta, "created_at").javaField());

        assertTrue(data.executedSql.stream().anyMatch(s -> s.contains("DATABASE()")),
                "表/列查询须限定当前库，避免跨库串表");
        assertTrue(data.executedSql.stream().anyMatch(s -> s.contains("ORDINAL_POSITION")),
                "列须按物理顺序返回，保证生成字段顺序稳定");
    }

    @Test
    void schemaMapping_blankOrMissingTableReturnsNull() {
        SchemaData blank = new SchemaData();
        assertEquals(null, schema(blank).tableMeta("  "));
        assertTrue(blank.executedSql.isEmpty(), "空表名应短路，不触发任何 SQL");

        SchemaData missing = new SchemaData();
        missing.tableExists = false;
        assertEquals(null, schema(missing).tableMeta("sys_not_exist"));
        assertEquals(1, missing.executedSql.size(), "表不存在时不应继续查询列元数据");
    }

    @Test
    void listTables_scopesToCurrentDatabaseAndReturnsNames() {
        SchemaData data = new SchemaData();
        List<String> tables = schema(data).listTables();

        assertEquals(List.of("sys_role", "sys_user"), tables);
        assertTrue(data.executedSql.get(0).contains("BASE TABLE"), "须排除视图");
        assertTrue(data.executedSql.get(0).contains("ORDER BY TABLE_NAME"), "须按表名排序");
    }

    // ---------------------------------------------------------------- 编排 / 生成层

    @Test
    void preview_endToEnd_generatesEightFilesFromRealSchema() {
        List<GeneratedFileVO> files = codeGen(new SchemaData()).preview(option());

        assertEquals(8, files.size(), "withFrontend=true 应为后端六层 + Vue + api.js");
        Set<String> paths = files.stream().map(GeneratedFileVO::path).collect(Collectors.toSet());
        assertEquals(8, paths.size(), "生成路径不得重复");
        assertTrue(paths.contains("src/main/java/com/acme/scaffold/user/UserDO.java"));
        assertTrue(paths.contains("src/main/java/com/acme/scaffold/user/UserServiceImpl.java"));

        GeneratedFileVO doFile = files.stream()
                .filter(f -> f.path().endsWith("UserDO.java")).findFirst().orElseThrow();
        assertTrue(doFile.content().startsWith("package com.acme.scaffold.user;"));
        assertTrue(doFile.content().contains("private Long id;"), "元数据 bigint 主键应落到实体");
        assertTrue(doFile.content().contains("private Boolean enabled;"), "tinyint(1) 应落为 Boolean");
        assertTrue(doFile.content().contains("private BigDecimal amount;"));
        assertTrue(doFile.content().contains("是否启用（中文注释）"), "中文列注释应无损写入生成物");

        GeneratedFileVO voFile = files.stream()
                .filter(f -> f.path().endsWith("UserVO.java")).findFirst().orElseThrow();
        assertTrue(voFile.content().contains("ToStringSerializer"),
                "雪花主键须字符串序列化，防止前端精度丢失");
    }

    @Test
    void downloadZip_entriesMatchPreviewWithUtf8Content() throws Exception {
        SchemaData data = new SchemaData();
        CodeGenService service = codeGen(data);
        GenOption option = option();
        List<GeneratedFileVO> files = service.preview(option);
        byte[] zip = service.downloadZip(option);

        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                entries.put(e.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }

        assertEquals(files.size(), entries.size(), "zip 条目数须与预览文件数一致");
        for (GeneratedFileVO f : files) {
            assertTrue(entries.containsKey(f.path()), "zip 缺少条目: " + f.path());
            assertEquals(f.content(), entries.get(f.path()),
                    "zip 内容须与预览逐字一致（含中文 UTF-8）: " + f.path());
        }
        assertTrue(entries.values().stream().anyMatch(c -> c.contains("是否启用（中文注释）")),
                "中文内容经 UTF-8 打包后不得乱码");
        assertEquals("codegen-sys_user.zip", service.zipName(option));
    }

    // ---------------------------------------------------------------- 边界拒绝

    @Test
    void requireMeta_missingTableThrowsNotFound() {
        SchemaData data = new SchemaData();
        data.tableExists = false;
        BusinessException ex = assertThrows(BusinessException.class,
                () -> codeGen(data).preview(option()));
        assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("表不存在"));
    }

    @Test
    void requireMeta_emptyColumnsThrowsValidationError() {
        SchemaData data = new SchemaData();
        data.columnsEmpty = true;
        BusinessException ex = assertThrows(BusinessException.class,
                () -> codeGen(data).preview(option()));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("表无字段"));
    }
}
