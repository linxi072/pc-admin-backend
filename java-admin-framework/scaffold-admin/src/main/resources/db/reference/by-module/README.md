# 按功能模块分类的参考 SQL（db/reference/by-module/）

本目录由 `scripts/build_reference_modular.py` 从 `db/migration/` 迁移链（V32~V59）自动派生，**仅供阅读与追溯**。

> ⚠️ 这些 `.sql` 不是 Flyway 迁移，不被扫描，也**不可当迁移执行**。
> 权威可执行来源始终是 `db/migration/` 迁移链（V57 为 Java 类迁移 `V57__backfill_tenant_id.java`）。
> by-module 文件是逻辑抽取、依赖 V32 基线已存在，仅应在已含基线的库上整体阅读。

## 模块 → 迁移映射

| 模块文件 | 标题 | 来源迁移 | 说明 |
| --- | --- | --- | --- |
| `baseline.sql` | 基础框架（V1~V31 合并） | V32 | 用户/角色/菜单/部门/岗位/字典/参数/审计/登录日志/权限/接口资源/工作流基线等核心表与种子 |
| `file-center.sql` | 文件中心 | V33, V50 | 文件管理（V33）+ 文件去重（V50） |
| `csv-export-center.sql` | CSV 导出中心 | V34 | 导出任务与模板 |
| `post-management.sql` | 岗位管理 | V35 | 岗位表（并修复菜单 id 碰撞） |
| `import-center.sql` | 导入中心 | V36 | 导入任务与错误明细 |
| `sms-record.sql` | 短信记录 | V37 | 短信发送记录 |
| `system-parameters.sql` | 系统参数 | V38 | 参数配置表 |
| `observability-center.sql` | 可观测中心 | V39 | 链路/N+1/幂等/告警收件箱；修复 V35/V36 菜单碰撞 |
| `notification-center.sql` | 通知中心 | V40, V41, V42, V43, V44, V45 | 公告已读回执 + 通知模板/记录/偏好/免打扰/摘要 |
| `mask-strategy.sql` | 脱敏策略中心 | V46 | 脱敏策略与规则 |
| `form-center.sql` | 表单中心 | V47, V53, V58 | 表单定义试点（V47）+ 提交存储（V53）+ 定义菜单（V58） |
| `openapi-client.sql` | 开放平台 | V48 | OpenAPI 客户端 |
| `workflow-monitor.sql` | 工作流监控 | V49 | 工作流监控视图/表 |
| `tenant-management.sql` | 租户管理（SaaS） | V51, V54, V57 | 租户与隔离基础（V51）+ 隔离态势菜单（V54）+ 租户回填（V57，Java 类迁移无 .sql） |
| `dashboard.sql` | 报表大屏 | V52 | 大屏菜单播种 |
| `realtime.sql` | 实时推送 | V55 | 实时推送菜单 |
| `data-scope.sql` | 数据权限 | V56 | 数据权限配置菜单 |
| `job-dependency.sql` | 任务依赖编排 | V59 | DAG 任务依赖 |

重新生成命令：

```bash
python3 scripts/build_reference_modular.py
```
