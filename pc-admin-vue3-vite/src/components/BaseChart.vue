<template>
  <div ref="el" class="base-chart" :style="{ height }"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
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

function render() {
  if (chart) chart.setOption(props.option, true)
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

watch(() => props.option, render, { deep: true })

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
