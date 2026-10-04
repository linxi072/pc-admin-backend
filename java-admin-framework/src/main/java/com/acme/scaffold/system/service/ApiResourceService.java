package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.ApiResourceView;
import com.acme.scaffold.system.dto.CreateApiResourceRequest;
import com.acme.scaffold.system.entity.SysApiResourceDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 接口资源服务。sys_api_resource 无 deleted 列，删除为物理删除（与原实现保持一致）。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Service
@RequiredArgsConstructor
public class ApiResourceService {

    private final DSLContext dsl;

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
}
