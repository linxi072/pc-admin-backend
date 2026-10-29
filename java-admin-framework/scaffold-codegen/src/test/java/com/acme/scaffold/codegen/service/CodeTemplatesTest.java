package com.acme.scaffold.codegen.service;

import com.acme.scaffold.codegen.config.CodeGenProperties;
import com.acme.scaffold.codegen.dto.ColumnMetaVO;
import com.acme.scaffold.codegen.dto.GenOption;
import com.acme.scaffold.codegen.dto.GeneratedFileVO;
import com.acme.scaffold.codegen.dto.TableMetaVO;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 代码生成纯函数单测：类型映射、命名推导、模板渲染（无 DB / Spring）。
 */
class CodeTemplatesTest {

    // ---------------------------------------------------------------- 类型映射

    @Test
    void toJavaType_commonMappings() {
        assertEquals("Boolean", JavaTypeMapper.toJavaType("tinyint", true));
        assertEquals("Integer", JavaTypeMapper.toJavaType("tinyint", false));
        assertEquals("Integer", JavaTypeMapper.toJavaType("int", false));
        assertEquals("Long", JavaTypeMapper.toJavaType("bigint", false));
        assertEquals("BigDecimal", JavaTypeMapper.toJavaType("decimal", false));
        assertEquals("String", JavaTypeMapper.toJavaType("varchar", false));
        assertEquals("LocalDateTime", JavaTypeMapper.toJavaType("datetime", false));
        assertEquals("LocalDate", JavaTypeMapper.toJavaType("date", false));
        assertEquals("byte[]", JavaTypeMapper.toJavaType("blob", false));
        // 未知类型回退 String
        assertEquals("String", JavaTypeMapper.toJavaType("geometry", false));
        assertEquals("String", JavaTypeMapper.toJavaType(null, false));
    }

    @Test
    void needsImport_onlyForBigDecimalAndTime() {
        assertTrue(JavaTypeMapper.needsImport("BigDecimal"));
        assertTrue(JavaTypeMapper.needsImport("LocalDateTime"));
        assertFalse(JavaTypeMapper.needsImport("String"));
        assertFalse(JavaTypeMapper.needsImport("Long"));
    }

    @Test
    void toJavaField_snakeToCamel() {
        assertEquals("userName", JavaTypeMapper.toJavaField("user_name"));
        assertEquals("id", JavaTypeMapper.toJavaField("id"));
        assertEquals("createdAt", JavaTypeMapper.toJavaField("created_at"));
        assertEquals("a", JavaTypeMapper.toJavaField("a"));
        assertEquals("field", JavaTypeMapper.toJavaField(null));
        assertEquals("field", JavaTypeMapper.toJavaField("  "));
    }

    @Test
    void toClassName_stripsCommonPrefixes() {
        assertEquals("User", JavaTypeMapper.toClassName("sys_user"));
        assertEquals("BizOrder", JavaTypeMapper.toClassName("biz_order"));
        assertEquals("Order", JavaTypeMapper.toClassName("t_order"));
        assertEquals("Entity", JavaTypeMapper.toClassName(null));
    }

    // ---------------------------------------------------------------- 入参校验

    @Test
    void genOption_rejectsBlankAndBadModule() {
        assertThrows(IllegalArgumentException.class, () -> new GenOption("", "user", null, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> new GenOption("sys_user", "", null, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> new GenOption("sys_user", "User", null, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> new GenOption("sys_user", "1user", null, null, null, false));
        // 合法：连字符模块名
        assertEquals("order-item", new GenOption("t_order_item", "order-item", null, null, null, true).moduleName());
    }

    // ---------------------------------------------------------------- 模板渲染

    private static final TableMetaVO META = new TableMetaVO(
            "sys_user",
            "User",
            "系统用户",
            List.of(
                    new ColumnMetaVO("id", "bigint", "Long", "id", "主键", true, false, true),
                    new ColumnMetaVO("username", "varchar", "String", "username", "用户名", false, false, false),
                    new ColumnMetaVO("amount", "decimal", "BigDecimal", "amount", "余额", false, true, false),
                    new ColumnMetaVO("created_at", "datetime", "LocalDateTime", "createdAt", "创建时间", false, true, false)));

    private static final CodeGenProperties PROPS = new CodeGenProperties();

    private static final GenOption OPTION = new GenOption(
            "sys_user", "user", "用户管理", null, "zhangsan", false);

    @Test
    void renderAll_backendSixFiles() {
        List<GeneratedFileVO> files = CodeTemplates.renderAll(META, OPTION, PROPS);
        assertEquals(6, files.size(), "withFrontend=false 时仅后端六层");
        List<String> paths = files.stream().map(GeneratedFileVO::path).toList();
        assertTrue(paths.contains("src/main/java/com/acme/scaffold/user/UserDO.java"));
        assertTrue(paths.contains("src/main/java/com/acme/scaffold/user/dto/UserVO.java"));
        assertTrue(paths.contains("src/main/java/com/acme/scaffold/user/dto/UserQuery.java"));
        assertTrue(paths.contains("src/main/java/com/acme/scaffold/user/UserController.java"));
        assertTrue(paths.contains("src/main/java/com/acme/scaffold/user/UserService.java"));
        assertTrue(paths.contains("src/main/java/com/acme/scaffold/user/UserServiceImpl.java"));
    }

    @Test
    void renderAll_withFrontend_addsVueAndJs() {
        List<GeneratedFileVO> files = CodeTemplates.renderAll(META,
                new GenOption("sys_user", "user", "用户管理", null, null, true), PROPS);
        assertEquals(8, files.size());
        assertTrue(files.stream().anyMatch(f -> f.path().endsWith("UserView.vue")));
        assertTrue(files.stream().anyMatch(f -> f.path().endsWith("api/user.js")));
    }

    @Test
    void doSource_fieldsImportsAndComments() {
        GeneratedFileVO f = fileOf(META, OPTION, "UserDO.java");
        assertTrue(f.content().startsWith("package com.acme.scaffold.user;"), f.content());
        assertTrue(f.content().contains("import java.math.BigDecimal;"), "decimal 字段需 import BigDecimal");
        assertTrue(f.content().contains("import java.time.LocalDateTime;"), "datetime 字段需 import LocalDateTime");
        assertTrue(f.content().contains("@Data"));
        assertTrue(f.content().contains("private Long id;"));
        assertTrue(f.content().contains("private String username;"));
        assertTrue(f.content().contains("private BigDecimal amount;"));
        assertTrue(f.content().contains("/** 用户名 */"), "列注释应写入字段注释");
    }

    @Test
    void voSource_snowflakeIdSerializedAsString() {
        GeneratedFileVO f = fileOf(META, OPTION, "UserVO.java");
        assertTrue(f.content().contains("@JsonSerialize(using = ToStringSerializer.class)"), "主键须字符串序列化防前端精度丢失");
        assertTrue(f.content().contains("public record UserVO("));
        assertTrue(f.content().contains("Long id,"), f.content());
        assertTrue(f.content().contains("String username,"), f.content());
    }

    @Test
    void controllerSource_permissionsAndRoutes() {
        GeneratedFileVO f = fileOf(META, OPTION, "UserController.java");
        assertTrue(f.content().contains("@RequestMapping(\"/api/system/user\")"), f.content());
        assertTrue(f.content().contains("hasAuthority('user:read')"), f.content());
        assertTrue(f.content().contains("hasAuthority('user:manage')"), f.content());
        assertTrue(f.content().contains("Result<PageResult<UserVO>> page(UserQuery query)"), f.content());
    }

    @Test
    void serviceImplSource_usesRealJooqWritersApi() {
        GeneratedFileVO f = fileOf(META, OPTION, "UserServiceImpl.java");
        // insert 为 void：先雪花 ID 再 insert，与既有模块（RefreshTokenServiceImpl 等）一致
        assertTrue(f.content().contains("setId(IdGenerator.nextId())"), f.content());
        assertTrue(f.content().contains("JooqWriters.insert(dsl, JooqTables.SYS_USER, user);"), f.content());
        assertTrue(f.content().contains("JooqWriters.updateById(dsl, JooqTables.SYS_USER, id, user);"), f.content());
        // 删除默认走框架逻辑删除（deleted=1）
        assertTrue(f.content().contains("JooqWriters.delete(dsl, JooqTables.SYS_USER, id, true);"), f.content());
        assertTrue(f.content().contains("JooqWriters.fetchById(dsl, JooqTables.SYS_USER, UserDO.class, id);"), f.content());
        // 生成即用骨架：toVO 必须真实映射，不得保留占位异常
        assertFalse(f.content().contains("UnsupportedOperationException"), "生成骨架不应保留占位异常");
        assertTrue(f.content().contains("private UserVO toVO("), f.content());
        assertTrue(f.content().contains("new UserVO("), "应生成真实 DO→VO 映射");
        assertTrue(f.content().contains("private Condition buildCondition("), f.content());
    }

    @Test
    void querySource_includesAllNonMetaFilterFields() {
        // sys_user：username / amount / created_at 三列，其中 created_at 为元数据列应被排除
        GeneratedFileVO f = fileOf(META, OPTION, "UserQuery.java");
        assertTrue(f.content().contains("String username"), "Query 应包含 username 字段");
        assertTrue(f.content().contains("BigDecimal amount"), "Query 应包含 amount 字段");
        assertFalse(f.content().contains("createdAt"), "Query 应排除元数据列 createdAt");
        assertFalse(f.content().contains("tenantId"), "Query 应排除租户隔离列 tenantId");
    }

    @Test
    void vueSource_toolbarPartitionAndNoStrayHeader() {
        GeneratedFileVO f = fileOf(META,
                new GenOption("sys_user", "user", "用户管理", null, null, true), "UserView.vue");
        assertTrue(f.content().contains("<template>"), f.content());
        // 不得出现 Java 注释头（会作为文本节点渲染到页面）
        assertFalse(f.content().contains("/**"), f.content());
        // 工具栏使用全局分区类
        assertTrue(f.content().contains("class=\"toolbar-filters\""), f.content());
        assertTrue(f.content().contains("class=\"toolbar-actions\""), f.content());
        assertTrue(f.content().contains("class=\"filter-group\""), f.content());
        // 表格列取非主键字段（username/amount/createdAt 截前 6 全量）
        assertTrue(f.content().contains("prop=\"username\""), f.content());
        assertTrue(f.content().contains("prop=\"amount\""), f.content());
        assertTrue(f.content().contains("prop=\"createdAt\""), f.content());
        assertTrue(f.content().contains("prop=\"id\""), f.content());
        // script 引用 api 模块
        assertTrue(f.content().contains("from '@/api/user'"), f.content());
    }

    @Test
    void jsSource_crudWrappers() {
        GeneratedFileVO f = fileOf(META,
                new GenOption("sys_user", "user", "用户管理", null, null, true), "user.js");
        assertTrue(f.content().contains("export const userPage = (params) =>"), f.content());
        assertTrue(f.content().contains("method: 'GET', url: '/api/system/user', params"), f.content());
        assertTrue(f.content().contains("method: 'POST', url: '/api/system/user', data"), f.content());
        assertTrue(f.content().contains("method: 'DELETE', url: '/api/system/user/' + id"), f.content());
    }

    @Test
    void renderAll_customClassNameOverridesDerived() {
        GeneratedFileVO f = fileOf(META,
                new GenOption("sys_user", "user", "用户管理", "SysUser", null, false), "SysUserDO.java");
        assertTrue(f.content().contains("public class SysUserDO {"), f.content());
    }

    @Test
    void renderAll_defaultAuthorFromProps() {
        GeneratedFileVO f = fileOf(META,
                new GenOption("sys_user", "user", null, null, "  ", false), "UserDO.java");
        assertTrue(f.content().contains("作者 scaffold"), f.content());
    }

    @Test
    void renderAll_functionNameFallsBackToTableComment() {
        GeneratedFileVO f = fileOf(META,
                new GenOption("sys_user", "user", "", null, null, false), "UserController.java");
        assertTrue(f.content().contains("@Tag(name = \"系统用户\")"), f.content());
    }

    private static GeneratedFileVO fileOf(TableMetaVO meta, GenOption option, String fileName) {
        return CodeTemplates.renderAll(meta, option, PROPS).stream()
                .filter(f -> f.path().endsWith(fileName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未生成文件: " + fileName));
    }
}
