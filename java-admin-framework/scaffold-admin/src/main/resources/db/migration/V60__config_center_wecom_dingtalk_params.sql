-- =====================================================================================
-- V60 参数中心补充：企业微信 / 钉钉机器人渠道参数（M35 渠道 + M36 热更新）
-- -------------------------------------------------------------------------------------
-- 目标库：MySQL 8.x（InnoDB + utf8mb4 + utf8mb4_0900_ai_ci）
-- 目的：将 M35 新增的企微/钉钉机器人渠道开关与默认地址沉淀为可配置参数，
--       使其可经参数中心界面或直连数据库动态调整，并配合 ConfigService 热更新即时生效。
--
-- 幂等说明：种子用 INSERT IGNORE（重复执行不报 1062）。本迁移不改动任何既有迁移文件，
--       仅向 sys_config 追加新行（独立 id 段 9001+，不与 V38 既有 4~52 行冲突）。
--       可直接在目标库重复执行。secret 属凭据，按规范经环境变量注入，不入库。
-- =====================================================================================

INSERT IGNORE INTO sys_config (id, tenant_id, config_key, config_name, config_value, default_value, config_type, remark, editable, status, created_at, updated_at)
VALUES
    -- ===== 企业微信机器人 =====
    (9001, 0, 'notification.wecom.enabled', '企业微信机器人渠道开关', 'false', 'false', 'BOOLEAN', '缺省关闭避免误发；开后经参数中心/直改库 + refresh 即热更新', 1, 'ACTIVE', NOW(3), NOW(3)),
    (9002, 0, 'notification.wecom.webhook-url', '企业微信机器人默认 Webhook 地址', NULL, NULL, 'STRING', '消息未自带 webhookUrl 时取此默认；通常含 key 参数', 1, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 钉钉机器人 =====
    (9003, 0, 'notification.dingtalk.enabled', '钉钉机器人渠道开关', 'false', 'false', 'BOOLEAN', '缺省关闭避免误发；开后经参数中心/直改库 + refresh 即热更新', 1, 'ACTIVE', NOW(3), NOW(3)),
    (9004, 0, 'notification.dingtalk.webhook-url', '钉钉机器人默认 Webhook 地址', NULL, NULL, 'STRING', '消息未自带 webhookUrl 时取此默认；通常含 access_token 参数', 1, 'ACTIVE', NOW(3), NOW(3));
