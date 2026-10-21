package com.acme.scaffold.system.bootstrap;

import com.acme.scaffold.system.service.ApiResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 接口资源自动增量同步：应用启动后把 {@code @PreAuthorize} 声明的权限码自动登记到 sys_api_resource。
 *
 * <p>同步语义（见 {@link ApiResourceService#sync}）：
 * <ul>
 *   <li>库中无此 (method, path) → 新增；</li>
 *   <li>库中已有但不一致 → 仅更新权限码/资源名/鉴权模式，人工维护的 status / riskLevel 原样保留；</li>
 *   <li>完全一致 → 不触碰；库中失效记录 → 保留，不删除。</li>
 * </ul>
 * 这样新增接口无需手工登记也不会出现 403；在 V11 已播种全量目录的基础上做增量补充。
 * 失败仅记录日志，不阻塞应用启动（如库尚未初始化）。
 */
@Slf4j
@Component
@Order(300)
@RequiredArgsConstructor
public class ApiResourceAutoSyncRunner implements ApplicationRunner {

    private final ApiResourceService apiResourceService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var result = apiResourceService.sync();
            log.info("接口资源自动同步完成：新增 {}，更新 {}，未变 {}，跳过 {}，失效保留 {}",
                    result.insertedCount(), result.updatedCount(),
                    result.unchangedCount(), result.skippedCount(), result.orphanCount());
        } catch (Exception e) {
            log.error("接口资源自动同步失败（不影响启动）: {}", e.getMessage(), e);
        }
    }
}
