/**
 * 全局 Spring 装配配置（Web MVC、安全、数据源、拦截器等）。
 *
 * <p><b>模块 ↔ 包映射例外说明</b>：本包位于 {@code scaffold-framework} 模块内，
 * 该模块的类分布在 {@code com.acme.scaffold.config} / observability / monitor（含 detection /
 * observability 子包）/ chaos 四个顶层包，并不存在 {@code com.acme.scaffold.framework} 包。若依把框架装配
 * 集中在 {@code com.ruoyi.framework}；本工程保持历史包根，理由与 {@code common.jooq} 一致：
 * 包重命名牵动组件扫描与大量 import，属于高风险、零收益的形式对齐。</p>
 *
 * <p>模块边界由 Maven 依赖与 {@code scripts/check-conventions.py} 的 H5 DAG 强制，
 * 而非包名；详见 {@code docs/MODULES.md} 与 {@code docs/DIRECTORY.md}。</p>
 */
package com.acme.scaffold.config;
