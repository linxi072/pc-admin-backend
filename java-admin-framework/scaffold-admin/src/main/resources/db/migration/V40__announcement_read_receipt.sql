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
