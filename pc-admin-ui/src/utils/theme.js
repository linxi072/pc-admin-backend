// 主题色板纯函数（随组件库发布，仅保留 BaseChart 需要的只读部分）。
// 与主应用 src/utils/theme.js 的色值同源；不在此处写 localStorage/DOM，避免副作用泄漏到库包。
import { ref } from 'vue'

export const THEMES = [
  { key: 'light' }, { key: 'dark' }, { key: 'warm' }, { key: 'brand' },
  { key: 'green' }, { key: 'rose' }, { key: 'cyan' }, { key: 'amber' }
]

export const currentTheme = ref('light')

/** 返回当前主题下 ECharts 需要的「字面量色板」（ECharts 无法解析 CSS 变量）。 */
export function getChartTheme() {
  const map = {
    light: {
      text: '#1f2329', sub: '#909399', axis: '#dcdfe6', split: '#ebeef5',
      tooltipBg: '#ffffff', tooltipBorder: '#ebeef5',
      palette: ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#a0cfff']
    },
    dark: {
      text: '#e6e8eb', sub: '#a8abb2', axis: '#3a404b', split: '#2c313a',
      tooltipBg: '#1a1d24', tooltipBorder: '#2c313a',
      palette: ['#3b82f6', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#60a5fa']
    },
    warm: {
      text: '#43291a', sub: '#8a5a3f', axis: '#dcc09c', split: '#ecd6ba',
      tooltipBg: '#fffaf1', tooltipBorder: '#ecd6ba',
      palette: ['#c2410c', '#5c8a0c', '#e0820a', '#d63a2f', '#b4977b', '#e8590c']
    },
    brand: {
      text: '#2a1a3a', sub: '#7c6aa0', axis: '#d4c6ee', split: '#e6def8',
      tooltipBg: '#ffffff', tooltipBorder: '#e6def8',
      palette: ['#7c3aed', '#4c9a2a', '#d98324', '#d63a2f', '#a896c4', '#9f67f5']
    },
    green: {
      text: '#14342b', sub: '#5b8a7c', axis: '#c4ddd3', split: '#dcebe5',
      tooltipBg: '#ffffff', tooltipBorder: '#dcebe5',
      palette: ['#10b981', '#16a34a', '#e6a23c', '#f56c6c', '#5b8a7c', '#34d399']
    },
    rose: {
      text: '#4a1d33', sub: '#9d5c79', axis: '#eec6da', split: '#f6deea',
      tooltipBg: '#ffffff', tooltipBorder: '#f6deea',
      palette: ['#ec4899', '#db2777', '#e6a23c', '#f56c6c', '#9d5c79', '#f472b6']
    },
    cyan: {
      text: '#0f3a47', sub: '#3f7e8f', axis: '#bcdce6', split: '#d6ecf2',
      tooltipBg: '#ffffff', tooltipBorder: '#d6ecf2',
      palette: ['#06b6d4', '#0891b2', '#e6a23c', '#f56c6c', '#3f7e8f', '#22d3ee']
    },
    amber: {
      text: '#4a3411', sub: '#92704a', axis: '#e8d4b6', split: '#f3e6d2',
      tooltipBg: '#ffffff', tooltipBorder: '#f3e6d2',
      palette: ['#d97706', '#b45309', '#e6a23c', '#f56c6c', '#92704a', '#f59e0b']
    }
  }
  return map[currentTheme.value] || map.light
}
