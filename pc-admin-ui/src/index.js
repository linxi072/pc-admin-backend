// @acme/admin-ui 统一出口（第一批：零业务依赖组件 + 随包纯函数）。
// 组件均为 <script setup> SFC，样式由 Vite lib mode 抽取到 admin-ui.css。
import './styles/tokens.css'

export { default as StatCard } from './components/StatCard.vue'
export { default as BaseChart } from './components/BaseChart.vue'
export { default as CopyText } from './components/CopyText.vue'

export { formatStat, trendOf } from './utils/stat'
export { getChartTheme, currentTheme, THEMES } from './utils/theme'
