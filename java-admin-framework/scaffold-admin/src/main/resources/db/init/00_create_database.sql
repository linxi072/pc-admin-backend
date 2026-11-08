-- ============================================================
-- 00_create_database.sql — 新建空库（手工执行）
-- 运行时已移除 Flyway：应用启动不再创建/变更任何表结构。
-- 由 DBA / 部署脚本按 00 -> 01 -> 02 顺序显式执行。
--
-- 【执行方式】
--   mysql --default-character-set=utf8mb4 -u<user> -p < 00_create_database.sql
-- 【按需修改】库名 `scaffold` 须与 application-*.yml 的 DB_PRIMARY_URL 一致。
-- 【字符集】utf8mb4 / utf8mb4_0900_ai_ci（MySQL 8 默认排序规则，与结构链一致）。
--           若服务端为 MySQL 5.7 或未启用 0900 排序规则，改为 utf8mb4_general_ci。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `scaffold`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `scaffold`;

SELECT DATABASE() AS `target_database`,
       @@character_set_database AS `charset`,
       @@collation_database AS `collation`;
