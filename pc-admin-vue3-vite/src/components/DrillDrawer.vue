<template>
    <el-drawer
    v-model="visible"
    :title="title"
    size="560px"
    @open="onOpen"
  >
    <div class="drill-body">
      <div v-if="loading" class="drill-loading">{{ t('drill.loading') }}</div>
      <el-alert v-else-if="error" type="error" :title="error" :closable="false" />
      <template v-else>
        <BaseChart v-if="series.length" :option="chartOption" height="320px" />
        <el-empty v-else :description="t('drill.empty')" />

        <el-card v-if="details" class="mt-16" :header="detailTitle">
          <el-table :data="details.rows" border stripe size="small" max-height="360">
            <el-table-column
              v-for="c in details.columns"
              :key="c.key"
              :prop="c.key"
              :label="c.label"
              show-overflow-tooltip
            />
          </el-table>
        </el-card>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import BaseChart from '@/components/BaseChart.vue'
import { reportDrill } from '@/api/report'
import { dashboardDrill } from '@/api/dashboard'
import { t } from '@/i18n'

/**
 * 通用下钻抽屉：根据 source 调用对应下钻端点，渲染逐小时/按天下钻序列与可选明细表。
 *
 * <p>两种模式：
 * <ul>
 *   <li>{@code source='report'}：传入 {@link reportParams}（含 type/rangeType/.../dimension/parent），
 *       调用 {@code /api/report/drill}，序列为按天或逐小时趋势，DURATION 维度附明细表；</li>
 *   <li>{@code source='dashboard'}：传入 {@link drillMetric}/{@link drillDate}，
 *       调用 {@code /api/system/dashboard/drill}，序列为指定日期 0–23 点柱状分布。</li>
 * </ul>
 * 父组件只需在图表 click 事件中设置对应 props 并打开抽屉，无需关心取数与归一逻辑。</p>
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  source: { type: String, default: 'report' }, // 'report' | 'dashboard'
  title: { type: String, default: () => t('drill.title') },
  // report 模式参数（原样透传至 /api/report/drill）
  reportParams: { type: Object, default: null },
  // dashboard 模式参数
  drillMetric: { type: String, default: '' },
  drillDate: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// 图表走 Canvas 渲染，无法解析 CSS 变量：运行时读取 :root 品牌色（失败回落原值）
const BRAND = (getComputedStyle(document.documentElement).getPropertyValue('--brand') || '').trim() || '#409eff'

const loading = ref(false)
const error = ref('')
const series = ref([]) // [{ label, value }]
const details = ref(null)
const detailTitle = ref(t('drill.detail'))

const chartOption = computed(() => {
  const isDashboard = props.source === 'dashboard'
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 20, top: 24, bottom: 32 },
    xAxis: { type: 'category', data: series.value.map((p) => p.label), axisLabel: { fontSize: 10, rotate: isDashboard ? 0 : 0 } },
    yAxis: { type: 'value' },
    series: [
      {
        type: isDashboard ? 'bar' : 'line',
        smooth: true,
        showSymbol: false,
        areaStyle: isDashboard ? undefined : { opacity: 0.12 },
        itemStyle: { color: BRAND },
        barWidth: isDashboard ? '55%' : undefined,
        data: series.value.map((p) => p.value)
      }
    ]
  }
})

async function onOpen() {
  loading.value = true
  error.value = ''
  series.value = []
  details.value = null
  try {
    if (props.source === 'dashboard') {
      const vo = await dashboardDrill(props.drillMetric, props.drillDate)
      series.value = (vo.series || []).map((p) => ({
        label: String(p.hour).padStart(2, '0') + ':00',
        value: p.value
      }))
    } else {
      const vo = await reportDrill(props.reportParams || {})
      series.value = (vo.series || []).map((p) => ({ label: p.bucket, value: p.visits }))
      if (vo.details && vo.details.columns && vo.details.columns.length) {
        details.value = vo.details
      }
    }
  } catch (e) {
    error.value = e.message || t('drill.error')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.drill-body {
  min-height: 200px;
}
.drill-loading {
  text-align: center;
  color: #8a919f;
  padding: 40px 0;
}
.mt-16 {
  margin-top: 16px;
}
</style>
