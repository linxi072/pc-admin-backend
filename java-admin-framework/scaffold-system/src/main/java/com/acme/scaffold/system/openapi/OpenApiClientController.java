package com.acme.scaffold.system.openapi;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.openapi.dto.ClientSecretView;
import com.acme.scaffold.system.openapi.dto.CreateOpenApiClientRequest;
import com.acme.scaffold.system.openapi.dto.OpenApiClientView;
import com.acme.scaffold.system.openapi.dto.UpdateOpenApiClientRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开放平台客户端接口（V48 API 开放平台 / M24）。
 *
 * <p><b>权限</b>：查询类端点取 {@code system:openapi-client:read}，写操作（注册/更新/删除/启停/轮换）
 * 取 {@code system:openapi-client:manage}——由 {@code ApiResourceAutoSyncRunner} 启动时扫描
 * {@code @PreAuthorize} 自动落库并授予 SUPER_ADMIN，无需手工登记。</p>
 *
 * <p><b>密钥一次性语义</b>：创建与轮换返回的 {@link ClientSecretView#plainSecret()} 仅此一次可见，
 * 服务端只存哈希；任何查询端点不回放明文。</p>
 *
 * <p><b>离线约束</b>：授权服务器依赖不在离线仓库，本接口只覆盖「客户端注册 + 密钥治理」，
 * 不提供 token 端点；协议级开放待联网补齐依赖后另行立项。</p>
 */
@Tag(name = "开放平台客户端")
@RestController
@RequestMapping("/api/system/openapi-clients")
@RequiredArgsConstructor
public class OpenApiClientController {

    private final OpenApiClientService service;

    @Operation(summary = "分页查询开放平台客户端（支持 clientId 模糊/status 筛选）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:openapi-client:read')")
    public Result<PageResult<OpenApiClientView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return Result.success(service.page(page, size, clientId, status, sortField, sortDirection));
    }

    @Operation(summary = "查询全部开放平台客户端（含停用）")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:openapi-client:read')")
    public Result<List<OpenApiClientView>> list() {
        return Result.success(service.list());
    }

    @Operation(summary = "查询单个开放平台客户端")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:openapi-client:read')")
    public Result<OpenApiClientView> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @Operation(summary = "注册客户端（返回明文密钥，仅此一次可见）")
    @PostMapping
    @PreAuthorize("hasAuthority('system:openapi-client:manage')")
    @AuditOperation(module = "system", type = "CREATE", name = "注册开放平台客户端")
    public Result<ClientSecretView> create(@Valid @RequestBody CreateOpenApiClientRequest request) {
        return Result.success(service.create(request));
    }

    @Operation(summary = "更新客户端（部分更新；client_id 不可变更，密钥变更走轮换）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:openapi-client:manage')")
    @AuditOperation(module = "system", type = "UPDATE", name = "更新开放平台客户端")
    public Result<Void> update(@PathVariable Long id,
                              @Valid @RequestBody UpdateOpenApiClientRequest request) {
        service.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除客户端（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:openapi-client:manage')")
    @AuditOperation(module = "system", type = "DELETE", name = "删除开放平台客户端")
    public Result<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return Result.success();
    }

    @Operation(summary = "启停客户端")
    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('system:openapi-client:manage')")
    @AuditOperation(module = "system", type = "UPDATE", name = "启停开放平台客户端")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        service.toggle(id, enabled);
        return Result.success();
    }

    @Operation(summary = "轮换密钥（旧密钥立即失效；返回新明文密钥，仅此一次可见）")
    @PostMapping("/{id}/rotate-secret")
    @PreAuthorize("hasAuthority('system:openapi-client:manage')")
    @AuditOperation(module = "system", type = "UPDATE", name = "轮换开放平台客户端密钥")
    public Result<ClientSecretView> rotateSecret(@PathVariable Long id) {
        return Result.success(service.rotateSecret(id));
    }
}
