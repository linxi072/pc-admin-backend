package com.acme.scaffold.system.post.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.post.domain.CreatePostRequest;
import com.acme.scaffold.system.post.domain.UpdatePostRequest;
import com.acme.scaffold.system.post.domain.model.SysPost;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;

/** 岗位编码唯一键并发兜底回归：数据库冲突须转成与预校验一致的业务 409，正常路径不受影响。 */
class PostServiceCodeConflictTest {

    private static PostService service() {
        return new PostService(mock(DSLContext.class));
    }

    private static CreatePostRequest createRequest() {
        return new CreatePostRequest("MANAGER", "总经理", 1, null);
    }

    private static SysPost existing() {
        SysPost d = new SysPost();
        d.setId(21L);
        d.setPostCode("OLD_CODE");
        d.setPostName("旧岗位");
        d.setPostSort(1);
        d.setStatus("ACTIVE");
        return d;
    }

    @Test
    void concurrentCreateShouldReturnConflict() {
        PostService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.SYS_POST),
                    any(SysPost.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(createRequest()));

            assertEquals("岗位编码已存在", ex.getMessage());
        }
    }

    @Test
    void concurrentCodeChangeOnUpdateShouldReturnConflict() {
        PostService service = service();

        UpdatePostRequest req = new UpdatePostRequest("NEW_CODE", null, null, null, null);

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_POST),
                    eq(SysPost.class), eq(21L))).thenReturn(existing());
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);
            writers.when(() -> SqlWriters.updateById(any(DSLContext.class), eq(SqlTables.SYS_POST),
                    eq(21L), any(SysPost.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(21L, req));

            assertEquals("岗位编码已存在", ex.getMessage());
        }
    }

    @Test
    void normalCreateShouldNotBeSwallowedByConflictGuard() {
        PostService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);

            assertDoesNotThrow(() -> service.create(createRequest()));
        }
    }
}
