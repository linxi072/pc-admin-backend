package com.acme.scaffold.system.export.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.export.ExportFormat;
import com.acme.scaffold.system.export.service.ExportCenterService.ExportedFile;
import com.acme.scaffold.system.file.service.FileStorageRouter;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

/** 异步导出任务单测（E5）：提交即返回、状态流转、落盘复用存储路由、失败降级。 */
class ExportJobServiceTest {

    @Test
    void shouldSubmitAndPersistResult() {
        ExportCenterService center = mock(ExportCenterService.class);
        FileStorageRouter router = mock(FileStorageRouter.class);
        when(center.downloadAsync(any())).thenReturn(CompletableFuture.completedFuture(
                new ExportedFile("pdf-bytes".getBytes(StandardCharsets.UTF_8), "report", ExportFormat.PDF)));
        when(router.store(any(), any())).thenReturn("stored.pdf");
        when(router.loadAsResource("stored.pdf"))
                .thenReturn(new ByteArrayResource("pdf-bytes".getBytes(StandardCharsets.UTF_8)));
        ExportJobService service = new ExportJobService(center, router, new ExportJobStore());

        String jobId = service.submit(Map.of("code", "sample", "format", "PDF"));
        assertEquals(ExportJob.Status.SUCCEEDED, service.status(jobId).status());
        assertEquals("stored.pdf", service.status(jobId).storedName());
        assertEquals("pdf-bytes", new String(service.result(jobId).body(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldMarkFailedWhenExportThrows() {
        ExportCenterService center = mock(ExportCenterService.class);
        CompletableFuture<ExportedFile> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("boom"));
        when(center.downloadAsync(any())).thenReturn(failed);
        FileStorageRouter router = mock(FileStorageRouter.class);
        ExportJobService service = new ExportJobService(center, router, new ExportJobStore());

        String jobId = service.submit(Map.of("code", "sample", "format", "CSV"));
        assertEquals(ExportJob.Status.FAILED, service.status(jobId).status());
        assertEquals("boom", service.status(jobId).error());
        assertThrows(RuntimeException.class, () -> service.result(jobId));
    }

    @Test
    void shouldRejectUnknownJob() {
        ExportJobService service = new ExportJobService(mock(ExportCenterService.class),
                mock(FileStorageRouter.class), new ExportJobStore());
        assertThrows(RuntimeException.class, () -> service.status("missing"));
    }
}
