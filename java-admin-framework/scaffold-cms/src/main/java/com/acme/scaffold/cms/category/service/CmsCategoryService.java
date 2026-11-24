package com.acme.scaffold.cms.category.service;

import com.acme.scaffold.cms.category.domain.model.CmsCategory;
import com.acme.scaffold.cms.category.domain.CategoryCreateRequest;
import com.acme.scaffold.cms.category.domain.rule.CmsCategoryRules;
import com.acme.scaffold.cms.category.domain.CategoryNode;
import com.acme.scaffold.cms.category.domain.CategoryUpdateRequest;
import com.acme.scaffold.cms.category.domain.CategoryView;
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
import com.acme.scaffold.common.tree.TreeBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
        CmsCategory entity = new CmsCategory();
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
        CmsCategory existing = requireExisting(req.getId());
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
     * 删除栏目（逻辑删除）。存在子栏目或关联文章时拒绝（C2：对齐前端提示文案，避免悬空引用）。
     */
    @Transactional
    public void delete(Long id) {
        requireExisting(id);
        long childCount = SqlWriters.count(dsl, CATEGORY, DSL.and(
                SqlWriters.notDeleted(CATEGORY),
                CATEGORY.field("parent_id", Long.class).eq(id)));
        long articleCount = SqlWriters.count(dsl, SqlTables.CMS_ARTICLE, DSL.and(
                SqlWriters.notDeleted(SqlTables.CMS_ARTICLE),
                SqlTables.CMS_ARTICLE.field("category_id", Long.class).eq(id)));
        if (!CmsCategoryRules.canDelete(childCount, articleCount)) {
            throw BusinessException.of(CommonErrorCode.CONFLICT,
                    CmsCategoryRules.deleteRejectMessage(childCount, articleCount));
        }
        SqlWriters.delete(dsl, CATEGORY, id, true);
    }

    /** 按 id 查询单条（含逻辑删除过滤），不存在抛 404。 */
    @Transactional(readOnly = true)
    public CategoryView getById(Long id) {
        CmsCategory entity = SqlWriters.fetchById(dsl, CATEGORY, CmsCategory.class, id);
        if (entity == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "栏目不存在: " + id);
        }
        return toView(entity);
    }

    /** 扁平列表（按 sort_no、category_name 升序）。 */
    @Transactional(readOnly = true)
    public List<CategoryView> list() {
        List<CmsCategory> entities = SqlWriters.fetchList(dsl, CATEGORY, CmsCategory.class,
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
     * 纯函数：由扁平栏目视图构建树（复用 {@link TreeBuilder}，与菜单/机构树同源实现）。与持久层解耦，便于独立测试。
     *
     * <p>语义说明（与迁移前实现逐一对齐）：</p>
     * <ul>
     *   <li>根节点：{@code parent_id} 为 null / 0，或父节点在数据中不存在（脏数据孤儿升为根，不丢弃）；</li>
     *   <li>排序：输入列表先按 sort_no、category_name 升序排列，TreeBuilder 分组保序即得全树有序；</li>
     *   <li>容错：数据成环时路径守卫截断，不无限递归。</li>
     * </ul>
     *
     * @param flat 扁平栏目列表（任意顺序）
     * @return 根节点集合（按 sort_no、category_name 升序）
     */
    public static List<CategoryNode> buildTree(List<CategoryView> flat) {
        List<CategoryView> ordered = new ArrayList<>(flat);
        ordered.sort(Comparator
                .comparing((CategoryView v) -> v.getSortNo() == null ? 0 : v.getSortNo())
                .thenComparing(v -> v.getCategoryName() == null ? "" : v.getCategoryName()));
        Set<Long> ids = ordered.stream().map(CategoryView::getId).collect(Collectors.toSet());
        return TreeBuilder.build(ordered,
                CategoryView::getId,
                v -> v.getParentId() == null ? 0L : v.getParentId(),
                v -> {
                    Long pid = v.getParentId();
                    return pid == null || pid == 0L || !ids.contains(pid);
                },
                (v, children) -> {
                    CategoryNode node = new CategoryNode(v.getId(), v.getCategoryCode(),
                            v.getCategoryName(), v.getSortNo(), v.getStatus());
                    // TreeBuilder 传回不可变空列表，包一层可变列表保持 CategoryNode 既有契约
                    node.setChildren(new ArrayList<>(children));
                    return node;
                });
    }

    // ---------------- 内部辅助 ----------------

    private CmsCategory requireExisting(Long id) {
        CmsCategory entity = SqlWriters.fetchById(dsl, CATEGORY, CmsCategory.class, id);
        if (entity == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "栏目不存在: " + id);
        }
        return entity;
    }

    private boolean exists(Long id) {
        return SqlWriters.fetchById(dsl, CATEGORY, CmsCategory.class, id) != null;
    }

    private boolean codeExists(String code, Long excludeId) {
        Condition cond = DSL.and(SqlWriters.notDeleted(CATEGORY),
                CATEGORY.field("category_code", String.class).eq(code));
        if (excludeId != null) {
            cond = cond.and(CATEGORY.field("id", Long.class).ne(excludeId));
        }
        return SqlWriters.fetchOne(dsl, CATEGORY, CmsCategory.class, cond) != null;
    }

    private Long normalizeParentId(Long parentId) {
        return CmsCategoryRules.normalizeParentId(parentId);
    }

    private CategoryView toView(CmsCategory e) {
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
