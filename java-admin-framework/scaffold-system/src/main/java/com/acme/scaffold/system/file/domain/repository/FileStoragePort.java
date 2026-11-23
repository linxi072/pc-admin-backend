package com.acme.scaffold.system.file.domain.repository;

import java.io.InputStream;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件物理存储 Port（E4 对象存储抽象）：FileService 只依赖本接口，不关心落盘/对象存储的差异。
 *
 * <p>默认实现为本地磁盘（{@code FileStorageService}）；远端对象存储（S3/OSS/MinIO）
 * 由外部 jar 经 {@link ObjectStoragePort} SPI 提供（见同包），框架本体零新增依赖。
 * 两条路径的方法语义完全一致，保证 FileService 无感知切换。</p>
 */
public interface FileStoragePort {

    /** 写入文件，返回服务端存储名（全局唯一，仅文件名/对象 key，不含目录）。 */
    String store(MultipartFile file, String ext);

    /** 以流读取文件内容（用于 SHA-256 秒传校验等），不存在抛 FILE_NOT_FOUND。 */
    InputStream openStream(String storedName);

    /** 读取为 Spring Resource（下载用），不存在抛 FILE_NOT_FOUND。 */
    Resource loadAsResource(String storedName);

    /** 删除物理文件/对象（不存在时静默忽略，避免删 DB 记录时因文件缺失失败）。 */
    void delete(String storedName);

    /** 物理文件/对象是否存在（秒传命中时校验存量未被外部清理）。 */
    boolean exists(String storedName);
}
