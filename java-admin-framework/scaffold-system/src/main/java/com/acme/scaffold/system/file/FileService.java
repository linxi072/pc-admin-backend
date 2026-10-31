package com.acme.scaffold.system.file;

import com.acme.scaffold.common.api.dto.KeysetPageQuery;
import com.acme.scaffold.common.api.dto.KeysetPageResult;
import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooq.paging.JooqKeysetPager;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.system.entity.SysFileDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
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
 *   <li>删除为逻辑删除（deleted=1），保留来源模块/上传人记录，物理文件按引用计数保护性清理；</li>
 *   <li>秒传（V50）：上传时计算内容 SHA-256，命中存量记录则复用物理文件不落盘。</li>
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

    /** 批量删除单次上限（防御性限制，避免超长 ID 列表拖垮事务）。 */
    private static final int BATCH_DELETE_LIMIT = 100;

    /** 桥接自代码生成层 {@code Tables.SYS_FILE} 的类型安全表引用（B22）。 */
    private static final JooqTables.TableRef FILE = JooqTables.SYS_FILE;

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
     * <p>秒传（V50）：魔数校验通过后计算内容 SHA-256，命中未删记录且物理文件仍存在时，
     * 复用其 stored_name / storage_path，不落盘、不产生新物理文件（响应 deduplicated=true）。
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

        // 秒传判定（V50）：内容 SHA-256 命中未删记录、且物理文件仍存在时复用，跳过落盘
        String contentHash;
        try (InputStream hashIn = file.getInputStream()) {
            contentHash = FileDedupRules.sha256Hex(hashIn);
        } catch (IOException e) {
            throw BusinessException.of(FileErrorCode.FILE_STORE_FAILED, "文件读取失败，无法计算内容摘要");
        }
        SysFileDO existing = findByContentHash(contentHash);
        boolean deduplicated = existing != null && fileStorageService.exists(existing.getStoredName());
        String storedName;
        Path abs;
        if (deduplicated) {
            storedName = existing.getStoredName();
            abs = fileStorageService.resolve(storedName);
        } else {
            // 先落盘（文件名服务端生成），再写记录；落盘失败直接抛异常，不会留脏数据
            storedName = fileStorageService.store(file, ext);
            abs = fileStorageService.resolve(storedName);
        }

        SysFileDO record = new SysFileDO();
        record.setOriginalName(file.getOriginalFilename());
        record.setStoredName(storedName);
        record.setStoragePath(abs.toString());
        record.setFileSize(file.getSize());
        record.setContentType(file.getContentType());
        record.setContentHash(contentHash);
        record.setSourceModule(sourceModule.trim());
        record.setBizId(bizId);
        record.setUploaderId(principal.userId());
        record.setUploaderName(principal.displayName() != null ? principal.displayName() : principal.username());
        try {
            JooqWriters.insert(dsl, FILE, record);
        } catch (RuntimeException e) {
            // DB 写入失败：仅清理本次新落盘的孤儿文件（秒传复用场景无新文件可清）
            if (!deduplicated) {
                fileStorageService.delete(storedName);
            }
            throw e;
        }
        return toView(record, deduplicated);
    }

    /** 下载：返回文件资源与原始文件名（Content-Disposition 由 Controller 设置）。 */
    public FileDownload download(Long id) {
        SysFileDO record = JooqWriters.fetchById(dsl, FILE, SysFileDO.class, id);
        if (record == null) {
            throw BusinessException.of(FileErrorCode.FILE_NOT_FOUND, FileErrorCode.FILE_NOT_FOUND.message());
        }
        Resource resource = fileStorageService.loadAsResource(record.getStoredName());
        // 下载计数 +1（乐观自增；失败仅告警，不阻塞下载）
        try {
            dsl.update(FILE.table())
                    .set(JooqTables.SYS_FILE.field("download_count", Long.class), JooqTables.SYS_FILE.field("download_count", Long.class).plus(1))
                    .where(JooqTables.SYS_FILE.field("id", Long.class).eq(id))
                    .execute();
        } catch (RuntimeException e) {
            log.warn("下载计数更新失败 fileId={}", id, e);
        }
        return new FileDownload(resource, record.getOriginalName(), record.getContentType());
    }

    /**
     * 逻辑删除文件记录（保留来源模块/上传人记录供审计）。
     *
     * <p>物理文件按引用计数保护（V50）：同一 stored_name 还存在其它未删记录时，
     * 仅逻辑删本记录、保留物理文件，避免误删仍被引用的文件。
     */
    @Transactional
    public void delete(Long id) {
        deleteOne(id);
    }

    /**
     * 批量逻辑删除（部分成功语义，V50）：单个 ID 失败（不存在/异常）不影响其余，
     * 逐项 try-catch 吞异常（事务内不传播即不回滚），返回成功数与失败明细。
     */
    @Transactional
    public BatchDeleteView batchDelete(List<Long> ids) {
        List<Long> distinct = FileDedupRules.distinctIds(ids);
        if (distinct.isEmpty()) {
            throw BusinessException.of(FileErrorCode.FILE_BATCH_EMPTY, FileErrorCode.FILE_BATCH_EMPTY.message());
        }
        if (distinct.size() > BATCH_DELETE_LIMIT) {
            throw BusinessException.of(FileErrorCode.FILE_BATCH_TOO_MANY,
                    "单次最多删除 " + BATCH_DELETE_LIMIT + " 个文件");
        }
        int deleted = 0;
        List<BatchDeleteFailure> failures = new ArrayList<>();
        for (Long id : distinct) {
            try {
                deleteOne(id);
                deleted++;
            } catch (BusinessException e) {
                failures.add(new BatchDeleteFailure(id, e.getMessage()));
            } catch (RuntimeException e) {
                log.warn("批量删除单项失败 fileId={}", id, e);
                failures.add(new BatchDeleteFailure(id, "删除失败，请稍后重试"));
            }
        }
        return new BatchDeleteView(deleted, failures);
    }

    /** 删除单条记录（无事务注解，供批量删除在同事务内逐项调用，避免 Spring 自调用不走代理）。 */
    private void deleteOne(Long id) {
        SysFileDO record = JooqWriters.fetchById(dsl, FILE, SysFileDO.class, id);
        if (record == null) {
            throw BusinessException.of(FileErrorCode.FILE_NOT_FOUND, FileErrorCode.FILE_NOT_FOUND.message());
        }
        long refCount = JooqWriters.count(dsl, FILE, DSL.and(
                JooqWriters.notDeleted(FILE),
                JooqTables.SYS_FILE.field("stored_name", String.class).eq(record.getStoredName())));
        if (FileDedupRules.shouldDeletePhysical(refCount)) {
            fileStorageService.delete(record.getStoredName());
        } else {
            log.info("物理文件被 {} 条记录共享，保留磁盘文件 storedName={}", refCount, record.getStoredName());
        }
        JooqWriters.delete(dsl, FILE, id, true);
    }

    /**
     * 秒传预检（V50）：前端上传前计算内容 SHA-256 回传，服务端告知是否已存在同内容文件。
     * 哈希非法（非 64 位十六进制）直接 400，不进入存储层。
     */
    @Transactional(readOnly = true)
    public DedupCheckView checkDedup(String hash) {
        String normalized = FileDedupRules.normalizeHash(hash);
        SysFileDO hit = findByContentHash(normalized);
        if (hit == null) {
            return new DedupCheckView(normalized, false, null, null, null);
        }
        return new DedupCheckView(normalized, true, hit.getId(), hit.getOriginalName(), hit.getFileSize());
    }

    /** 文件统计（V50）：按来源模块聚合数量与占用空间；空表自然返回零值，无需特殊分支。 */
    @Transactional(readOnly = true)
    public FileStatsView stats() {
        Result<Record3<String, Integer, BigDecimal>> rs = dsl
                .select(JooqTables.SYS_FILE.field("source_module", String.class), DSL.count(), DSL.sum(JooqTables.SYS_FILE.field("file_size", Long.class)))
                .from(FILE.table())
                .where(JooqWriters.notDeleted(FILE))
                .groupBy(JooqTables.SYS_FILE.field("source_module", String.class))
                .orderBy(DSL.count().desc())
                .fetch();
        List<ModuleFileStat> modules = new ArrayList<>(rs.size());
        long totalCount = 0L;
        long totalSize = 0L;
        for (Record3<String, Integer, BigDecimal> r : rs) {
            long count = r.value2().longValue();
            long size = r.value3() == null ? 0L : r.value3().longValue();
            modules.add(new ModuleFileStat(r.value1(), count, size));
            totalCount += count;
            totalSize += size;
        }
        return new FileStatsView(totalCount, totalSize, modules);
    }

    /** 按内容哈希查未删记录（秒传锚点）；无命中返回 null。 */
    private SysFileDO findByContentHash(String contentHash) {
        return JooqWriters.fetchOne(dsl, FILE, SysFileDO.class, DSL.and(
                JooqWriters.notDeleted(FILE),
                JooqTables.SYS_FILE.field("content_hash", String.class).eq(contentHash)));
    }

    /** 分页查询（含来源模块 / 上传人 / 文件名筛选，逻辑删除自动排除）。 */
    @Transactional(readOnly = true)
    public PageResult<FileView> page(int page, int size, String sourceModule, String uploaderName,
                                     String originalName, String sortField, String sortDirection) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(FILE));
        if (sourceModule != null && !sourceModule.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("source_module", String.class).eq(sourceModule.trim()));
        }
        if (uploaderName != null && !uploaderName.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("uploader_name", String.class).like("%" + uploaderName.trim() + "%"));
        }
        if (originalName != null && !originalName.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("original_name", String.class).like("%" + originalName.trim() + "%"));
        }
        Condition cond = DSL.and(conds);
        PageQuery q = PageQuery.of(page, size, sortField, sortDirection);
        var raw = JooqWriters.page(dsl, FILE, SysFileDO.class, cond, q,
                JooqSorts.resolve(FILE, q, FILE_SORT_FIELDS,
                        JooqTables.SYS_FILE.field("created_at", java.time.LocalDateTime.class).desc()));
        List<FileView> views = toViewsWithSharedFlag(raw.records());
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    /** 导出用全量查询（无分页），筛选条件与 {@link #page} 一致。 */
    @Transactional(readOnly = true)
    public List<FileView> listAll(String sourceModule, String uploaderName, String originalName) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(FILE));
        if (sourceModule != null && !sourceModule.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("source_module", String.class).eq(sourceModule.trim()));
        }
        if (uploaderName != null && !uploaderName.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("uploader_name", String.class).like("%" + uploaderName.trim() + "%"));
        }
        if (originalName != null && !originalName.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("original_name", String.class).like("%" + originalName.trim() + "%"));
        }
        Condition cond = DSL.and(conds);
        List<SysFileDO> records = JooqWriters.fetchList(dsl, FILE, SysFileDO.class, cond,
                JooqTables.SYS_FILE.field("created_at", java.time.LocalDateTime.class).desc());
        return toViewsWithSharedFlag(records);
    }

    /**
     * keyset 游标分页（深翻页优化）：固定按 (created_at DESC, id DESC) 排序，
     * 游标由服务端基于上一页末条记录的排序键生成，避免 offset 分页随偏移量退化。
     *
     * <p>筛选条件与 {@link #page} 一致；排序键含 id 作为 tie-breaker 以保证顺序唯一确定。
     */
    @Transactional(readOnly = true)
    public KeysetPageResult<FileView> keysetPage(int size, String cursor,
                                                String sourceModule, String uploaderName, String originalName) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(FILE));
        if (sourceModule != null && !sourceModule.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("source_module", String.class).eq(sourceModule.trim()));
        }
        if (uploaderName != null && !uploaderName.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("uploader_name", String.class).like("%" + uploaderName.trim() + "%"));
        }
        if (originalName != null && !originalName.isBlank()) {
            conds.add(JooqTables.SYS_FILE.field("original_name", String.class).like("%" + originalName.trim() + "%"));
        }
        Condition cond = DSL.and(conds);
        KeysetPageQuery q = new KeysetPageQuery(size, cursor);
        Field<?>[] keyFields = new Field<?>[]{
                JooqTables.SYS_FILE.field("created_at", java.time.LocalDateTime.class),
                JooqTables.SYS_FILE.field("id", Long.class)
        };
        boolean[] ascending = {false, false}; // created_at DESC, id DESC
        KeysetPageResult<SysFileDO> raw = JooqKeysetPager.query(dsl, FILE, SysFileDO.class,
                cond, q, f -> new Object[]{f.getCreatedAt(), f.getId()}, keyFields, ascending);
        List<FileView> views = toViewsWithSharedFlag(raw.records());
        return KeysetPageResult.of(raw.size(), views, raw.nextCursor());
    }

    private FileView toView(SysFileDO d, boolean deduplicated) {
        return new FileView(d.getId(), d.getOriginalName(), d.getFileSize(), d.getContentType(),
                d.getSourceModule(), d.getBizId(), d.getUploaderId(), d.getUploaderName(),
                d.getDownloadCount(), d.getCreatedAt(), deduplicated);
    }

    /**
     * 列表视图映射 + 「物理文件共享」标记（V50）：一次 GROUP BY 统计本页各 stored_name 的
     * 未删引用数，&gt;1 即秒传共享组（删除时不会释放物理文件），前端以标签列展示。
     */
    private List<FileView> toViewsWithSharedFlag(List<SysFileDO> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<String> names = new java.util.LinkedHashSet<>();
        for (SysFileDO d : records) {
            names.add(d.getStoredName());
        }
        Result<Record2<String, Integer>> rs = dsl
                .select(JooqTables.SYS_FILE.field("stored_name", String.class), DSL.count())
                .from(FILE.table())
                .where(DSL.and(JooqWriters.notDeleted(FILE),
                        JooqTables.SYS_FILE.field("stored_name", String.class).in(names)))
                .groupBy(JooqTables.SYS_FILE.field("stored_name", String.class))
                .fetch();
        Set<String> shared = new java.util.HashSet<>();
        for (Record2<String, Integer> r : rs) {
            if (r.value2().longValue() > 1) {
                shared.add(r.value1());
            }
        }
        List<FileView> views = new ArrayList<>(records.size());
        for (SysFileDO d : records) {
            views.add(toView(d, shared.contains(d.getStoredName())));
        }
        return views;
    }
}
