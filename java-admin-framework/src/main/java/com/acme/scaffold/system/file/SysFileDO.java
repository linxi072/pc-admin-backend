package com.acme.scaffold.system.file;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件实体（对应 {@code sys_file}，DDL 见 V33__file_management.sql）。
 *
 * <p>上传人信息冗余存储（uploader_id / uploader_name），即使用户后续被删除，文件记录仍可读出其来源。
 */
@Data
public class SysFileDO {

    private Long id;

    private Long tenantId = 0L;

    private String originalName;

    private String storedName;

    private String storagePath;

    private Long fileSize;

    private String contentType;

    /** 来源功能模块，如 workflow / report / file-management。 */
    private String sourceModule;

    /** 业务关联 ID（可选）。 */
    private String bizId;

    private Long uploaderId;

    private String uploaderName;

    private Long downloadCount = 0L;

    private Integer version = 0;

    private Integer deleted = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
