package com.acme.scaffold.system.form;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.form.domain.FormSubmissionDO;
import com.acme.scaffold.system.form.dto.FormDefinitionView;
import com.acme.scaffold.system.form.dto.FormSubmissionAdminQuery;
import com.acme.scaffold.system.form.dto.FormSubmissionFormCount;
import com.acme.scaffold.system.form.dto.FormSubmissionManageView;
import com.acme.scaffold.system.form.dto.FormSubmissionStat;
import com.acme.scaffold.system.form.dto.FormSubmissionView;
import com.acme.scaffold.system.form.rules.FormSubmissionRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 表单提交应用服务（V53 / M22 正式立项）。
 *
 * <p><b>落库目标</b>：动态表单提交值经 schema 校验后写入独立 {@code sys_form_submission} 表，
 * 不侵入业务表、不做运行时 DDL（承接 V47 spike 的 {@code FORM_SUBMISSION} 决策点）。</p>
 *
 * <p><b>提交校验链路</b>：{@link FormDefinitionService#getEnabledByCode} 取启用中定义（停用/不存在抛 NOT_FOUND）
 * → {@link FormSubmissionRules#validateSubmission} 按 schema 字段校验（必填/类型/长度/选项白名单/未知字段拒绝）
 * → 校验通过才 {@link JooqWriters#insert} 落库；任一项校验失败整体拒绝、不落库。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormSubmissionService {

    private final DSLContext dsl;
    private final FormDefinitionService definitionService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 单页上限，防异常大翻页拖垮查询。 */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 提交表单（落库 FORM_SUBMISSION 目标）。
     *
     * @param formCode 表单编码（须为启用中定义）
     * @param values   提交值（字段键 → 值），非空且须通过 schema 校验
     * @param operator 提交人（当前登录用户名）
     * @return 落库后的提交记录视图（含自增 id 与提交时间）
     */
    public FormSubmissionView submit(String formCode, Map<String, Object> values, String operator) {
        FormDefinitionView def = definitionService.getEnabledByCode(formCode);
        List<String> errors = FormSubmissionRules.validateSubmission(def.schemaJson(), values);
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "表单校验未通过：" + String.join("；", errors));
        }
        FormSubmissionDO d = new FormSubmissionDO();
        d.setFormDefinitionId(def.id());
        d.setFormCode(def.formCode());
        d.setSubmittedBy(operator);
        try {
            d.setDataJson(MAPPER.writeValueAsString(values));
        } catch (Exception e) {
            log.error("表单提交数据序列化失败 formCode={}", formCode, e);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "提交数据序列化失败");
        }
        JooqWriters.insert(dsl, JooqTables.FORM_SUBMISSION, d);
        return FormSubmissionView.of(d);
    }

    /**
     * 表单提交记录分页查询（按提交时间倒序）。
     *
     * @param formCode 表单编码
     * @param page     页码（从 1 起，自动钳制）
     * @param size     每页条数（自动钳制 [1, MAX_PAGE_SIZE]）
     */
    public PageResult<FormSubmissionView> listByForm(String formCode, int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        PageQuery q = PageQuery.of(p, s);
        PageResult<FormSubmissionDO> raw = JooqWriters.page(dsl, JooqTables.FORM_SUBMISSION,
                FormSubmissionDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.FORM_SUBMISSION),
                        JooqTables.FORM_SUBMISSION.field("form_code", String.class).eq(formCode)),
                q,
                JooqTables.FORM_SUBMISSION.field("created_at", LocalDateTime.class).desc());
        return PageResult.of(raw.page(), raw.size(), raw.total(),
                raw.records().stream().map(FormSubmissionView::of).toList());
    }

    /**
     * 跨表单提交记录统一分页（M47 跨表单提交管理）。
     *
     * <p>在渲染面「按单表单查询」之上提供全局检索：可按表单编码精确过滤、提交人模糊匹配、
     * 提交值 JSON 关键词检索；排序字段经白名单映射（created_at / submitted_by / form_code），
     * 方向仅接受 ASC/DESC，杜绝客户端任意列名注入。表单名称通过编码映射回填，避免逐行联表。</p>
     *
     * @param q 查询入参（页码/每页大小已钳制）
     */
    public PageResult<FormSubmissionManageView> adminPage(FormSubmissionAdminQuery q) {
        int p = Math.max(1, q.page());
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, q.size()));
        PageQuery pq = PageQuery.of(p, s);
        Condition cond = buildAdminCondition(q);
        OrderField<?> order = resolveAdminSort(q.sortField(), q.sortDir());
        PageResult<FormSubmissionDO> raw = JooqWriters.page(dsl, JooqTables.FORM_SUBMISSION,
                FormSubmissionDO.class, cond, pq, order);
        Map<String, String> nameByCode = formNameByCode();
        List<FormSubmissionManageView> views = raw.records().stream()
                .map(d -> FormSubmissionManageView.of(d, nameByCode.getOrDefault(d.getFormCode(), d.getFormCode())))
                .toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    /**
     * 表单提交记录详情（M47 管理后台）。
     *
     * @param id 提交记录 ID
     * @return 管理视图（含表单名称），不存在抛 NOT_FOUND
     */
    public FormSubmissionManageView getManageById(Long id) {
        FormSubmissionDO d = JooqWriters.fetchById(dsl, JooqTables.FORM_SUBMISSION, FormSubmissionDO.class, id);
        if (d == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "表单提交记录不存在");
        }
        Map<String, String> nameByCode = formNameByCode();
        return FormSubmissionManageView.of(d, nameByCode.getOrDefault(d.getFormCode(), d.getFormCode()));
    }

    /**
     * 逻辑删除单条提交记录（M47 管理后台）。不存在抛 NOT_FOUND。
     *
     * @param id 提交记录 ID
     */
    public void removeSubmission(Long id) {
        FormSubmissionDO existing = JooqWriters.fetchById(dsl, JooqTables.FORM_SUBMISSION,
                FormSubmissionDO.class, id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "表单提交记录不存在");
        }
        JooqWriters.delete(dsl, JooqTables.FORM_SUBMISSION, id, true);
    }

    /**
     * 跨表单提交统计（M47 管理后台统计卡）。
     *
     * <p>返回全部未删除提交总数 + 按表单编码聚合的计数明细（按数量降序）。
     * 归并在 Java 端完成而非下推 SQL groupBy——提交数据为用户级、规模可控，
     * 且便于离线单测（沿用 {@code mockStatic(JooqWriters)} 范式，规避 jOOQ 链式聚合的脆弱打桩）；
     * 若后续表单提交量显著增长，可改为 raw DSL {@code groupBy}（与 {@code FileService#stats} 同范式）。</p>
     */
    public FormSubmissionStat stats() {
        Condition notDeleted = JooqWriters.notDeleted(JooqTables.FORM_SUBMISSION);
        long total = JooqWriters.count(dsl, JooqTables.FORM_SUBMISSION, notDeleted);
        List<FormSubmissionDO> rows = JooqWriters.fetchList(dsl, JooqTables.FORM_SUBMISSION,
                FormSubmissionDO.class, notDeleted);
        Map<String, Long> counts = rows.stream()
                .collect(Collectors.groupingBy(FormSubmissionDO::getFormCode, Collectors.counting()));
        Map<String, String> nameByCode = formNameByCode();
        List<FormSubmissionFormCount> byForm = counts.entrySet().stream()
                .map(e -> new FormSubmissionFormCount(e.getKey(),
                        nameByCode.getOrDefault(e.getKey(), e.getKey()), e.getValue()))
                .sorted(Comparator.comparingLong(FormSubmissionFormCount::count).reversed())
                .toList();
        return new FormSubmissionStat(total, byForm);
    }

    /**
     * 跨表单提交记录导出取数（M47 导出中心模板）。
     *
     * @param q 查询入参（仅取过滤/排序语义，不分页，全量导出）
     * @return 管理视图列表（含表单名称）
     */
    public List<FormSubmissionManageView> exportList(FormSubmissionAdminQuery q) {
        Condition cond = buildAdminCondition(q);
        OrderField<?> order = resolveAdminSort(q.sortField(), q.sortDir());
        List<FormSubmissionDO> rows = JooqWriters.fetchList(dsl, JooqTables.FORM_SUBMISSION,
                FormSubmissionDO.class, cond, order);
        Map<String, String> nameByCode = formNameByCode();
        return rows.stream()
                .map(d -> FormSubmissionManageView.of(d, nameByCode.getOrDefault(d.getFormCode(), d.getFormCode())))
                .toList();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /** 组装管理后台查询条件：逻辑删除 + 可选的表单编码/提交人/关键词过滤。 */
    private Condition buildAdminCondition(FormSubmissionAdminQuery q) {
        var t = JooqTables.FORM_SUBMISSION;
        Condition c = JooqWriters.notDeleted(t);
        if (q.formCode() != null) {
            c = c.and(t.field("form_code", String.class).eq(q.formCode()));
        }
        if (q.submittedBy() != null) {
            c = c.and(t.field("submitted_by", String.class).like("%" + q.submittedBy() + "%"));
        }
        if (q.keyword() != null) {
            c = c.and(t.field("data_json", String.class).like("%" + q.keyword() + "%"));
        }
        return c;
    }

    /** 排序字段/方向白名单映射，杜绝任意列名注入；非法字段回退 created_at，非法方向回退 DESC。 */
    private OrderField<?> resolveAdminSort(String field, String dir) {
        var t = JooqTables.FORM_SUBMISSION;
        Field<?> f = switch (field == null ? "" : field) {
            case "submitted_by" -> t.field("submitted_by", String.class);
            case "form_code" -> t.field("form_code", String.class);
            default -> t.field("created_at", LocalDateTime.class);
        };
        return "ASC".equalsIgnoreCase(dir) ? f.asc() : f.desc();
    }

    /** 编码 → 名称映射（基于当前全部表单定义，含停用；缺失编码回退为编码本身）。 */
    private Map<String, String> formNameByCode() {
        return definitionService.list().stream()
                .collect(Collectors.toMap(FormDefinitionView::formCode, FormDefinitionView::formName,
                        (a, b) -> a));
    }
}
