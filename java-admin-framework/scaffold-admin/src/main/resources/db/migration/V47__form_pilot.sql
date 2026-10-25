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
    updated_at DATETIME(3) NOT NULL CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
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
