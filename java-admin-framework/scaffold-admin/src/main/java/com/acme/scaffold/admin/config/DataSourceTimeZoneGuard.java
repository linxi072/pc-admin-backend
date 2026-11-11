package com.acme.scaffold.admin.config;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 生产启动时校验「JDBC 连接声明的时区」与「JVM 默认时区」是否一致。
 *
 * <p>背景见 {@code QA-TEST-REPORT-TEN-MILLION-V1.1.0.md} §8.1：数据源 URL 统一声明
 * {@code serverTimezone=UTC}，若 JVM 默认为 {@code Asia/Shanghai}，Connector/J 会按连接时区
 * 解释 DATETIME 再换算到 JVM 本地时区，导致所有时间读回整体 +8h，且 1970/2099 临界值跨日跨年。
 * H2 单测无法暴露该问题（仅真实 MySQL 路径），因此以「部署统一 UTC + 启动期 fail-fast」闭环。</p>
 *
 * <p>仅在 {@code prod} profile 生效，避免阻断本地开发；仅当 URL 显式声明了可静态解析的
 * 时区参数且与 JVM 偏移量不一致时才拒绝启动。未声明或声明为 {@code SERVER}/无法解析时只告警，
 * 避免误伤合法部署。可用 {@code app.timezone.guard.enabled=false} 显式关闭。</p>
 */
@Slf4j
@Component
@Profile("prod")
public class DataSourceTimeZoneGuard implements ApplicationRunner {

    static final String ENABLED_PROPERTY = "app.timezone.guard.enabled";
    static final String PRIMARY_URL_PROPERTY = "app.datasources.entries.primary.url";

    private static final Pattern ZONE_PARAM = Pattern.compile(
            "[?&](?:serverTimezone|connectionTimeZone)=([^&#\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> UTC_ALIASES = Set.of(
            "UTC", "GMT", "Z", "ETC/UTC", "ETC/GMT", "GMT+0", "UTC+0");

    private final Environment environment;

    public DataSourceTimeZoneGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        verify();
    }

    /**
     * 公开入口便于契约测试用 {@code MockEnvironment} 直接驱动，无需启动 Spring 上下文。
     *
     * @throws IllegalStateException JDBC 声明时区与 JVM 默认时区偏移量不一致
     */
    public void verify() {
        if (!Boolean.TRUE.equals(environment.getProperty(ENABLED_PROPERTY, Boolean.class, Boolean.TRUE))) {
            log.warn("数据源时区一致性校验已关闭（{}=false），请自行确保 JVM 与 JDBC 时区一致",
                    ENABLED_PROPERTY);
            return;
        }
        String url = environment.getProperty(PRIMARY_URL_PROPERTY);
        if (url == null || url.isBlank()) {
            url = environment.getProperty("spring.datasource.url");
        }
        String declared = declaredZone(url);
        if (declared == null) {
            log.warn("JDBC URL 未声明 serverTimezone/connectionTimeZone：MySQL DATETIME 语义将依赖"
                    + "服务器会话时区，建议显式声明 serverTimezone=UTC 并统一 JVM 为 UTC");
            return;
        }
        Integer jdbcOffset = declaredOffsetMillis(declared);
        if (jdbcOffset == null) {
            log.warn("JDBC URL 声明的时区 [{}] 无法静态解析（或为 SERVER 语义），跳过一致性校验；"
                    + "请人工确认 JVM 时区与 MySQL 会话时区一致", declared);
            return;
        }
        TimeZone jvm = TimeZone.getDefault();
        if (jdbcOffset.intValue() != jvm.getRawOffset()) {
            throw new IllegalStateException(
                    "数据源时区与 JVM 时区不一致，MySQL DATETIME 读回会整体偏移：jdbc=" + declared
                            + "(" + jdbcOffset + "ms), jvm=" + jvm.getID()
                            + "(" + jvm.getRawOffset() + "ms)。请设置容器 TZ=UTC 并在 JAVA_OPTS"
                            + " 增加 -Duser.timezone=UTC，或调整 JDBC URL 时区与之对齐；"
                            + "确认无风险时可设置 " + ENABLED_PROPERTY + "=false 关闭本校验。");
        }
        log.info("数据源时区一致性校验通过：jdbc={}({}ms), jvm={}({}ms)",
                declared, jdbcOffset, jvm.getID(), jvm.getRawOffset());
    }

    /** 从 JDBC URL 查询参数中提取时区声明，兼容新旧 Connector/J 属性名。 */
    static String declaredZone(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = ZONE_PARAM.matcher(url);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /**
     * 将时区声明换算为毫秒偏移；{@code SERVER} 与无法识别的值返回 {@code null}（只告警不阻断）。
     */
    static Integer declaredOffsetMillis(String zone) {
        if (zone == null || zone.isBlank()) {
            return null;
        }
        String normalized = zone.trim();
        if ("SERVER".equalsIgnoreCase(normalized)) {
            return null;
        }
        boolean known = ZoneId.getAvailableZoneIds().contains(normalized)
                || UTC_ALIASES.contains(normalized.toUpperCase(Locale.ROOT));
        if (!known) {
            return null;
        }
        return TimeZone.getTimeZone(normalized).getRawOffset();
    }
}
