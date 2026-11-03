package com.acme.scaffold.workflow.support;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

/**
 * 测试用 H2（内存）上下文工厂：加载 {@code wf-h2-schema.sql} 建表，返回绑定活动连接的 jOOQ DSLContext。
 *
 * <p>每个调用使用独立的内存库名，保证用例间相互隔离；H2 开启 MySQL 兼容模式与
 * DATABASE_TO_LOWER，使未引号标识符与列名统一小写，贴近生产 MySQL 的列名语义。
 * 内存库经 {@code DB_CLOSE_DELAY=-1} 在 JVM 内常驻，故建表连接关闭后，jOOQ 复用的连接
 * 仍可读写同一库。</p>
 */
public final class WfH2 {

    private WfH2() {
    }

    public static DSLContext newContext() throws Exception {
        String dbName = "wf_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try (Connection ddlConn = DriverManager.getConnection(url, "sa", "");
             Statement st = ddlConn.createStatement()) {
            for (String stmt : readSchema().split(";")) {
                if (!stmt.trim().isEmpty()) {
                    st.execute(stmt.trim());
                }
            }
        }
        Connection conn = DriverManager.getConnection(url, "sa", "");
        return DSL.using(conn, SQLDialect.H2);
    }

    private static String readSchema() throws Exception {
        try (InputStream in = WfH2.class.getResourceAsStream("/wf-h2-schema.sql");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }
}
