-- V13 自定义工作流设计表：支持用户动态新增/删除/排序审批节点与执行步骤、
-- 配置条件分支，保存为草稿并发布为可生效的 Flowable 流程定义。
CREATE TABLE IF NOT EXISTS wf_workflow_design (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    process_key VARCHAR(128) NOT NULL,
    process_name VARCHAR(200) NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    nodes_json JSON NULL,
    edges_json JSON NULL,
    form_schema JSON NULL,
    bpmn_xml LONGTEXT NULL,
    deployment_id VARCHAR(64) NULL,
    process_definition_id VARCHAR(128) NULL,
    published_by BIGINT UNSIGNED NULL,
    published_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_key (tenant_id, process_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
