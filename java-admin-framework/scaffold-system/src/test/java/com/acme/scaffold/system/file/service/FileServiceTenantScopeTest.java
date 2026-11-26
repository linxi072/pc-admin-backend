package com.acme.scaffold.system.file.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.DynamicMapper;
import com.acme.scaffold.mybatis.dsl.DynamicStatement;
import com.acme.scaffold.system.file.domain.model.SysFile;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

/** 文件管理直连 DSL 的租户隔离回归：统计/聚合/计数更新都不能绕过 sys_file 的 tenant_id 条件。 */
class FileServiceTenantScopeTest {

    private CapturingMapper mapper;
    private FileService service;

    @BeforeEach
    void setUp() {
        TenantProperties props = new TenantProperties();
        props.setEnabled(true);
        props.setEnforce(true);
        props.setDefaultTenantId(0L);
        props.setPlatformTables(List.of("sys_menu", "sys_dict_data"));
        TenantEnforcement.configure(props);
        TenantContext.set(7L, false);

        mapper = new CapturingMapper();
        FileStorageRouter storage = mock(FileStorageRouter.class);
        when(storage.loadAsResource("stored-1")).thenReturn(mock(Resource.class));
        service = new FileService(new DSLContext(mapper), storage, mock(
                com.acme.scaffold.common.context.SecurityContextFacade.class));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TenantEnforcement.configure(null);
    }

    @Test
    void statsShouldFilterByTenant() {
        service.stats();

        assertTrue(mapper.statements.stream()
                .anyMatch(s -> s.rawSql().contains("GROUP BY") && hasTenant(s)));
    }

    @Test
    void sharedFlagCountShouldFilterByTenant() throws Exception {
        SysFile file = new SysFile();
        file.setId(10L);
        file.setStoredName("stored-1");
        Method method = FileService.class.getDeclaredMethod("toViewsWithSharedFlag", List.class);
        method.setAccessible(true);

        method.invoke(service, List.of(file));

        assertTrue(mapper.statements.stream()
                .anyMatch(s -> s.rawSql().contains("stored_name") && hasTenant(s)));
    }

    @Test
    void downloadCountUpdateShouldFilterByTenant() {
        mapper.selectRows = List.of(CapturingMapper.fileRow());
        service.download(10L);

        assertTrue(mapper.statements.stream()
                .anyMatch(s -> s.rawSql().contains("UPDATE") && hasTenant(s)));
    }

    @Test
    void enforceDisabledShouldNotInjectTenantCondition() {
        TenantEnforcement.configure(null);
        mapper.statements.clear();

        service.stats();

        assertFalse(mapper.statements.stream().anyMatch(s -> hasTenant(s)));
    }

    private static boolean hasTenant(DynamicStatement statement) {
        return statement.rawSql().contains("tenant_id") && statement.getBinds().contains(7L);
    }

    private static final class CapturingMapper implements DynamicMapper {

        private final List<DynamicStatement> statements = new ArrayList<>();
        private List<Map<String, Object>> selectRows = List.of();

        @Override
        public List<Map<String, Object>> select(DynamicStatement statement) {
            statements.add(statement);
            return new ArrayList<>(selectRows);
        }

        @Override
        public Map<String, Object> selectOne(DynamicStatement statement) {
            statements.add(statement);
            return fileRow();
        }

        @Override
        public int update(DynamicStatement statement) {
            statements.add(statement);
            return 1;
        }

        @Override
        public int insert(DynamicStatement statement) {
            statements.add(statement);
            return 1;
        }

        @Override
        public int delete(DynamicStatement statement) {
            statements.add(statement);
            return 1;
        }

        private static Map<String, Object> fileRow() {
            LocalDateTime now = LocalDateTime.now();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 10L);
            row.put("tenant_id", 7L);
            row.put("stored_name", "stored-1");
            row.put("original_name", "a.txt");
            row.put("content_type", "text/plain");
            row.put("file_size", 12L);
            row.put("source_module", "file-management");
            row.put("uploader_id", 42L);
            row.put("uploader_name", "alice");
            row.put("download_count", 0L);
            row.put("deleted", 0);
            row.put("created_at", Timestamp.valueOf(now));
            row.put("updated_at", Timestamp.valueOf(now));
            return row;
        }
    }
}
