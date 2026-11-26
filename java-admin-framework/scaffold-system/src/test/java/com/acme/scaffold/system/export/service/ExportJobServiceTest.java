package com.acme.scaffold.system.export.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.ExportFormat;
import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.system.export.service.ExportCenterService.ExportedFile;
import com.acme.scaffold.system.file.service.FileStorageRouter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

/** 异步导出任务单测（E5）：状态流转、落盘复用、失败降级与任务归属校验。 */
class ExportJobServiceTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldSubmitAndPersistResult() {
        ExportCenterService center = successfulCenter();
        FileStorageRouter router = successfulRouter();
        ExportJobService service = service(center, router, 42L, 1L);

        String jobId = service.submit(Map.of("code", "sample", "format", "PDF"));

        assertEquals(ExportJob.Status.SUCCEEDED, service.status(jobId).status());
        assertEquals(42L, service.status(jobId).submitterUserId());
        assertEquals(1L, service.status(jobId).tenantId());
        assertEquals("stored.pdf", service.status(jobId).storedName());
        assertEquals("pdf-bytes", new String(service.result(jobId).body(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldMarkFailedWhenExportThrows() {
        CompletableFuture<ExportedFile> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("boom"));
        ExportCenterService center = mock(ExportCenterService.class);
        when(center.downloadAsync(any())).thenReturn(failed);
        ExportJobService service = service(center, mock(FileStorageRouter.class), 42L, 1L);

        String jobId = service.submit(Map.of("code", "sample", "format", "CSV"));

        assertEquals(ExportJob.Status.FAILED, service.status(jobId).status());
        assertEquals("boom", service.status(jobId).error());
        assertThrows(RuntimeException.class, () -> service.result(jobId));
    }

    @Test
    void shouldRejectUnknownJob() {
        ExportJobService service = service(mock(ExportCenterService.class),
                mock(FileStorageRouter.class), 42L, 1L);

        assertThrows(RuntimeException.class, () -> service.status("missing"));
    }

    @Test
    void otherUserShouldNotAccessJob() {
        ExportJobService service = service(successfulCenter(), successfulRouter(), 42L, 1L);
        String jobId = service.submit(Map.of("code", "sample", "format", "PDF"));

        SecurityContextFacade other = facade(43L);
        ExportJobService otherService = new ExportJobService(successfulCenter(), successfulRouter(),
                jobStoreOf(service), other);

        assertNotFound(jobId, otherService);
    }

    @Test
    void otherTenantShouldNotAccessJob() {
        ExportJobService service = service(successfulCenter(), successfulRouter(), 42L, 1L);
        String jobId = service.submit(Map.of("code", "sample", "format", "PDF"));

        TenantContext.set(2L, false);
        ExportJobService sameUserOtherTenant = service(successfulCenter(), successfulRouter(), 42L, 2L);

        assertNotFound(jobId, sameUserOtherTenant);
    }

    @Test
    void anonymousShouldNotAccessJob() {
        ExportJobService service = service(successfulCenter(), successfulRouter(), 42L, 1L);
        String jobId = service.submit(Map.of("code", "sample", "format", "PDF"));

        SecurityContextFacade anonymous = mock(SecurityContextFacade.class);
        when(anonymous.getCurrentPrincipal()).thenReturn(Optional.empty());
        ExportJobService anonymousService = new ExportJobService(successfulCenter(), successfulRouter(),
                jobStoreOf(service), anonymous);

        assertNotFound(jobId, anonymousService);
    }

    private static void assertNotFound(String jobId, ExportJobService service) {
        BusinessException statusEx = assertThrows(BusinessException.class, () -> service.status(jobId));
        assertEquals(CommonErrorCode.NOT_FOUND.code(), statusEx.getErrorCode().code());
        BusinessException resultEx = assertThrows(BusinessException.class, () -> service.result(jobId));
        assertEquals(CommonErrorCode.NOT_FOUND.code(), resultEx.getErrorCode().code());
    }

    private static ExportCenterService successfulCenter() {
        ExportCenterService center = mock(ExportCenterService.class);
        when(center.downloadAsync(any())).thenReturn(CompletableFuture.completedFuture(
                new ExportedFile("pdf-bytes".getBytes(StandardCharsets.UTF_8), "report", ExportFormat.PDF)));
        return center;
    }

    private static FileStorageRouter successfulRouter() {
        FileStorageRouter router = mock(FileStorageRouter.class);
        when(router.store(any(), any())).thenReturn("stored.pdf");
        when(router.loadAsResource("stored.pdf"))
                .thenReturn(new ByteArrayResource("pdf-bytes".getBytes(StandardCharsets.UTF_8)));
        return router;
    }

    private static ExportJobService service(ExportCenterService center, FileStorageRouter router,
                                            Long userId, Long tenantId) {
        TenantContext.set(tenantId, false);
        return new ExportJobService(center, router, new ExportJobStore(), facade(userId));
    }

    private static SecurityContextFacade facade(Long userId) {
        SecurityContextFacade facade = mock(SecurityContextFacade.class);
        when(facade.getCurrentPrincipal()).thenReturn(Optional.of(new CurrentPrincipal(
                userId, "user" + userId, "User", 1L, "web", 1, Set.of("USER"), Set.of())));
        return facade;
    }

    private static ExportJobStore jobStoreOf(ExportJobService service) {
        try {
            var field = ExportJobService.class.getDeclaredField("jobStore");
            field.setAccessible(true);
            return (ExportJobStore) field.get(service);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
