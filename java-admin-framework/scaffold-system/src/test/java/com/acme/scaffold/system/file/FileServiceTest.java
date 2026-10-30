package com.acme.scaffold.system.file;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.entity.SysFileDO;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileService} 单元测试：以 {@code mockStatic(JooqWriters)} 作为打库替身，
 * 固化 V50 三项新能力的关键语义——秒传复用（命中不落盘 / 物理缺失回退落盘）、
 * 引用计数删除保护、批量删除部分成功与上限、秒传预检。离线可跑。
 *
 * <p>约定：仅对「会改变对象状态」的写（insert/delete）与存储副作用做关键断言，
 * jOOQ 链式查询（stats / 共享标记）交由 MySQL 集成测试覆盖，不做脆弱打桩。
 */
class FileServiceTest {

    private static final byte[] CONTENT = "hello world".getBytes(StandardCharsets.UTF_8);

    private final DSLContext dsl = mock(DSLContext.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final SecurityContextFacade facade = mock(SecurityContextFacade.class);

    private FileService service() {
        FileService s = new FileService(dsl, storage, facade);
        ReflectionTestUtils.setField(s, "maxBytes", 10_485_760L);
        ReflectionTestUtils.setField(s, "allowedExtensionsRaw", "jpg,png,txt,zip");
        return s;
    }

    private FileService serviceWithMaxBytes(long maxBytes) {
        FileService s = new FileService(dsl, storage, facade);
        ReflectionTestUtils.setField(s, "maxBytes", maxBytes);
        ReflectionTestUtils.setField(s, "allowedExtensionsRaw", "jpg,png,txt,zip");
        return s;
    }

    private static MultipartFile file() {
        return new MockMultipartFile("file", "hello.txt", "text/plain", CONTENT);
    }

    private static SysFileDO existingRecord() {
        SysFileDO d = new SysFileDO();
        d.setId(7L);
        d.setStoredName("exists.png");
        d.setStoragePath("/tmp/uploads/file/exists.png");
        d.setOriginalName("已存在.png");
        d.setFileSize(2048L);
        return d;
    }

    private void stubPrincipal() {
        when(facade.requireCurrentPrincipal()).thenReturn(
                new CurrentPrincipal(1L, "admin", "管理员", 0L, null, 0, Set.of(), Set.of()));
    }

    // ------------------------------------------------------------------
    // 上传：秒传
    // ------------------------------------------------------------------

    @Test
    @DisplayName("秒传命中：复用存量物理文件不落盘，记录带 content_hash，deduplicated=true")
    void upload_dedupHit_reusesPhysicalFileAndSkipsStore() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchOne(any(), eq(JooqTables.SYS_FILE), eq(SysFileDO.class), any()))
                    .thenReturn(existingRecord());
            SysFileDO[] captured = {null};
            mw.when(() -> JooqWriters.insert(any(), eq(JooqTables.SYS_FILE), any())).thenAnswer(inv -> {
                captured[0] = inv.getArgument(2);
                captured[0].setId(99L);
                return null;
            });
            when(storage.exists("exists.png")).thenReturn(true);
            when(storage.resolve("exists.png")).thenReturn(Path.of("/tmp/uploads/file/exists.png"));
            stubPrincipal();

            FileView view = service().upload(file(), "file-management", null);

            assertTrue(view.deduplicated());
            assertEquals(99L, view.id());
            assertEquals("exists.png", captured[0].getStoredName());
            assertTrue(captured[0].getContentHash().matches("[0-9a-f]{64}"), "content_hash 应为 64 位小写十六进制");
            verify(storage, never()).store(any(), anyString());
        }
    }

    @Test
    @DisplayName("无秒传命中：正常落盘并写记录，deduplicated=false")
    void upload_noHit_storesNewFile() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchOne(any(), eq(JooqTables.SYS_FILE), eq(SysFileDO.class), any()))
                    .thenReturn(null);
            SysFileDO[] captured = {null};
            mw.when(() -> JooqWriters.insert(any(), eq(JooqTables.SYS_FILE), any())).thenAnswer(inv -> {
                captured[0] = inv.getArgument(2);
                return null;
            });
            when(storage.store(any(), eq("txt"))).thenReturn("newstored.txt");
            when(storage.resolve("newstored.txt")).thenReturn(Path.of("/tmp/uploads/file/newstored.txt"));
            stubPrincipal();

            FileView view = service().upload(file(), "file-management", null);

            assertFalse(view.deduplicated());
            assertEquals("newstored.txt", captured[0].getStoredName());
            assertTrue(captured[0].getContentHash().matches("[0-9a-f]{64}"));
            verify(storage).store(any(), eq("txt"));
        }
    }

    @Test
    @DisplayName("秒传命中但物理文件已缺失：回退为正常落盘，避免指向不存在文件的记录")
    void upload_dedupHitButPhysicalMissing_fallsBackToStore() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchOne(any(), eq(JooqTables.SYS_FILE), eq(SysFileDO.class), any()))
                    .thenReturn(existingRecord());
            mw.when(() -> JooqWriters.insert(any(), eq(JooqTables.SYS_FILE), any())).thenAnswer(inv -> {
                inv.getArgument(2, SysFileDO.class).setId(99L);
                return null;
            });
            when(storage.exists("exists.png")).thenReturn(false);
            when(storage.store(any(), eq("txt"))).thenReturn("fresh.txt");
            when(storage.resolve("fresh.txt")).thenReturn(Path.of("/tmp/uploads/file/fresh.txt"));
            stubPrincipal();

            FileView view = service().upload(file(), "file-management", null);

            assertFalse(view.deduplicated());
            verify(storage).store(any(), eq("txt"));
        }
    }

    @Test
    @DisplayName("上传空文件抛 FILE_EMPTY")
    void upload_emptyFile_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().upload(new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]),
                        "file-management", null));
        assertEquals("FILE_001", ex.getErrorCode().code());
    }

    @Test
    @DisplayName("上传超限抛 FILE_TOO_LARGE")
    void upload_oversize_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> serviceWithMaxBytes(4L).upload(file(), "file-management", null));
        assertEquals("FILE_002", ex.getErrorCode().code());
    }

    // ------------------------------------------------------------------
    // 删除：引用计数保护
    // ------------------------------------------------------------------

    @Test
    @DisplayName("删除：物理文件仅一条记录引用时同步移除")
    void delete_singleReference_removesPhysicalFile() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            SysFileDO rec = existingRecord();
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_FILE), eq(SysFileDO.class), eq(7L)))
                    .thenReturn(rec);
            mw.when(() -> JooqWriters.count(any(), eq(JooqTables.SYS_FILE), any())).thenReturn(1L);

            service().delete(7L);

            verify(storage).delete("exists.png");
            mw.verify(() -> JooqWriters.delete(any(), eq(JooqTables.SYS_FILE), eq(7L), eq(true)));
        }
    }

    @Test
    @DisplayName("删除：物理文件被多条记录共享（秒传组）时仅逻辑删记录、保留磁盘文件")
    void delete_sharedPhysicalFile_keepsPhysicalFile() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            SysFileDO rec = existingRecord();
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_FILE), eq(SysFileDO.class), eq(7L)))
                    .thenReturn(rec);
            mw.when(() -> JooqWriters.count(any(), eq(JooqTables.SYS_FILE), any())).thenReturn(2L);

            service().delete(7L);

            verify(storage, never()).delete(anyString());
            mw.verify(() -> JooqWriters.delete(any(), eq(JooqTables.SYS_FILE), eq(7L), eq(true)));
        }
    }

    @Test
    @DisplayName("删除不存在记录抛 FILE_NOT_FOUND")
    void delete_notFound_throws() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_FILE), eq(SysFileDO.class), eq(7L)))
                    .thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service().delete(7L));
            assertEquals("FILE_404", ex.getErrorCode().code());
        }
    }

    // ------------------------------------------------------------------
    // 批量删除：部分成功语义
    // ------------------------------------------------------------------

    @Test
    @DisplayName("批量删除空列表抛 FILE_BATCH_EMPTY")
    void batchDelete_empty_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().batchDelete(null));
        assertEquals("FILE_006", ex.getErrorCode().code());
        ex = assertThrows(BusinessException.class, () -> service().batchDelete(List.of()));
        assertEquals("FILE_006", ex.getErrorCode().code());
    }

    @Test
    @DisplayName("批量删除超上限抛 FILE_BATCH_TOO_MANY")
    void batchDelete_tooMany_throws() {
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().batchDelete(ids));
        assertEquals("FILE_007", ex.getErrorCode().code());
    }

    @Test
    @DisplayName("批量删除部分成功：失败项收集明细，不影响其余项")
    void batchDelete_partialFailure_collectsFailures() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            SysFileDO rec = existingRecord();
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_FILE), eq(SysFileDO.class), eq(1L)))
                    .thenReturn(rec);
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_FILE), eq(SysFileDO.class), eq(999L)))
                    .thenReturn(null);
            mw.when(() -> JooqWriters.count(any(), eq(JooqTables.SYS_FILE), any())).thenReturn(1L);

            BatchDeleteView view = service().batchDelete(List.of(1L, 999L));

            assertEquals(1, view.deletedCount());
            assertEquals(1, view.failures().size());
            assertEquals(999L, view.failures().get(0).id());
            assertEquals("文件不存在或已删除", view.failures().get(0).reason());
            verify(storage).delete("exists.png");
        }
    }

    @Test
    @DisplayName("批量删除全部成功：去重后逐项删除，无失败明细")
    void batchDelete_allSuccess() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            SysFileDO rec = existingRecord();
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_FILE), eq(SysFileDO.class), any()))
                    .thenReturn(rec);
            mw.when(() -> JooqWriters.count(any(), eq(JooqTables.SYS_FILE), any())).thenReturn(1L);

            BatchDeleteView view = service().batchDelete(List.of(1L, 2L, 1L));

            assertEquals(2, view.deletedCount()); // 重复 ID 去重
            assertTrue(view.failures().isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // 秒传预检
    // ------------------------------------------------------------------

    @Test
    @DisplayName("秒传预检：非法哈希直接 400，不打库")
    void checkDedup_invalidHash_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().checkDedup("not-a-hash"));
        assertEquals("FILE_005", ex.getErrorCode().code());
        assertNull(ex.getMessageKey()); // 自定义消息透传
    }

    @Test
    @DisplayName("秒传预检命中：返回可秒传与命中文件信息")
    void checkDedup_hit_returnsMatchedFile() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchOne(any(), eq(JooqTables.SYS_FILE), eq(SysFileDO.class), any()))
                    .thenReturn(existingRecord());

            DedupCheckView view = service().checkDedup("A".repeat(64));

            assertTrue(view.exists());
            assertEquals("a".repeat(64), view.contentHash()); // 统一小写
            assertEquals(7L, view.fileId());
            assertEquals("已存在.png", view.originalName());
            assertEquals(2048L, view.fileSize());
        }
    }

    @Test
    @DisplayName("秒传预检未命中：exists=false")
    void checkDedup_miss_returnsNotExists() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchOne(any(), eq(JooqTables.SYS_FILE), eq(SysFileDO.class), any()))
                    .thenReturn(null);

            DedupCheckView view = service().checkDedup("b".repeat(64));

            assertFalse(view.exists());
            assertNull(view.fileId());
        }
    }
}
