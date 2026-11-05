package com.acme.scaffold.admin.config;

import com.acme.scaffold.common.audit.domain.SysOperationLogDO;
import com.acme.scaffold.jooq.mapping.SnakeRecordMapper;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据源时区一致性契约测试（闭环 {@code QA-TEST-REPORT-TEN-MILLION-V1.1.0.md} §8.1 中危风险）。
 *
 * <p>三层防线：① 生产启动期 fail-fast 校验；② DATETIME 边界值在真实映射器下的往返语义
 * （把「+8h 来自 JVM 时区而非 SnakeRecordMapper」固化为可执行断言）；③ 部署配置契约，
 * 防止 Dockerfile / compose / CI / 环境模板的 UTC 配置被改回去。</p>
 *
 * <p>纯 JVM，不依赖 MySQL 服务，可随常规 surefire 门禁执行。</p>
 */
@DisplayName("数据源时区一致性契约（千万级测试 §8.1 闭环）")
class DataSourceTimeZoneGuardTest {

    private static final String UTC_URL = "jdbc:mysql://db:3306/scaffold?useSSL=false&serverTimezone=UTC";
    private static final String SHANGHAI_URL =
            "jdbc:mysql://db:3306/scaffold?useSSL=false&serverTimezone=Asia/Shanghai";

    // ---------------- 启动期 fail-fast ----------------

    @Test
    @DisplayName("JVM 与 JDBC 同为 UTC 时放行")
    void passesWhenBothUtc() {
        withTimeZone("UTC", () -> {
            assertDoesNotThrow(() -> guard(UTC_URL, null).verify());
            return null;
        });
    }

    @Test
    @DisplayName("JVM 为 Asia/Shanghai、JDBC 声明 UTC 时 fail-fast 并给出修复指引")
    void failsFastWhenJvmIsShanghai() {
        withTimeZone("Asia/Shanghai", () -> {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> guard(UTC_URL, null).verify());
            assertTrue(ex.getMessage().contains("-Duser.timezone=UTC"), "须给出 JVM 修复指引");
            assertTrue(ex.getMessage().contains("TZ=UTC"), "须给出容器修复指引");
            assertTrue(ex.getMessage().contains("Asia/Shanghai"), "须回显实际 JVM 时区");
            return null;
        });
    }

    @Test
    @DisplayName("运维注入 serverTimezone=Asia/Shanghai 而 JVM 为 UTC 时同样拦截")
    void failsFastWhenInjectedUrlDeclaresOtherZone() {
        withTimeZone("UTC", () -> {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> guard(SHANGHAI_URL, null).verify());
            assertTrue(ex.getMessage().contains("Asia/Shanghai"));
            return null;
        });
    }

    @Test
    @DisplayName("URL 未声明时区、声明 SERVER 或值无法解析时只告警不阻断")
    void warnsButDoesNotBlockWhenZoneIsUnknown() {
        withTimeZone("UTC", () -> {
            assertDoesNotThrow(() -> guard("jdbc:mysql://db:3306/scaffold?useSSL=false", null).verify());
            assertDoesNotThrow(() -> guard("jdbc:mysql://db:3306/scaffold?connectionTimeZone=SERVER", null)
                    .verify());
            assertDoesNotThrow(() -> guard("jdbc:mysql://db:3306/scaffold?serverTimezone=NOT_A_ZONE", null)
                    .verify());
            return null;
        });
    }

    @Test
    @DisplayName("显式关闭校验后错配也不阻断（逃生开关）")
    void canBeDisabled() {
        withTimeZone("Asia/Shanghai", () -> {
            assertDoesNotThrow(() -> guard(UTC_URL, false).verify());
            return null;
        });
    }

    @Test
    @DisplayName("未配置多数据源时回退读取 spring.datasource.url")
    void fallsBackToSpringDatasourceUrl() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.url", UTC_URL);
        withTimeZone("Asia/Shanghai", () -> {
            assertThrows(IllegalStateException.class, () -> new DataSourceTimeZoneGuard(env).verify(),
                    "回退 URL 也必须参与一致性校验");
            return null;
        });
    }

    @Test
    @DisplayName("兼容新旧 Connector/J 参数名与偏移解析")
    void parsesZoneParameterAliases() {
        assertEquals("UTC", DataSourceTimeZoneGuard.declaredZone(
                "jdbc:mysql://db/x?connectionTimeZone=UTC"));
        assertEquals("UTC", DataSourceTimeZoneGuard.declaredZone(
                "jdbc:mysql://db/x?a=1&serverTimezone=UTC&b=2"));
        assertNull(DataSourceTimeZoneGuard.declaredZone("jdbc:mysql://db/x?useSSL=false"));
        assertEquals(Integer.valueOf(0), DataSourceTimeZoneGuard.declaredOffsetMillis("UTC"));
        assertEquals(Integer.valueOf(8 * 3600 * 1000),
                DataSourceTimeZoneGuard.declaredOffsetMillis("Asia/Shanghai"));
        assertNull(DataSourceTimeZoneGuard.declaredOffsetMillis("SERVER"));
        assertNull(DataSourceTimeZoneGuard.declaredOffsetMillis("NOT_A_ZONE"));
    }

    // ---------------- DATETIME 边界语义 ----------------

    @Test
    @DisplayName("UTC 对齐时 1970/2099 临界 DATETIME 经真实映射器往返无偏移")
    void boundaryTimestampsRoundTripUnderUtc() {
        withTimeZone("UTC", () -> {
            assertRoundTrip(LocalDateTime.of(1970, 1, 1, 0, 0, 0), 0);
            assertRoundTrip(LocalDateTime.of(2099, 12, 31, 23, 59, 59, 999_000_000), 0);
            return null;
        });
    }

    @Test
    @DisplayName("JVM 非 UTC 时同一 epoch 精确 +8h（固化根因，防止误判为映射器缺陷）")
    void boundaryTimestampsShiftWhenJvmIsShanghai() {
        withTimeZone("Asia/Shanghai", () -> {
            assertRoundTrip(LocalDateTime.of(1970, 1, 1, 0, 0, 0), 8);
            assertRoundTrip(LocalDateTime.of(2099, 12, 31, 23, 59, 59, 999_000_000), 8);
            return null;
        });
    }

    // ---------------- 部署配置契约 ----------------

    @Test
    @DisplayName("容器/CI/数据源模板全部显式 UTC，杜绝配置漂移")
    void deploymentConfigurationForcesUtc() throws IOException {
        Path root = repoRoot();
        String dockerfile = read(root.resolve("deploy/Dockerfile"));
        assertTrue(dockerfile.contains("-Duser.timezone=UTC"), "Dockerfile 必须显式钉死 JVM 时区");
        assertTrue(dockerfile.contains("TZ=UTC"), "Dockerfile 必须设置容器 TZ");

        String compose = read(root.resolve("deploy/docker-compose.yml"));
        assertTrue(compose.contains("TZ: UTC"), "compose 必须为应用/MySQL 设置 TZ: UTC");
        assertTrue(compose.contains("--default-time-zone=+00:00"), "MySQL 会话时区须为 UTC");

        String ci = read(root.resolve(".github/workflows/ci.yml"));
        assertTrue(ci.contains("TZ: UTC"), "CI 的测试 job 必须设置 TZ: UTC");

        String prod = read(root.resolve("scaffold-admin/src/main/resources/application-prod.yml"));
        assertTrue(prod.contains("timezone:") && prod.contains("guard:") && prod.contains("enabled: true"),
                "prod 必须显式启用时区一致性校验");

        assertJdbcUrlsDeclareUtc(root.resolve("scaffold-admin/src/main/resources/application-local.yml"));
        assertJdbcUrlsDeclareUtc(root.resolve("scaffold-admin/src/test/resources/application-integration.yml"));
        assertJdbcUrlsDeclareUtc(root.resolve("scripts/init/env.template"));
        assertJdbcUrlsDeclareUtc(root.resolve(".github/workflows/ci.yml"));
    }

    // ---------------- 辅助 ----------------

    private static DataSourceTimeZoneGuard guard(String url, Boolean enabled) {
        MockEnvironment env = new MockEnvironment();
        if (url != null) {
            env.setProperty(DataSourceTimeZoneGuard.PRIMARY_URL_PROPERTY, url);
        }
        if (enabled != null) {
            env.setProperty(DataSourceTimeZoneGuard.ENABLED_PROPERTY, enabled.toString());
        }
        return new DataSourceTimeZoneGuard(env);
    }

    /**
     * 模拟 Connector/J：DATETIME 文本按连接时区 UTC 解释为 epoch 后交给 jOOQ Record，
     * 再由生产映射器 {@link SnakeRecordMapper} 转成 LocalDateTime。
     */
    private static void assertRoundTrip(LocalDateTime written, int expectedShiftHours) {
        Timestamp ts = Timestamp.from(written.toInstant(ZoneOffset.UTC));
        Field<Timestamp> field = DSL.field("occurred_at", Timestamp.class);
        Record record = DSL.using(SQLDialect.MYSQL).newRecord(field);
        record.set(field, ts);
        SysOperationLogDO log = new SnakeRecordMapper<>(SysOperationLogDO.class).map(record);
        assertEquals(written.plusHours(expectedShiftHours), log.getOccurredAt(),
                "DATETIME 往返偏移应为 " + expectedShiftHours + "h（jvm=" + TimeZone.getDefault().getID() + "）");
    }

    private static void assertJdbcUrlsDeclareUtc(Path file) throws IOException {
        Matcher matcher = Pattern.compile("jdbc:mysql://[^\\s\"'<>]+").matcher(read(file));
        int count = 0;
        while (matcher.find()) {
            count++;
            assertTrue(matcher.group().contains("serverTimezone=UTC"),
                    file.getFileName() + " 的 JDBC URL 缺少 serverTimezone=UTC: " + matcher.group());
        }
        assertTrue(count > 0, file.getFileName() + " 未找到 JDBC URL，请检查配置是否被误删");
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "缺少配置文件: " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Path repoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && current != null; i++, current = current.getParent()) {
            if (Files.isDirectory(current.resolve("deploy")) && Files.exists(current.resolve("pom.xml"))) {
                return current;
            }
        }
        throw new IllegalStateException("未定位到仓库根，cwd=" + System.getProperty("user.dir"));
    }

    private static <T> T withTimeZone(String zone, Supplier<T> body) {
        synchronized (DataSourceTimeZoneGuardTest.class) {
            TimeZone original = TimeZone.getDefault();
            try {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                return body.get();
            } finally {
                TimeZone.setDefault(original);
            }
        }
    }
}
