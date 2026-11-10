package com.acme.scaffold.common.validation;

/**
 * 新增场景校验分组（Bean Validation marker interface）。
 *
 * <p><b>刻意保留的公共扩展点</b>：2026-11-10 若依对齐取证复核确认，本仓
 * {@code scaffold-*} 源码中零引用（既无 {@code groups = CreateGroup.class}，也无
 * 实现 / 注入）。作为 {@code scaffold-common} 对外暴露的分组契约保留，供下游模块
 * 按 {@code @Validated(CreateGroup.class)} 启用新增态的分组校验；不做死代码删除，
 * 以免破坏公共 API 兼容性。登记见 {@code docs/RUOYI-ALIGNMENT.md} §3.7。</p>
 */
public interface CreateGroup {
}
