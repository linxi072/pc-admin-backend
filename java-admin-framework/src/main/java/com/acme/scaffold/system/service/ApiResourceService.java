package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.ApiResourceScanResult;
import com.acme.scaffold.system.dto.ApiResourceSyncResult;
import com.acme.scaffold.system.dto.ApiResourceView;
import com.acme.scaffold.system.dto.CreateApiResourceRequest;
import com.acme.scaffold.system.dto.ScannedApiResource;
import com.acme.scaffold.system.dto.UpdateApiResourceRequest;
import com.acme.scaffold.system.entity.SysApiResourceDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 接口资源服务。sys_api_resource 无 deleted 列，删除为物理删除（与原实现保持一致）。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ。
 *
 * <p>除手工 CRUD 外，本服务还承担「扫描同步」：把 {@link ApiResourceScanner} 从
 * {@code @PreAuthorize} 解析出的权限码自动落库，避免新增接口后忘记在页面上手工登记。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiResourceService {

    private final DSLContext dsl;
    private final ApiResourceScanner scanner;

    public List<ApiResourceView> list() {
        return JooqWriters.fetchList(dsl, JooqTables.SYS_API_RESOURCE, SysApiResourceDO.class,
                        JooqWriters.notDeleted(JooqTables.SYS_API_RESOURCE),
                        JooqTables.SYS_API_RESOURCE.field("id", Long.class).asc())
                .stream().map(ApiResourceView::from).collect(Collectors.toList());
    }

    @Transactional
    public Long create(CreateApiResourceRequest request) {
        SysApiResourceDO api = new SysApiResourceDO();
        api.setResourceName(request.resourceName());
        api.setPermissionCode(request.permissionCode());
        api.setHttpMethod(request.httpMethod().toUpperCase());
        api.setPathPattern(request.pathPattern());
        api.setAuthMode(request.authMode() == null ? "REQUIRED" : request.authMode());
        api.setRiskLevel(request.riskLevel() == null ? "NORMAL" : request.riskLevel());
        JooqWriters.insert(dsl, JooqTables.SYS_API_RESOURCE, api);
        return api.getId();
    }

    @Transactional
    public void delete(Long id) {
        SysApiResourceDO api = JooqWriters.fetchById(dsl, JooqTables.SYS_API_RESOURCE,
                SysApiResourceDO.class, id);
        if (api == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "接口资源不存在");
        }
        // 该表无 deleted 列，保持与迁移前一致的物理删除语义
        JooqWriters.delete(dsl, JooqTables.SYS_API_RESOURCE, id, false);
    }

    @Transactional
    public void update(Long id, UpdateApiResourceRequest request) {
        SysApiResourceDO existing = JooqWriters.fetchById(dsl, JooqTables.SYS_API_RESOURCE,
                SysApiResourceDO.class, id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "接口资源不存在");
        }
        // 该表无 deleted 列；tenant_id 维持原值
        SysApiResourceDO api = new SysApiResourceDO();
        api.setTenantId(existing.getTenantId());
        api.setResourceName(request.resourceName());
        api.setPermissionCode(request.permissionCode());
        api.setHttpMethod(request.httpMethod().toUpperCase());
        api.setPathPattern(request.pathPattern());
        api.setAuthMode(request.authMode() == null ? existing.getAuthMode() : request.authMode());
        api.setStatus(request.status() == null ? existing.getStatus() : request.status());
        api.setRiskLevel(request.riskLevel() == null ? existing.getRiskLevel() : request.riskLevel());
        JooqWriters.updateById(dsl, JooqTables.SYS_API_RESOURCE, id, api);
    }

    // ------------------------------------------------------------------
    // 扫描同步：把 @PreAuthorize 权限码自动登记到 sys_api_resource
    // ------------------------------------------------------------------

    /**
     * 扫描并与库内现有数据比对，<b>不写库</b>，供前端预览差异。
     *
     * @return 差异结果：待新增 / 待更新 / 失效 / 无变化
     */
    public ApiResourceScanResult scanPreview() {
        List<ScannedApiResource> scanned = scanner.scan();
        Map<String, SysApiResourceDO> existing = mapByUniqueKey();

        List<ScannedApiResource> fresh = new ArrayList<>();
        List<ScannedApiResource> changed = new ArrayList<>();
        Set<String> liveKeys = new HashSet<>();
        long unchanged = 0L;

        for (ScannedApiResource item : scanned) {
            // 免鉴权接口不参与授权模型，跳过比对
            if (!scanner.shouldPersist(item)) {
                continue;
            }
            liveKeys.add(item.uniqueKey());
            SysApiResourceDO current = existing.get(item.uniqueKey());
            if (current == null) {
                fresh.add(item);
            } else if (isOutdated(item, current)) {
                changed.add(item);
            } else {
                unchanged++;
            }
        }

        // 库里有、代码里已无的接口：仅报告不删除（sys_role_api 可能仍引用）
        List<ApiResourceView> orphans = existing.entrySet().stream()
                .filter(e -> !liveKeys.contains(e.getKey()))
                .map(e -> ApiResourceView.from(e.getValue()))
                .toList();

        return new ApiResourceScanResult(fresh, changed, orphans, unchanged, scanned.size());
    }

    /**
     * 执行同步：把扫描结果写入库。
     *
     * <p>语义：
     * <ul>
     *   <li>库中无此 (method, path) → 新增</li>
     *   <li>库中已有但权限码/资源名/鉴权模式不一致 → <b>仅更新这三个字段</b>，
     *       人工填写的 status / riskLevel 原样保留</li>
     *   <li>完全一致 → 不触碰（保证 updated_at 不被无意义刷新）</li>
     *   <li>库中失效记录 → 保留，不删除</li>
     * </ul>
     *
     * @return 本次写入统计
     * @throws BusinessException 数据库层写入失败时向上抛出（唯一键冲突等）
     */
    @Transactional
    public ApiResourceSyncResult sync() {
        ApiResourceScanResult preview = scanPreview();
        Map<String, SysApiResourceDO> existing = mapByUniqueKey();

        long inserted = 0L;
        long updated = 0L;

        for (ScannedApiResource item : preview.newResources()) {
            SysApiResourceDO api = new SysApiResourceDO();
            api.setResourceName(item.resourceName());
            api.setPermissionCode(item.permissionCode());
            api.setHttpMethod(item.httpMethod());
            api.setPathPattern(item.pathPattern());
            api.setControllerMethod(item.controllerMethod());
            api.setAuthMode(item.authMode());
            JooqWriters.insert(dsl, JooqTables.SYS_API_RESOURCE, api);
            inserted++;
        }

        for (ScannedApiResource item : preview.changedResources()) {
            SysApiResourceDO current = existing.get(item.uniqueKey());
            if (current == null) {
                // 扫描与写入之间被并发删除：降级为新增，避免丢记录
                SysApiResourceDO api = new SysApiResourceDO();
                api.setResourceName(item.resourceName());
                api.setPermissionCode(item.permissionCode());
                api.setHttpMethod(item.httpMethod());
                api.setPathPattern(item.pathPattern());
                api.setControllerMethod(item.controllerMethod());
                api.setAuthMode(item.authMode());
                JooqWriters.insert(dsl, JooqTables.SYS_API_RESOURCE, api);
                inserted++;
                continue;
            }
            SysApiResourceDO patch = new SysApiResourceDO();
            patch.setTenantId(current.getTenantId());
            patch.setResourceName(item.resourceName());
            patch.setPermissionCode(item.permissionCode());
            patch.setHttpMethod(item.httpMethod());
            patch.setPathPattern(item.pathPattern());
            patch.setControllerMethod(item.controllerMethod());
            patch.setAuthMode(item.authMode());
            // status / riskLevel 由人工维护，同步不得覆盖。
            // 注意：SysApiResourceDO 对这两个字段做了默认初始化（ACTIVE / NORMAL），
            // 若不显式置 null，updateById 的「跳过 null」策略会失效并把人工值覆盖掉。
            patch.setStatus(null);
            patch.setRiskLevel(null);
            JooqWriters.updateById(dsl, JooqTables.SYS_API_RESOURCE, current.getId(), patch);
            updated++;
        }

        long skipped = preview.total() - preview.newResources().size()
                - preview.changedResources().size() - preview.unchangedCount();
        ApiResourceSyncResult result = new ApiResourceSyncResult(
                inserted, updated, preview.unchangedCount(), Math.max(skipped, 0), preview.orphanedResources().size());
        log.info("接口资源同步完成: {}", result);
        return result;
    }

    /** 现有记录按「METHOD path」建索引，与扫描结果的 uniqueKey 对齐。单次查询，避免 N+1。 */
    private Map<String, SysApiResourceDO> mapByUniqueKey() {
        List<SysApiResourceDO> rows = JooqWriters.fetchList(dsl, JooqTables.SYS_API_RESOURCE,
                SysApiResourceDO.class, JooqWriters.notDeleted(JooqTables.SYS_API_RESOURCE));
        Map<String, SysApiResourceDO> map = new HashMap<>();
        for (SysApiResourceDO row : rows) {
            if (row.getHttpMethod() == null || row.getPathPattern() == null) {
                continue;
            }
            map.put(row.getHttpMethod().toUpperCase() + " " + row.getPathPattern(), row);
        }
        return map;
    }

    /** 判断库中记录是否已与代码不一致（只看自动同步负责的三个字段）。 */
    private boolean isOutdated(ScannedApiResource item, SysApiResourceDO current) {
        return !Objects.equals(item.permissionCode(), current.getPermissionCode())
                || !Objects.equals(item.resourceName(), current.getResourceName())
                || !Objects.equals(item.authMode(), current.getAuthMode());
    }
}
