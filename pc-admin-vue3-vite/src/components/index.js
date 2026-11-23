// 业务组件出口（前端共享 components 的统一引入点）。
// 便于跨模块复用通用业务组件，并统一 tree-shaking。
//
// V1.2 组件库抽取（第一批）：BaseChart / StatCard / CopyText 已迁入 @acme/admin-ui，
// 此处保留同名 re-export（薄兼容层），既有 `import { Xxx } from '@/components'` 调用方零改动；
// 耦合业务 API/i18n 的组件（DrillDrawer/ReportPanel/NotificationBell 等）仍留在主应用，
// 待后续去耦后迁入。
export { BaseChart, StatCard, CopyText } from '@acme/admin-ui'

export { default as DrillDrawer } from './DrillDrawer.vue'
export { default as DynamicForm } from './DynamicForm.vue'
export { default as FormDesigner } from './FormDesigner.vue'
export { default as IconPicker } from './IconPicker.vue'
export { default as NotificationBell } from './NotificationBell.vue'
export { default as ReportPanel } from './ReportPanel.vue'
export { default as ThemeSwitcher } from './ThemeSwitcher.vue'
