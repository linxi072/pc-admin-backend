package com.acme.scaffold.workbench.dashboard.application;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.SqlSorts;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.workbench.dashboard.domain.model.DashboardTemplateDO;
import com.acme.scaffold.workbench.dashboard.dto.DashboardTemplateVO;
import com.acme.scaffold.workbench.dashboard.dto.TemplateSaveRequest;
import com.acme.scaffold.workbench.dashboard.dto.TemplateUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.Field;
import com.acme.scaffold.mybatis.dsl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据看板模板服务：模板 CRUD、配置（layout_json 以 JSON 字符串存储并经 Jackson 校验）、
 * 发布态流转（DRAFT / PUBLISHED / OFFLINE）与浏览计数。
 *
 * <p>持久化经 {@link SqlWriters}（动态 DSL），租户隔离由框架统一裁决；
 * 布局配置以 TEXT 承载，由前端约定结构、后端经 Jackson 仅做合法性校验（不做语义约束）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardTemplateService {

    /** 列表可排序字段白名单：客户端字段名 → 数据库列名。 */
    private static final Map<String, String> TEMPLATE_SORT_FIELDS = SqlSorts.whitelist(
            "id", "id",
            "templateCode", "template_code",
            "templateName", "template_name",
            "status", "status",
            "viewCount", "view_count",
            "createdAt", "created_at");

    private static final SqlTables.TableRef T = SqlTables.DASHBOARD_TEMPLATE;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;

    /** 创建模板；默认进入 DRAFT 草稿态。 */
    @Transactional
    public Long create(TemplateSaveRequest req) {
        validateLayoutJson(req.getLayoutJson());
        DashboardTemplateDO entity = new DashboardTemplateDO();
        entity.setTemplateCode(req.getTemplateCode());
        entity.setTemplateName(req.getTemplateName());
        entity.setDescription(req.getDescription());
        entity.setLayoutJson(req.getLayoutJson());
        entity.setStatus(req.getStatus() == null ? "DRAFT" : req.getStatus());
        entity.setViewCount(0L);
        entity.setCreatedBy(currentOperator());
        entity.setUpdatedBy(currentOperator());
        SqlWriters.insert(dsl, T, entity);
        return entity.getId();
    }

    /** 更新模板（按 id 选择性更新，null 字段不覆盖）。 */
    @Transactional
    public void update(TemplateUpdateRequest req) {
        DashboardTemplateDO existing = requireExisting(req.getId());
        validateLayoutJson(req.getLayoutJson());
        existing.setTemplateCode(req.getTemplateCode());
        existing.setTemplateName(req.getTemplateName());
        existing.setDescription(req.getDescription());
        if (req.getLayoutJson() != null) {
            existing.setLayoutJson(req.getLayoutJson());
        }
        if (req.getStatus() != null) {
            existing.setStatus(req.getStatus());
        }
        existing.setUpdatedBy(currentOperator());
        SqlWriters.updateById(dsl, T, existing.getId(), existing);
    }

    /**
     * 发布模板：DRAFT / OFFLINE → PUBLISHED。
     */
    @Transactional
    public void publish(Long id) {
        DashboardTemplateDO existing = requireExisting(id);
        existing.setStatus("PUBLISHED");
        existing.setUpdatedBy(currentOperator());
        SqlWriters.updateById(dsl, T, existing.getId(), existing);
        log.info("看板模板已发布 templateId={} code={}", existing.getId(), existing.getTemplateCode());
    }

    /** 撤销发布：PUBLISHED → OFFLINE。 */
    @Transactional
    public void unpublish(Long id) {
        DashboardTemplateDO existing = requireExisting(id);
        existing.setStatus("OFFLINE");
        existing.setUpdatedBy(currentOperator());
        SqlWriters.updateById(dsl, T, existing.getId(), existing);
        log.info("看板模板已撤销发布 templateId={} code={}", existing.getId(), existing.getTemplateCode());
    }

    /** 浏览计数 +1（乐观自增，失败仅告警不阻塞）。 */
    @Transactional
    public void incrementViewCount(Long id) {
        Field<Long> vc = T.field("view_count", Long.class);
        try {
            dsl.update(T.table())
                    .set(vc, vc.plus(1))
                    .where(T.field("id", Long.class).eq(id))
                    .execute();
        } catch (RuntimeException e) {
            log.warn("看板模板浏览计数更新失败 templateId={}", id, e);
        }
    }

    /** 按 id 查询（含逻辑删除过滤），不存在抛 404。 */
    @Transactional(readOnly = true)
    public DashboardTemplateVO getById(Long id) {
        DashboardTemplateDO entity = SqlWriters.fetchById(dsl, T, DashboardTemplateDO.class, id);
        if (entity == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "看板模板不存在: " + id);
        }
        return toView(entity);
    }

    /** 删除模板（逻辑删除）。 */
    @Transactional
    public void delete(Long id) {
        requireExisting(id);
        SqlWriters.delete(dsl, T, id, true);
    }

    /** 分页查询（支持模板编码 / 名称关键字 / 状态筛选，逻辑删除自动排除）。 */
    @Transactional(readOnly = true)
    public PageResult<DashboardTemplateVO> page(int page, int size, String templateCode,
                                                String templateName, String status,
                                                String sortField, String sortDirection) {
        List<Condition> conds = new ArrayList<>();
        conds.add(SqlWriters.notDeleted(T));
        if (templateCode != null && !templateCode.isBlank()) {
            conds.add(T.field("template_code", String.class).eq(templateCode.trim()));
        }
        if (templateName != null && !templateName.isBlank()) {
            conds.add(T.field("template_name", String.class).like("%" + templateName.trim() + "%"));
        }
        if (status != null && !status.isBlank()) {
            conds.add(T.field("status", String.class).eq(status.trim()));
        }
        Condition cond = DSL.and(conds);
        PageQuery q = PageQuery.of(page, size, sortField, sortDirection);
        var raw = SqlWriters.page(dsl, T, DashboardTemplateDO.class, cond, q,
                SqlSorts.resolve(T, q, TEMPLATE_SORT_FIELDS,
                        T.field("created_at", LocalDateTime.class).desc(),
                        T.field("id", Long.class).desc()));
        List<DashboardTemplateVO> views = raw.records().stream().map(this::toView).toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    // ---------------- 内部辅助 ----------------

    private DashboardTemplateDO requireExisting(Long id) {
        DashboardTemplateDO entity = SqlWriters.fetchById(dsl, T, DashboardTemplateDO.class, id);
        if (entity == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "看板模板不存在: " + id);
        }
        return entity;
    }

    /** 若传入 layout_json，则校验其为合法 JSON（语义交由前端约定，后端仅做语法校验）。 */
    private void validateLayoutJson(String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return;
        }
        try {
            OBJECT_MAPPER.readTree(layoutJson);
        } catch (Exception e) {
            throw BusinessException.badRequest("layout_json 不是合法 JSON: " + e.getMessage());
        }
    }

    private DashboardTemplateVO toView(DashboardTemplateDO e) {
        return new DashboardTemplateVO(e.getId(), e.getTemplateCode(), e.getTemplateName(),
                e.getDescription(), e.getLayoutJson(), e.getStatus(), e.getViewCount(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private String currentOperator() {
        return securityContextFacade.getCurrentPrincipal()
                .map(p -> {
                    CurrentPrincipal cp = p;
                    return cp.displayName() != null ? cp.displayName() : cp.username();
                })
                .orElse("system");
    }
}
