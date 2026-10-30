package com.acme.scaffold.config.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多数据源配置（绑定前缀 app.datasources）。
 *
 * <p>所有连接类敏感信息（url/username/password）统一通过占位符引用环境变量，
 * 此处及 yml 中均不硬编码任何凭据；本地开发可在 application-local.yml 以 ${ENV:默认值} 提供回退。
 *
 * <p>结构示例（各 profile yml 仅填充 entries，primary 名称在 application.yml 声明）：
 * <pre>
 * app:
 *   datasources:
 *     primary: primary                 # 默认数据源名称（不切换时使用的库）
 *     entries:
 *       primary:
 *         url: ${DB_PRIMARY_URL:jdbc:mysql://localhost:3306/scaffold?...}
 *         username: ${DB_PRIMARY_USERNAME:root}
 *         password: ${DB_PRIMARY_PASSWORD:root}
 *       report:                        # 第二个库按需启用，凭据同样走环境变量
 *         url: ${DB_REPORT_URL}
 *         username: ${DB_REPORT_USERNAME}
 *         password: ${DB_REPORT_PASSWORD}
 * </pre>
 */
@ConfigurationProperties(prefix = "app.datasources")
public class MultiDataSourceProperties {

    /** 默认（主）数据源名称；未通过 DataSourceContextHolder / @WithDataSource 切换时使用。 */
    private String primary = "primary";

    /** 具名数据源集合，key 为数据源名称。 */
    private Map<String, DataSourceProperties> entries = new LinkedHashMap<>();

    public String getPrimary() {
        return primary;
    }

    public void setPrimary(String primary) {
        this.primary = primary;
    }

    public Map<String, DataSourceProperties> getEntries() {
        return entries;
    }

    public void setEntries(Map<String, DataSourceProperties> entries) {
        this.entries = entries;
    }

    public static class DataSourceProperties {

        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private Pool pool = new Pool();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public Pool getPool() {
            return pool;
        }

        public void setPool(Pool pool) {
            this.pool = pool;
        }
    }

    public static class Pool {

        private int minimumIdle = 10;
        private int maximumPoolSize = 50;
        private long connectionTimeout = 3000;
        private long validationTimeout = 1000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;
        private String connectionTestQuery = "SELECT 1";

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public long getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public long getValidationTimeout() {
            return validationTimeout;
        }

        public void setValidationTimeout(long validationTimeout) {
            this.validationTimeout = validationTimeout;
        }

        public long getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public long getMaxLifetime() {
            return maxLifetime;
        }

        public void setMaxLifetime(long maxLifetime) {
            this.maxLifetime = maxLifetime;
        }

        public String getConnectionTestQuery() {
            return connectionTestQuery;
        }

        public void setConnectionTestQuery(String connectionTestQuery) {
            this.connectionTestQuery = connectionTestQuery;
        }
    }
}
