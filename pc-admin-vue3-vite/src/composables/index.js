// 组合式函数出口（前端共享 composables 的统一引入点）。
// 便于后续跨模块复用与统一 tree-shaking。

export { useBreakpoint, resolveBreakpoint, isMobileWidth, MOBILE_MAX_WIDTH } from './useBreakpoint'
