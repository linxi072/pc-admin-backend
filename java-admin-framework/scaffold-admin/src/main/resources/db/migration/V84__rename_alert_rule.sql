-- V84 告警规则表补模块前缀（若依规范对齐 P2-2）：alert_rule -> sys_alert_rule
--
-- 背景：V70 建立的 alert_rule 是全仓唯一缺少 sys_/wf_/cms_/im_ 等业务前缀的主数据表，
--   与「表由模块独占写入、按域加前缀」的既有约定不一致。本版本只改表名，不改任何列、
--   索引、数据、接口与前端行为。
--
-- 版本链约束：db/migration/README.md 明令「版本文件字节不可变、结构性变更一律前向追加」，
--   故 V70 的 CREATE TABLE alert_rule 保持原样；新库由 V70 建表 → 本文件重命名，
--   最终落态与「V70 直接建 sys_alert_rule」等价，且存量库可平滑升级。
--
-- 幂等：information_schema 双守卫 —— 仅当 alert_rule 存在且 sys_alert_rule 不存在时才 RENAME；
--   重复执行 / 已是新表名的库命中 'DO 0' 无操作，不会撞 1050/1060 类错误。
--   MySQL 的 RENAME TABLE 是原子 DDL，索引（idx_metric_enabled）、数据、自增状态均随表保留。
--
-- 【部署顺序·必读】这是结构级不兼容变更，Java 侧 SqlTables.ALERT_RULE 已同步指向 sys_alert_rule：
--   1) 先备份：mysqldump --single-transaction <db> alert_rule > alert_rule_backup.sql
--   2) 停写窗口内执行本迁移（RENAME 需短暂 MDL 锁，期间告警规则读写会阻塞）
--   3) 确认 SELECT COUNT(*) FROM sys_alert_rule 可用后，再发布含新常量的应用版本
--   反向发布（先上应用、后跑迁移）会因表不存在导致告警规则模块整体不可用。
--
-- 【回滚】如需回到旧表名，先下线新版应用，再执行（同样需要停写窗口）：
--   RENAME TABLE `sys_alert_rule` TO `alert_rule`;
--   并同步把 SqlTables.ALERT_RULE 的表名字符串改回 alert_rule 后重新发布。

SET @ddl = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alert_rule') = 1
        AND (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_alert_rule') = 0,
        'RENAME TABLE `alert_rule` TO `sys_alert_rule`',
        'DO 0')
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
