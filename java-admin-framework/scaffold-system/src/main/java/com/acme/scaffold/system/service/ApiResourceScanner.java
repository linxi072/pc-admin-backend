package com.acme.scaffold.system.service;

import com.acme.scaffold.system.dto.ScannedApiResource;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 接口资源扫描器：从 Spring MVC 的已注册路由反向生成 {@code sys_api_resource} 记录。
 *
 * <p><b>解决什么问题</b>：项目里每个 Controller 方法都用 {@code @PreAuthorize("hasAuthority('x:y:z')")}
 * 声明了权限码，而 {@code sys_api_resource} 表<b>没有任何种子数据</b>，导致新增接口后
 * 必须在「接口资源管理」页面手工逐条登记，否则角色无法被授予该权限（表现为 403）。
 * 62 处注解意味着 62 次手工操作，且极易漏登。本扫描器把这些注解自动落库。
 *
 * <p><b>数据来源</b>：{@link RequestMappingHandlerMapping}（即应用真实生效的路由表），
 * 而非源码解析。因此它看到的路径与最终对外暴露的一致，包含类级 {@code @RequestMapping} 前缀拼接结果。
 *
 * <p><b>权限码提取</b>：仅识别 {@code hasAuthority('...')} / {@code hasAnyAuthority('a','b')} 形式，
 * 多个权限码取第一个作为主权限码（框架当前均为单权限写法）。
 * 识别不到时 {@link ScannedApiResource#permissionCode()} 为 {@link ScannedApiResource#NO_PERMISSION}。
 *
 * <p><b>不做物理删除</b>：代码中已移除的接口会被标记为「失效」供人工确认，
 * 不会自动从库中删除——因为 {@code sys_role_api} 可能仍引用它，贸然删除会造成授权悬空。
 */
@Slf4j
@Component
public class ApiResourceScanner {

    /** 只扫描业务接口前缀，actuator / swagger / error 等基础设施路径自动排除。 */
    private static final String API_PREFIX = "/api/";

    /** 显式匹配 hasAuthority('x') 与 hasAnyAuthority('x', 'y')。 */
    private static final Pattern AUTHORITY_PATTERN =
            Pattern.compile("has(?:Any)?Authority\\(\\s*'([^']+)'");

    /** 权限码格式：module:resource:action，扫描结果按此做基本合法性校验。 */
    private static final Pattern PERMISSION_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");

    /**
     * 免鉴权路径前缀（与 SecurityConfig 的白名单保持一致）。
     * <p>注意不含 {@code /api/auth/logout}——登出虽无 {@code @PreAuthorize}，但仍需令牌才能确定吊销谁，
     * 属于鉴权接口。
     */
    private static final List<String> ANONYMOUS_PREFIXES = List.of(
            "/api/auth/login", "/api/auth/refresh");

    private final RequestMappingHandlerMapping handlerMapping;

    public ApiResourceScanner(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    /**
     * 扫描全部业务接口。
     *
     * @return 按「路径 → 方法」排序的接口列表；无接口时返回空列表（不抛异常）
     */
    public List<ScannedApiResource> scan() {
        List<ScannedApiResource> result = new ArrayList<>();
        // 用 Set 兜底去重：同一 Controller 方法可能被多个条件组合重复注册
        Set<String> seen = new LinkedHashSet<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();
            if (handlerMethod == null) {
                continue;
            }
            String path = info.getPathPatternsCondition() == null
                    ? null : info.getPathPatternsCondition().getPatternValues().stream()
                    .findFirst().orElse(null);
            if (path == null || !path.startsWith(API_PREFIX)) {
                continue;
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            if (methods.isEmpty()) {
                // 未限定方法（如 @RequestMapping 无 method），按 ALL 登记，避免漏掉可被任意方法访问的路由
                addOnce(result, seen, build(path, "ALL", handlerMethod));
                continue;
            }
            for (RequestMethod method : methods) {
                addOnce(result, seen, build(path, method.name(), handlerMethod));
            }
        }

        result.sort(Comparator.comparing(ScannedApiResource::pathPattern)
                .thenComparing(ScannedApiResource::httpMethod));
        log.info("接口资源扫描完成，共识别 {} 个业务接口", result.size());
        return result;
    }

    /** 由 path + method 构建扫描记录；重复键直接跳过。 */
    private void addOnce(List<ScannedApiResource> result, Set<String> seen, ScannedApiResource item) {
        if (seen.add(item.uniqueKey())) {
            result.add(item);
        }
    }

    /** 从 HandlerMethod 上提取路径、方法、权限码与中文名。 */
    private ScannedApiResource build(String path, String httpMethod, HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        String permissionCode = extractPermissionCode(method);
        boolean anonymous = isAnonymous(path);
        String authMode = anonymous ? ScannedApiResource.AUTH_ANONYMOUS : ScannedApiResource.AUTH_REQUIRED;
        // extractPermissionCode 在无注解时返回占位符而非 null，故此处用占位符判定「是否真有权限码」
        boolean hasPermission = !ScannedApiResource.NO_PERMISSION.equals(permissionCode);
        return new ScannedApiResource(
                httpMethod.toUpperCase(Locale.ROOT),
                path,
                permissionCode,
                extractSummary(method),
                method.getDeclaringClass().getSimpleName() + "#" + method.getName(),
                authMode,
                hasPermission);
    }

    /**
     * 解析 {@code @PreAuthorize} 中的权限码。
     *
     * @return 权限码；无注解、非 {@code hasAuthority} 形式或格式非法时返回 {@link ScannedApiResource#NO_PERMISSION}
     */
    private String extractPermissionCode(Method method) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        if (preAuthorize == null) {
            return ScannedApiResource.NO_PERMISSION;
        }
        Matcher matcher = AUTHORITY_PATTERN.matcher(preAuthorize.value());
        if (!matcher.find()) {
            return ScannedApiResource.NO_PERMISSION;
        }
        String code = matcher.group(1).trim();
        // 权限码会参与角色授权匹配，格式非法的（如写错模块名）不入库，避免污染授权数据
        if (!PERMISSION_CODE_PATTERN.matcher(code).matches()) {
            log.warn("权限码格式非法，已跳过自动登记: method={} code={}", method.getName(), code);
            return ScannedApiResource.NO_PERMISSION;
        }
        return code;
    }

    /** 资源名优先取 {@code @Operation(summary=...)}；缺失时用方法名兜底，避免出现空白名称。 */
    private String extractSummary(Method method) {
        Operation operation = method.getAnnotation(Operation.class);
        if (operation != null && operation.summary() != null && !operation.summary().isBlank()) {
            return operation.summary().trim();
        }
        return method.getName();
    }

    /** 是否在鉴权白名单路径内。 */
    private boolean isAnonymous(String path) {
        return ANONYMOUS_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /**
     * 供同步流程使用：判断扫描记录是否「应当落库」。
     *
     * <p>以下两类接口不落库：
     * <ul>
     *   <li><b>无权限码</b>：未声明 {@code @PreAuthorize} 的接口（如个人中心 /api/profile/**，
     *       走「只能操作自己数据」的行级校验，不参与权限码授权）。写入占位符 {@code -} 会污染授权数据；</li>
     *   <li><b>免鉴权接口</b>：登录/刷新令牌，不进授权模型。</li>
     * </ul>
     */
    public boolean shouldPersist(ScannedApiResource item) {
        return item.hasPermission() && ScannedApiResource.AUTH_REQUIRED.equals(item.authMode());
    }
}
