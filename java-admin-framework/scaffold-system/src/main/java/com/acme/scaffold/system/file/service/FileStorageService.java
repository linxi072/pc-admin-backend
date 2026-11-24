package com.acme.scaffold.system.file.service;

import com.acme.scaffold.common.config.SystemConfigs;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.system.file.domain.repository.FileStoragePort;
import com.acme.scaffold.system.file.enums.FileErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本地文件存储。文件名完全由服务端生成（UUID + 扩展名），杜绝路径穿越与覆盖攻击；
 * 所有落盘路径都约束在 baseDir 之内，下载时按 stored_name 重新解析并二次校验。
 */
@Slf4j
@Component
public class FileStorageService implements FileStoragePort {

    @Value("${app.file.storage-dir:./uploads/file}")
    private String storageDir;

    /** 存储目录：优先系统变量 app.file.storage-dir，缺省回退 @Value 默认值。 */
    private String storageDir() {
        return SystemConfigs.getString("app.file.storage-dir", storageDir);
    }

    /** 写入文件，返回服务端存储名（仅文件名，不含目录）。 */
    @Override
    public String store(MultipartFile file, String ext) {
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(storageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(storedName).normalize();
            if (!target.startsWith(dir)) {
                throw BusinessException.of(FileErrorCode.FILE_STORE_FAILED, "非法的存储路径");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
            return storedName;
        } catch (IOException e) {
            log.error("文件存储失败 ext={}", ext, e);
            throw BusinessException.of(FileErrorCode.FILE_STORE_FAILED, "文件存储失败，请稍后重试");
        }
    }

    /** 由存储名解析绝对路径；目录固定为 baseDir，并校验不越界（本地实现专用，Router.storagePath 会调用）。 */
    public Path resolve(String storedName) {
        Path dir = Paths.get(storageDir()).toAbsolutePath().normalize();
        Path target = dir.resolve(storedName).normalize();
        if (!target.startsWith(dir)) {
            throw BusinessException.of(FileErrorCode.FILE_STORE_FAILED, "非法的存储路径");
        }
        return target;
    }

    /** 以流读取文件内容（Port 契约）；文件不存在返回 404 业务异常。 */
    @Override
    public InputStream openStream(String storedName) {
        Path target = resolve(storedName);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw BusinessException.of(FileErrorCode.FILE_NOT_FOUND, "文件不存在或已删除");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            log.error("读取文件流失败 storedName={}", storedName, e);
            throw BusinessException.of(FileErrorCode.FILE_STORE_FAILED, "文件读取失败，请稍后重试");
        }
    }

    /** 读取为资源；文件不存在返回 404 业务异常。 */
    @Override
    public Resource loadAsResource(String storedName) {
        Path target = resolve(storedName);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw BusinessException.of(FileErrorCode.FILE_NOT_FOUND, "文件不存在或已删除");
        }
        return new FileSystemResource(target);
    }

    /** 删除物理文件（不存在时静默忽略，避免删除 DB 记录时因文件缺失而失败）。 */
    @Override
    public void delete(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException e) {
            log.warn("删除物理文件失败 storedName={}", storedName, e);
        }
    }

    /** 物理文件是否存在（秒传命中时校验存量文件未被外部清理，V50 新增）。 */
    @Override
    public boolean exists(String storedName) {
        Path target = resolve(storedName);
        return Files.exists(target) && Files.isRegularFile(target);
    }
}
