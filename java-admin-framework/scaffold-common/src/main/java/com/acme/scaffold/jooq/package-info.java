/**
 * jOOQ 动态 DSL 基础设施：表引用、类型化字段、分页与映射工具。
 *
 * <p><b>包根例外说明</b>：本包位于 {@code scaffold-common} 模块内，但包根是
 * {@code com.acme.scaffold.jooq} 而非 {@code com.acme.scaffold.common.jooq}。
 * 这是既有事实，全仓数百处 import 依赖该路径；重排包根不改变任何技术收益，却会
 * 造成大规模 import 迁移与潜在编译断裂，因此按「避免无关功能重写」原则保留。</p>
 *
 * <p>本包只提供无状态 DSL 工具（JooqTables / JooqWriters / JooqSorts 等），
 * 不持有业务实体、不定义数据表结构；表与列由各业务模块的服务代码显式声明。</p>
 */
package com.acme.scaffold.jooq;
