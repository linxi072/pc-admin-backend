-- ============================================================
-- 模块参考 SQL：表单中心
-- 说明：表单定义试点（V47）+ 提交存储（V53）+ 定义菜单（V58）
-- 来源：db/migration/ 中对应版本迁移（原文拼接，未经改写）
-- 依赖：需先存在 V32 基线；本文件为逻辑抽取，仅供阅读/追溯，
--       不可作为独立迁移执行（Flyway 只扫描 db/migration/）。
-- 重新生成：python3 scripts/build_reference_modular.py
-- ============================================================

-- ============================================================
-- Source migration: V47__form_pilot.sql
-- ============================================================

-- V47 低代码表单试点（M22 spike）：sys_form_definition，以 JSON Schema 描述表单（字段/校验/布局），
-- 前端按 schema 零依赖渲染，后端按 schema 结构校验后经 /schema/{code} 下发。
--
-- 设计要点（spike 范围，严格收口）：
--   1) 仅交付「契约 + 渲染原型 + 1 个试点表单」：本迁移只建表并播种试点表单 contact-feedback；
--      **不含**提交落库与绑定目标实现——决策点①（绑定既有表 vs 独立 form_submission 表）
--      与决策点②（是否引轻量 schema 库）留待正式立项评审，本期一律按「零依赖手写渲染」收敛。
--   2) target_type 为占位列：NONE（默认，spike 状态）/ BUSINESS_TABLE / FORM_SUBMISSION，
--      取值为后续决策预留，当前读写均不依赖其具体语义。
--   3) schema_json 结构校验集中在 system.form.rules.FormSchemaRules（纯函数）：
--      version + fields[]，字段 key（驼峰标识符）/label/type（白名单 INPUT/TEXTAREA/NUMBER/
--      SELECT/RADIO/SWITCH/DATE）/required/options（SELECT/RADIO 必填非空）/长度范围。
--   4) 接口资源（system:form-definition:read）不在此手工登记，
--      由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--   5) 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。

-- 1) 表：sys_form_definition（逻辑删除 + 乐观锁；(tenant_id, form_code) 唯一约束兜底并发写入）
CREATE TABLE IF NOT EXISTS sys_form_definition (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    form_code VARCHAR(64) NOT NULL,
    form_name VARCHAR(128) NOT NULL,
    schema_json JSON NOT NULL,
    target_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    remark VARCHAR(255) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, form_code),
    KEY idx_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 试点表单：contact-feedback（联系反馈）——覆盖 INPUT/TEXTAREA/SELECT/SWITCH/DATE 五类控件渲染
INSERT IGNORE INTO sys_form_definition (id, tenant_id, form_code, form_name, schema_json, target_type, enabled, remark, version, deleted, created_at, updated_at)
VALUES (1, 0, 'contact-feedback', '联系反馈',
        JSON_OBJECT(
            'version', 1,
            'title', '联系反馈',
            'fields', JSON_ARRAY(
                JSON_OBJECT('key', 'name', 'label', '姓名', 'type', 'INPUT', 'required', true, 'maxLength', 32, 'placeholder', '请输入姓名'),
                JSON_OBJECT('key', 'mobile', 'label', '手机号', 'type', 'INPUT', 'required', true, 'maxLength', 11, 'placeholder', '11 位手机号'),
                JSON_OBJECT('key', 'channel', 'label', '反馈渠道', 'type', 'SELECT', 'required', true,
                            'options', JSON_ARRAY(
                                JSON_OBJECT('label', '电话', 'value', 'PHONE'),
                                JSON_OBJECT('label', '邮件', 'value', 'EMAIL'),
                                JSON_OBJECT('label', '在线客服', 'value', 'IM'))),
                JSON_OBJECT('key', 'urgent', 'label', '是否紧急', 'type', 'SWITCH'),
                JSON_OBJECT('key', 'content', 'label', '反馈内容', 'type', 'TEXTAREA', 'required', true,
                            'minLength', 10, 'maxLength', 500, 'rows', 4, 'placeholder', '请详细描述您的问题（至少 10 字）'),
                JSON_OBJECT('key', 'followUpAt', 'label', '期望回访日期', 'type', 'DATE')
            )
        ),
        'NONE', 1, 'M22 spike 试点表单：契约 + 渲染验证，不含提交落库（绑定目标决策点待定）', 0, 0, NOW(3), NOW(3));

-- 3) 菜单：低代码表单试点（挂在「系统管理」1002 下，sort_no=111 紧随脱敏策略中心之后）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1121, 0, 1002, 'system:form-pilot', '低代码表单试点', 'MENU', '/system/form-pilot', 'system/FormPilot', 'system:form-definition:read', 'EditPen', 1, 0, 111, 'ACTIVE', NOW(3), NOW(3));

-- 4) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1121;


-- ============================================================
-- Source migration: V53__form_submission.sql
-- ============================================================

-- V53 表单提交落库（M22 正式立项）：sys_form_submission，承接 V47 低代码表单试点的
-- 「提交落库与绑定目标」决策点（target_type=FORM_SUBMISSION），将动态表单提交值
-- 以 JSON 持久化到独立提交表，不侵入业务表、不做运行时 DDL。
--
-- 设计要点：
--   1) 通用提交表：以 (form_definition_id, form_code) 关联表单定义，data_json 存提交值；
--      后端提交前经 FormSubmissionRules 按 schema 字段校验（必填/类型/长度/选项白名单/未知字段拒绝）。
--   2) 沿用全库约定：tenant_id 默认 0（与 FORM_DEFINITION 同口径），逻辑删除 deleted + 乐观锁 version，
--      created_at/updated_at 由 JooqWriters.insert 自动填充。
--   3) data_json 不在此做结构约束（schema 校验在应用层完成），便于后续字段演进。
--   4) 将 V47 试点表单 contact-feedback 的 target_type 由 NONE 置为 FORM_SUBMISSION，
--      使其正式具备提交能力（idempotent UPDATE，重复执行幂等）。
--   5) 接口资源 system:form-submission:create / system:form-submission:read 不在此手工登记，
--      由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--   6) 依赖：V47（sys_form_definition、菜单 id=1121 低代码表单试点）。

-- 1) 表：sys_form_submission（逻辑删除 + 乐观锁；按 (tenant_id, form_code, created_at) 建查询索引）
CREATE TABLE IF NOT EXISTS sys_form_submission (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    form_definition_id BIGINT UNSIGNED NOT NULL,
    form_code VARCHAR(64) NOT NULL,
    submitted_by VARCHAR(64) NULL,
    data_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_form_created (tenant_id, form_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 试点表单绑定目标落库：contact-feedback 由 NONE → FORM_SUBMISSION（幂等 UPDATE）
UPDATE sys_form_definition
SET target_type = 'FORM_SUBMISSION'
WHERE form_code = 'contact-feedback'
  AND target_type = 'NONE';


-- ============================================================
-- Source migration: V58__form_definition_menu.sql
-- ============================================================

-- V58 表单定义管理菜单（M22 深化）：在「系统管理」(1002) 下新增「表单定义管理」入口，
-- 承载表单定义 CRUD + JSON-Schema 可视化编辑 + 实时预览（复用前端 DynamicForm 组件）。
-- 与既有「低代码表单试点」(id=1121) 互补：本页负责定义生命周期管理，试点页负责运行时
-- 渲染 / 提交 / 提交记录查看。
--
-- 设计要点：
--   1) 后端接口前缀 /api/system/form-definitions；读权限复用 system:form-definition:read
--      （由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN）；
--      写权限 system:form-definition:create / update / delete 同样自动登记。
--   2) route_path 必须与前端路由（system/form-definition）严格一致，否则路由守卫会重定向到工作台。
--   3) 依赖：V47（sys_form_definition、低代码表单试点菜单 1121）、V53（提交落库）。
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1），可重复执行。
--   5) sort_no=117 紧随数据权限(1127/116) 之后，置于系统管理目录末尾。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1128, 0, 1002, 'system:form-definition', '表单定义管理', 'MENU', '/system/form-definition', 'system/FormDefinitionView', 'system:form-definition:read', 'Document', 1, 0, 117, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
-- （read 权限由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1128;

