package com.acme.scaffold.config.center.dto;

/**
 * 配置中心状态视图（R16）。
 *
 * @param centerType                配置中心类型（local / nacos / apollo）
 * @param dynamicRefreshSupported   是否支持动态热刷新（远端中心为 true）
 * @param effectiveCount            当前生效（白名单前缀下）配置项数量
 */
public record ConfigCenterStatusView(String centerType, boolean dynamicRefreshSupported, int effectiveCount) {
}
