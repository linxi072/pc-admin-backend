package com.acme.scaffold.system.file;

import com.acme.scaffold.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地文件存储。文件名完全由服务端生成（UUID + 扩展名），杜绝路径穿越与覆盖攻击；
 * 所有落盘路径都约束在 baseDir 之内，下载时按 stored_name 重新解析并二次校验。
 */
@Slf4j
@Component
public class FileStorageService {

    @Value("${app.file.storage-dir:./uploads/file}")
    private String storageDir;

    /** 写入文件，返回服务端存储名（仅文件名，不含目录）。 */
    public String store(MultipartFile file, String ext) {
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(storageDir).toAbsolutePath().normalize();
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

    /** 由存储名解析绝对路径；目录固定为 baseDir，并校验不越界。 */
    public Path resolve(String storedName) {
        Path dir = Paths.get(storageDir).toAbsolutePath().normalize();
        Path target = dir.resolve(storedName).normalize();
        if (!target.startsWith(dir)) {
            throw BusinessException.of(FileErrorCode.FILE_STORE_FAILED, "非法的存储路径");
        }
        return target;
    }

    /** 读取为资源；文件不存在返回 404 业务异常。 */
    public Resource loadAsResource(String storedName) {
        Path target = resolve(storedName);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw BusinessException.of(FileErrorCode.FILE_NOT_FOUND, "文件不存在或已删除");
        }
        return new FileSystemResource(target);
    }

    /** 删除物理文件（不存在时静默忽略，避免删除 DB 记录时因文件缺失而失败）。 */
    public void delete(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException e) {
            log.warn("删除物理文件失败 storedName={}", storedName, e);
        }
    }
}
