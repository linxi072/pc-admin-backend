package com.acme.scaffold.system.export.service;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.ExportFormat;
import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.system.export.service.ExportCenterService.ExportedFile;
import com.acme.scaffold.system.file.service.FileStorageRouter;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 异步导出任务编排（E5）：提交任务 → 后台执行 → 产物经 FileStoragePort 落盘 → 状态可轮询、结果可下载。
 *
 * <p>任务状态存内存（重启丢失可接受），产物落盘复用 E4 存储抽象，因此本地/远端对象存储无需分支代码。
 * 执行仍复用 exportExecutor（见 ExportCenterService.downloadAsync），不新增线程池。
 * 提交时固化提交人与租户归属，状态与产物仅提交人在同租户下可访问。</p>
 */
@Service
public class ExportJobService {

    private final ExportCenterService exportCenterService;
    private final FileStorageRouter fileStorageRouter;
    private final ExportJobStore jobStore;
    private final SecurityContextFacade securityContextFacade;

    public ExportJobService(ExportCenterService exportCenterService,
                            FileStorageRouter fileStorageRouter,
                            ExportJobStore jobStore,
                            SecurityContextFacade securityContextFacade) {
        this.exportCenterService = exportCenterService;
        this.fileStorageRouter = fileStorageRouter;
        this.jobStore = jobStore;
        this.securityContextFacade = securityContextFacade;
    }

    /** 提交异步导出，立即返回任务 ID（状态 PENDING/RUNNING）。 */
    public String submit(Map<String, String> allParams) {
        String jobId = UUID.randomUUID().toString().replace("-", "");
        String code = allParams.get("code");
        ExportFormat format = ExportFormat.from(allParams.get("format"));
        Long submitterUserId = securityContextFacade.getCurrentPrincipal()
                .map(p -> p.userId())
                .orElse(null);
        Long tenantId = TenantContext.isSet() ? TenantContext.requireTenantId() : null;
        ExportJob pending = new ExportJob(jobId, submitterUserId, tenantId,
                code, format == null ? "CSV" : format.name(),
                ExportJob.Status.PENDING, null, null, null, System.currentTimeMillis(), null);
        jobStore.put(pending);
        jobStore.update(jobId, job -> transition(job, ExportJob.Status.RUNNING, null, null));

        Map<String, String> params = new LinkedHashMap<>(allParams);
        exportCenterService.downloadAsync(params).whenComplete((file, error) -> {
            if (error != null) {
                jobStore.update(jobId, job -> transition(job, ExportJob.Status.FAILED,
                        null, rootMessage(error)));
                return;
            }
            try {
                String storedName = fileStorageRouter.store(
                        new ByteArrayMultipartFile(file.body(), file.fileName() + "." + file.format().extension()),
                        file.format().extension());
                jobStore.update(jobId, job -> new ExportJob(job.jobId(), job.submitterUserId(), job.tenantId(),
                        job.templateCode(), job.format(),
                        ExportJob.Status.SUCCEEDED, file.fileName(), storedName, null,
                        job.submittedAt(), System.currentTimeMillis()));
            } catch (Exception e) {
                jobStore.update(jobId, job -> transition(job, ExportJob.Status.FAILED, null, rootMessage(e)));
            }
        });
        return jobId;
    }

    /** 查询任务状态；不存在、已过期或不属于当前主体时统一抛 404，避免泄露任务存在性。 */
    public ExportJob status(String jobId) {
        ExportJob job = jobStore.get(jobId);
        if (job == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "导出任务不存在或已过期: " + jobId);
        }
        assertOwner(job);
        return job;
    }

    /** 归属校验：仅提交人本人且处于同一租户作用域可访问。 */
    private void assertOwner(ExportJob job) {
        Long currentUserId = securityContextFacade.getCurrentPrincipal()
                .map(p -> p.userId())
                .orElse(null);
        Long currentTenantId = TenantContext.isSet() ? TenantContext.requireTenantId() : null;
        if (currentUserId == null
                || !Objects.equals(job.submitterUserId(), currentUserId)
                || !Objects.equals(job.tenantId(), currentTenantId)) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "导出任务不存在或已过期: " + job.jobId());
        }
    }

    /** 读取成功任务的产物字节；任务未完成/失败时拒绝。 */
    public ExportedFile result(String jobId) {
        ExportJob job = status(jobId);
        if (job.status() != ExportJob.Status.SUCCEEDED) {
            throw BusinessException.badRequest("导出任务尚未完成或已失败（状态: " + job.status() + "）");
        }
        ExportFormat format = ExportFormat.from(job.format());
        if (format == null) {
            throw BusinessException.badRequest("未知导出格式: " + job.format());
        }
        try (InputStream in = fileStorageRouter.loadAsResource(job.storedName()).getInputStream()) {
            return new ExportedFile(in.readAllBytes(), job.fileName(), format);
        } catch (IOException e) {
            throw new IllegalStateException("读取导出产物失败", e);
        }
    }

    private static ExportJob transition(ExportJob job, ExportJob.Status status,
                                        String storedName, String error) {
        return new ExportJob(job.jobId(), job.submitterUserId(), job.tenantId(),
                job.templateCode(), job.format(), status,
                job.fileName(), storedName != null ? storedName : job.storedName(), error,
                job.submittedAt(), status == ExportJob.Status.RUNNING ? null : System.currentTimeMillis());
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }
}
