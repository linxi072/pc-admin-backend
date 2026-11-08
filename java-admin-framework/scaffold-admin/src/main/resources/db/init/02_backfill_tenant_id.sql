-- ============================================================
-- 02_backfill_tenant_id.sql — 存量库 tenant_id 回填（可选 / 手工执行）
--
-- 等价替代原 Flyway Java 迁移 V57__backfill_tenant_id.java。
-- 【全新空库】01_full_schema.sql 建表时 tenant_id 已有 DEFAULT 0，无 NULL 行，
--             本脚本是 no-op，可跳过；仅在「从旧库升级 / 导入历史数据」时执行。
-- 【执行方式】mysql --default-character-set=utf8mb4 -u<user> -p <db> < 02_backfill_tenant_id.sql
-- 【作用面】以 information_schema 为权威，遍历当前库中所有含 tenant_id 列的业务表，
--           把 NULL 归位到平台租户 0；平台共享维表与 schema_baseline 恒不触碰。
-- 【幂等】仅处理 tenant_id IS NULL 的行，可重复执行。
-- 【零孤儿断言】过程内逐表复核，回填后若仍存在 NULL 行则 SIGNAL 中断（fail-fast）。
-- ============================================================

DROP PROCEDURE IF EXISTS `scaffold_backfill_tenant_id`;

DELIMITER $$
CREATE PROCEDURE `scaffold_backfill_tenant_id`()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE tname VARCHAR(128);
    DECLARE affected BIGINT DEFAULT 0;
    DECLARE cur CURSOR FOR
        SELECT c.TABLE_NAME
          FROM information_schema.COLUMNS c
         WHERE c.TABLE_SCHEMA = DATABASE()
           AND c.COLUMN_NAME = 'tenant_id'
           AND c.TABLE_NAME NOT IN (
               'sys_tenant', 'sys_menu', 'sys_dict_type', 'sys_dict_data',
               'sys_config', 'sys_api_resource', 'schema_baseline');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    -- 1) 回填：tenant_id IS NULL -> 平台租户 0
    OPEN cur;
    backfill_loop: LOOP
        FETCH cur INTO tname;
        IF done THEN
            LEAVE backfill_loop;
        END IF;
        SET @sql = CONCAT('UPDATE `', tname, '` SET `tenant_id` = 0 WHERE `tenant_id` IS NULL');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        SET affected = affected + ROW_COUNT();
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE cur;

    -- 2) 零孤儿断言：回填后不得残留 tenant_id IS NULL 的业务行
    SET done = 0;
    OPEN cur;
    assert_loop: LOOP
        FETCH cur INTO tname;
        IF done THEN
            LEAVE assert_loop;
        END IF;
        SET @sql = CONCAT('SELECT COUNT(1) INTO @nulls FROM `', tname,
                          '` WHERE `tenant_id` IS NULL');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        IF @nulls > 0 THEN
            SIGNAL SQLSTATE '45000'
              SET MESSAGE_TEXT = 'tenant_id 回填后仍存在 NULL 孤儿行，请检查表结构或执行顺序';
        END IF;
    END LOOP;
    CLOSE cur;

    SELECT affected AS `backfilled_rows`;
END$$
DELIMITER ;

CALL `scaffold_backfill_tenant_id`();
DROP PROCEDURE IF EXISTS `scaffold_backfill_tenant_id`;
