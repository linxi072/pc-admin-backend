package com.acme.scaffold.system.export.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.system.export.config.ExportProperties;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/** 异步导出上下文传播回归：租户作用域与认证主体必须随任务离场，且任务结束后不污染工作线程。 */
class ExportContextPropagationTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void asyncExportShouldInheritTenantAndPrincipal() throws Exception {
        AtomicReference<Long> tenantSeen = new AtomicReference<>();
        AtomicReference<Long> userIdSeen = new AtomicReference<>();
        ExportCenterService service = service(task(tenantSeen, userIdSeen), command -> new Thread(command).start());
        installCallerContext();

        ExportCenterService.ExportedFile file = service.downloadAsync(
                Map.of("code", "ctx", "format", "CSV")).get(5, TimeUnit.SECONDS);

        assertNotNull(file.body());
        assertEquals(7L, tenantSeen.get());
        assertEquals(42L, userIdSeen.get());
    }

    @Test
    void workerThreadContextShouldBeRestoredAfterExport() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            ExportCenterService service = service(task(new AtomicReference<>(), new AtomicReference<>()), pool);
            installCallerContext();

            service.downloadAsync(Map.of("code", "ctx", "format", "CSV")).get(5, TimeUnit.SECONDS);

            AtomicReference<TenantContext.TenantScope> tenantAfter = new AtomicReference<>();
            AtomicReference<Authentication> authAfter = new AtomicReference<>();
            CompletableFuture.runAsync(() -> {
                tenantAfter.set(TenantContext.current());
                authAfter.set(SecurityContextHolder.getContext().getAuthentication());
            }, pool).get(5, TimeUnit.SECONDS);

            assertNull(tenantAfter.get());
            assertNull(authAfter.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void asyncExportWithoutCallerContextShouldStillComplete() throws Exception {
        AtomicReference<Long> tenantSeen = new AtomicReference<>();
        AtomicReference<Long> userIdSeen = new AtomicReference<>();
        ExportCenterService service = service(task(tenantSeen, userIdSeen), command -> new Thread(command).start());

        ExportCenterService.ExportedFile file = service.downloadAsync(
                Map.of("code", "ctx", "format", "CSV")).get(5, TimeUnit.SECONDS);

        assertNotNull(file.body());
        assertNull(tenantSeen.get());
        assertNull(userIdSeen.get());
    }

    private static ExportCenterService service(ExportTask task, Executor executor) throws Exception {
        ExportCenterService service = new ExportCenterService(List.of(task), new ExportProperties());
        Field field = ExportCenterService.class.getDeclaredField("exportExecutor");
        field.setAccessible(true);
        field.set(service, executor);
        return service;
    }

    private static void installCallerContext() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal(), null, List.of()));
        SecurityContextHolder.setContext(context);
        TenantContext.set(7L, false);
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(42L, "alice", "Alice", 7L, "web", 1,
                Set.of("USER"), Set.of("system:export:read"));
    }

    private static ExportTask task(AtomicReference<Long> tenantSeen, AtomicReference<Long> userIdSeen) {
        return new ExportTask() {
            @Override
            public String code() {
                return "ctx";
            }

            @Override
            public String displayName() {
                return "上下文测试";
            }

            @Override
            public String fileName() {
                return "ctx";
            }

            @Override
            public List<String> headers() {
                return List.of("value");
            }

            @Override
            public List<List<String>> rows(Map<String, String> params) {
                tenantSeen.set(TenantContext.isSet() ? TenantContext.requireTenantId() : null);
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                userIdSeen.set(auth != null && auth.getPrincipal() instanceof CurrentPrincipal p
                        ? p.userId() : null);
                return List.of(List.of("ok"));
            }
        };
    }
}
