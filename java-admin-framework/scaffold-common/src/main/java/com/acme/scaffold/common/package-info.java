/**
 * 跨模块公共能力根包，对应若依 {@code com.ruoyi.common} 的位置。
 *
 * <p><b>组织方式</b>：本工程采用「扁平特性子包」，而非若依的
 * annotation / config / constant / core / enums / exception / filter / utils 八层分包；
 * 每个子包承载一个自洽的公共能力，包内可再分 domain / dto / service / rules 层。
 * 这是既有重构约定，不因对齐若依而重命名包——包重命名会改动数百处 import 与
 * Spring 组件扫描结果，属于会引入回归的大规模搬迁。</p>
 *
 * <p><b>职责分组</b>（新增类先按此表定位，避免制造第二事实来源）：</p>
 * <ul>
 *   <li>契约层：api（统一响应 / 分页 / 游标）、error（错误码）、constant（跨模块字面值）、
 *       i18n、validation；</li>
 *   <li>运行时支撑：context、web、controller、exception、security、sensitive、tenant、
 *       observability、resilience；</li>
 *   <li>通用能力：audit、event、export、importer、id、idempotency、messaging、notification、
 *       oauth2、plugin、text、tree、util。</li>
 * </ul>
 *
 * <p><b>落位决策树</b>：跨模块且语义稳定的字面值 → constant；已有单一事实来源的直接引用，
 * 不重复搬运（错误码归 error.CommonErrorCode、分页护栏归 api.PagingRules、
 * 文本截断归 text.TextUtils）；纯函数规则 → 对应能力包的 rules 子包；
 * 数据结构 → dto / vo / domain（check-conventions.py 的 H4 硬规则）。</p>
 *
 * <p><b>禁止</b>：在特性包根放数据类；从本包反向依赖任何业务模块；新增顶层包根
 * （历史例外仅 {@code com.acme.scaffold.jooq}，见其包说明）。</p>
 */
package com.acme.scaffold.common;
