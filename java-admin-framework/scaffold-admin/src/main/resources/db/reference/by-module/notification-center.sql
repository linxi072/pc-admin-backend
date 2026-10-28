-- ============================================================
-- 模块参考 SQL：通知中心
-- 说明：公告已读回执 + 通知模板/记录/偏好/免打扰/摘要
-- 来源：db/migration/ 中对应版本迁移（原文拼接，未经改写）
-- 依赖：需先存在 V32 基线；本文件为逻辑抽取，仅供阅读/追溯，
--       不可作为独立迁移执行（Flyway 只扫描 db/migration/）。
-- 重新生成：python3 scripts/build_reference_modular.py
-- ============================================================

-- ============================================================
-- Source migration: V40__announcement_read_receipt.sql
-- ============================================================

-- =====================================================================================
-- V40 公告已读回执中心（Announcement Read Receipt Center）
-- -------------------------------------------------------------------------------------
-- 目标库：MySQL 8.x（InnoDB + utf8mb4 + utf8mb4_0900_ai_ci）
--
-- 背景与模型取舍：
--   站内信（V8）采用「发送即 eager 全员投递」：一条 sys_message 配 N 条
--   sys_message_receipt，已读/未读天然可枚举。公告的受众却是「全体活跃用户」，
--   且阅读者通常远小于受众——若沿用 eager 模型，每发布一条公告就要为全量用户
--   预插回执，发布动作写放大（读多写少场景下绝大多数行永远不会被读到）。
--
--   故公告回执采用「懒插入（lazy-insert）」模型：
--     - 回执行只在用户确认已读（markRead）时才写入，行数 ≈ 实际阅读人数；
--     - 「谁还没读」不再由回执表反推，而是用反连接得出：
--       活跃用户集合 − 已读回执集合（见 AnnouncementReadService.unreadUsers）；
--     - 分母问题（已读率 = 已读人数 / 受众数）由 sys_announcement.audience_count
--       解决：发布时快照当时活跃用户数，作为统计分母；V40 之前的存量公告
--       该列为 0，前端统计按 readCount 兜底展示，不除零。
--
-- 幂等设计：
--     - uk_ann_user (tenant_id, announcement_id, user_id) 唯一键保证同一用户对
--       同一公告只有一条回执，markRead 并发/重放不会产生重复行；
--     - sys_announcement.read_count 由「插入成功」事件驱动 +1，与回执行一一对应；
--     - 建表用 CREATE TABLE IF NOT EXISTS；加列用 information_schema 守卫做幂等
--       ALTER（重复执行不报 1060）。可直接在目标库重复执行。
--
-- 接口资源（system:announcement:read-receipt / system:announcement:remind）
-- 不在此手工登记，由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize
-- 自动落库并授予 SUPER_ADMIN；前端入口内嵌于既有公告管理页（无新菜单）。
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1) 表：sys_announcement_read（公告已读回执，懒插入）
--    列名与 com.acme.scaffold.jooq.dsl.JooqTables.SYS_ANNOUNCEMENT_READ 注册严格一致。
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_announcement_read (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    announcement_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    -- 已读回执时间（markRead 时刻）
    read_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- 幂等锚点：同一用户对同一公告只留一条回执；markRead 并发场景下
    -- 先查后插若被唯一键拒绝（DataIntegrityViolationException），按已读幂等处理
    UNIQUE KEY uk_ann_user (tenant_id, announcement_id, user_id),
    -- 「我的已读公告」按用户反查；公告维度统计按 (announcement_id) 走 uk 前缀
    KEY idx_user (tenant_id, user_id),
    KEY idx_ann (tenant_id, announcement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------------------------------
-- 2) 幂等扩展：为既有 sys_announcement 补两列（仅当列不存在时才加）
--     audience_count：发布时活跃用户快照（统计分母）
--     read_count   ：已读人数（markRead 成功时 +1）
-- -------------------------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_announcement` ADD COLUMN `audience_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''发布时活跃用户快照（已读率分母）''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_announcement' AND COLUMN_NAME = 'audience_count'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_announcement` ADD COLUMN `read_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''已读人数（markRead 成功时 +1）''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_announcement' AND COLUMN_NAME = 'read_count'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ============================================================
-- Source migration: V41__notification_template.sql
-- ============================================================

-- V41 通知模板中心（M10）：为 M6 多渠道通知提供可复用模板样板。
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:notification-template:read / system:notification-template:manage）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。

-- 1) 表：sys_notification_template（逻辑删除 + 启用开关，编码租户内唯一）
CREATE TABLE IF NOT EXISTS sys_notification_template (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    title VARCHAR(200) NULL,
    content TEXT NOT NULL,
    msg_type VARCHAR(16) NOT NULL DEFAULT 'NOTICE',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    remark VARCHAR(255) NULL,
    version INT NOT NULL DEFAULT 0,
    deleted INT NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, template_code),
    KEY idx_tenant_channel (tenant_id, channel),
    KEY idx_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：通知模板（挂在「系统管理」1002 下，sort_no=104 紧随可观测中心）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1095, 0, 1002, 'system:notification-template', '通知模板', 'MENU', '/system/notification-templates', 'system/NotificationTemplate', 'system:notification-template:read', 'Bell', 1, 0, 104, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1095;


-- ============================================================
-- Source migration: V42__notification_record.sql
-- ============================================================

-- V42 通知发送记录（系统管理 → 通知记录），补全 M12：全五渠道统一发送审计面。
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:notification-record:read）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--
-- 口径说明（与 sys_sms_record 的分工）：
--   本表记「每次 NotificationService.dispatch 尝试」（含被拒与失败，覆盖 IN_APP/WEBHOOK/
--   WEBSOCKET/EMAIL/SMS 全渠道，是统一审计面，M11 模板发送回填 template_code）；
--   sys_sms_record 保持「仅校验后实际尝试的网关投递」（网关账单对账面）。
--   短信一次发送双写两表，属刻意设计而非重复。

-- 1) 表：sys_notification_record（摘要 + 掩码存储：标题/正文摘要化、接收方按渠道掩码，PII 不明文落库）
CREATE TABLE IF NOT EXISTS sys_notification_record (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    channel VARCHAR(16) NOT NULL,
    template_code VARCHAR(64) NULL,
    msg_type VARCHAR(32) NULL,
    title_summary VARCHAR(256) NOT NULL,
    content_summary VARCHAR(512) NOT NULL,
    receiver_summary VARCHAR(512) NOT NULL,
    receiver_count INT NOT NULL DEFAULT 0,
    delivered INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(512) NULL,
    operator_id BIGINT UNSIGNED NULL,
    operator_name VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    sent_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, sent_at),
    KEY idx_channel_time (channel, sent_at),
    KEY idx_status_time (status, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：通知记录（挂在「系统管理」1002 下，sort_no=105 紧随通知模板）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1096, 0, 1002, 'system:notification-record', '通知记录', 'MENU', '/system/notification-records', 'system/NotificationRecord', 'system:notification-record:read', 'Tickets', 1, 0, 105, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1096;


-- ============================================================
-- Source migration: V43__notification_preference.sql
-- ============================================================

-- V43 通知偏好中心（M13）：sys_notification_preference，让 M6 多渠道通知首次具备
-- 「谁想收什么」的接收侧口径，并修复 sys_user 三个 notify_* 渠道总开关为「死开关」的缺陷
-- （此前唯一读取方是个人中心自身，发送链路从不读取，用户关闭站内信通知仍照收）。
--
-- 设计要点：
--   1) 懒行模型：无行 = 默认开通，由 sys_user 渠道级总开关（notify_site_message /
--      notify_email / notify_mobile）兜底；恢复默认即物理删行，故无 deleted 列。
--   2) 唯一键 uk_user_channel_type：同一用户同一 (渠道, 消息类型) 至多一行，
--      写入走「查现存 → 更新或插入」，唯一索引兜底并发（与幂等记录表同范式）。
--   3) 渠道白名单 IN_APP / EMAIL / SMS / WEBSOCKET：WEBHOOK 的地址属外部系统，
--      无个人偏好语义，拒绝建行（规则见 NotificationPreferenceRules）。
--   4) 自服务页面（个人中心 → 通知订阅）：无菜单、无权限码播种，
--      接口 /api/notification-preferences/mine 仅登录可访问，userId 一律服务端取自安全上下文。

CREATE TABLE IF NOT EXISTS sys_notification_preference (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    channel VARCHAR(16) NOT NULL,
    msg_type VARCHAR(32) NOT NULL DEFAULT 'NOTICE',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_channel_type (user_id, channel, msg_type),
    KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- Source migration: V44__notification_quiet_hours.sql
-- ============================================================

-- V44 通知免打扰时段（M14）：sys_notification_quiet_hours，让 M6 多渠道通知首次具备
-- 「什么时候收」的时间维口径——M13 偏好中心闭合「谁想收什么」，本模块补齐接收侧最后一维。
--
-- 设计要点：
--   1) 懒行模型：无行 = 无免打扰（与 M13「无行默认开通」互为镜像）；关停免打扰保留配置行
--      （enabled=0），故无「恢复默认」删行语义，也无 deleted 列。
--   2) 按接收者维度、不按渠道：免打扰是时间维总闸（先于 M13 偏好过滤生效），
--      一名用户至多一行，唯一键 uk_user 兜底幂等，写入走「查现存 → 更新或插入」。
--   3) 时间口径：start_time / end_time 为 TIME 列，半开区间 [start, end)，按服务器本地时间
--      裁决；start > end 表达跨午夜窗口（默认 22:00–08:00）；start = end 为退化窗口，
--      由服务端校验拒绝（见 NotificationQuietHoursService.update）。
--   4) 告警穿透：alert_breakthrough 默认 1——ALERT 类型消息在免打扰窗口内照常投递，
--      故障告警不应被时间窗吞掉；其余消息类型（NOTICE/SYSTEM/CC）受抑制。
--   5) 广播（IN_APP 仅 roleIds/orgIds、WEBSOCKET 无定向接收者）与 WEBHOOK 不过滤：
--      受众规模不定且无个人作息语义，属文档化限制（见开发手册 M14 章节）。
--   6) 自服务页面（个人中心 → 免打扰）：无菜单、无权限码播种，
--      接口 /api/notification-quiet-hours/mine 仅登录可访问，userId 一律服务端取自安全上下文。

CREATE TABLE IF NOT EXISTS sys_notification_quiet_hours (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    alert_breakthrough TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user (user_id),
    KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- Source migration: V45__notification_digest.sql
-- ============================================================

-- V45 通知聚合摘要（M17）：sys_notification_digest，让 M6 多渠道通知具备「频次治理」能力——
-- 低价值高频通知（NOTICE 类）先聚合、按阈值或最大等待时长合并为一条摘要再投递，
-- 与 M13（谁想收什么）、M14（什么时候收）共同构成接收侧完整口径。
--
-- 设计要点：
--   1) 一次性投递语义：行无论摘要投递成功/跳过/失败均<b>物理删除</b>（无 status、
--      无 deleted 列）——聚合行是「待投递缓冲」而非档案，反复重投只会制造 FAILED
--      日志轰炸；审计由 M12 sys_notification_record 承担（摘要消息本身也走 dispatch 留痕）。
--   2) 唯一键 uk_user_channel_type：同一用户同一 (渠道, 消息类型) 至多一条聚合行，
--      新消息到达即合并（item_count 累加、items_json 保留最近 maxItems 条、
--      last_at 前推），写入走「查现存 → 更新或插入」（本仓动态 DSL 无 upsert API），
--      与 M13/M14 同范式。
--   3) 汇总触发双条件（先到先投）：item_count 达 app.notifications.digest.threshold
--      立即在挂点内刷新投递；或 last_at 距今超过 max-wait-minutes 由定时任务兜底
--      （idx_last_at 支撑扫描），防止低流量用户摘要永远囤在缓冲区。
--   4) 按接收者维度、不按广播：仅聚合有明确 userId 的定向消息（IN_APP/WEBSOCKET 的
--      receiverIds、EMAIL/SMS 反查 sys_user 全部命中的地址）；广播与 WEBHOOK 无个人
--      投递箱语义，不聚合（与 M13/M14 广播不过滤口径一致）。
--   5) items_json 为 JSON 字符串数组（每条 = 标题优先、正文回退的单行摘要，截断），
--      由服务端序列化写入；投递时按 userId 现查 email/mobile 生成摘要消息，
--      用户已删或无地址则直接删行（无处可投）。
--   6) 摘要消息 msgType 恒为 "DIGEST"：不在 app.notifications.digest.msg-types 配置内
--      （不会再被挂起，防递归），也不在 M13 偏好白名单内（无偏好行、默认开通，
--      仅受 sys_user 渠道总开关约束），天然复用 M13/M14/M16 全链路。

CREATE TABLE IF NOT EXISTS sys_notification_digest (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    channel VARCHAR(16) NOT NULL,
    msg_type VARCHAR(32) NOT NULL,
    item_count INT NOT NULL DEFAULT 0,
    items_json TEXT NULL,
    first_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_channel_type (user_id, channel, msg_type),
    KEY idx_tenant_user (tenant_id, user_id),
    KEY idx_last_at (last_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

