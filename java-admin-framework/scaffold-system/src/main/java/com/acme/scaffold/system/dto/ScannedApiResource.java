package com.acme.scaffold.system.dto;

/**
 * 扫描结果中单条接口记录（尚未落库）。
 *
 * @param httpMethod       HTTP 方法（GET/POST/PUT/DELETE/PATCH）
 * @param pathPattern      完整路径，如 {@code /api/system/users/{id}}
 * @param permissionCode   {@code @PreAuthorize} 中解析出的权限码；无注解时为 {@link #NO_PERMISSION}
 * @param resourceName     中文资源名，取自 {@code @Operation(summary=...)}，缺失时回退为「方法名」
 * @param controllerMethod {@code 类简名#方法名}，便于定位源码
 * @param authMode         鉴权模式：{@link #AUTH_REQUIRED} / {@link #AUTH_ANONYMOUS}
 * @param hasPermission    是否声明了权限注解
 */
public record ScannedApiResource(String httpMethod, String pathPattern, String permissionCode,
                                 String resourceName, String controllerMethod,
                                 String authMode, boolean hasPermission) {

    /** 权限码占位符：接口未声明 {@code @PreAuthorize} 时使用，不参与授权匹配。 */
    public static final String NO_PERMISSION = "-";

    /** 需鉴权。 */
    public static final String AUTH_REQUIRED = "REQUIRED";

    /** 免鉴权（白名单接口），如登录、刷新令牌。 */
    public static final String AUTH_ANONYMOUS = "ANONYMOUS";

    /**
     * 业务唯一键，与表上 {@code uk_method_path (tenant_id, http_method, path_pattern)} 对齐。
     * 同步时以此判定「新增」还是「更新」。
     */
    public String uniqueKey() {
        return httpMethod + " " + pathPattern;
    }
}
