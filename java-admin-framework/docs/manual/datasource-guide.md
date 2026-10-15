# 多数据源使用指南（java-admin-framework）

> 本文是**多数据源（multi-datasource）** 的专项使用说明，面向「需要在运行时连接并切换多个数据库」的开发者与部署负责人。
> 四本手册中仅保留要点与索引，完整配置/代码/排障以本文为准：
> - 架构设计要点见《架构手册》§9.6；
> - 开发侧切换 API 见《开发手册》§34；
> - 运维侧连接池/健康见《运维手册》§31；
> - 部署侧环境变量见《部署手册》§15。

---

## 1. 背景与适用场景

框架默认以**单库**承载业务表、Flyway 迁移、JobRunr 调度、Flowable 流程引擎。但在以下场景需要多库：

- **读写分离 / 报表库**：把高频聚合查询（报表、审计分析）下沉到独立的只读副本或报表库，避免拖慢主库写入。
- **多租户 / 多业务域**：不同业务域的数据物理隔离到不同 MySQL 实例。
- **遗留系统对接**：只读接入第三方系统的库做数据对账。

> 设计原则：**主库回落（primary fallback）**。不切换时所有查询落主库，因此既有服务（注入 `DSLContext` / `DataSource`）行为完全不变——多数据源是「增量能力」，不是破坏性改造。

---

## 2. 配置组织结构

原 `spring.datasource`（单库、driver + url + user + pass 散落多文件）已统一收敛为 **`app.datasources`** 多数据源结构，并**关闭 Spring Boot 单库自动装配**：

```yaml
app:
  # 默认主数据源名称；不切换时所有查询落主库。
  datasources:
    primary: primary
    entries:
      primary:                       # 主库（必填，业务/Flyway/JobRunr/Flowable 均在主库）
        url: ${DB_PRIMARY_URL:jdbc:mysql://localhost:3306/scaffold?...}
        username: ${DB_PRIMARY_USERNAME:root}
        password: ${DB_PRIMARY_PASSWORD:root}
      report:                        # 第二个库按需启用，凭据同样走环境变量
        url: ${DB_REPORT_URL}
        username: ${DB_REPORT_USERNAME}
        password: ${DB_REPORT_PASSWORD}
```

每一项的字段：

| 字段 | 说明 | 默认值 |
| --- | --- | --- |
| `url` | JDBC 连接串 | 无（必填，缺失则启动 fail-fast） |
| `username` / `password` | 账号 / 密码 | 无（必须经环境变量注入） |
| `driver-class-name` | JDBC 驱动 | `com.mysql.cj.jdbc.Driver` |
| `pool.minimum-idle` | Hikari 最小空闲连接 | 5 |
| `pool.maximum-pool-size` | Hikari 最大连接 | 20 |
| `pool.connection-timeout` | 连接获取超时(ms) | 3000 |
| `pool.validation-timeout` | 连接校验超时(ms) | 1000 |
| `pool.idle-timeout` | 空闲回收(ms) | 600000 |
| `pool.max-lifetime` | 连接最大存活(ms) | 1800000 |
| `pool.connection-test-query` | 保活校验 SQL | `SELECT 1` |

`primary` 名称在 **`application.yml`** 声明；各 profile 的 `entries` 在 **`application-local.yml` / `application-prod.yml`** 填充，凭据一律 `${ENV_VAR}` 占位，**yml 中不硬编码任何凭据**。

---

## 3. 加载方式（启动期绑定 + fail-fast）

```mermaid
flowchart LR
  A[application.yml\napp.datasources.primary] --> B[MultiDataSourceProperties\n@ConfigurationProperties]
  C[application-{profile}.yml\napp.datasources.entries.*] --> B
  B --> D[DataSourceRegistry\n构造时建 Hikari 池]
  D -->|fail-fast| E{主库存在?\nurl 非空?}
  E -->|否| F[启动抛 IllegalStateException]
  E -->|是| G[RoutingDataSource\n@Primary]
  G --> H[Map&lt;String,DSLContext&gt;\n每库一个独立上下文]
```

- `MultiDataSourceProperties`（`@ConfigurationProperties("app.datasources")`）在 `DataSourceConfig` 经 `@EnableConfigurationProperties` 注册为 Bean，松绑定聚合主配置与 profile 配置。
- `DataSourceRegistry`（`@Component`）构造时遍历 `entries`，逐库 `new HikariDataSource()` 初始化连接池；**缺失主库**或**某库缺 `url`** 立即抛 `IllegalStateException`——配置错误在启动期暴露，不留到运行期。
- `DataSourceConfig` 装配三个核心 Bean：
  - **`@Primary RoutingDataSource`**（路由数据源）；
  - **`@Primary PlatformTransactionManager`**（事务与路由一致，保证 `@Transactional` 在连接获取阶段即按当前路由解析目标库）；
  - **`Map<String, DSLContext>`**（每个具名数据源一个独立 jOOQ `DSLContext`，供显式按名访问，不受路由上下文影响）。
- 原有的单 `DSLContext`（`JooqConfig` 产出、`@Primary`）底层指向 `RoutingDataSource`，因此既有服务无需改动即自动具备路由能力。

> **为什么要关闭自动装配**：`spring.autoconfigure.exclude=...DataSourceAutoConfiguration` 已在 `application.yml` 设置。否则 Spring Boot 会自建一个 `spring.datasource` 单库 Bean，与我们的多数据源定义冲突。

---

## 4. 路由与切换逻辑

采用标准 **`AbstractRoutingDataSource` + ThreadLocal 上下文** 模式：

```
请求/方法 → DataSourceContextHolder.set("report")
        → RoutingDataSource.determineCurrentLookupKey() 返回 "report"
        → 命中 targetDataSources["report"]
        → 未切换（null）时回落 defaultTargetDataSource（主库）
```

`RoutingDataSource` 每次获取连接时调用 `determineCurrentLookupKey()` 解析当前库名：
- 返回具体名称 → 命中对应具名数据源；
- 返回 `null`（未切换）→ 回落主库。

因此**现有所有服务在「不切换」时行为完全不变**。

### 4.1 两种切换方式

| 方式 | 适用 | 还原语义 |
| --- | --- | --- |
| **声明式** `@WithDataSource("report")` | 调用带 `@Transactional` 服务方法的外层编排方法 | 切面 `@Order(HIGHEST_PRECEDENCE)` 包裹事务切面，结束后还原为调用前状态 |
| **编程式** `DataSourceRouter.with("report", () -> ...)` | 跨库查询编排、规避切面顺序顾虑 | `finally` 中还原为调用前状态 |

#### 声明式示例

```java
@Service
public class ReportOrchestrationService {

    private final ReportRepository reportRepository;   // 注入的 DSLContext 走路由
    private final OrderService orderService;           // 主库服务

    // 标注后方法体内所有 DB 访问落到 report 库；结束后自动还原
    @WithDataSource("report")
    public ReportSnapshot build() {
        var rows = reportRepository.queryFromReportDb();   // → report 库
        var recent = orderService.recentOrders();          // → 主库（orderService 内部未切换）
        return merge(rows, recent);
    }
}
```

> 切面优先级：`DataSourceSwitchAspect` 置 `Ordered.HIGHEST_PRECEDENCE`，使其**早于**事务切面生效，确保 `@Transactional` 开启连接时已解析到目标库（避免回落主库）。

#### 编程式示例

```java
@Service
public class ReportOrchestrationService {

    private final DataSourceRouter router;     // 注入
    private final DSLContext reportDsl;        // 或直接用 router.dsl("report")

    public ReportSnapshot build() throws Exception {
        // 方式一：在指定库上下文中执行任务（自动还原）
        List<Row> rows = router.with("report", () ->
                reportDsl.selectFrom(REPORT_TABLE).fetch());

        // 方式二：直接拿具名 DSLContext（不受路由上下文影响，适合长链路多库混访）
        DSLContext report = router.dsl("report");
        DSLContext primary = router.primaryDsl();
        var a = report.selectFrom(REPORT_A).fetch();
        var b = primary.selectFrom(ORDER).fetch();
        return merge(a, b);
    }
}
```

### 4.2 API 速查

`DataSourceRouter`（`@Service`，推荐注入使用）：

| 方法 | 说明 |
| --- | --- |
| `DSLContext dsl(String name)` | 取指定名称数据源的 `DSLContext`，未配置抛 `IllegalArgumentException` |
| `DSLContext primaryDsl()` | 取主数据源 `DSLContext` |
| `<T> T with(String name, Callable<T> task)` | 在指定库上下文执行有返回值任务，自动还原 |
| `void with(String name, Runnable task)` | 同上，无返回值 |

`DataSourceContextHolder`（底层静态工具，一般不直接用）：`set` / `get` / `clear`。

---

## 5. 新增一个数据源（五步）

以新增一个只读 `analytics` 库为例：

1. **声明配置名**（如 `analytics`，非 `primary`）。
2. **填充 profile 配置**（`application-local.yml` 与 `application-prod.yml` 均加）：

   ```yaml
   app:
     datasources:
       entries:
         analytics:
           url: ${DB_ANALYTICS_URL}
           username: ${DB_ANALYTICS_USERNAME}
           password: ${DB_ANALYTICS_PASSWORD}
   ```
3. **注入环境变量**（部署侧）：`DB_ANALYTICS_URL` / `DB_ANALYTICS_USERNAME` / `DB_ANALYTICS_PASSWORD`。
   - local 可用 `${DB_ANALYTICS_URL:默认值}` 提供开发回退；
   - **prod 不要写默认值**，强制由环境变量注入（避免凭据误落库）。
4. **使用**：`@WithDataSource("analytics")` 或 `router.with("analytics", ...)` / `router.dsl("analytics")`。
5. **启动验证**：观察启动日志确认 `hikari-analytics` 连接池初始化成功；不切换时主库业务不受影响。

> 无需新建 Java 类——注册表按 `entries` 自动建池，路由与 `Map<String, DSLContext>` 自动包含新库。

---

## 6. 故障排查与注意事项

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 启动即报 `多数据源配置缺失主数据源` | `app.datasources.primary` 指向的库未在 `entries` 中 | 核对 `primary` 名称与 `entries` key 一致 |
| 启动报 `数据源 [x] 缺少 url` | 该库 `url` 未注入（环境变量缺失 / 占位符拼写错） | 确认 `DB_<NAME>_URL` 已设置；local 可用 `:默认值` 回退 |
| 切换后仍查到主库数据 | 切换发生在 `@Transactional` 开启连接**之后**（切面顺序问题） | 用 `@WithDataSource` 标注在**外层**编排方法，或改用 `router.with(...)`；不要在被 `@Transactional` 方法内部才 `set` |
| 同一事务内跨库 | 单 `@Transactional` 只能绑定一个物理连接 | 跨库业务拆成多个服务方法分别切换，或用 `router.dsl(name)` 显式多库混访 |
| 连接池指标只有一个 | 监控面板只看默认 `hikari` 池名 | 多库池名为 `hikari-<name>`（如 `hikari-report`），按名区分 |
| 关闭应用时连接未释放 | 注册表 `@PreDestroy` 才释放 | 正常优雅停机即可；强行 `kill -9` 残留由 MySQL 侧 `wait_timeout` 回收 |

### 关键约束

- **敏感信息零硬编码**：`url`/`username`/`password` 全部走 `${DB_<NAME>_*}`；`local` 带开发回退默认值，`prod` 无默认值（强制环境变量）。
- **prod 环境变量更名**：原单库变量 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 已更名为 `DB_PRIMARY_URL` / `DB_PRIMARY_USERNAME` / `DB_PRIMARY_PASSWORD`，**部署侧必须同步**。
- **主库职责**：Flyway / JobRunr / Flowable 一律走主库，不要在从库/报表库上跑迁移或调度。
- **默认回落**：未显式切换即主库，既有代码零改动；新增库只增不减。
