// @acme/admin-ui 统一出口。
// 组件均为 <script setup> SFC，样式由 Vite lib mode 抽取到 style.css。
import './styles/tokens.css'

// 第一批：零业务依赖组件
export { default as StatCard } from './components/StatCard.vue'
export { default as BaseChart } from './components/BaseChart.vue'
export { default as CopyText } from './components/CopyText.vue'

// 第二批：文档点名的通用组件（PLATFORM-EVOLUTION-PLAN 方向 A2）
export { default as ProTable } from './components/ProTable.vue'
export { default as QueryForm } from './components/QueryForm.vue'
export { default as StatusTag } from './components/StatusTag.vue'
export { default as DescriptionList } from './components/DescriptionList.vue'
export { default as ConfirmDialog } from './components/ConfirmDialog.vue'
export { default as DictSelect } from './components/DictSelect.vue'

// 随包纯函数
export { formatStat, trendOf } from './utils/stat'
export { getChartTheme, currentTheme, THEMES } from './utils/theme'
