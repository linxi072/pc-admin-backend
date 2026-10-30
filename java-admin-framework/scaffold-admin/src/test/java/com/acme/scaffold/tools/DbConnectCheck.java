package com.acme.scaffold.tools;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 本地 MySQL 连接自检工具（默认 root/root）。
 *
 * <p>用于在不启动整个 Spring 上下文的前提下，验证「本机 MySQL + root/root」连接是否可用，
 * 对应 {@code src/test/resources/application-integration.yml} 中的数据源配置。
 *
 * <p>运行方式（需 mysql-connector-j 在 classpath 中）：
 * <pre>
 *   javac -cp mysql-connector-j-8.3.0.jar -d /tmp/dbpkg DbConnectCheck.java
 *   java  -cp /tmp/dbpkg:mysql-connector-j-8.3.0.jar \
 *         com.acme.scaffold.tools.DbConnectCheck [dbName]
 * </pre>
 * 可选参数 {@code dbName} 默认 {@code java_admin}（与集成测试数据源一致）。
 */
public final class DbConnectCheck {

    private static final String URL_TEMPLATE =
            "jdbc:mysql://127.0.0.1:3306/%s"
            + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
            + "&characterEncoding=utf8&createDatabaseIfNotExist=true";

    private static final String USER = "root";
    private static final String PASSWORD = "root";

    private DbConnectCheck() {
    }

    public static void main(String[] args) throws Exception {
        String dbName = (args.length > 0) ? args[0] : "java_admin";
        String url = String.format(URL_TEMPLATE, dbName);

        Class.forName("com.mysql.cj.jdbc.Driver");
        Properties props = new Properties();
        props.setProperty("user", USER);
        props.setProperty("password", PASSWORD);

        System.out.println("→ 正在连接: " + url + "  (user=" + USER + ")");
        try (Connection conn = DriverManager.getConnection(url, props)) {
            DatabaseMetaData meta = conn.getMetaData();
            System.out.println("✅ 连接成功");
            System.out.println("   JDBC 驱动  : " + meta.getDriverName() + " " + meta.getDriverVersion());
            System.out.println("   数据库产品 : " + meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion());
            System.out.println("   连接 URL   : " + meta.getURL());
            System.out.println("   自动提交   : " + conn.getAutoCommit());
            System.out.println("   连接有效   : " + conn.isValid(3));

            int tableCount = 0;
            StringBuilder tables = new StringBuilder("   数据表     : ");
            try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    if (tableCount > 0) {
                        tables.append(", ");
                    }
                    tables.append(rs.getString("TABLE_NAME"));
                    tableCount++;
                }
            }
            System.out.println(tables + "  (共 " + tableCount + " 张，Flyway 迁移后会增加)");
        } catch (SQLException e) {
            System.err.println("❌ 连接失败: " + e.getMessage());
            throw e;
        }
    }
}
