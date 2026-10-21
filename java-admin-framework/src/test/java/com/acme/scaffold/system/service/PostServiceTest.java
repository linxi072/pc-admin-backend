package com.acme.scaffold.system.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.AssignUserPostsRequest;
import com.acme.scaffold.system.dto.CreatePostRequest;
import com.acme.scaffold.system.dto.PostOptionVO;
import com.acme.scaffold.system.dto.PostView;
import com.acme.scaffold.system.dto.UpdatePostRequest;
import com.acme.scaffold.system.entity.SysPostDO;
import com.acme.scaffold.system.entity.SysUserPostDO;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * {@link PostService} 单元测试：以 {@code mockStatic(JooqWriters)} 作为打库替身，
 * 验证写入路径的字段映射、唯一性/存在性校验、用户岗位整体替换与物理清旧语义，
 * 以及批量装载映射（防 N+1）。不依赖数据库，离线可跑。
 *
 * <p>约定：DB 交互的调用次数不做脆弱链式核验，仅对「会改变对象状态」的写
 * （insert/updateById/delete/deleteByColumn）做关键断言。
 */
class PostServiceTest {

    private final DSLContext dsl = mock(DSLContext.class);

    // ------------------------------------------------------------------
    // create
    // ------------------------------------------------------------------

    @Test
    void create_validSetsFieldsAndInserts() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.count(any(), eq(JooqTables.SYS_POST), any())).thenReturn(0L);
            SysPostDO[] captured = {null};
            mw.when(() -> JooqWriters.insert(any(), eq(JooqTables.SYS_POST), any())).thenAnswer(inv -> {
                captured[0] = inv.getArgument(2);
                captured[0].setId(99L);
                return null;
            });

            PostService service = new PostService(dsl);
            Long id = service.create(new CreatePostRequest("GM-001", "总经理", 3, "高管岗"));

            assertEquals(99L, id);
            assertEquals("GM-001", captured[0].getPostCode());
            assertEquals("总经理", captured[0].getPostName());
            assertEquals(3, captured[0].getPostSort());
            assertEquals("ACTIVE", captured[0].getStatus()); // 新建默认启用
            assertEquals("高管岗", captured[0].getRemark());
        }
    }

    @Test
    void create_invalidCodeThrows() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            PostService service = new PostService(dsl);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.create(new CreatePostRequest("bad code!", "总经理", 1, null)));
            assertTrue(ex.getMessage().contains("岗位编码"));
        }
    }

    @Test
    void create_duplicateCodeThrows() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.count(any(), eq(JooqTables.SYS_POST), any())).thenReturn(1L);
            PostService service = new PostService(dsl);
            assertThrows(BusinessException.class,
                    () -> service.create(new CreatePostRequest("GM-001", "总经理", 1, null)));
        }
    }

    // ------------------------------------------------------------------
    // update
    // ------------------------------------------------------------------

    @Test
    void update_partialUpdatesOnlyProvided() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            SysPostDO exist = new SysPostDO();
            exist.setId(5L);
            exist.setPostCode("GM-001");
            exist.setPostName("旧名");
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_POST), eq(SysPostDO.class), eq(5L)))
                    .thenReturn(exist);
            mw.when(() -> JooqWriters.count(any(), eq(JooqTables.SYS_POST), any())).thenReturn(0L);
            SysPostDO[] captured = {null};
            mw.when(() -> JooqWriters.updateById(any(), eq(JooqTables.SYS_POST), eq(5L), any())).thenAnswer(inv -> {
                captured[0] = inv.getArgument(3);
                return null;
            });

            PostService service = new PostService(dsl);
            service.update(5L, new UpdatePostRequest(null, "新名", 7, "DISABLED", null));

            assertEquals("新名", captured[0].getPostName());
            assertEquals(7, captured[0].getPostSort());
            assertEquals("DISABLED", captured[0].getStatus());
            assertEquals(null, captured[0].getPostCode()); // 未改编码 → 不写入
        }
    }

    @Test
    void update_codeChangeToDuplicateThrows() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            SysPostDO exist = new SysPostDO();
            exist.setId(5L);
            exist.setPostCode("GM-001");
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_POST), eq(SysPostDO.class), eq(5L)))
                    .thenReturn(exist);
            mw.when(() -> JooqWriters.count(any(), eq(JooqTables.SYS_POST), any())).thenReturn(1L);
            PostService service = new PostService(dsl);
            assertThrows(BusinessException.class,
                    () -> service.update(5L, new UpdatePostRequest("GM-002", null, null, null, null)));
        }
    }

    // ------------------------------------------------------------------
    // delete
    // ------------------------------------------------------------------

    @Test
    void delete_logicalDeletesAndCleansUserRel() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            SysPostDO exist = new SysPostDO();
            exist.setId(5L);
            exist.setPostCode("GM-001");
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_POST), eq(SysPostDO.class), eq(5L)))
                    .thenReturn(exist);

            PostService service = new PostService(dsl);
            service.delete(5L);

            mw.verify(() -> JooqWriters.delete(any(), eq(JooqTables.SYS_POST), eq(5L), eq(true))); // 逻辑删除
            mw.verify(() -> JooqWriters.deleteByColumn(any(), eq(JooqTables.SYS_USER_POST), eq("post_id"), eq(5L)));
        }
    }

    // ------------------------------------------------------------------
    // 用户岗位分配
    // ------------------------------------------------------------------

    @Test
    void replaceUserPosts_normalizedDedupThenInsert() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            SysPostDO p1 = new SysPostDO(); p1.setId(1L);
            SysPostDO p2 = new SysPostDO(); p2.setId(2L);
            mw.when(() -> JooqWriters.fetchList(any(), eq(JooqTables.SYS_POST), eq(SysPostDO.class), any()))
                    .thenReturn(List.of(p1, p2));
            List<SysUserPostDO> inserted = new java.util.ArrayList<>();
            mw.when(() -> JooqWriters.insert(any(), eq(JooqTables.SYS_USER_POST), any())).thenAnswer(inv -> {
                inserted.add(inv.getArgument(2));
                return null;
            });

            PostService service = new PostService(dsl);
            service.replaceUserPosts(7L, java.util.Arrays.asList(1L, 2L, 1L, null)); // 去重后 [1,2]

            mw.verify(() -> JooqWriters.deleteByColumn(any(), eq(JooqTables.SYS_USER_POST), eq("user_id"), eq(7L)));
            assertEquals(2, inserted.size()); // 去重后两条
        }
    }

    @Test
    void replaceUserPosts_emptyClears() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            PostService service = new PostService(dsl);
            List<SysUserPostDO> inserted = new java.util.ArrayList<>();
            mw.when(() -> JooqWriters.insert(any(), eq(JooqTables.SYS_USER_POST), any())).thenAnswer(inv -> {
                inserted.add(inv.getArgument(2));
                return null;
            });
            service.replaceUserPosts(7L, List.of()); // 空列表 → 清空

            mw.verify(() -> JooqWriters.deleteByColumn(any(), eq(JooqTables.SYS_USER_POST), eq("user_id"), eq(7L)));
            assertEquals(0, inserted.size());
        }
    }

    @Test
    void assignUserPostsDelegatesToReplace() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            SysPostDO p1 = new SysPostDO(); p1.setId(1L);
            mw.when(() -> JooqWriters.fetchList(any(), eq(JooqTables.SYS_POST), eq(SysPostDO.class), any()))
                    .thenReturn(List.of(p1));
            List<SysUserPostDO> inserted = new java.util.ArrayList<>();
            mw.when(() -> JooqWriters.insert(any(), eq(JooqTables.SYS_USER_POST), any())).thenAnswer(inv -> {
                inserted.add(inv.getArgument(2));
                return null;
            });

            PostService service = new PostService(dsl);
            service.assignUserPosts(7L, new AssignUserPostsRequest(List.of(1L)));

            mw.verify(() -> JooqWriters.deleteByColumn(any(), eq(JooqTables.SYS_USER_POST), eq("user_id"), eq(7L)));
            assertEquals(1, inserted.size());
        }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Test
    void options_returnsActiveMappedToVO() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            SysPostDO a = new SysPostDO(); a.setId(1L); a.setPostName("总经理"); a.setStatus("ACTIVE");
            SysPostDO b = new SysPostDO(); b.setId(2L); b.setPostName("停用岗"); b.setStatus("DISABLED");
            mw.when(() -> JooqWriters.fetchList(any(), eq(JooqTables.SYS_POST), eq(SysPostDO.class),
                    any(), any(OrderField.class), any(OrderField.class))).thenReturn(List.of(a, b));

            PostService service = new PostService(dsl);
            List<PostOptionVO> opts = service.options();

            assertEquals(2, opts.size());
            assertEquals("总经理", opts.get(0).postName());
            assertEquals("停用岗", opts.get(1).postName());
        }
    }

    @Test
    void loadPostNamesByUserIds_mapsCorrectly() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            SysUserPostDO r1 = new SysUserPostDO(); r1.setUserId(1L); r1.setPostId(10L);
            SysUserPostDO r2 = new SysUserPostDO(); r2.setUserId(1L); r2.setPostId(11L);
            SysUserPostDO r3 = new SysUserPostDO(); r3.setUserId(2L); r3.setPostId(12L);
            mw.when(() -> JooqWriters.fetchList(any(), eq(JooqTables.SYS_USER_POST), eq(SysUserPostDO.class), any()))
                    .thenReturn(List.of(r1, r2, r3));

            SysPostDO p10 = new SysPostDO(); p10.setId(10L); p10.setPostName("总经理");
            SysPostDO p11 = new SysPostDO(); p11.setId(11L); p11.setPostName("总监");
            SysPostDO p12 = new SysPostDO(); p12.setId(12L); p12.setPostName("经理");
            mw.when(() -> JooqWriters.fetchList(any(), eq(JooqTables.SYS_POST), eq(SysPostDO.class), any()))
                    .thenReturn(List.of(p10, p11, p12));

            PostService service = new PostService(dsl);
            Map<Long, Map<Long, String>> result = service.loadPostNamesByUserIds(List.of(1L, 2L));

            assertEquals(2, result.size());
            assertEquals(Map.of(10L, "总经理", 11L, "总监"), result.get(1L));
            assertEquals(Map.of(12L, "经理"), result.get(2L));
        }
    }

    @Test
    void loadPostNamesByUserIds_emptyInputReturnsEmpty() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            PostService service = new PostService(dsl);
            assertTrue(service.loadPostNamesByUserIds(List.of()).isEmpty());
        }
    }
}
