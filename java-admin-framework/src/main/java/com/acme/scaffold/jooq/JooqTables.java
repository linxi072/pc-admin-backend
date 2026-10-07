package com.acme.scaffold.jooq;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.util.Set;

/**
 * 表元数据定义。采用 jOOQ 动态 DSL（无需构建期代码生成、无需 Docker、无需连接数据库）。
 * <p>
 * 重要：{@code DSL.table(name)} 创建的是"普通 SQL 表"，不带任何字段元数据，
 * {@code table.field("col")} 会返回 {@code null}。因此这里显式登记每张表的列清单，
 * 并用 {@link TableRef#field}（内部 {@code DSL.field(name, type)}）创建字段，
 * 保证动态 DSL 下字段始终可用，且能正确判断"列是否存在"
 * （用于审计字段自动填充、逻辑删除等可选列场景）。
 * <p>
 * 表名与列名与 Flyway 迁移脚本（db/migration/*.sql）严格一致。
 */
public final class JooqTables {

    private JooqTables() {
    }

    // ---------------- RBAC 核心 ----------------

    // avatar_url 与偏好开关为 V10 个人中心新增，列名与 V10__user_profile.sql 严格一致
    public static final TableRef SYS_USER = new TableRef("sys_user",
            "id", "tenant_id", "username", "password_hash", "display_name", "mobile", "email",
            "avatar_url", "notify_site_message", "notify_email", "notify_mobile",
            "show_login_log", "mask_mobile", "discoverable",
            "primary_org_id", "role_id", "org_id", "status", "failed_login_count", "locked_until",
            "password_changed_at", "password_expired", "token_version", "mfa_enabled", "last_login_at",
            "version", "deleted", "created_by", "created_at", "updated_by", "updated_at");

    public static final TableRef SYS_ORG = new TableRef("sys_org",
            "id", "tenant_id", "parent_id", "ancestors", "org_code", "org_name", "org_type", "sort_no",
            "leader_user_id", "status", "version", "deleted", "created_by", "created_at", "updated_by",
            "updated_at");

    public static final TableRef SYS_ROLE = new TableRef("sys_role",
            "id", "tenant_id", "role_code", "role_name", "role_type", "status", "sort_no", "version",
            "deleted", "created_by", "created_at", "updated_by", "updated_at");

    public static final TableRef SYS_MENU = new TableRef("sys_menu",
            "id", "tenant_id", "parent_id", "menu_code", "menu_name", "menu_type", "route_path",
            "component_path", "permission_code", "icon", "visible", "keep_alive", "external_url",
            "sort_no", "status", "deleted", "created_at", "updated_at");

    /** 注意：该表无 deleted 列，删除为物理删除。 */
    public static final TableRef SYS_API_RESOURCE = new TableRef("sys_api_resource",
            "id", "tenant_id", "resource_name", "permission_code", "http_method", "path_pattern",
            "controller_method", "auth_mode", "status", "risk_level", "created_at", "updated_at");

    /** 复合主键关联表：无 id 列。 */
    public static final TableRef SYS_ROLE_MENU = new TableRef("sys_role_menu",
            "tenant_id", "role_id", "menu_id", "created_at");

    /** 复合主键关联表：无 id 列。 */
    public static final TableRef SYS_ROLE_API = new TableRef("sys_role_api",
            "tenant_id", "role_id", "api_id", "created_at");

    public static final TableRef SYS_ROLE_DATA_SCOPE = new TableRef("sys_role_data_scope",
            "id", "tenant_id", "role_id", "resource_code", "scope_type", "combine_mode", "created_at",
            "updated_at");

    /** 复合主键关联表：无 id 列。 */
    public static final TableRef SYS_ROLE_DATA_SCOPE_ORG = new TableRef("sys_role_data_scope_org",
            "tenant_id", "rule_id", "org_id");

    // ---------------- 系统配置类 ----------------

    public static final TableRef SYS_DICT_TYPE = new TableRef("sys_dict_type",
            "id", "tenant_id", "dict_code", "dict_name", "status", "sort_no", "remark",
            "version", "deleted", "created_by", "created_at", "updated_by", "updated_at");

    public static final TableRef SYS_DICT_DATA = new TableRef("sys_dict_data",
            "id", "tenant_id", "dict_type_code", "dict_label", "dict_value", "dict_sort", "status",
            "remark", "version", "deleted", "created_by", "created_at", "updated_by", "updated_at");

    public static final TableRef SYS_CONFIG = new TableRef("sys_config",
            "id", "tenant_id", "config_key", "config_name", "config_value", "config_type", "remark",
            "status", "version", "deleted", "created_by", "created_at", "updated_by", "updated_at");

    // ---------------- 公告 / 站内信 / 监控 ----------------

    public static final TableRef SYS_ANNOUNCEMENT = new TableRef("sys_announcement",
            "id", "tenant_id", "title", "content", "status", "is_top", "publish_at", "expire_at",
            "published_at", "offline_at", "publisher_id", "view_count", "version", "deleted",
            "created_by", "created_at", "updated_by", "updated_at");

    public static final TableRef SYS_MESSAGE = new TableRef("sys_message",
            "id", "tenant_id", "title", "content", "msg_type", "sender_id", "filter_role_ids",
            "filter_org_ids", "receiver_ids", "total_count", "read_count", "sent_at", "version",
            "deleted", "created_by", "created_at", "updated_by", "updated_at");

    public static final TableRef SYS_MESSAGE_RECEIPT = new TableRef("sys_message_receipt",
            "id", "tenant_id", "message_id", "user_id", "is_read", "read_at", "created_at");

    public static final TableRef SYS_MONITOR_SAMPLE = new TableRef("sys_monitor_sample",
            "id", "tenant_id", "cpu_usage", "memory_usage", "system_memory_usage", "disk_usage",
            "used_heap_bytes", "max_heap_bytes", "used_memory_bytes", "total_memory_bytes",
            "online_users", "active_sessions", "thread_count", "sampled_at");

    // ---------------- 安全与审计 ----------------

    /** 注意：该表无 created_at / updated_at 列，时间语义为 issued_at / expires_at。 */
    public static final TableRef SYS_REFRESH_TOKEN = new TableRef("sys_refresh_token",
            "id", "tenant_id", "user_id", "client_id", "session_id", "family_id", "token_hash",
            "device_id", "user_agent", "ip_address", "issued_at", "expires_at", "last_used_at",
            "revoked_at", "revoke_reason", "replaced_by_id", "reuse_detected", "version");

    public static final TableRef SYS_PASSWORD_HISTORY = new TableRef("sys_password_history",
            "id", "tenant_id", "user_id", "password_hash", "created_at");

    public static final TableRef SYS_LOGIN_LOG = new TableRef("sys_login_log",
            "id", "tenant_id", "user_id", "username", "login_type", "result", "failure_code",
            "ip_address", "user_agent", "trace_id", "occurred_at");

    public static final TableRef SYS_OPERATION_LOG = new TableRef("sys_operation_log",
            "id", "tenant_id", "module_code", "operation_type", "operation_name", "operator_id",
            "operator_name", "request_method", "request_path", "request_summary", "result_summary",
            "result_code", "duration_ms", "ip_address", "trace_id", "success", "occurred_at");

    // ---------------- 工作流扩展 ----------------

    public static final TableRef WF_DEFINITION_EXT = new TableRef("wf_definition_ext",
            "id", "tenant_id", "process_key", "process_name", "version", "deployment_id",
            "process_definition_id", "form_schema", "status", "published_by", "published_at",
            "created_at");

    public static final TableRef WF_NODE_CONFIG = new TableRef("wf_node_config",
            "id", "definition_ext_id", "activity_id", "node_name", "approval_mode", "approval_ratio",
            "assignee_type", "assignee_expression", "reject_policy", "reject_target_activity_id",
            "allow_transfer", "allow_delegate", "timeout_minutes", "config_json");

    public static final TableRef WF_INSTANCE_EXT = new TableRef("wf_instance_ext",
            "id", "tenant_id", "process_instance_id", "process_definition_id", "business_type",
            "business_id", "title", "starter_user_id", "starter_org_id", "current_activity_id",
            "status", "started_at", "finished_at", "version");

    public static final TableRef WF_TASK_EXT = new TableRef("wf_task_ext",
            "id", "tenant_id", "task_id", "process_instance_id", "activity_id", "assignee_user_id",
            "original_assignee_user_id", "status", "approval_mode", "due_at", "claimed_at",
            "completed_at", "version");

    public static final TableRef WF_APPROVAL_RECORD = new TableRef("wf_approval_record",
            "id", "tenant_id", "operation_id", "process_instance_id", "task_id", "activity_id",
            "action", "operator_user_id", "from_user_id", "to_user_id", "opinion",
            "attachment_refs", "snapshot", "trace_id", "occurred_at");

    /**
     * 表引用：物理表名 + 列清单 + 类型化字段构造。
     */
    public static final class TableRef {

        private final String name;
        private final Table<Record> table;
        private final Set<String> columns;

        public TableRef(String name, String... columns) {
            this.name = name;
            this.table = DSL.table(DSL.name(name));
            this.columns = Set.of(columns);
        }

        public String name() {
            return name;
        }

        /** jOOQ 表对象，供 selectFrom / update / deleteFrom / insertInto 使用。 */
        public Table<Record> table() {
            return table;
        }

        /** 该表是否包含指定列。 */
        public boolean has(String column) {
            return columns.contains(column);
        }

        /**
         * 构造类型化字段。使用不限定列名（{@code col} 而非 {@code table.col}），
         * 以便同时适用于 SELECT / WHERE 与 UPDATE SET 子句。
         */
        public <T> Field<T> field(String column, Class<T> type) {
            return DSL.field(DSL.name(column), type);
        }
    }
}
