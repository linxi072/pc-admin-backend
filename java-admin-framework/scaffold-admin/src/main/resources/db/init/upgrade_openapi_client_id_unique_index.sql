-- 存量库升级：为 sys_openapi_client 补「未删除客户端 ID 全局唯一」约束。
-- 新库无需执行：db/init/ddl.sql 已内联生成列 client_id_active 与同名唯一索引。
-- 执行约束：先备份、停写窗口内执行；本脚本不做列/索引存在性守卫，重复执行会报重复列。
-- 前置检查（应为 0 行）：
--   SELECT column_name FROM information_schema.COLUMNS
--   WHERE table_schema = DATABASE() AND table_name = 'sys_openapi_client'
--     AND column_name = 'client_id_active';
USE `scaffold`;

ALTER TABLE `sys_openapi_client`
    ADD COLUMN `client_id_active` VARCHAR(64)
    GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `client_id` ELSE NULL END) STORED;

-- 去重：同一未删除客户端 ID 只保留一条，优先高版本、其次最新 id；
-- 逻辑删除行不会进入唯一键（生成列为 NULL），无需清理。
-- 注意：client_id 是 OAuth2 认证绑定键，去重丢弃的行对应的外部系统须重新注册并换发密钥。
DELETE r FROM `sys_openapi_client` r
JOIN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY client_id_active
                   ORDER BY version DESC, id DESC
               ) AS rn
        FROM `sys_openapi_client`
        WHERE client_id_active IS NOT NULL
    ) ranked
    WHERE rn > 1
) dup ON dup.id = r.id;

CREATE UNIQUE INDEX `uk_openapi_client_id_active`
    ON `sys_openapi_client` (`client_id_active`);
