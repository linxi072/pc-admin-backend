-- V85 数据看板模板表补模块前缀（若依规范对齐·取证局限收口）：dashboard_template -> sys_dashboard_template
--
-- 背景：V78 建立的 dashboard_template 是全仓第二张、也是最后一张缺少 sys_/wf_/cms_/im_
--   等域前缀的主数据表。原始《若依规范对齐差异清单》§2.2.4 只统计到 V71（51 张表），
--   因此只发现 alert_rule（已由 V84 归一）；本次对 V32~V84 全部 CREATE TABLE 做全量
--   抽取（实到 58 张表）后确认 dashboard_template 为唯一剩余裸名表。本版本只改表名，
--   不改任何列、索引、数据、接口与前端行为。
--
-- 版本链约束：db/migration/README.md 明令「版本文件字节不可变、结构性变更一律前向追加」，
--   故 V78 的 CREATE TABLE dashboard_template 保持原样；新库由 V78 建表 → 本文件重命名，
--   最终落态与「V78 直接建 sys_dashboard_template」等价，且存量库可平滑升级。
--
-- 幂等：information_schema 双守卫 —— 仅当 dashboard_template 存在且 sys_dashboard_template
--   不存在时才 RENAME；重复执行 / 已是新表名的库命中 'DO 0' 无操作，不会撞 1050/1060 类错误。
--   MySQL 的 RENAME TABLE 是原子 DDL，V78 建立的 3 个索引、数据、自增状态均随表保留。
--
-- 【部署顺序·必读】这是结构级不兼容变更，Java 侧 SqlTables.DASHBOARD_TEMPLATE 已同步指向
--   sys_dashboard_template：
--   1) 先备份：mysqldump --single-transaction <db> dashboard_template > dashboard_template_backup.sql
--   2) 停写窗口内执行本迁移（RENAME 需短暂 MDL 锁，期间看板模板读写会阻塞）
--   3) 确认 SELECT COUNT(*) FROM sys_dashboard_template 可用后，再发布含新常量的应用版本
--   反向发布（先上应用、后跑迁移）会因表不存在导致看板模板模块整体不可用。
--   V84（alert_rule 重命名）同为结构级变更，两者可合并进同一个停写窗口执行。
--
-- 【回滚】如需回到旧表名，先下线新版应用，再执行（同样需要停写窗口）：
--   RENAME TABLE `sys_dashboard_template` TO `dashboard_template`;
--   并同步把 SqlTables.DASHBOARD_TEMPLATE 的表名字符串改回 dashboard_template 后重新发布。

SET @ddl = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dashboard_template') = 1
        AND (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dashboard_template') = 0,
        'RENAME TABLE `dashboard_template` TO `sys_dashboard_template`',
        'DO 0')
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
