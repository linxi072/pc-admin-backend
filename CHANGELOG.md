# Changelog

本文件记录 pc-admin-backend（java-admin-framework + pc-admin-vue3-vite）的版本演进。
版本号以仓库根 `CHANGELOG.md` 为准，与后端根 POM、前端 package.json 强制一致
（由门禁 `scripts/check-version.py` 守护）。格式参考 Keep a Changelog。

## [1.2.0] - 2026-11-23（规划中，版本待发布；本条目跟踪组件库抽取进度）

主题：平台化演进方向 A —— 前端独立组件库包（P1，分两批交付）。

### Added（新增）
- `pc-admin-ui/` 独立组件库包（`@acme/admin-ui@1.1.0`，纯 JS + Vite lib mode）：
  - **第一批**：迁移零业务依赖组件 `BaseChart` / `StatCard` / `CopyText` 及随包纯函数 `stat` / `theme`，新增 design tokens（`--acme-ui-*`）。
  - **第二批**：按 `PLATFORM-EVOLUTION-PLAN.md` A2 补齐 6 个通用组件 `ProTable` / `QueryForm` / `StatusTag` / `DescriptionList` / `ConfirmDialog` / `DictSelect`。
  - 产出 ESM + CJS 双格式 + 独立 CSS；peerDependencies 外部化 vue / element-plus / echarts，规避双实例。
  - 配套 21 例单测（9 组件 SSR smoke + 纯函数契约）与 SFC/i18n 门禁。
- 主应用接入：`file:../pc-admin-ui` 依赖 + Vite alias（默认源码直引，`ACME_UI_PACKAGE=1` 切包引用）；`src/components/index.js` 薄 re-export 兼容层；`JobListView` 完成库包直引切换（验收④）。
- CI 增量：`.github/workflows/ci.yml` 新增 gates 库包扫描步骤与 `ui-lib` job（保留既有 4 job 与覆盖率门禁）。
- 验证手册新增组件库专章（9 组件输入输出/边界/异常契约、两种接入模式、验证命令）。

> 注：版本号以根 POM 为准（当前 1.1.0），本条为下一版本规划跟踪；`check-version.py` 仍以 `[1.1.0]` 条目为权威判据。

## [1.1.0] - 2026-11-18

主题：**工程可信度恢复**（部署制品出库、质量门禁与 CI 重建、测试覆盖重建立项、版本治理统一）。

### Removed（移出交付范围）

- `java-admin-framework/monitoring/`（Prometheus 抓取/告警规则、Grafana 看板与数据源配置）移出版本控制，
  由部署仓库/运维平台托管；`.gitignore` 增加 deploy/monitoring/Dockerfile/systemd 等防误提交规则
  （V1.0.9 已移除 `deploy/`：Dockerfile、docker-compose、systemd、启停/回滚脚本）。

### Added（新增）

- 重建质量门禁脚本套件 `java-admin-framework/scripts/`：
  `check-all.py`（统一编排）+ `check-version.py` / `check-conventions.py` / `check-naming.py` /
  `check-directory.py` / `check-cluster-layout.py` / `check-permission.py` / `check-api-contract.py`
  + 前端 `check-sfc.mjs` / `i18n-leak-scan.mjs`；配套 `dependency-check-suppressions.xml` 与 `gate_lib.py`。
- 重建 CI：`.github/workflows/ci.yml`（门禁、后端构建+测试、前端构建+测试、覆盖率门禁，
  全部必经且失败阻断）与 `.github/workflows/security-scan-scheduled.yml`（每周 SCA）。
- 版本一致性门禁（check-version.py）：根 POM ≡ 14 模块 parent ≡ package.json ≡ CHANGELOG 条目 ≡ 文档 jar 引用。
- 关键模块单测重建立项：scaffold-common（Result/SensitiveMasker/CircuitBreaker）、
  scaffold-security（PasswordPolicy）等，详见 `docs/TEST-COVERAGE-PLAN.md`。
- **补全云大模型接入（此前为占位）**：`CloudLlmProvider` 实现 OpenAI 兼容 chat/completions
  真实调用（JDK HttpClient + Jackson，零新依赖），含 Markdown 围栏剥离、JSON 平衡提取、
  表名/列名 snake_case 校验、JDBC 类型白名单归一与 `JavaTypeMapper` 映射；
  运行期失败（网络/协议/解析）经 `ai.llm.fallback-on-failure=true`（默认）降级回退启发式提供者，
  审计字段如实标记实际来源；未配置端点仍抛 `LlmNotImplementedException`（501）；
  编排层与解析逻辑配套 12 例离线单测；`application.yml` 新增 `ai.llm.*` 配置段
  （端点/密钥经 `AI_LLM_*` 环境变量注入，不入库）。
- 测试覆盖计划文档 `docs/TEST-COVERAGE-PLAN.md`（阈值演进：起步线 0.10/0.05 → 1.3.0 目标 0.70/0.60）。
- 平台化演进方案 `docs/PLATFORM-EVOLUTION-PLAN.md`：独立组件库包（P1）/ 网关层（P2）/
  多区域一致性（P3），含改动项、需变更文件、配置项、接口影响与验收标准。

### Changed（变更）

- 版本号统一：根 POM 与 14 个模块 parent 1.0.0 → **1.1.0**；前端 package.json 1.0.0 → **1.1.0**；
  文档中 `scaffold-admin-1.0.0.jar` 引用同步更新。
- 文档漂移修复：前端 README 死链（docs/manual/、docs/index.md）改为指向 docs/USAGE.md 与 docs/README.md。
- **前端构建修复（遗留语法错误，此前生产构建无法通过）**：修复 4 个 `.vue` 文件中
  双反引号模板字符串（`` `` `` 非法 JS）与 `:placeholder` 属性内未转义引号
  （JobListView / TemplateCenterView / DesignListView / WorkflowDesigner）；
  修复后 `npm run build` 与 vitest（9/9）全部通过。
- Topbar 用户菜单 else 分支为死代码（菜单仅含 profile/settings/logout），
  "演示环境暂未实现该页面"文案改为未知命令防御提示。
- ESLint 全量治理：清零 6 处 error，含 3 处运行时 bug——`openApiClient.js` `requestRaw`
  未导入（OAuth2 客户端令牌签发调用即崩）、`ArticleView.vue` `viewArticle` 未导入
  （cms.js 与后端端点均已存在，仅视图漏 import，打开详情即崩）、`DynamicForm.vue` i18n 残留 `t`；
  `formSchema.js` 校验器移除未使用的 `t` 形参；`realtime.js`/`tree.js` 风格修正。
- 前端单测扩容：新增 `tree.test.js`（9 例，成环守卫/自定义键）与 `formSchema.test.js`
  （13 例，默认值/条件可见性/校验规则/离线校验），前端用例 9 → 31。
- CMS 文章详情接入真实浏览计数：`POST /api/cms/article/{id}/view`（后端端点已存在），
  修复视图漏 import 导致的打开详情崩溃，前端用例同步通过。
- 测试覆盖第二批启动：`JavaTypeMapperTest`（11 例，全类型映射矩阵）、
  `TreeBuilderTest`（6 例，层级/保序/森林升根/成环守卫）。
- 模块职责与命名治理（行为不变重构）：
  - 重复实现收敛：`ImportController` 删除与 `CsvExporter` 重复的 CSV 公式注入防护
    （统一由写出侧中和，附带对齐口径——失败报告中 `-5` 等合法数值不再被强制文本化）；
    列权限通用掩码下沉 `SensitiveMasker.genericMask`（security 委托 common，消除双实现）；
    CMS 栏目树改用 `TreeBuilder`（消除第三套手写树构建，孤儿升根/成环守卫语义逐一对齐，
    新增 7 例纯函数单测）。
  - 命名/目录统一：workflow `WorkflowNodeDTO`/`WorkflowEdgeDTO` → `*View`、job
    `JobVO`/`JobLogVO`/`JobHandlerOptionVO` → `*View`（响应视图统一 `View` 后缀）；
    job/monitor 控制器目录统一 `api/`（消除 `controller/` 与 `api/` 双约定及 monitor
    alert 双层分裂）；`docs/NAMING.md` 增补语义层约定（§7）。
  - 前端重复逻辑收敛：新增 `utils/datetime.js`（9 个视图同构时间格式化归一，
    占位符逐字保留）、`utils/statusTag.js`（状态→标签查表统一）；`TenantGovernanceDialog`
    归位 `views/system/components/`；4 处 `fetchXxx` 统一 `loadXxx`；前端用例 31 → 40。

### Known Issues

- 后端全量测试覆盖恢复与 JaCoCo 阈值收紧（0.10/0.05 起步线 → 0.70/0.60）按
  `docs/TEST-COVERAGE-PLAN.md` 分批推进。
- 契约步（check-api-contract.py）当前为信息性；backend_only 与 duplicate 输出待治理后
  可升级 `--strict` 阻断。

## [1.0.9] - 2026-11-11

主题：工程治理与规范化（无功能新增）。

- 若依规范对齐（RUOYI-ALIGNMENT）：业务模块收敛为 56 个子域簇、V83/V84/V85 前向迁移、
  6 个 Controller `{旧,新}` 双挂别名过渡。
- 门禁体系升级：D12–D14 目录门禁、P1 权限码一致性门禁、N1–N4 命名门禁、行为回归扩充至 100 例、
  `check-all.py` 7 步编排（注：门禁脚本本体在后续 `c9d248e` 提交中被删除，1.1.0 已重建）。
- 代码规范基线：editorconfig / checkstyle / eslint / prettier。
- 前端移除自研 i18n：静态文案内联中文、动态键改用静态字典（tx）。
- 数据库初始化改造：运行时移除 Flyway，改用 `db/init/ddl.sql` + `dml.sql` 手工初始化；
  删除 migration 历史版本迁移脚本（V32–V85）。
- 大量重构归一：`@Slf4j` 统一、remove→delete 归一、import 清理与排序。

## [1.0.7] - 2026-11-08（口径日期，以文档基线为准）

主题：上线就绪整改 + 能力扩容（R1–R16 全线落地）。

- 上线就绪评估整改 16 项（P0×3 + P1×9 + P2×4）：数据权限 fail-closed、Webhook 令牌校验、
  通知异步化+熔断、keyset 分页、限流 fail-closed、JSON 日志等。
- 扩容：插件 SPI、领域事件总线、AI 脚手架（启发式）、多区域数据源路由、CMS、IM、审批门户、
  看板模板、移动端自适应、低代码深化、组件库沉淀、微前端宿主、OTel 追踪、APM 大盘、
  混沌注入、配置中心。

## [1.0.6] - 2026-11-08

- 抽出 `scaffold-security` 独立安全域模块；docs/ 体系重建
  （ARCHITECTURE / MODULES / KEY-FLOWS / USAGE / DIRECTORY / NAMING / ROADMAP）。
- 模块结构整合：17 → 14 模块（plugin→common、approval→workflow、ai→codegen，包路径不变）。

## [1.0.3] - 2026-11-08

- 按 RuoYi-Vue 思路完成模块化单体拆分，建立 14 模块结构与依赖 DAG。
