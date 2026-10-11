<template>
  <div>
    <h2 class="page-title">报表管理</h2>

    <!-- 报表类型 Tab -->
    <el-tabs v-model="type" class="type-tabs" @tab-change="onTypeChange">
      <el-tab-pane v-for="t in types" :key="t.code" :name="t.code" :label="t.label" />
    </el-tabs>

    <!-- 筛选工具栏：时间范围 + 类别 + 状态 + 排序 -->
    <el-card class="toolbar" shadow="never">
      <el-radio-group v-model="rangeType" @change="onFilterChange">
        <el-radio-button value="TODAY">今日</el-radio-button>
        <el-radio-button value="LAST_7D">近 7 天</el-radio-button>
        <el-radio-button value="LAST_30D">近 30 天</el-radio-button>
        <el-radio-button value="CUSTOM">自定义</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-if="rangeType === 'CUSTOM'"
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :disabled-date="disableFuture"
        class="ml-12"
        @change="onFilterChange"
      />
      <el-select v-model="module" placeholder="全部类别" clearable class="ml-12 filter-item" @change="onFilterChange">
        <el-option v-for="m in moduleOptions" :key="m.value" :label="m.label" :value="m.value" />
      </el-select>
      <el-select v-model="status" placeholder="全部状态" class="ml-12 filter-item" @change="onFilterChange">
        <el-option label="全部状态" value="" />
        <el-option label="成功" :value="true" />
        <el-option label="失败" :value="false" />
      </el-select>
      <el-select v-model="sortBy" class="ml-12 filter-item" @change="onFilterChange">
        <el-option label="按次数" value="COUNT" />
        <el-option label="按占比" value="RATIO" />
        <el-option label="按名称" value="NAME" />
      </el-select>
      <el-select v-model="order" class="ml-12 filter-item" @change="onFilterChange">
        <el-option label="降序" value="DESC" />
        <el-option label="升序" value="ASC" />
      </el-select>

      <span class="toolbar-actions">
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-button type="success" :loading="exporting" @click="download">导出 CSV</el-button>
      </span>
      <span class="window-hint">统计窗口：{{ report?.start }} ~ {{ report?.end }}</span>
    </el-card>

    <el-alert v-if="error" class="mt-12" type="error" show-icon :closable="false" :title="error" />
    <el-skeleton v-else-if="loading" :rows="8" animated class="mt-12" />

    <template v-else>
      <!-- 汇总指标卡片 -->
      <el-row :gutter="16" class="summary-row">
        <el-col :xs="12" :sm="12" :md="8" :lg="8" :xl="8" v-for="s in summaries" :key="s.key">
          <el-card shadow="hover" class="summary-card">
            <div class="summary-label">{{ s.label }}</div>
            <div class="summary-value">
              {{ s.value }}<span class="summary-unit">{{ s.unit }}</span>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 访问趋势 -->
      <el-card class="mt-16" header="访问趋势（访问量 / 活跃用户数）">
        <BaseChart :option="trendOption" height="340px" />
      </el-card>

      <el-row :gutter="16" class="mt-16">
        <!-- 类别分布 -->
        <el-col :xs="24" :sm="24" :md="14" :lg="14" :xl="14">
          <el-card header="类别分布（模块使用频次 Top 10）">
            <el-empty v-if="!report.categoryStats.length" description="该筛选条件下暂无数据" />
            <BaseChart v-else :option="categoryOption" height="420px" />
          </el-card>
        </el-col>
        <!-- 状态分布 -->
        <el-col :xs="24" :sm="24" :md="10" :lg="10" :xl="10">
          <el-card header="状态分布（成功 / 失败）">
            <BaseChart :option="statusOption" height="420px" />
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import BaseChart from '@/components/BaseChart.vue'
import { reportTypes, reportData, reportExport } from '@/api/report'

// 模块编码 → 中文名（仅展示用，缺失时回退原始编码；非业务映射，可随菜单扩展）
const MODULE_LABELS = {
  user: '用户管理', role: '角色管理', menu: '菜单管理', 'api-resource': '接口资源',
  department: '部门管理', dict: '字典管理', config: '系统变量', announcement: '消息中心',
  monitor: '系统监控', workflow: '工作流', org: '组织机构', message: '站内信',
  profile: '个人中心', auth: '认证授权', audit: '审计日志', system: '系统', unknown: '未知'
}
const labelOf = (code) => MODULE_LABELS[code] || code || '未知'
const moduleOptions = Object.keys(MODULE_LABELS).map((k) => ({ value: k, label: MODULE_LABELS[k] }))

const types = ref([])
const type = ref('USAGE')
const rangeType = ref('LAST_7D')
const dateRange = ref([])
const module = ref('')
const status = ref('')
const sortBy = ref('COUNT')
const order = ref('DESC')

const loading = ref(false)
const exporting = ref(false)
const error = ref('')
const report = ref(null)

function disableFuture(date) {
  return date.getTime() > Date.now()
}

function buildParams() {
  const params = { type: type.value, rangeType: rangeType.value, sortBy: sortBy.value, order: order.value }
  if (rangeType.value === 'CUSTOM') {
    if (dateRange.value && dateRange.value.length === 2) {
      params.start = dateRange.value[0]
      params.end = dateRange.value[1]
    }
  }
  if (module.value) params.module = module.value
  if (status.value !== '' && status.value !== null) params.success = status.value
  return params
}

function fmtDuration(ms) {
  if (!ms) return '0'
  return ms >= 1000 ? (ms / 1000).toFixed(2) + ' s' : ms + ' ms'
}

const summaries = computed(() => {
  const s = report.value?.summary
  if (!s) return []
  return [
    { key: 'visits', label: '访问量', value: s.totalVisits, unit: '次' },
    { key: 'active', label: '活跃用户数', value: s.activeUsers, unit: '人' },
    { key: 'success', label: '成功率', value: s.successRate, unit: '%' },
    { key: 'module', label: '涉及模块数', value: s.moduleCount, unit: '个' },
    { key: 'avg', label: '平均耗时', value: fmtDuration(s.avgDurationMs), unit: '' }
  ]
})

const trendOption = computed(() => {
  const trend = report.value?.trend || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['访问量', '活跃用户数'] },
    grid: { left: 48, right: 24, top: 40, bottom: 32 },
    xAxis: { type: 'category', boundaryGap: false, data: trend.map((p) => p.bucket) },
    yAxis: { type: 'value' },
    series: [
      {
        name: '访问量', type: 'line', smooth: true, showSymbol: false,
        areaStyle: { opacity: 0.12 }, itemStyle: { color: '#409eff' },
        data: trend.map((p) => p.visits)
      },
      {
        name: '活跃用户数', type: 'line', smooth: true, showSymbol: false,
        areaStyle: { opacity: 0.12 }, itemStyle: { color: '#67c23a' },
        data: trend.map((p) => p.activeUsers)
      }
    ]
  }
})

const categoryOption = computed(() => {
  const stats = report.value?.categoryStats || []
  const labels = stats.map((f) => labelOf(f.moduleCode)).reverse()
  const counts = stats.map((f) => f.count).reverse()
  const meta = stats.slice().reverse()
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const f = meta[params[0].dataIndex]
        return `${labelOf(f.moduleCode)}<br/>次数：${f.count}<br/>占比：${f.ratio}%`
      }
    },
    grid: { left: 96, right: 48, top: 16, bottom: 24 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: labels },
    series: [
      {
        type: 'bar', data: counts, barWidth: '55%',
        itemStyle: { color: '#909399', borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right' }
      }
    ]
  }
})

const statusOption = computed(() => {
  const stats = report.value?.statusStats || []
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['40%', '65%'], center: ['50%', '45%'],
      data: stats.map((s) => ({ name: s.statusLabel, value: s.count })),
      itemStyle: { color: (p) => (p.name === '成功' ? '#67c23a' : '#f56c6c') },
      label: { formatter: '{b}: {c}' }
    }]
  }
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = buildParams()
    if (rangeType.value === 'CUSTOM' && (!params.start || !params.end)) {
      error.value = '请选择自定义时间范围的开始与结束日期'
      loading.value = false
      return
    }
    report.value = await reportData(params)
  } catch (e) {
    report.value = null
    error.value = e.message || '报表加载失败'
  } finally {
    loading.value = false
  }
}

async function download() {
  exporting.value = true
  try {
    const params = buildParams()
    const resp = await reportExport(params)
    const blob = resp.data
    const cd = resp.headers?.['content-disposition'] || resp.headers?.contentDisposition || ''
    let filename = '报表管理.csv'
    const m = /filename="?([^"]+)"?/.exec(cd)
    if (m) filename = decodeURIComponent(m[1])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } catch (e) {
    error.value = e.message || '导出失败'
  } finally {
    exporting.value = false
  }
}

function onFilterChange() { load() }
function onTypeChange() { load() }
function reset() {
  type.value = types.value[0]?.code || 'USAGE'
  rangeType.value = 'LAST_7D'
  dateRange.value = []
  module.value = ''
  status.value = ''
  sortBy.value = 'COUNT'
  order.value = 'DESC'
  load()
}

onMounted(async () => {
  try {
    types.value = await reportTypes()
    if (types.value.length) type.value = types.value[0].code
  } catch (e) {
    // 类型列表为可选增强，失败不影响默认 USAGE 渲染
    types.value = [{ code: 'USAGE', label: '使用统计', description: '' }]
  }
  load()
})
</script>

<style scoped>
.page-title { margin: 0 0 12px; font-size: 20px; font-weight: 600; }
.type-tabs { margin-bottom: 4px; }
.toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.filter-item { width: 140px; }
.toolbar-actions { margin-left: auto; display: flex; gap: 8px; }
.window-hint { margin-left: 8px; color: #8a919f; font-size: 13px; }
.ml-12 { margin-left: 12px; }
.mt-12 { margin-top: 12px; }
.mt-16 { margin-top: 16px; }
.summary-row { margin-top: 16px; }
.summary-card { margin-bottom: 16px; }
.summary-label { color: #8a919f; font-size: 13px; }
.summary-value { margin-top: 8px; font-size: 26px; font-weight: 600; color: #303133; }
.summary-unit { font-size: 13px; font-weight: 400; color: #8a919f; margin-left: 4px; }
</style>
