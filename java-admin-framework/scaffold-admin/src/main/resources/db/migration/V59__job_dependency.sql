-- V59 任务依赖编排 DAG（M33）：为 sys_job 增加 depends_on 列，声明本任务依赖的前置任务。
--
-- 设计要点：
--   1) 依赖以 JSON 数组存储（如 [101, 102]），零新增表、零新索引负担；依赖的解析与环检测
--      全部在 JobDagRules 纯函数层完成（无 Spring / DB 依赖，可离线单测）。
--   2) WAITING 状态：存在未满足依赖的任务启动时进入 WAITING（不向调度器注册自身触发），
--      待其全部依赖成功执行后由完成事件驱动一次「即时触发」（复用 JobSchedulerPort.scheduleOneTime）。
--   3) 依赖检索走全量解析（任务量级小，无需为 depends_on 建索引），由 JooqJobDagAdapter 完成。
--   4) 幂等：列已存在则跳过（MySQL 8 的 ALTER TABLE ADD COLUMN 不支持 IF NOT EXISTS，故用
--      information_schema 探测后动态执行）。

SET @db = DATABASE();
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_job' AND COLUMN_NAME = 'depends_on');
SET @sql = IF(@col = 0,
    'ALTER TABLE sys_job ADD COLUMN depends_on VARCHAR(1024) DEFAULT NULL COMMENT ''依赖任务ID列表(JSON数组)''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
