-- =====================================================================================
-- V50 文件管理增强（File Management Enhancement）
-- -------------------------------------------------------------------------------------
-- 目标库：MySQL 8.x（InnoDB + utf8mb4 + utf8mb4_0900_ai_ci）
--
-- 背景与模型取舍：
--   sys_file 每上传一个文件就落一条记录 + 一份物理文件，常见管理场景存在三类浪费：
--     a) 重复上传：同一份内容（如公共头像、模板文档）被不同业务/用户反复上传，
--        磁盘与记录双倍膨胀；
--     b) 误删物理文件：两条记录 stored_name 相同时（历史批量导入或复制），删一条
--        会把另一条指向的物理文件一并删掉，下载即 404；
--     c) 缺少统计：管理者看不到「哪个来源模块传了多少文件、占用多少空间」。
--
--   故本迁移为 sys_file 增加 content_hash 列，支撑三项增强：
--     1) 秒传（dedup）：上传时计算内容 SHA-256，命中未删记录则复用其
--        stored_name/storage_path，不落盘、不产生新物理文件；
--     2) 引用计数删除保护：删除前按 stored_name 统计未删记录数，>1 时仅逻辑删
--        记录、保留物理文件；
--     3) 统计：按 source_module 分组聚合（无需新列，本迁移只加索引支撑）。
--
-- 幂等设计：
--   - 加列与加索引均用 information_schema 守卫做幂等 ALTER（重复执行不报
--     1060/1061  duplicate 错误），可直接在目标库重复执行；
--   - 存量行 content_hash 为 NULL，不参与秒传去重（NULL 不匹配等值查询），
--     语义安全；后续重新上传同内容时自然回填哈希并纳入去重；
--   - CHAR(64) 固定长度存 SHA-256 十六进制，定长列比较快且与 normalizeHash
--     校验严格对齐。
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1) 幂等扩展：sys_file 增加 content_hash 列（内容 SHA-256 十六进制，秒传去重锚点）
--    位置置于 content_type 之后，与 JooqTables.SYS_FILE 列顺序无关（DSL 按名映射）。
-- -------------------------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_file` ADD COLUMN `content_hash` CHAR(64) NULL COMMENT ''内容 SHA-256 十六进制（秒传去重锚点）'' AFTER `content_type`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'content_hash'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------------------------------
-- 2) 幂等扩展：content_hash 加普通索引（秒传命中等值查询；NULL 值不进索引，零膨胀）
-- -------------------------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_file` ADD KEY `idx_content_hash` (`content_hash`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_content_hash'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
