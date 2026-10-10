# jOOQ 代码生成（并行层）

本目录与 `jooq-codegen.xml` 共同构成 jOOQ 代码生成的**并行探索层**，用于评估「以生成代码替代手写动态 DSL」的可行性。

## 与手写 DSL 的关系（重要）

- **手写动态 DSL（生产主线）**：`src/main/java/com/acme/scaffold/jooq/`（核心类 `JooqTables` / `TableRef`）。
  采用 jOOQ 动态 DSL，无需构建期代码生成、无需 Docker、无需连库，是当前所有数据访问逻辑的实际承载者。
- **本生成层（并行、可选）**：生成代码落入独立包 `com.acme.scaffold.jooqgen`，**与手写 DSL 互不覆盖**。
  仅作为对照/评估用途，默认不参与业务查询。

> 将生成代码**全面替换**手写 DSL 属高风险改造（涉及全量数据访问路径、需真实库验证、需风险评审），
> 当前**暂缓**。详见需求差距分析文档 §A6 / G5a 注记。

## 脚本来源

`V1__*.sql` ~ `V16__*.sql` 为从 `src/main/resources/db/migration/` 的 Flyway 迁移脚本归一化出的
**H2 兼容 DDL**（`DDLDatabase` 据此反向工程表元数据，无需活库）。新增迁移后需同步更新此处脚本与
`jooq-codegen.xml` 的 `<scripts>` 列表。

## 如何生成（需联网）

离线仓库未收录 `jooq-codegen` / `jooq-meta-extensions` / `h2` 构件，须在**具备网络**的机器执行：

```bash
# 连接 Maven Central 拉取代码生成器（pom 中 jooq-codegen profile 已追加 repo-central 仓库）
mvn -Pjooq-codegen generate-sources
```

生成结果写入 `src/main/java/com/acme/scaffold/jooqgen/` 并纳入版本控制；
之后常规离线构建（`mvn -o compile`）可直接使用已提交生成代码，无需重跑。

## 验证建议（替换前）

若未来决定以生成层替换手写 DSL，建议步骤：
1. 在真实 MySQL 上跑通生成（Dialect 对齐，避免 H2 与 MySQL 类型偏差）；
2. 选取 1~2 个低风险服务做试点，保持单测全绿；
3. 灰度切换并保留手写 DSL 回滚通道，直至全量验证通过。
