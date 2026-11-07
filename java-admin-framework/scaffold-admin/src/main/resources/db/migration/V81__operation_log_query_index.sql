-- V81 审计日志查询索引补齐（E2E F1/F2 性能修复的数据层部分）。
-- 设计要点：
--   1) 现状：sys_operation_log 仅有 idx_module_time(tenant_id, module_code, occurred_at)、
--      idx_operator_time(tenant_id, operator_id, occurred_at)、idx_trace_id(trace_id)
--      三个二级索引，均以 tenant_id 打头。而「操作日志」页与工作台「最近动态」的查询条件
--      不带 tenant_id 前缀（单租户部署下 tenant_id 恒为 0，条件里没有该列），
--      导致 idx_operator_time 只能退化为全索引扫描（实测 127 万条 index 条目）
--      再叠加 ORDER BY occurred_at, id 的文件排序，单次请求 11.9~16.4s；
--   2) 本次补两个「无 tenant_id 前缀」的覆盖索引：
--        - idx_operator_occurred (operator_id, occurred_at, id)
--             服务 WHERE operator_id = ? ORDER BY occurred_at DESC, id DESC LIMIT n
--             （工作台「最近动态」/ 按操作人筛审计）；
--        - idx_occurred_id (occurred_at, id)
--             服务无筛选时的 ORDER BY occurred_at DESC, id DESC LIMIT n（列表首页）
--             与 occurred_at 区间筛选（rows 直接由索引给出，免回表排序）；
--   3) 排序键包含 id 作为 tie-breaker，索引列顺序与之严格一致，保证倒序扫描即可满足
--      ORDER BY，不再触发 filesort；
--   4) 索引名全局唯一（已全量检索 db/ 目录确认无重名）；标识符统一用反引号包裹并保留
--      snake_case 小写（与 V74 等既有迁移一致）。注意：MySQL 未开启 ANSI_QUOTES 时双引号是
--      字符串字面量而非标识符，CREATE INDEX "x" ... 会直接语法报错（本文件首版即踩此坑）；
--   5) CREATE INDEX 不支持 IF NOT EXISTS，Flyway 保证本文件只执行一次。

CREATE INDEX `idx_operator_occurred` ON `sys_operation_log` (`operator_id`, `occurred_at`, `id`);

CREATE INDEX `idx_occurred_id` ON `sys_operation_log` (`occurred_at`, `id`);
