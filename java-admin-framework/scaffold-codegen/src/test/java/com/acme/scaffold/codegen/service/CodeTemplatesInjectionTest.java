package com.acme.scaffold.codegen.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.codegen.config.CodeGenProperties;
import com.acme.scaffold.codegen.domain.ColumnMetaView;
import com.acme.scaffold.codegen.domain.GenOption;
import com.acme.scaffold.codegen.domain.GeneratedFileView;
import com.acme.scaffold.codegen.domain.TableMetaView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 代码生成模板注入回归：不可信文本不得闭合注释 / 跳出字符串 / 注入 Vue 模板 / 穿越 zip 路径。 */
class CodeTemplatesInjectionTest {

    private static final CodeGenProperties PROPS = new CodeGenProperties();

    @Test
    void shouldNeutralizeJavaCommentAndAnnotationInjection() {
        List<GeneratedFileView> files = CodeTemplates.renderAll(
                meta("sys_user", "User", "用户表", "用户名"),
                option("用户*/public class Injected{", "Scaffold*/import evil;", "User"), PROPS);

        String controller = content(files, "UserController.java");
        assertFalse(controller.contains("*/public class Injected"));
        assertFalse(controller.contains("*/import evil;"));
        assertFalse(controller.contains("\nimport evil;"));
        assertTrue(controller.contains("@Tag(name = \"用户* /public class Injected｛\")"));

        String doSource = content(files, "UserDO.java");
        assertFalse(doSource.contains("作者 Scaffold*/import evil;"));
        assertTrue(doSource.contains("作者 Scaffold* /import evil;"));
    }

    @Test
    void shouldNeutralizeVueCommentAndTemplateInjection() {
        String payload = "用户-->\"><script>alert(1)</script>{{evil}}";
        List<GeneratedFileView> files = CodeTemplates.renderAll(
                meta("sys_user", "User", "用户表", "用户名"), option(payload, "scaffold", "User"), PROPS);

        String vue = content(files, "UserView.vue");
        assertFalse(vue.contains("-->\"><script>"));
        assertFalse(vue.contains("<script>"));
        assertFalse(vue.contains("{{evil}}"));
        assertTrue(vue.contains("--›“›‹script›"));
        assertTrue(vue.contains("｛｛evil｝｝"));

        String js = content(files, "user.js");
        assertFalse(js.contains("<script>"));
        assertTrue(js.contains("// 用户--›“›‹script›"));
    }

    @Test
    void shouldNeutralizeColumnCommentInJavaAndVue() {
        String columnComment = "名称\" @click=\"evil() */ --> <b>";
        List<GeneratedFileView> files = CodeTemplates.renderAll(
                meta("sys_user", "User", "用户表", columnComment),
                option("用户管理", "scaffold", "User"), PROPS);

        String vue = content(files, "UserView.vue");
        assertFalse(vue.contains("名称\" @click"));
        assertFalse(vue.contains("<b>"));
        assertTrue(vue.contains("名称“ @click=“evil()"));

        String doSource = content(files, "UserDO.java");
        assertFalse(doSource.contains("*/ --›‹b› */"));
        assertTrue(doSource.contains("名称“ @click=“evil() * / --› ‹b›"));
    }

    @Test
    void shouldRejectUnsafeClassNameDerivedFromTableMeta() {
        TableMetaView unsafeMeta = meta("sys_user", "../Injected", "用户表", "用户名");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CodeTemplates.renderAll(unsafeMeta, option("用户管理", "scaffold", null), PROPS));

        assertTrue(ex.getMessage().contains("className"));
    }

    @Test
    void shouldDeriveSafeSqlTableIdentifier() {
        List<GeneratedFileView> files = CodeTemplates.renderAll(
                meta("sys-user; DROP", "User", "用户表", "用户名"),
                option("用户管理", "scaffold", "User"), PROPS);

        String serviceImpl = content(files, "UserServiceImpl.java");
        assertTrue(serviceImpl.contains("SqlTables.SYS_USER__DROP"));
        assertFalse(serviceImpl.contains("SqlTables.SYS-USER; DROP"));
    }

    @Test
    void shouldKeepSafeTextUnchanged() {
        List<GeneratedFileView> files = CodeTemplates.renderAll(
                meta("sys_user", "User", "用户表", "用户名"),
                option("用户管理", "scaffold", "User"), PROPS);

        String controller = content(files, "UserController.java");
        assertTrue(controller.contains("@Tag(name = \"用户管理\")"));
        assertTrue(content(files, "UserView.vue").contains("<h2 class=\"page-title\">用户管理</h2>"));
        assertTrue(content(files, "UserDO.java").contains("/** 用户名 */"));
        assertTrue(content(files, "UserDO.java").contains("作者 scaffold"));
    }

    private static TableMetaView meta(String tableName, String className, String tableComment,
                                      String columnComment) {
        return new TableMetaView(tableName, className, tableComment, List.of(
                new ColumnMetaView("id", "bigint", "Long", "id", "主键", true, false, true),
                new ColumnMetaView("user_name", "varchar", "String", "userName",
                        columnComment, false, true, false)));
    }

    private static GenOption option(String functionName, String author, String className) {
        return new GenOption("sys_user", "user", functionName, className, author, true);
    }

    private static String content(List<GeneratedFileView> files, String suffix) {
        return files.stream()
                .filter(f -> f.path().endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到生成文件: " + suffix))
                .content();
    }
}
