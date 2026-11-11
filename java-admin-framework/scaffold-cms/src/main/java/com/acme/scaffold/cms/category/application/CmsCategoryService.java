package com.acme.scaffold.cms.category.application;

import com.acme.scaffold.cms.category.domain.model.CmsCategoryDO;
import com.acme.scaffold.cms.category.dto.CategoryCreateRequest;
import com.acme.scaffold.cms.category.dto.CategoryNode;
import com.acme.scaffold.cms.category.dto.CategoryUpdateRequest;
import com.acme.scaffold.cms.category.dto.CategoryView;
import com.acme.scaffold.cms.support.CmsStatus;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlSorts;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 栏目服务：树形栏目（自引用 parent_id）的 CRUD 与树构建。
 *
 * <p>设计要点：
 * <ul>
 *   <li>持久化经 {@link SqlWriters}（动态 DSL），租户隔离由 {@link TenantEnforcement} 统一裁决；</li>
 *   <li>栏目编码租户内唯一，新建/改名均做冲突校验；</li>
 *   <li>删除前校验「无子栏目」，避免悬空父引用；</li>
 *   <li>树构建为纯函数（{@link #buildTree}），与 DB 解耦，便于单元测试。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsCategoryService {

    /** 列表/树可排序字段白名单：客户端字段名 → 数据库列名。 */
    private static final Map<String, String> CATEGORY_SORT_FIELDS = SqlSorts.whitelist(
            "id", "id",
            "categoryName", "category_name",
            "sortNo", "sort_no",
            "createdAt", "created_at");

    private static final SqlTables.TableRef CATEGORY = SqlTables.CMS_CATEGORY;

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;

    /** 创建栏目；编码租户内唯一。 */
    @Transactional
    public Long create(CategoryCreateRequest req) {
        if (codeExists(req.getCategoryCode(), null)) {
            throw BusinessException.of(CommonErrorCode.CONFLICT, "栏目编码已存在: " + req.getCategoryCode());
        }
        if (req.getParentId() != null && req.getParentId() > 0 && !exists(req.getParentId())) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "父栏目不存在: " + req.getParentId());
        }
        CmsCategoryDO entity = new CmsCategoryDO();
        entity.setCategoryCode(req.getCategoryCode());
        entity.setCategoryName(req.getCategoryName());
        entity.setDescription(req.getDescription());
        entity.setParentId(normalizeParentId(req.getParentId()));
        entity.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        entity.setStatus(req.getStatus() == null ? CmsStatus.CATEGORY_ACTIVE : req.getStatus());
        entity.setCreatedBy(currentOperator());
        entity.setUpdatedBy(currentOperator());
        SqlWriters.insert(dsl, CATEGORY, entity);
        return entity.getId();
    }

    /** 更新栏目；编码冲突校验排除自身。 */
    @Transactional
    public void update(CategoryUpdateRequest req) {
        CmsCategoryDO existing = requireExisting(req.getId());
        if (req.getParentId() != null && req.getParentId() > 0 && !exists(req.getParentId())) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "父栏目不存在: " + req.getParentId());
        }
        if (codeExists(req.getCategoryCode(), req.getId())) {
            throw BusinessException.of(CommonErrorCode.CONFLICT, "栏目编码已存在: " + req.getCategoryCode());
        }
        existing.setCategoryCode(req.getCategoryCode());
        existing.setCategoryName(req.getCategoryName());
        existing.setDescription(req.getDescription());
        existing.setParentId(normalizeParentId(req.getParentId()));
        existing.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        existing.setStatus(req.getStatus() == null ? CmsStatus.CATEGORY_ACTIVE : req.getStatus());
        existing.setUpdatedBy(currentOperator());
        SqlWriters.updateById(dsl, CATEGORY, existing.getId(), existing);
    }

    /**
     * 删除栏目（逻辑删除）。存在子栏目时拒绝，避免悬空父引用。
     */
    @Transactional
    public void delete(Long id) {
        requireExisting(id);
        long childCount = SqlWriters.count(dsl, CATEGORY, DSL.and(
                SqlWriters.notDeleted(CATEGORY),
                CATEGORY.field("parent_id", Long.class).eq(id)));
        if (childCount > 0) {
            throw BusinessException.of(CommonErrorCode.CONFLICT, "栏目下存在 " + childCount + " 个子栏目，无法删除");
        }
        SqlWriters.delete(dsl, CATEGORY, id, true);
    }

    /** 按 id 查询单条（含逻辑删除过滤），不存在抛 404。 */
    @Transactional(readOnly = true)
    public CategoryView getById(Long id) {
        CmsCategoryDO entity = SqlWriters.fetchById(dsl, CATEGORY, CmsCategoryDO.class, id);
        if (entity == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "栏目不存在: " + id);
        }
        return toView(entity);
    }

    /** 扁平列表（按 sort_no、category_name 升序）。 */
    @Transactional(readOnly = true)
    public List<CategoryView> list() {
        List<CmsCategoryDO> entities = SqlWriters.fetchList(dsl, CATEGORY, CmsCategoryDO.class,
                SqlWriters.notDeleted(CATEGORY),
                CATEGORY.field("sort_no", Integer.class).asc(),
                CATEGORY.field("category_name", String.class).asc());
        return entities.stream().map(this::toView).toList();
    }

    /**
     * 栏目树（根节点 parent_id 为 null 或 0）。子节点按 sort_no、category_name 升序。
     */
    @Transactional(readOnly = true)
    public List<CategoryNode> tree() {
        List<CategoryView> all = list();
        return buildTree(all);
    }

    /**
     * 纯函数：由扁平栏目视图构建树。与持久层解耦，便于独立测试。
     *
     * @param flat 扁平栏目列表（任意顺序）
     * @return 根节点集合（按 sort_no、category_name 升序）
     */
    public static List<CategoryNode> buildTree(List<CategoryView> flat) {
        Map<Long, CategoryNode> nodes = new LinkedHashMap<>();
        for (CategoryView v : flat) {
            nodes.put(v.getId(), new CategoryNode(v.getId(), v.getCategoryCode(),
                    v.getCategoryName(), v.getSortNo(), v.getStatus()));
        }
        List<CategoryNode> roots = new ArrayList<>();
        for (CategoryView v : flat) {
            CategoryNode node = nodes.get(v.getId());
            Long pid = v.getParentId();
            if (pid == null || pid == 0L || !nodes.containsKey(pid)) {
                roots.add(node);
            } else {
                nodes.get(pid).getChildren().add(node);
            }
        }
        Comparator<CategoryNode> bySort = Comparator.comparing(
                (CategoryNode n) -> n.getSortNo() == null ? 0 : n.getSortNo(), Integer::compareTo)
                .thenComparing((CategoryNode n) -> n.getCategoryName() == null ? "" : n.getCategoryName());
        sortRecursive(roots, bySort);
        return roots;
    }

    private static void sortRecursive(List<CategoryNode> nodes, Comparator<CategoryNode> cmp) {
        nodes.sort(cmp);
        for (CategoryNode n : nodes) {
            sortRecursive(n.getChildren(), cmp);
        }
    }

    // ---------------- 内部辅助 ----------------

    private CmsCategoryDO requireExisting(Long id) {
        CmsCategoryDO entity = SqlWriters.fetchById(dsl, CATEGORY, CmsCategoryDO.class, id);
        if (entity == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "栏目不存在: " + id);
        }
        return entity;
    }

    private boolean exists(Long id) {
        return SqlWriters.fetchById(dsl, CATEGORY, CmsCategoryDO.class, id) != null;
    }

    private boolean codeExists(String code, Long excludeId) {
        Condition cond = DSL.and(SqlWriters.notDeleted(CATEGORY),
                CATEGORY.field("category_code", String.class).eq(code));
        if (excludeId != null) {
            cond = cond.and(CATEGORY.field("id", Long.class).ne(excludeId));
        }
        return SqlWriters.fetchOne(dsl, CATEGORY, CmsCategoryDO.class, cond) != null;
    }

    private Long normalizeParentId(Long parentId) {
        return (parentId == null || parentId == 0L) ? null : parentId;
    }

    private CategoryView toView(CmsCategoryDO e) {
        return new CategoryView(e.getId(), e.getParentId(), e.getCategoryCode(), e.getCategoryName(),
                e.getDescription(), e.getSortNo(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
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
