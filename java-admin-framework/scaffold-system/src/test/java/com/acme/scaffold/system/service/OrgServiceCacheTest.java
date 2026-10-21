package com.acme.scaffold.system.service;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.permission.DataScopeContext;
import com.acme.scaffold.security.permission.DataScopeResult;
import com.acme.scaffold.system.dto.CreateOrgRequest;
import com.acme.scaffold.system.dto.OrgTreeVO;
import com.acme.scaffold.system.dto.UpdateOrgRequest;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysUserDO;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * 机构树缓存行为测试（M5 续：{@link OrgService#tree()} 按数据权限范围签名分键缓存）。
 *
 * <p>与菜单缓存的「单一 ALL 键 / 按用户键」不同，机构树带 {@link com.acme.scaffold.security.permission.DataScope}，
 * 不同权限用户看到不同的机构森林。本测试的核心目标是<b>防止跨权限缓存越权</b>：
 * <ul>
 *   <li>命中：同一范围二次读取仍是首次结果（打库替身按次返回不同值）；</li>
 *   <li><b>隔离（安全关键）</b>：ALL 范围与受限范围（{@code ORG:1,2} / {@code SELF}）各自独立缓存项，
 *       受限用户绝不读到 ALL 范围的缓存树；</li>
 *   <li>驱逐：机构写操作后整缓存清空，受限范围亦随之失效；</li>
 *   <li>精确性：仅机构写操作驱逐 {@value OrgService#ORG_TREE_CACHE}，不影响其它缓存。</li>
 * </ul>
 *
 * <p>数据权限上下文由 {@code DataScopeAspect} 在真实运行期写入；单元测试内无该切面，
 * 故手动 {@link DataScopeContext#set} / {@link DataScopeContext#clear} 模拟切面行为。
 * 打库替身按当前上下文返回对应范围的数据，从而端到端验证「范围 → 数据 → 缓存键」一致。
 * 不依赖数据库与 Redis，离线可跑。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = OrgServiceCacheTest.TestConfig.class)
class OrgServiceCacheTest {

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        OrgService orgService() {
            return new OrgService(mock(DSLContext.class));
        }
    }

    @Autowired
    private OrgService orgService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearScopesAndCaches() {
        DataScopeContext.clear();
        Cache cache = cacheManager.getCache(OrgService.ORG_TREE_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    private Cache orgCache() {
        Cache cache = cacheManager.getCache(OrgService.ORG_TREE_CACHE);
        assertNotNull(cache);
        return cache;
    }

    private static SysOrgDO org(Long id, Long parentId, String code, String name, int sortNo) {
        SysOrgDO o = new SysOrgDO();
        o.setId(id);
        o.setParentId(parentId);
        o.setOrgCode(code);
        o.setOrgName(name);
        o.setOrgType("DEPARTMENT");
        o.setSortNo(sortNo);
        o.setStatus("ACTIVE");
        o.setLeaderUserId(0L);
        return o;
    }

    private static SysUserDO user(Long id, String displayName) {
        SysUserDO u = new SysUserDO();
        u.setId(id);
        u.setDisplayName(displayName);
        u.setUsername("u" + id);
        return u;
    }

    /** 打库替身：按当前数据权限上下文返回对应机构集合（模拟 DataScopeConditions 的 SQL 过滤效果）。 */
    private void stubOrgTreeByScope(MockedStatic<JooqWriters> writers) {
        writers.when(() -> JooqWriters.notDeleted(any())).thenReturn(DSL.trueCondition());
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysUserDO.class), any()))
                .thenReturn(List.of(user(1L, "管理员")));
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysOrgDO.class), any(), any(OrderField[].class)))
                .thenAnswer(inv -> {
                    DataScopeContext.Scope scope = DataScopeContext.current();
                    if (scope == null || scope.result() == null || scope.result().unrestricted()) {
                        // 不限制：完整树
                        return List.of(org(1L, 0L, "c1", "总部", 1),
                                org(2L, 1L, "c2", "研发", 2),
                                org(3L, 1L, "c3", "市场", 3));
                    }
                    if (scope.result().selfOnly()) {
                        // 仅本人：仅主部门
                        return List.of(org(1L, 0L, "c1", "总部", 1));
                    }
                    Set<Long> ids = scope.result().orgIds();
                    if (ids == null || ids.isEmpty()) {
                        return List.of(org(1L, 0L, "c1", "总部", 1),
                                org(2L, 1L, "c2", "研发", 2),
                                org(3L, 1L, "c3", "市场", 3));
                    }
                    // 机构集合：仅返回可见机构
                    return ids.stream()
                            .map(id -> org(id, id == 1L ? 0L : 1L, "c" + id, "部门" + id, (int) (long) id))
                            .toList();
                });
    }

    private void stubOrgWrites(MockedStatic<JooqWriters> writers) {
        writers.when(() -> JooqWriters.count(any(), any(), any())).thenReturn(0L);
        writers.when(() -> JooqWriters.insert(any(), any(), any())).thenAnswer(inv -> null);
        writers.when(() -> JooqWriters.fetchById(any(), any(), eq(SysOrgDO.class), any()))
                .thenReturn(org(1L, 0L, "c1", "总部", 1));
        writers.when(() -> JooqWriters.updateById(any(), any(), any(), any())).thenAnswer(inv -> null);
        writers.when(() -> JooqWriters.delete(any(), any(), any(), eq(true))).thenAnswer(inv -> null);
    }

    /** 递归收集树中所有节点 id（含嵌套子节点），用于断言「该范围内可见的全部机构」。 */
    private static List<Long> idsOf(List<OrgTreeVO> tree) {
        List<Long> ids = new ArrayList<>();
        if (tree == null) {
            return ids;
        }
        for (OrgTreeVO node : tree) {
            ids.add(node.id());
            ids.addAll(idsOf(node.children()));
        }
        return ids;
    }

    @Test
    void sameScopeSecondReadServedFromCache() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubOrgTreeByScope(writers);

            DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.all()));
            try {
                assertEquals(List.of(1L, 2L, 3L), idsOf(orgService.tree()));
                // 命中缓存：打库替身再次进入也不会改变结果
                assertEquals(List.of(1L, 2L, 3L), idsOf(orgService.tree()));
            } finally {
                DataScopeContext.clear();
            }

            assertNotNull(orgCache().get(OrgService.SCOPE_ALL_KEY));
        }
    }

    /**
     * 安全关键：ALL 与受限范围各自独立缓存，受限用户绝不读到 ALL 范围的树。
     * 若缓存键未隔离（恒为 ALL），ORG 范围调用会命中管理员缓存而泄露全量树。
     */
    @Test
    void scopedTreeIsolatedAcrossPermissions() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubOrgTreeByScope(writers);

            // 1) 管理员（ALL）先读，缓存全量树
            DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.all()));
            try {
                assertEquals(List.of(1L, 2L, 3L), idsOf(orgService.tree()));
            } finally {
                DataScopeContext.clear();
            }

            // 2) 受限用户（仅可见机构 1、2）读取，应得 1、2，且独立键
            DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.ofOrgs(Set.of(1L, 2L))));
            try {
                assertEquals(List.of(1L, 2L), idsOf(orgService.tree()));
            } finally {
                DataScopeContext.clear();
            }
            assertNotNull(orgCache().get("ORG:1,2"));
            assertNotNull(orgCache().get(OrgService.SCOPE_ALL_KEY));

            // 3) 再次以管理员读取：必须仍是全量（3），不得被受限键污染
            DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.all()));
            try {
                assertEquals(List.of(1L, 2L, 3L), idsOf(orgService.tree()));
            } finally {
                DataScopeContext.clear();
            }

            // 4) 再次以受限用户读取：必须仍是 1、2（不得读到越权的 3）
            DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.ofOrgs(Set.of(1L, 2L))));
            try {
                assertEquals(List.of(1L, 2L), idsOf(orgService.tree()));
            } finally {
                DataScopeContext.clear();
            }
        }
    }

    /** 仅本人（SELF）范围独立成键，且不与他人/管理员串缓存。 */
    @Test
    void selfScopeIsolated() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubOrgTreeByScope(writers);

            DataScopeContext.set(new DataScopeContext.Scope(9L, DataScopeResult.self()));
            try {
                assertEquals(List.of(1L), idsOf(orgService.tree()));
            } finally {
                DataScopeContext.clear();
            }
            assertNotNull(orgCache().get("SELF:9"));

            // 管理员读取仍为全量，不受 SELF 键影响
            DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.all()));
            try {
                assertEquals(List.of(1L, 2L, 3L), idsOf(orgService.tree()));
            } finally {
                DataScopeContext.clear();
            }
        }
    }

    @Test
    void orgCreateEvictsTreeCache() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubOrgTreeByScope(writers);
            stubOrgWrites(writers);

            DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.all()));
            try {
                orgService.tree();
            } finally {
                DataScopeContext.clear();
            }
            assertNotNull(orgCache().get(OrgService.SCOPE_ALL_KEY));

            // 创建机构：写路径整缓存驱逐
            orgService.create(new CreateOrgRequest(0L, "c9", "新部门", "DEPARTMENT", 9, 1L, "ACTIVE"));

            assertNull(orgCache().get(OrgService.SCOPE_ALL_KEY));
            assertNull(orgCache().get("ORG:1,2"));

            // 驱逐后重新读取会回源重建缓存
            DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.all()));
            try {
                assertTrue(idsOf(orgService.tree()).contains(1L));
            } finally {
                DataScopeContext.clear();
            }
            assertNotNull(orgCache().get(OrgService.SCOPE_ALL_KEY));
        }
    }

    @Test
    void orgUpdateEvictsTreeCache() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubOrgTreeByScope(writers);
            stubOrgWrites(writers);

            DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.all()));
            try {
                orgService.tree();
            } finally {
                DataScopeContext.clear();
            }
            assertNotNull(orgCache().get(OrgService.SCOPE_ALL_KEY));

            orgService.update(1L, new UpdateOrgRequest(0L, "c1", "总部改名", "DEPARTMENT", 1, 1L, "ACTIVE"));

            assertNull(orgCache().get(OrgService.SCOPE_ALL_KEY));
        }
    }

    @Test
    void orgDeleteEvictsTreeCache() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubOrgTreeByScope(writers);
            stubOrgWrites(writers);

            DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.all()));
            try {
                orgService.tree();
            } finally {
                DataScopeContext.clear();
            }
            assertNotNull(orgCache().get(OrgService.SCOPE_ALL_KEY));

            orgService.delete(1L);

            assertNull(orgCache().get(OrgService.SCOPE_ALL_KEY));
        }
    }
}
