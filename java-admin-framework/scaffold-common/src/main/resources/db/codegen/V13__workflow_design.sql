-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V13__workflow_design.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.

CREATE TABLE IF NOT EXISTS wf_workflow_design (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, process_key VARCHAR(128) NOT NULL, process_name VARCHAR(200) NOT NULL, description VARCHAR(500) NULL, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', version INT NOT NULL DEFAULT 0, nodes_json VARCHAR(4000) NULL, edges_json VARCHAR(4000) NULL, form_schema VARCHAR(4000) NULL, bpmn_xml TEXT NULL, deployment_id VARCHAR(64) NULL, process_definition_id VARCHAR(128) NULL, published_by BIGINT NULL, published_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX wf_workflow_design_uk_tenant_key ON wf_workflow_design (tenant_id, process_key);

