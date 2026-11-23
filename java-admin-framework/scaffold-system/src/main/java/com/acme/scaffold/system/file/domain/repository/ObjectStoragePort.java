package com.acme.scaffold.system.file.domain.repository;

import java.io.InputStream;

/**
 * 远端对象存储 SPI（E4）：由外部 jar（如 s3/oss/minio 适配包）实现并以 Spring Bean 注入。
 *
 * <p>框架本体不提供实现，以保持零新增依赖与离线可构建；未提供 Bean 时
 * {@code FileStorageRouter} 自动回退到本地 {@link FileStoragePort}，行为与旧版一致（零回归）。</p>
 *
 * <p>与本地 Port 的差异：远端对象以 key 寻址，故 {@code store} 返回 key；调用方
 * （FileService）不关心 key 的具体语义，仅持久化并回传。</p>
 */
public interface ObjectStoragePort extends FileStoragePort {

    /** 远端适配器标识（如 "s3" / "oss" / "minio"），供运维控制台与日志辨识。 */
    String backendId();

    /**
     * 以对象 key 写入输入流（供 Router 在「上传 multipart」与「远端复制」间共用）。
     * 默认实现委托 {@link #store(org.springframework.web.multipart.MultipartFile, String)} 不适用，
     * 由具体适配器覆盖；这里保留接口以便未来非 multipart 来源写入。
     */
    default String storeStream(String key, InputStream in, String ext) {
        throw new UnsupportedOperationException("对象存储适配器未实现 storeStream");
    }
}
