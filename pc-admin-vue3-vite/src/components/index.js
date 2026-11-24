// 业务组件出口（前端共享 components 的统一引入点）。
// 便于跨模块复用通用业务组件，并统一 tree-shaking。
// 图表/统计/复制等通用组件由主应用 src/components 下的 SFC 直接提供，
// 调用方按需从具体文件引入（如 @/components/BaseChart.vue）。

export { default as DrillDrawer } from './DrillDrawer.vue'
export { default as DynamicForm } from './DynamicForm.vue'
export { default as FormDesigner } from './FormDesigner.vue'
export { default as IconPicker } from './IconPicker.vue'
export { default as NotificationBell } from './NotificationBell.vue'
export { default as ReportPanel } from './ReportPanel.vue'
export { default as ThemeSwitcher } from './ThemeSwitcher.vue'
