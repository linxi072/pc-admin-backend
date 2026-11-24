package com.acme.scaffold.system.file.service;

import com.acme.scaffold.system.file.domain.repository.FileStoragePort;
import com.acme.scaffold.system.file.domain.repository.ObjectStoragePort;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储统一路由（E4）：FileService 只依赖本类，后端切换对业务无感。
 *
 * <p>选择策略：容器中存在 {@link ObjectStoragePort} Bean（由外部 jar 经 SPI 提供）时用远端，
 * 否则回退本地 {@link FileStorageService}。框架本体不提供远端实现，保持零新增依赖与离线构建；
 * 未提供远端时行为与旧版完全一致（零回归）。</p>
 */
@Slf4j
@Component
public class FileStorageRouter implements FileStoragePort {

    private final FileStorageService local;
    private final ObjectStoragePort remote;
    private final boolean remoteEnabled;

    public FileStorageRouter(FileStorageService local,
                             @Autowired(required = false) ObjectStoragePort remote,
                             org.springframework.core.env.Environment environment) {
        this.local = local;
        this.remote = remote;
        this.remoteEnabled = remote != null
                && environment.getProperty("app.file.storage-backend", "local").equalsIgnoreCase("remote");
        if (remoteEnabled) {
            log.info("文件存储后端已切换为远端对象存储：{}", remote.backendId());
        }
    }

    private FileStoragePort backend() {
        return remoteEnabled ? remote : local;
    }

    /** 当前后端标识（供运维/日志辨识）。 */
    public String backendId() {
        return remoteEnabled ? remote.backendId() : "local";
    }

    /**
     * 存储位置描述（写入 DB storage_path 列）：本地为绝对路径（兼容旧数据/旧下载），
     * 远端为 {@code remote://{backendId}/{storedName}} 逻辑 URI。下载不走此字段，
     * 故远端无本地路径语义也不会破坏下载。
     */
    public String storagePath(String storedName) {
        return remoteEnabled
                ? "remote://" + remote.backendId() + "/" + storedName
                : local.resolve(storedName).toString();
    }

    @Override
    public String store(MultipartFile file, String ext) {
        return backend().store(file, ext);
    }

    @Override
    public InputStream openStream(String storedName) {
        return backend().openStream(storedName);
    }

    @Override
    public Resource loadAsResource(String storedName) {
        return backend().loadAsResource(storedName);
    }

    @Override
    public void delete(String storedName) {
        backend().delete(storedName);
    }

    @Override
    public boolean exists(String storedName) {
        return backend().exists(storedName);
    }
}
