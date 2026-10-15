package com.acme.scaffold.codegen;

import com.acme.scaffold.codegen.dto.ColumnMetaVO;
import com.acme.scaffold.codegen.dto.GenOption;
import com.acme.scaffold.codegen.dto.GeneratedFileVO;
import com.acme.scaffold.codegen.dto.TableMetaVO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.acme.scaffold.job.service.JobService;

/**
 * 代码生成模板纯函数：按表结构拼装后端六层（DO / VO / Query / Controller / Service /
 * ServiceImpl）与前端两文件（Vue 页面 / api.js）的源码文本。
 *
 * <p>与 Spring 及 DB 解耦（输入为 {@link TableMetaVO}），便于离线单测；产物对齐框架既有范式：
 * DO 用 Lombok {@code @Data}，VO 用 record 且雪花 ID 字符串序列化，Controller 走
 * {@code Result} / {@code PageResult} 包装 + {@code @PreAuthorize} 权限码，ServiceImpl 经
 * 构造器注入 {@code DSLContext} 并调用 {@code JooqWriters}。</p>
 *
 * <p>ServiceImpl 的 CRUD 为可用骨架（分页 / 新增 / 修改 / 删除 / 详情），业务校验以
 * {@code TODO} 标注由开发者补齐——生成器负责消除重复劳动，不臆测业务规则。</p>
 */
public final class CodeTemplates {

    private CodeTemplates() {
    }

    /** 模块名（连字符）→ REST 路径段。 */
    private static String pathSegment(String moduleName) {
        return moduleName.trim();
    }

    /** 模块名（连字符）→ Java 包级片段（点分）。 */
    private static String packageSegment(String moduleName) {
        return moduleName.trim().replace('-', '.');
    }

    /** 全部生成文件（顺序即预览 Tab 顺序）。 */
    public static List<GeneratedFileVO> renderAll(TableMetaVO meta, GenOption option, CodeGenProperties props) {
        String author = (option.author() == null || option.author().isBlank()) ? props.getAuthor() : option.author();
        String functionName = (option.functionName() == null || option.functionName().isBlank())
                ? (meta.comment() == null || meta.comment().isBlank() ? meta.className() : meta.comment())
                : option.functionName();
        Ctx ctx = new Ctx(meta, option, props, author, functionName);
        String cls = ctx.cls;

        List<GeneratedFileVO> files = new ArrayList<>();
        files.add(javaFile(ctx, cls + "DO", doSource(ctx), ctx.basePkg()));
        files.add(javaFile(ctx, cls + "VO", voSource(ctx), ctx.basePkg() + ".dto"));
        files.add(javaFile(ctx, cls + "Query", querySource(ctx), ctx.basePkg() + ".dto"));
        files.add(javaFile(ctx, cls + "Controller", controllerSource(ctx), ctx.basePkg()));
        files.add(javaFile(ctx, cls + "Service", serviceSource(ctx), ctx.basePkg()));
        files.add(javaFile(ctx, cls + "ServiceImpl", serviceImplSource(ctx), ctx.basePkg()));
        if (option.withFrontend()) {
            files.add(vueFile(ctx));
            files.add(jsFile(ctx));
        }
        return files;
    }

    // ------------------------------------------------------------------
    // 渲染上下文
    // ------------------------------------------------------------------

    /** 模板共享上下文（派生命名一次算清）。 */
    private static final class Ctx {
        final TableMetaVO meta;
        final GenOption option;
        final CodeGenProperties props;
        final String author;
        final String functionName;
        /** 类名（如 User）。 */
        final String cls;
        /** 实例名（如 user）。 */
        final String var;
        /** 模块包级片段（如 user / order.item）。 */
        final String pkgSeg;
        /** REST 路径段（如 user / order-item）。 */
        final String pathSeg;
        /** 主键字段（无主键时 null）。 */
        final ColumnMetaVO pk;
        /** 非主键字段。 */
        final List<ColumnMetaVO> fields;
        /** 权限码前缀（如 user）。 */
        final String perm;

        Ctx(TableMetaVO meta, GenOption option, CodeGenProperties props, String author, String functionName) {
            this.meta = meta;
            this.option = option;
            this.props = props;
            this.author = author;
            this.functionName = functionName;
            this.cls = (option.className() == null || option.className().isBlank())
                    ? meta.className() : option.className().trim();
            this.var = Character.toLowerCase(cls.charAt(0)) + cls.substring(1);
            this.pkgSeg = packageSegment(option.moduleName());
            this.pathSeg = pathSegment(option.moduleName());
            this.perm = pkgSeg.replace('.', ':');
            this.pk = meta.columns().stream().filter(ColumnMetaVO::primaryKey).findFirst().orElse(null);
            this.fields = meta.columns().stream().filter(c -> !c.primaryKey()).collect(Collectors.toList());
        }

        String basePkg() {
            return props.getBasePackage() + "." + pkgSeg;
        }

        String tableRef() {
            return "JooqTables." + toUpperSnake(meta.tableName());
        }

        String pkJavaType() {
            return pk == null ? "Long" : pk.javaType();
        }
    }

    // ------------------------------------------------------------------
    // 后端：DO
    // ------------------------------------------------------------------

    private static String doSource(Ctx c) {
        Set<String> imports = new LinkedHashSet<>();
        imports.add("lombok.Data");
        for (ColumnMetaVO col : c.meta.columns()) {
            if (JavaTypeMapper.needsImport(col.javaType())) {
                imports.add(importOf(col.javaType()));
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(header(c, "数据实体（对应 " + c.meta.tableName() + "）"));
        sb.append("import ").append(String.join(";\nimport ", imports)).append(";\n\n");
        sb.append("@Data\n");
        sb.append("public class ").append(c.cls).append("DO {\n\n");
        for (ColumnMetaVO col : c.meta.columns()) {
            sb.append("    /** ").append(commentOf(col)).append(" */\n");
            sb.append("    private ").append(col.javaType()).append(' ').append(col.javaField()).append(";\n\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 后端：VO
    // ------------------------------------------------------------------

    private static String voSource(Ctx c) {
        Set<String> imports = new LinkedHashSet<>();
        imports.add("com.fasterxml.jackson.databind.annotation.JsonSerialize");
        imports.add("com.fasterxml.jackson.databind.ser.std.ToStringSerializer");
        for (ColumnMetaVO col : c.meta.columns()) {
            if (JavaTypeMapper.needsImport(col.javaType())) {
                imports.add(importOf(col.javaType()));
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(header(c, "列表/详情视图"));
        sb.append("import ").append(String.join(";\nimport ", imports)).append(";\n\n");
        sb.append("public record ").append(c.cls).append("VO(\n");
        List<String> lines = new ArrayList<>();
        for (ColumnMetaVO col : c.meta.columns()) {
            String ann = (col == c.pk)
                    ? "    @JsonSerialize(using = ToStringSerializer.class)\n"
                    : "";
            lines.add(ann + "    " + col.javaType() + " " + col.javaField());
        }
        sb.append(String.join(",\n", lines)).append("\n) {\n}\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 后端：Query
    // ------------------------------------------------------------------

    private static String querySource(Ctx c) {
        ColumnMetaVO firstField = c.fields.isEmpty() ? null : c.fields.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(header(c, "分页查询入参（示例以首字段模糊匹配，其余字段按需扩充）"));
        sb.append("""
                public record %1$sQuery(%2$sint page, int size) {

                    public %1$sQuery {
                        page = Math.max(page, 1);
                        size = Math.min(Math.max(size, 1), 100);
                    }
                }
                """.formatted(
                c.cls,
                firstField == null ? "" : "String " + firstField.javaField() + ", "));
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 后端：Controller
    // ------------------------------------------------------------------

    private static String controllerSource(Ctx c) {
        String api = "/api/" + c.pathSeg;
        String permRead = c.perm + ":read";
        String permManage = c.perm + ":manage";
        return header(c, "接口")
                + """
                import com.acme.scaffold.common.api.PageResult;
                import com.acme.scaffold.common.api.Result;
                import %1$s.dto.%2$sQuery;
                import %1$s.dto.%2$sVO;
                import io.swagger.v3.oas.annotations.Operation;
                import io.swagger.v3.oas.annotations.tags.Tag;
                import lombok.RequiredArgsConstructor;
                import org.springframework.security.access.prepost.PreAuthorize;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PathVariable;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.PutMapping;
                import org.springframework.web.bind.annotation.DeleteMapping;
                import org.springframework.web.bind.annotation.RequestBody;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                import java.util.List;

                @Tag(name = "%3$s")
                @RestController
                @RequestMapping("%4$s")
                @RequiredArgsConstructor
                public class %2$sController {

                    private final %2$sService %5$sService;

                    @Operation(summary = "分页查询")
                    @GetMapping
                    @PreAuthorize("hasAuthority('%6$s')")
                    public Result<PageResult<%2$sVO>> page(%2$sQuery query) {
                        return Result.success(%5$sService.page(query));
                    }

                    @Operation(summary = "详情")
                    @GetMapping("/{id}")
                    @PreAuthorize("hasAuthority('%6$s')")
                    public Result<%2$sVO> detail(@PathVariable %7$s id) {
                        return Result.success(%5$sService.detail(id));
                    }

                    @Operation(summary = "新增")
                    @PostMapping
                    @PreAuthorize("hasAuthority('%8$s')")
                    public Result<%7$s> create(@RequestBody %2$sDO %5$s) {
                        return Result.success(%5$sService.create(%5$s));
                    }

                    @Operation(summary = "修改")
                    @PutMapping("/{id}")
                    @PreAuthorize("hasAuthority('%8$s')")
                    public Result<Void> update(@PathVariable %7$s id, @RequestBody %2$sDO %5$s) {
                        %5$sService.update(id, %5$s);
                        return Result.success();
                    }

                    @Operation(summary = "删除")
                    @DeleteMapping("/{id}")
                    @PreAuthorize("hasAuthority('%8$s')")
                    public Result<Void> delete(@PathVariable %7$s id) {
                        %5$sService.delete(id);
                        return Result.success();
                    }

                    @Operation(summary = "全部列表（下拉选项）")
                    @GetMapping("/all")
                    @PreAuthorize("hasAuthority('%6$s')")
                    public Result<List<%2$sVO>> all() {
                        return Result.success(%5$sService.all());
                    }
                }
                """.formatted(c.basePkg(), c.cls, c.functionName, api, c.var, permRead, c.pkJavaType(), permManage);
    }

    // ------------------------------------------------------------------
    // 后端：Service 接口
    // ------------------------------------------------------------------

    private static String serviceSource(Ctx c) {
        return header(c, "服务接口")
                + """
                import com.acme.scaffold.common.api.PageResult;
                import %1$s.dto.%2$sQuery;
                import %1$s.dto.%2$sVO;

                import java.util.List;

                public interface %2$sService {

                    PageResult<%2$sVO> page(%2$sQuery query);

                    %2$sVO detail(%3$s id);

                    %3$s create(%2$sDO %4$s);

                    void update(%3$s id, %2$sDO %4$s);

                    void delete(%3$s id);

                    List<%2$sVO> all();
                }
                """.formatted(c.basePkg(), c.cls, c.pkJavaType(), c.var);
    }

    // ------------------------------------------------------------------
    // 后端：ServiceImpl
    // ------------------------------------------------------------------

    private static String serviceImplSource(Ctx c) {
        return header(c, "服务实现（CRUD 骨架；业务校验以 TODO 标注，按需补齐）")
                + """
                import com.acme.scaffold.common.IdGenerator;
                import com.acme.scaffold.common.api.PageQuery;
                import com.acme.scaffold.common.api.PageResult;
                import com.acme.scaffold.common.error.CommonErrorCode;
                import com.acme.scaffold.common.exception.BusinessException;
                import com.acme.scaffold.jooq.JooqTables;
                import com.acme.scaffold.jooq.JooqWriters;
                import %1$s.dto.%2$sQuery;
                import %1$s.dto.%2$sVO;
                import lombok.RequiredArgsConstructor;
                import lombok.extern.slf4j.Slf4j;
                import org.jooq.Condition;
                import org.jooq.DSLContext;
                import org.springframework.stereotype.Service;

                import java.util.List;

                @Slf4j
                @Service
                @RequiredArgsConstructor
                public class %2$sServiceImpl implements %2$sService {

                    private final DSLContext dsl;

                    @Override
                    public PageResult<%2$sVO> page(%2$sQuery query) {
                        // TODO 按 query 字段拼装 Condition（模糊/精确），示例见既有 JobService#buildCondition
                        Condition cond = JooqWriters.notDeleted(%3$s);
                        PageQuery pq = PageQuery.of(query.page(), query.size());
                        PageResult<%2$sDO> raw = JooqWriters.page(dsl, %3$s, %2$sDO.class, cond, pq,
                                %3$s.field("id", Long.class).desc());
                        List<%2$sVO> records = raw.records().stream().map(this::toVO).toList();
                        return PageResult.of(raw.page(), raw.size(), raw.total(), records);
                    }

                    @Override
                    public %2$sVO detail(%4$s id) {
                        %2$sDO %5$s = JooqWriters.fetchById(dsl, %3$s, %2$sDO.class, id);
                        if (%5$s == null) {
                            throw new BusinessException(CommonErrorCode.NOT_FOUND, "记录不存在");
                        }
                        return toVO(%5$s);
                    }

                    @Override
                    public %4$s create(%2$sDO %5$s) {
                        // TODO 唯一性 / 业务校验（如编码重复）在此补齐
                        %5$s.setId(IdGenerator.nextId());
                        JooqWriters.insert(dsl, %3$s, %5$s);
                        return %5$s.getId();
                    }

                    @Override
                    public void update(%4$s id, %2$sDO %5$s) {
                        %5$s.setId(id);
                        JooqWriters.updateById(dsl, %3$s, id, %5$s);
                    }

                    @Override
                    public void delete(%4$s id) {
                        // 物理删除；如需逻辑删除将最后一个参数改 true
                        JooqWriters.delete(dsl, %3$s, id, false);
                    }

                    @Override
                    public List<%2$sVO> all() {
                        return JooqWriters.fetchList(dsl, %3$s, %2$sDO.class, JooqWriters.notDeleted(%3$s))
                                .stream().map(this::toVO).toList();
                    }

                    private %2$sVO toVO(%2$sDO %5$s) {
                        // TODO 字段映射（DO → VO）；枚举编码可在此转中文标签
                        throw new UnsupportedOperationException("待补齐 DO → VO 映射");
                    }
                }
                """.formatted(c.basePkg(), c.cls, c.tableRef(), c.pkJavaType(), c.var);
    }

    // ------------------------------------------------------------------
    // 前端：Vue 页面
    // ------------------------------------------------------------------

    private static GeneratedFileVO vueFile(Ctx c) {
        return new GeneratedFileVO(
                props_path(c) + "/views/" + c.pkgSeg.replace('.', '/') + "/" + c.cls + "View.vue",
                "VUE",
                """
                <!-- %1$s（表 %2$s）——由代码生成器生成，业务规则请手工补齐 -->
                <template>
                """.formatted(c.functionName, c.meta.tableName())
                        + vueTemplateBody(c));
    }

    /** Vue 模板主体（模板 + script + style），由 {@link #vueFile} 拼接生成声明头。 */
    private static String vueTemplateBody(Ctx c) {
        return """
                          <div>
                            <h2 class="page-title">%1$s</h2>

                            <!-- 筛选工具栏：条件区（上）/ 操作区（下）分区布局（全局 styles.css） -->
                            <el-card class="toolbar" shadow="never">
                              <div class="toolbar-filters">
                                <div class="filter-group">
                                  <span class="filter-label">关键字</span>
                                  <el-input
                                    v-model="query.keyword"
                                    placeholder="按名称搜索"
                                    clearable
                                    style="width: 220px"
                                    @keyup.enter="load"
                                  />
                                </div>
                              </div>
                              <div class="toolbar-actions">
                                <div class="action-buttons">
                                  <el-button type="primary" :icon="Search" @click="load">查询</el-button>
                                  <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
                                </div>
                                <div class="action-buttons">
                                  <el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button>
                                </div>
                              </div>
                            </el-card>

                            <el-card class="mt-12" shadow="never">
                              <el-table :data="rows" v-loading="loading" border stripe>
                %2$s
                                <el-table-column label="操作" width="160" fixed="right">
                                  <template #default="{ row }">
                                    <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
                                    <el-popconfirm title="确认删除该记录？" @confirm="onDelete(row)">
                                      <template #reference>
                                        <el-button link type="danger" :icon="Delete" @click.stop>删除</el-button>
                                      </template>
                                    </el-popconfirm>
                                  </template>
                                </el-table-column>
                              </el-table>
                              <el-pagination
                                class="pager"
                                v-model:current-page="query.page"
                                v-model:page-size="query.size"
                                :total="total"
                                :page-sizes="[10, 20, 50]"
                                layout="total, sizes, prev, pager, next"
                                @current-change="load"
                                @size-change="load"
                              />
                            </el-card>

                            <!-- 新增 / 编辑对话框 -->
                            <el-dialog v-model="dialogVisible" :title="editingId ? '编辑' : '新增'" width="560px">
                              <el-form :model="form" label-width="90px">
                %3$s
                              </el-form>
                              <template #footer>
                                <el-button @click="dialogVisible = false">取消</el-button>
                                <el-button type="primary" :loading="saving" @click="onSubmit">确定</el-button>
                              </template>
                            </el-dialog>
                          </div>
                        </template>

                        <script setup>
                        import { ref, reactive, onMounted } from 'vue'
                        import { Search, RefreshLeft, Plus, Edit, Delete } from '@element-plus/icons-vue'
                        import { ElMessage } from 'element-plus'
                        import { %4$sPage, %4$sDetail, %4$sCreate, %4$sUpdate, %4$sRemove } from '@/api/%5$s'

                        const rows = ref([])
                        const total = ref(0)
                        const loading = ref(false)
                        const saving = ref(false)
                        const dialogVisible = ref(false)
                        const editingId = ref(null)

                        const query = reactive({ keyword: '', page: 1, size: 10 })
                        const form = reactive({})
                        const emptyForm = () => ({})
                        const resetForm = () => Object.assign(form, emptyForm())

                        async function load() {
                          loading.value = true
                          try {
                            const data = await %4$sPage(query)
                            rows.value = data.records || []
                            total.value = data.total || 0
                          } catch (e) {
                            // 响应拦截器已统一提示错误
                          } finally {
                            loading.value = false
                          }
                        }

                        function resetQuery() {
                          query.keyword = ''
                          query.page = 1
                          load()
                        }

                        function openCreate() {
                          resetForm()
                          editingId.value = null
                          dialogVisible.value = true
                        }

                        async function openEdit(row) {
                          resetForm()
                          try {
                            const detail = await %4$sDetail(row.id)
                            Object.assign(form, detail)
                            editingId.value = row.id
                            dialogVisible.value = true
                          } catch (e) { /* 已提示 */ }
                        }

                        async function onSubmit() {
                          saving.value = true
                          try {
                            if (editingId.value) {
                              await %4$sUpdate(editingId.value, form)
                            } else {
                              await %4$sCreate(form)
                            }
                            ElMessage.success('保存成功')
                            dialogVisible.value = false
                            load()
                          } catch (e) { /* 已提示 */ } finally {
                            saving.value = false
                          }
                        }

                        async function onDelete(row) {
                          try {
                            await %4$sRemove(row.id)
                            ElMessage.success('删除成功')
                            load()
                          } catch (e) { /* 已提示 */ }
                        }

                        onMounted(load)
                        </script>

                        <style scoped>
                        .page-title { margin: 0 0 12px; font-size: 20px; font-weight: 600; }
                        .pager { margin-top: 12px; justify-content: flex-end; }
                        </style>
                """.formatted(
                        c.functionName,
                        tableColumns(c),
                        formItems(c),
                        c.var,
                        c.option.moduleName().trim());
    }

    /** 表格列（取前 6 个非主键字段 + 主键列）。 */
    private static String tableColumns(Ctx c) {
        StringBuilder sb = new StringBuilder();
        if (c.pk != null) {
            sb.append("""
                                    <el-table-column prop="%1$s" label="%2$s" width="90" />
                    """.formatted(c.pk.javaField(), commentOf(c.pk)));
        }
        c.fields.stream().limit(6).forEach(col -> sb.append("""
                                    <el-table-column prop="%1$s" label="%2$s" min-width="130" show-overflow-tooltip />
                """.formatted(col.javaField(), commentOf(col))));
        return sb.toString();
    }

    /** 表单项（取前 8 个非自增字段）。 */
    private static String formItems(Ctx c) {
        StringBuilder sb = new StringBuilder();
        c.fields.stream()
                .filter(col -> !col.autoIncrement())
                .limit(8)
                .forEach(col -> sb.append("""
                                        <el-form-item label="%1$s">
                                          <el-input v-model="form.%2$s" placeholder="请输入%1$s" />
                                        </el-form-item>
                    """.formatted(commentOf(col), col.javaField())));
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 前端：api.js
    // ------------------------------------------------------------------

    private static GeneratedFileVO jsFile(Ctx c) {
        String var = c.var;
        String module = c.option.moduleName().trim();
        return new GeneratedFileVO(
                props_path(c) + "/api/" + module + ".js",
                "JS",
                """
                import { request } from './http'

                // %1$s（%2$s）：CRUD 接口封装

                // GET /api/%3$s -> PageResult<%4$sVO>
                export const %4$sPage = (params) =>
                  request({ method: 'GET', url: '/api/%3$s', params })

                // GET /api/%4$s/all -> %4$sVO[]
                export const %4$sAll = () =>
                  request({ method: 'GET', url: '/api/%3$s/all' })

                // GET /api/%3$s/{id} -> %4$sVO
                export const %4$sDetail = (id) =>
                  request({ method: 'GET', url: '/api/%3$s/' + id })

                // POST /api/%3$s
                export const %4$sCreate = (data) =>
                  request({ method: 'POST', url: '/api/%3$s', data })

                // PUT /api/%3$s/{id}
                export const %4$sUpdate = (id, data) =>
                  request({ method: 'PUT', url: '/api/%3$s/' + id, data })

                // DELETE /api/%3$s/{id}
                export const %4$sRemove = (id) =>
                  request({ method: 'DELETE', url: '/api/%3$s/' + id })
                """.formatted(c.functionName, c.meta.tableName(), c.pathSeg, var));
    }

    // ------------------------------------------------------------------
    // 公共
    // ------------------------------------------------------------------

    /** Java 文件载体：包声明 + 源码体（文件头注释由各模板自带，避免重复）。 */
    private static GeneratedFileVO javaFile(Ctx c, String fileName, String body, String pkg) {
        String src = "package " + pkg + ";\n\n" + body;
        return new GeneratedFileVO(
                "src/main/java/" + pkg.replace('.', '/') + "/" + fileName + ".java",
                "JAVA",
                src);
    }

    /** 文件头注释（作者 + 表名 + 生成声明）。 */
    private static String header(Ctx c, String role) {
        String rolePart = role == null ? "" : "：" + role;
        return "/**\n"
                + " * " + c.cls + rolePart + "\n"
                + " *\n"
                + " * <p>由代码生成器基于表 {@code " + c.meta.tableName() + "} 生成"
                + (c.meta.comment() == null || c.meta.comment().isBlank() ? "" : "（" + c.meta.comment() + "）")
                + "，作者 " + c.author + "。生成代码为骨架，业务规则请手工补齐。</p>\n"
                + " */\n";
    }

    private static String props_path(Ctx c) {
        return c.props.getWebPath();
    }

    private static String importOf(String javaType) {
        return switch (javaType) {
            case "BigDecimal" -> "java.math.BigDecimal";
            case "LocalDate" -> "java.time.LocalDate";
            case "LocalTime" -> "java.time.LocalTime";
            case "LocalDateTime" -> "java.time.LocalDateTime";
            default -> throw new IllegalArgumentException("无 import 映射: " + javaType);
        };
    }

    private static String commentOf(ColumnMetaVO col) {
        return (col.comment() == null || col.comment().isBlank()) ? col.columnName() : col.comment();
    }

    private static String toUpperSnake(String tableName) {
        return tableName.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
