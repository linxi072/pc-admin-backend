/**
 * 跨模块通用常量，对应若依 {@code common.constant} 的约定位置。
 *
 * <p>收录标准（三条同时满足）：字面值稳定、语义跨模块一致、且尚无其它单一事实来源。
 * 各模块以委托方式引用，禁止复制字面值。刻意不收录业务错误码、分页护栏阈值与
 * 各模块专属配置前缀——它们已分别由 error.CommonErrorCode、api.PagingRules 与各自的
 * {@code @ConfigurationProperties} 类维护，搬过来只会产生第二个事实来源。</p>
 *
 * <p>新增常量必须同步补 {@code CommonConstantsTest} 的字面值断言，确保后续重构
 * 不会静默改变 HTTP 响应、日期序列化格式或静态资源路径。</p>
 */
package com.acme.scaffold.common.constant;
