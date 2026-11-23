<template>
  <div ref="el" class="base-chart" :style="{ height }"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { getChartTheme, currentTheme } from '../utils/theme'
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
  option: { type: Object, required: true },
  height: { type: String, default: '320px' }
})

const emit = defineEmits(['chart-click'])

const el = ref(null)
let chart = null
let ro = null

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
.base-chart { width: 100%; }
</style>
