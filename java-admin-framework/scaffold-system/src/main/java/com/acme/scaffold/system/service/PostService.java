package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.AssignUserPostsRequest;
import com.acme.scaffold.system.dto.CreatePostRequest;
import com.acme.scaffold.system.dto.PostOptionVO;
import com.acme.scaffold.system.dto.PostQuery;
import com.acme.scaffold.system.dto.PostView;
import com.acme.scaffold.system.dto.UpdatePostRequest;
import com.acme.scaffold.system.entity.SysPostDO;
import com.acme.scaffold.system.entity.SysUserPostDO;
import com.acme.scaffold.system.rules.PostRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位管理服务：岗位 CRUD、下拉选项、用户岗位分配。
 *
 * <p><b>与用户模块的集成</b>：用户创建/编辑时提交 postIds 由 UserService 整体替换；
 * 本服务提供独立的「用户岗位分配」端点（PUT /api/system/posts/users/{userId}），
 * 供岗位管理页对存量用户补充分配。两处共用
 * {@link #replaceUserPosts(Long, List)} 保证语义一致。
 *
 * <p><b>数据访问层</b>：本服务采用手写动态 DSL（{@code JooqTables.SYS_POST} /
 * {@code JooqTables.SYS_USER_POST} 经 {@link JooqTables.TableRef} 桥接）调用
 * {@link JooqWriters}/{@link JooqSorts}，由动态 DSL 统一处理自动填充、逻辑删除、
 * 租户隔离与白名单排序。条件字段使用 {@link JooqTables.TableRef#field(String, Class)}
 * 的类型化构造（如 {@code JooqTables.SYS_POST.field("post_code", String.class)}），
 * 获得运行时列名校验及与上述语义的一致保障。
 *
 * <p><b>删除语义</b>：岗位为逻辑删除；同时物理清理 sys_user_post 关联行，
 * 避免已删除岗位的悬挂关联（该表无 deleted 列，与 sys_user_org 处理一致）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private static final String ACTIVE = "ACTIVE";

    /** 生成层类型安全表桥接为读写层 {@link JooqTables.TableRef}（物理表与条件字段同源，无别名歧义）。 */
    private static final JooqTables.TableRef POST = JooqTables.SYS_POST;
    private static final JooqTables.TableRef USER_POST = JooqTables.SYS_USER_POST;

    /** 岗位列表可排序字段白名单：客户端字段名 → 数据库列名（白名单外一律忽略）。 */
    private static final Map<String, String> POST_SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "postCode", "post_code",
            "postName", "post_name",
            "postSort", "post_sort",
            "status", "status",
            "createdAt", "created_at");

    private final DSLContext dsl;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 分页查询岗位。默认排序：post_sort 升序，其次 id 倒序（后创建的稳定岗位靠前）。 */
    public PageResult<PostView> list(PostQuery query) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(POST));

        if (StringUtils.hasText(query.keyword())) {
            String kw = "%" + query.keyword().trim() + "%";
            conds.add(DSL.or(
                    JooqTables.SYS_POST.field("post_code", String.class).like(kw),
                    JooqTables.SYS_POST.field("post_name", String.class).like(kw)));
        }
        if (StringUtils.hasText(query.status())) {
            conds.add(JooqTables.SYS_POST.field("status", String.class).eq(query.status().trim()));
        }

        var pageQuery = query.toPageQuery();
        PageResult<SysPostDO> page = JooqWriters.page(dsl, POST, SysPostDO.class, DSL.and(conds), pageQuery,
                JooqSorts.resolve(POST, pageQuery, POST_SORT_FIELDS,
                        JooqTables.SYS_POST.field("post_sort", Integer.class).asc(),
                        JooqTables.SYS_POST.field("id", Long.class).desc()));

        List<PostView> views = page.records().stream().map(PostView::from).toList();
        return new PageResult<>(page.page(), page.size(), page.total(), views);
    }

    /** 全部启用岗位（下拉选项）：按 post_sort 升序、id 升序稳定返回。 */
    public List<PostOptionVO> options() {
        List<SysPostDO> posts = JooqWriters.fetchList(dsl, POST, SysPostDO.class,
                DSL.and(JooqWriters.notDeleted(POST), JooqTables.SYS_POST.field("status", String.class).eq(ACTIVE)),
                JooqTables.SYS_POST.field("post_sort", Integer.class).asc(),
                JooqTables.SYS_POST.field("id", Long.class).asc());
        return posts.stream()
                .map(p -> new PostOptionVO(p.getId(), p.getPostName()))
                .toList();
    }

    // ------------------------------------------------------------------
    // 写操作
    // ------------------------------------------------------------------

    /** 新建岗位。编码租户内唯一（仅比对未删除行）。 */
    @Transactional
    public Long create(CreatePostRequest request) {
        String code = PostRules.normalizeCode(request.postCode());
        if (!PostRules.isValidCode(code)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "岗位编码仅允许字母、数字、下划线、连字符");
        }
        String name = PostRules.normalizeName(request.postName());
        if (name == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "岗位名称不能为空");
        }
        assertCodeAbsent(code, null);

        SysPostDO post = new SysPostDO();
        post.setPostCode(code);
        post.setPostName(name);
        post.setPostSort(request.postSort() != null ? request.postSort() : 0);
        post.setStatus(ACTIVE);
        post.setRemark(request.remark());
        JooqWriters.insert(dsl, POST, post);
        return post.getId();
    }

    /** 编辑岗位；postCode 变更时重新校验唯一性。 */
    @Transactional
    public void update(Long id, UpdatePostRequest request) {
        SysPostDO exist = getDO(id);

        SysPostDO post = new SysPostDO();
        if (request.postCode() != null) {
            String code = PostRules.normalizeCode(request.postCode());
            if (!PostRules.isValidCode(code)) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "岗位编码仅允许字母、数字、下划线、连字符");
            }
            if (!code.equals(exist.getPostCode())) {
                assertCodeAbsent(code, id);
            }
            post.setPostCode(code);
        }
        if (request.postName() != null) {
            String name = PostRules.normalizeName(request.postName());
            if (name == null) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "岗位名称不能为空");
            }
            post.setPostName(name);
        }
        if (request.postSort() != null) {
            post.setPostSort(request.postSort());
        }
        if (request.status() != null) {
            post.setStatus(request.status().trim());
        }
        if (request.remark() != null) {
            post.setRemark(request.remark());
        }
        JooqWriters.updateById(dsl, POST, id, post);
    }

    /** 删除岗位（逻辑删除），并物理清理该岗位的全部用户关联。 */
    @Transactional
    public void delete(Long id) {
        getDO(id);
        JooqWriters.delete(dsl, POST, id, true);
        JooqWriters.deleteByColumn(dsl, USER_POST, "post_id", id);
    }

    // ------------------------------------------------------------------
    // 用户岗位分配
    // ------------------------------------------------------------------

    /**
     * 为指定用户分配岗位（整体替换）：提交集合即最终状态，空列表表示清空。
     * 全部岗位必须存在（不要求启用——停用岗位允许保留在历史分配中，由业务决定）。
     */
    @Transactional
    public void assignUserPosts(Long userId, AssignUserPostsRequest request) {
        replaceUserPosts(userId, request.postIds());
    }

    /**
     * 用户岗位整体替换（UserService 创建/编辑用户时共用）。
     * 归一（去重/限额）→ 存在性校验 → 物理清旧 → 逐条插新。
     */
    @Transactional
    public void replaceUserPosts(Long userId, List<Long> postIds) {
        List<Long> normalized = PostRules.normalizePostIds(postIds);
        if (!normalized.isEmpty()) {
            assertPostsExist(normalized);
        }
        JooqWriters.deleteByColumn(dsl, USER_POST, "user_id", userId);
        for (Long postId : normalized) {
            SysUserPostDO rel = new SysUserPostDO();
            rel.setUserId(userId);
            rel.setPostId(postId);
            JooqWriters.insert(dsl, USER_POST, rel);
        }
    }

    /** 物理清理指定用户的全部岗位关联（用户删除时由 UserService 调用）。 */
    @Transactional
    public void removeUserPosts(Long userId) {
        JooqWriters.deleteByColumn(dsl, USER_POST, "user_id", userId);
    }

    /**
     * 批量装载用户的岗位 ID → 名称映射（防 N+1：一次 IN 查关联 + 一次 IN 查岗位）。
     *
     * @return userId → (postId → postName)；无岗位的用户不出现在 Map 中
     */
    public Map<Long, Map<Long, String>> loadPostNamesByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserPostDO> rels = JooqWriters.fetchList(dsl, USER_POST, SysUserPostDO.class,
                JooqTables.SYS_USER_POST.field("user_id", Long.class).in(userIds));
        if (rels.isEmpty()) {
            return Map.of();
        }
        List<Long> postIds = rels.stream().map(SysUserPostDO::getPostId).distinct().toList();
        List<SysPostDO> posts = JooqWriters.fetchList(dsl, POST, SysPostDO.class,
                DSL.and(JooqWriters.notDeleted(POST), JooqTables.SYS_POST.field("id", Long.class).in(postIds)));
        Map<Long, String> postNameOf = new HashMap<>();
        for (SysPostDO post : posts) {
            postNameOf.put(post.getId(), post.getPostName());
        }
        Map<Long, Map<Long, String>> result = new HashMap<>();
        for (SysUserPostDO rel : rels) {
            String name = postNameOf.get(rel.getPostId());
            if (name != null) {
                result.computeIfAbsent(rel.getUserId(), k -> new LinkedHashMap<>())
                        .put(rel.getPostId(), name);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private SysPostDO getDO(Long id) {
        SysPostDO post = JooqWriters.fetchById(dsl, POST, SysPostDO.class, id);
        if (post == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "岗位不存在");
        }
        return post;
    }

    /** 编码唯一性校验（仅比对未删除行；excludeId 为编辑时排除自身）。 */
    private void assertCodeAbsent(String code, Long excludeId) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(POST));
        conds.add(JooqTables.SYS_POST.field("post_code", String.class).eq(code));
        if (excludeId != null) {
            conds.add(JooqTables.SYS_POST.field("id", Long.class).ne(excludeId));
        }
        long count = JooqWriters.count(dsl, POST, DSL.and(conds));
        if (count > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "岗位编码已存在");
        }
    }

    /** 批量存在性校验：数量不一致即存在无效/已删除岗位。 */
    private void assertPostsExist(List<Long> postIds) {
        List<SysPostDO> posts = JooqWriters.fetchList(dsl, POST, SysPostDO.class,
                DSL.and(JooqWriters.notDeleted(POST), JooqTables.SYS_POST.field("id", Long.class).in(postIds)));
        if (posts.size() != postIds.size()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "包含不存在或已删除的岗位");
        }
    }
}
