package com.acme.scaffold.jooq.dsl;

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
            "status", "failed_login_count", "locked_until",
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

    /** 用户-角色关联（多对多，回退 V6 收敛）。is_primary 标记主角色。无 deleted 列，物理删除。 */
    public static final TableRef SYS_USER_ROLE = new TableRef("sys_user_role",
            "tenant_id", "user_id", "role_id", "is_primary", "created_at");

    /** 用户-部门关联（多对多，回退 V6 收敛）。sys_org 为部门表，is_primary 标记主部门。无 deleted 列，物理删除。 */
    public static final TableRef SYS_USER_ORG = new TableRef("sys_user_org",
            "tenant_id", "user_id", "org_id", "is_primary", "created_at");

    /** 岗位：编码租户内唯一，软删除 + 乐观锁，范式对齐 sys_role。 */
    public static final TableRef SYS_POST = new TableRef("sys_post",
            "id", "tenant_id", "post_code", "post_name", "post_sort", "status", "remark",
            "version", "deleted", "created_by", "created_at", "updated_by", "updated_at");

    /** 用户-岗位关联（多对多）。无 deleted 列，分配整体替换为物理删除。 */
    public static final TableRef SYS_USER_POST = new TableRef("sys_user_post",
            "id", "tenant_id", "user_id", "post_id", "created_at");

    // ---------------- 系统配置类 ----------------

    public static final TableRef SYS_DICT_TYPE = new TableRef("sys_dict_type",
            "id", "tenant_id", "dict_code", "dict_name", "status", "sort_no", "remark",
            "version", "deleted", "created_by", "created_at", "updated_by", "updated_at");

    public static final TableRef SYS_DICT_DATA = new TableRef("sys_dict_data",
            "id", "tenant_id", "dict_type_code", "dict_label", "dict_value", "dict_sort", "status",
            "remark", "version", "deleted", "created_by", "created_at", "updated_by", "updated_at");

    public static final TableRef SYS_CONFIG = new TableRef("sys_config",
            "id", "tenant_id", "config_key", "config_name", "config_value", "default_value", "config_type",
            "remark", "editable", "status", "version", "deleted", "created_by", "created_at",
            "updated_by", "updated_at");

    // ---------------- 公告 / 站内信 / 监控 ----------------

    // audience_count / read_count 为 V40 公告已读回执中心新增，列名与 V40 迁移严格一致
    public static final TableRef SYS_ANNOUNCEMENT = new TableRef("sys_announcement",
            "id", "tenant_id", "title", "content", "status", "is_top", "publish_at", "expire_at",
            "published_at", "offline_at", "publisher_id", "view_count", "audience_count",
            "read_count", "version", "deleted", "created_by", "created_at", "updated_by", "updated_at");

    /** 公告已读回执（懒插入模型：仅 markRead 时落行），与站内信 eager 投递对称互补。 */
    public static final TableRef SYS_ANNOUNCEMENT_READ = new TableRef("sys_announcement_read",
            "id", "tenant_id", "announcement_id", "user_id", "read_at", "created_at");

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

    /**
     * 幂等记录表（V16）。无 deleted / version 列：到期物理删除，并发由唯一索引裁决而非乐观锁。
     */
    public static final TableRef SYS_IDEMPOTENCY_RECORD = new TableRef("sys_idempotency_record",
            "id", "tenant_id", "idempotency_key", "api_scope", "user_id", "request_fingerprint",
            "status", "response_snapshot", "error_code", "error_message", "expires_at",
            "created_at", "updated_at");

    public static final TableRef SYS_PASSWORD_HISTORY = new TableRef("sys_password_history",
            "id", "tenant_id", "user_id", "password_hash", "created_at");

    public static final TableRef SYS_LOGIN_LOG = new TableRef("sys_login_log",
            "id", "tenant_id", "user_id", "username", "login_type", "result", "failure_code",
            "ip_address", "user_agent", "trace_id", "occurred_at");

    public static final TableRef SYS_OPERATION_LOG = new TableRef("sys_operation_log",
            "id", "tenant_id", "module_code", "operation_type", "operation_name", "operator_id",
            "operator_name", "request_method", "request_path", "request_summary", "result_summary",
            "result_code", "duration_ms", "ip_address", "trace_id", "success", "occurred_at");

    // ---------------- 文件管理（V33） ----------------

    /** 文件表（列名与 V33__file_management.sql 严格一致）。逻辑删除（deleted），删除时仅清理物理文件。 */
    public static final TableRef SYS_FILE = new TableRef("sys_file",
            "id", "tenant_id", "original_name", "stored_name", "storage_path", "file_size",
            "content_type", "source_module", "biz_id", "uploader_id", "uploader_name",
            "download_count", "version", "deleted", "created_at", "updated_at");

    // ---------------- 在线用户管理（V27） ----------------

    /**
     * 在线会话跟踪表（V27）。业务键 (tenant_id, user_id, device_id)，无 deleted 列：
     * 会话行永久留存作为「最近下线记录」，通过 status 区分 ONLINE / OFFLINE。
     */
    public static final TableRef SYS_ONLINE_SESSION = new TableRef("sys_online_session",
            "id", "tenant_id", "user_id", "device_id", "client_id", "username", "display_name",
            "login_ip", "user_agent", "login_at", "last_active_at", "status", "offline_reason",
            "created_at", "updated_at");

    /**
     * 临时登录限制表（V27）。无 deleted 列：记录即审计，ACTIVE / RELEASED 状态机流转。
     */
    public static final TableRef SYS_LOGIN_RESTRICTION = new TableRef("sys_login_restriction",
            "id", "tenant_id", "user_id", "username", "display_name", "reason", "duration_minutes",
            "restricted_by", "restricted_by_name", "restricted_at", "expire_at", "released_at",
            "released_by", "release_type", "status", "created_at", "updated_at");

    // ---------------- 定时任务管理（JobRunr 双层架构：配置/日志自管，调度交 JobRunr） ----------------

    /** 定时任务配置表（列名与 V21__job_management.sql 严格一致）。 */
    public static final TableRef SYS_JOB = new TableRef("sys_job",
            "id", "tenant_id", "job_name", "job_group", "task_key", "trigger_type", "cron_expression",
            "interval_value", "interval_unit", "one_time_at", "job_params", "status", "scheduler_ref",
            "last_fire_time", "next_fire_time", "total_runs", "success_runs", "failure_runs",
            "last_status", "last_error", "last_duration_ms", "remark",
            "version", "deleted", "created_by", "created_at", "updated_by", "updated_at");

    /** 定时任务执行日志表（列名与 V21__job_management.sql 严格一致）。 */
    public static final TableRef SYS_JOB_LOG = new TableRef("sys_job_log",
            "id", "tenant_id", "job_id", "job_name", "task_key", "trigger_type", "fire_time",
            "finished_time", "duration_ms", "status", "error_message", "trace_id", "created_at");

    // ---------------- 短信发送记录（V37） ----------------

    /**
     * 短信发送记录表（列名与 V37__sms_record.sql 严格一致）。无 deleted 列：记录即审计，永久留存。
     * 手机号列存掩码值（{@code 138****8000}），正文列存摘要，避免 PII 明文落库。
     */
    public static final TableRef SYS_SMS_RECORD = new TableRef("sys_sms_record",
            "id", "tenant_id", "batch_no", "mobiles_masked", "mobile_count", "content_summary",
            "provider", "status", "error_message", "operator_id", "operator_name",
            "trace_id", "sent_at");

    // ---------------- 通知发送记录（V42） ----------------

    /**
     * 通知发送记录表（列名与 V42__notification_record.sql 严格一致）。无 deleted 列：记录即审计，永久留存。
     * 与 {@link #SYS_SMS_RECORD} 的分工：本表记「每次 {@code NotificationService.dispatch} 尝试」
     * （含被拒与失败，覆盖全部五渠道，是统一审计面）；短信记录保持「仅校验后实际尝试的网关投递」
     * （网关账单对账面）。短信一次发送双写两表，口径差异见开发手册 M12 章节。
     * 标题/正文/接收方均以摘要 + 掩码形态落库，PII 不明文存储。
     */
    public static final TableRef SYS_NOTIFICATION_RECORD = new TableRef("sys_notification_record",
            "id", "tenant_id", "channel", "template_code", "msg_type", "title_summary",
            "content_summary", "receiver_summary", "receiver_count", "delivered", "status",
            "error_message", "operator_id", "operator_name", "trace_id", "sent_at");

    // ---------------- 通知模板中心（V41） ----------------

    /**
     * 通知模板表（列名与 V41__notification_template.sql 严格一致）。逻辑删除（deleted），
     * 启用状态（enabled）独立控制；渠道名存 {@code Channel} 枚举大写
     * （IN_APP / EMAIL / SMS / WEBHOOK / WEBSOCKET）。
     */
    public static final TableRef SYS_NOTIFICATION_TEMPLATE = new TableRef("sys_notification_template",
            "id", "tenant_id", "template_code", "template_name", "channel", "title", "content",
            "msg_type", "enabled", "remark", "version", "deleted", "created_by", "created_at",
            "updated_by", "updated_at");

    // ---------------- 通知偏好中心（V43） ----------------

    /**
     * 通知偏好表（列名与 V43__notification_preference.sql 严格一致）。无 deleted / version 列：
     * 「无行 = 默认开通」的懒行模型，恢复默认即物理删行；唯一键 uk_user_channel_type 兜底幂等，
     * 并发写入以唯一索引裁决（与幂等记录表同范式）。渠道名存 {@code Channel} 枚举大写
     * （仅 IN_APP / EMAIL / SMS / WEBSOCKET 四路可设偏好，WEBHOOK 无个人偏好语义拒绝建行）。
     */
    public static final TableRef SYS_NOTIFICATION_PREFERENCE = new TableRef("sys_notification_preference",
            "id", "tenant_id", "user_id", "channel", "msg_type", "enabled",
            "created_at", "updated_at");

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

    public static final TableRef WF_WORKFLOW_DESIGN = new TableRef("wf_workflow_design",
            "id", "tenant_id", "process_key", "process_name", "description", "status", "version",
            "deleted", "nodes_json", "edges_json", "form_schema", "bpmn_xml", "deployment_id",
            "process_definition_id", "published_by", "published_at", "created_at", "updated_at");

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
