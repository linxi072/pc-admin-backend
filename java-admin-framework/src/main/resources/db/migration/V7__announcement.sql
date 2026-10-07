-- V7 新增功能模块：系统公告。
-- 表名 / 列名与 com.acme.scaffold.jooq.JooqTables 中的 SYS_ANNOUNCEMENT 注册严格一致。
--
-- 状态机：DRAFT(草稿) --publish--> PUBLISHED(已发布) --offline--> OFFLINE(已下线)
-- PUBLISHED 到期(publish_at/expire_at 越界)由定时任务自动下线。
-- 置顶：is_top=1 恒排在其余之前，其次按 publish_at 倒序。
--
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_announcement (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    -- DRAFT / PUBLISHED / OFFLINE
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    -- 是否置顶
    is_top TINYINT UNSIGNED NOT NULL DEFAULT 0,
    -- 定时生效时间(为空表示发布即生效)；<= NOW 时对 PUBLISHED 可见
    publish_at DATETIME(3) NULL,
    -- 有效期截止(为空表示长期有效)；< NOW 时自动下线
    expire_at DATETIME(3) NULL,
    -- 实际发布时间 / 下线时间
    published_at DATETIME(3) NULL,
    offline_at DATETIME(3) NULL,
    publisher_id BIGINT UNSIGNED NULL,
    view_count INT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tenant_status_pub (tenant_id, status, publish_at),
    KEY idx_tenant_top (tenant_id, is_top, publish_at),
    KEY idx_expire (tenant_id, status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
