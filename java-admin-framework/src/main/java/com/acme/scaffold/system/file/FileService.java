package com.acme.scaffold.system.file;

import com.acme.scaffold.common.api.PageQuery;
import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqSorts;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文件管理公共接口（可复用）：供「文件管理」菜单与任意业务模块调用。
 *
 * <p>业务模块调用 {@link #upload(MultipartFile, String, String)} 时传入 sourceModule（来源功能模块），
 * 以便后续按模块归集与审计；上传人取自当前登录主体（userId + 姓名），冗余落库以保证可溯。
 *
 * <p>设计要点：
 * <ul>
 *   <li>落盘文件名完全由服务端生成（UUID），客户端原始名仅作展示，杜绝路径穿越与覆盖；</li>
 *   <li>先落盘再写记录，DB 写入失败时清理孤儿文件，保证最终一致；</li>
 *   <li>删除为逻辑删除（deleted=1），保留来源模块/上传人记录，物理文件同步清理。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    /** 列表可排序字段白名单：客户端字段名 → 数据库列名。 */
    private static final Map<String, String> FILE_SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "originalName", "original_name",
            "fileSize", "file_size",
            "sourceModule", "source_module",
            "uploaderName", "uploader_name",
            "downloadCount", "download_count",
            "createdAt", "created_at");

    private final DSLContext dsl;
    private final FileStorageService fileStorageService;
    private final SecurityContextFacade securityContextFacade;

    @Value("${app.file.max-bytes:10485760}")
    private long maxBytes;

    @Value("${app.file.allowed-extensions:jpg,jpeg,png,gif,webp,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,zip,rar,7z}")
    private String allowedExtensionsRaw;

    /** 扩展名白名单（每次解析，便于运行时通过配置热更新）。 */
    private Set<String> allowedExtensions() {
        Set<String> set = new java.util.LinkedHashSet<>();
        for (String s : allowedExtensionsRaw.split(",")) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) {
                set.add(t);
            }
        }
        return set;
    }

    /**
     * 上传文件（公共可复用接口）。
     *
     * @param file          文件内容
     * @param sourceModule  来源功能模块（必填，如 workflow / report / file-management）
     * @param bizId         业务关联 ID（可选，如流程实例 ID / 报表 ID）
     * @return 文件元数据视图
     */
    @Transactional
    public FileView upload(MultipartFile file, String sourceModule, String bizId) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(FileErrorCode.FILE_EMPTY, FileErrorCode.FILE_EMPTY.message());
        }
        if (sourceModule == null || sourceModule.isBlank()) {
            throw BusinessException.badRequest("来源功能模块不能为空");
        }
        if (file.getSize() > maxBytes) {
            throw BusinessException.of(FileErrorCode.FILE_TOO_LARGE,
                    "文件不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }
        Set<String> allowed = allowedExtensions();
        String ext = FileValidator.resolveExtension(file.getOriginalFilename(), allowed);
        try (InputStream magic = file.getInputStream()) {
            FileValidator.verifyContent(ext, magic);
        } catch (IOException e) {
            throw BusinessException.of(FileErrorCode.FILE_CONTENT_MISMATCH, "文件读取失败");
        }

        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();

        // 先落盘（文件名服务端生成），再写记录；落盘失败直接抛异常，不会留脏数据
        String storedName = fileStorageService.store(file, ext);
        Path abs = fileStorageService.resolve(storedName);

        SysFileDO record = new SysFileDO();
        record.setOriginalName(file.getOriginalFilename());
        record.setStoredName(storedName);
        record.setStoragePath(abs.toString());
        record.setFileSize(file.getSize());
        record.setContentType(file.getContentType());
        record.setSourceModule(sourceModule.trim());
        record.setBizId(bizId);
        record.setUploaderId(principal.userId());
        record.setUploaderName(principal.displayName() != null ? principal.displayName() : principal.username());
        try {
            JooqWriters.insert(dsl, JooqTables.SYS_FILE, record);
        } catch (RuntimeException e) {
            // DB 写入失败：清理已落盘的孤儿文件，避免磁盘残留
            fileStorageService.delete(storedName);
            throw e;
        }
        return toView(record);
    }

    /** 下载：返回文件资源与原始文件名（Content-Disposition 由 Controller 设置）。 */
    public FileDownload download(Long id) {
        SysFileDO record = JooqWriters.fetchById(dsl, JooqTables.SYS_FILE, SysFileDO.class, id);
        if (record == null) {
            throw BusinessException.of(FileErrorCode.FILE_NOT_FOUND, FileErrorCode.FILE_NOT_FOUND.message());
        }
        Resource resource = fileStorageService.loadAsResource(record.getStoredName());
        // 下载计数 +1（乐观自增；失败仅告警，不阻塞下载）
        try {
            dsl.update(JooqTables.SYS_FILE.table())
                    .set(JooqTables.SYS_FILE.field("download_count", Long.class),
                            JooqTables.SYS_FILE.field("download_count", Long.class).plus(1))
                    .where(JooqTables.SYS_FILE.field("id", Long.class).eq(id))
                    .execute();
        } catch (RuntimeException e) {
            log.warn("下载计数更新失败 fileId={}", id, e);
        }
        return new FileDownload(resource, record.getOriginalName(), record.getContentType());
    }

    /** 逻辑删除文件记录并移除物理文件（保留来源模块/上传人记录供审计）。 */
    @Transactional
    public void delete(Long id) {
        SysFileDO record = JooqWriters.fetchById(dsl, JooqTables.SYS_FILE, SysFileDO.class, id);
        if (record == null) {
            throw BusinessException.of(FileErrorCode.FILE_NOT_FOUND, FileErrorCode.FILE_NOT_FOUND.message());
        }
        fileStorageService.delete(record.getStoredName());
        JooqWriters.delete(dsl, JooqTables.SYS_FILE, id, true);
    }

    /** 分页查询（含来源模块 / 上传人 / 文件名筛选，逻辑删除自动排除）。 */
    @Transactional(readOnly = true)
    public PageResult<FileView> page(int page, int size, String sourceModule, String uploaderName,
                                     String originalName, String sortField, String sortDirection) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(JooqTables.SYS_FILE));
        if (sourceModule != null && !sourceModule.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("source_module", String.class).eq(sourceModule.trim()));
        }
        if (uploaderName != null && !uploaderName.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("uploader_name", String.class)
                    .like("%" + uploaderName.trim() + "%"));
        }
        if (originalName != null && !originalName.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("original_name", String.class)
                    .like("%" + originalName.trim() + "%"));
        }
        Condition cond = DSL.and(conds);
        PageQuery q = PageQuery.of(page, size, sortField, sortDirection);
        var raw = JooqWriters.page(dsl, JooqTables.SYS_FILE, SysFileDO.class, cond, q,
                JooqSorts.resolve(JooqTables.SYS_FILE, q, FILE_SORT_FIELDS,
                        JooqTables.SYS_FILE.field("created_at", LocalDateTime.class).desc()));
        List<FileView> views = raw.records().stream().map(this::toView).toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    /** 导出用全量查询（无分页），筛选条件与 {@link #page} 一致。 */
    @Transactional(readOnly = true)
    public List<FileView> listAll(String sourceModule, String uploaderName, String originalName) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(JooqTables.SYS_FILE));
        if (sourceModule != null && !sourceModule.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("source_module", String.class).eq(sourceModule.trim()));
        }
        if (uploaderName != null && !uploaderName.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("uploader_name", String.class)
                    .like("%" + uploaderName.trim() + "%"));
        }
        if (originalName != null && !originalName.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("original_name", String.class)
                    .like("%" + originalName.trim() + "%"));
        }
        Condition cond = DSL.and(conds);
        List<SysFileDO> records = JooqWriters.fetchList(dsl, JooqTables.SYS_FILE, SysFileDO.class, cond,
                JooqTables.SYS_FILE.field("created_at", LocalDateTime.class).desc());
        return records.stream().map(this::toView).toList();
    }

    private FileView toView(SysFileDO d) {
        return new FileView(d.getId(), d.getOriginalName(), d.getFileSize(), d.getContentType(),
                d.getSourceModule(), d.getBizId(), d.getUploaderId(), d.getUploaderName(),
                d.getDownloadCount(), d.getCreatedAt());
    }
}
