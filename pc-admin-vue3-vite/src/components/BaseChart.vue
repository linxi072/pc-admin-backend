<template>
  <div ref="el" class="base-chart" :style="{ height }"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { getChartTheme, currentTheme } from '@/utils/theme'
// ECharts 按需引入：仅注册本项目实际用到的图表与组件（bar / line / pie +
// grid / tooltip / legend / title + Canvas 渲染器），避免全量包（~1MB）进首屏。
// 新增图表类型时在此处同步补充对应 import（如 RadarChart、GaugeChart）。
import { use, init } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([
  BarChart, LineChart, PieChart,
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
  CanvasRenderer
])

const props = defineProps({
  // ECharts option 对象（完整配置）；变化时按 merge=false 重绘，保证数据切换干净
  option: { type: Object, required: true },
  height: { type: String, default: '320px' }
})

const emit = defineEmits(['chart-click'])

const el = ref(null)
let chart = null
let ro = null

/**
 * 将主题兜底色注入 option（仅当父级未显式指定时），保证深色下坐标轴、图例、
 * tooltip 文字可读。ECharts 无法解析 CSS 变量，故此处使用具体色值。
 * 仅做浅拷贝 + 在原嵌套对象上补字段（幂等、不破坏父级传入的函数式 formatter）。
 */
function applyChartThemeDefaults(opt, ct) {
  opt.textStyle = opt.textStyle || {}
  if (opt.textStyle.color == null) opt.textStyle.color = ct.text
  if (!opt.color) opt.color = ct.palette
  if (opt.legend) {
    opt.legend.textStyle = opt.legend.textStyle || {}
    if (opt.legend.textStyle.color == null) opt.legend.textStyle.color = ct.sub
  }
  if (opt.tooltip) {
    opt.tooltip.backgroundColor = opt.tooltip.backgroundColor || ct.tooltipBg
    opt.tooltip.borderColor = opt.tooltip.borderColor || ct.tooltipBorder
    opt.tooltip.textStyle = opt.tooltip.textStyle || {}
    if (opt.tooltip.textStyle.color == null) opt.tooltip.textStyle.color = ct.text
  }
  const axes = []
  if (opt.xAxis) axes.push(...(Array.isArray(opt.xAxis) ? opt.xAxis : [opt.xAxis]))
  if (opt.yAxis) axes.push(...(Array.isArray(opt.yAxis) ? opt.yAxis : [opt.yAxis]))
  axes.forEach((a) => {
    if (!a) return
    a.axisLine = a.axisLine || {}
    if (a.axisLine.lineStyle) {
      if (a.axisLine.lineStyle.color == null) a.axisLine.lineStyle.color = ct.axis
    } else {
      a.axisLine.lineStyle = { color: ct.axis }
    }
    a.axisLabel = a.axisLabel || {}
    if (a.axisLabel.color == null) a.axisLabel.color = ct.sub
    a.splitLine = a.splitLine || {}
    if (a.splitLine.show !== false) {
      if (a.splitLine.lineStyle) {
        if (a.splitLine.lineStyle.color == null) a.splitLine.lineStyle.color = ct.split
      } else {
        a.splitLine.lineStyle = { color: ct.split }
      }
    }
  })
}

function render() {
  if (!chart) return
  const opt = { ...props.option }
  applyChartThemeDefaults(opt, getChartTheme())
  chart.setOption(opt, true)
}

function handleResize() {
  if (chart) chart.resize()
}

onMounted(async () => {
  await nextTick()
  chart = init(el.value)
  render()
  // 点击下钻：把 ECharts 的点击参数（含坐标轴类目 name / 系列值）透传出去，
  // 由父组件判断是否触发下钻（向后兼容：旧调用方不监听 chart-click 时无任何副作用）。
  chart.on('click', (params) => emit('chart-click', params))
  ro = new ResizeObserver(handleResize)
  ro.observe(el.value)
  window.addEventListener('resize', handleResize)
})

watch(() => [props.option, currentTheme.value], render, { deep: true })

onBeforeUnmount(() => {
  if (ro) ro.disconnect()
  window.removeEventListener('resize', handleResize)
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.base-chart {
  width: 100%;
}
</style>
