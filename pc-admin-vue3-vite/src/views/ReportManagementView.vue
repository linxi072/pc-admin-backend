<template>
  <div>
    <h2 class="page-title">报表管理</h2>

    <!-- 报表类型 Tab -->
    <el-tabs v-model="type" class="type-tabs" @tab-change="onTypeChange">
      <el-tab-pane v-for="t in types" :key="t.code" :name="t.code" :label="t.label" />
    </el-tabs>

    <!-- 筛选工具栏：上「条件区」+ 下「操作区」两行布局，按钮与条件分区展示，避免挤压堆积 -->
    <el-card class="toolbar" shadow="never">
      <div class="toolbar-filters">
        <div class="filter-group">
          <span class="filter-label">时间范围</span>
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
            class="ml-8"
            @change="onFilterChange"
          />
        </div>

        <!-- 关键字筛选：仅统计视角（任务名 / 用户名 / 请求路径） -->
        <div v-if="isStatView" class="filter-group">
          <span class="filter-label">关键字</span>
          <el-input
            v-model="keyword"
            :placeholder="keywordPlaceholder"
            clearable
            class="keyword-input"
            @keyup.enter="load"
            @clear="load"
          />
        </div>

        <!-- 类别 / 状态 / 排序：仅使用视角 -->
        <template v-else>
          <div class="filter-group">
            <span class="filter-label">类别</span>
            <el-select v-model="module" placeholder="全部类别" clearable class="filter-select" @change="onFilterChange">
              <el-option v-for="m in moduleOptions" :key="m.value" :label="m.label" :value="m.value" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">状态</span>
            <el-select v-model="status" placeholder="全部状态" class="filter-select" @change="onFilterChange">
              <el-option label="全部状态" value="" />
              <el-option label="成功" :value="true" />
              <el-option label="失败" :value="false" />
            </el-select>
          </div>
          <div class="filter-group">
            <span class="filter-label">排序</span>
            <el-select v-model="sortBy" class="filter-select" @change="onFilterChange">
              <el-option label="按次数" value="COUNT" />
              <el-option label="按占比" value="RATIO" />
              <el-option label="按名称" value="NAME" />
            </el-select>
            <el-select v-model="order" class="filter-select" @change="onFilterChange">
              <el-option label="降序" value="DESC" />
              <el-option label="升序" value="ASC" />
            </el-select>
          </div>
        </template>
      </div>

      <div class="toolbar-actions">
        <span class="window-hint">统计窗口：{{ report?.start }} ~ {{ report?.end }}</span>
        <span class="action-buttons">
          <el-button type="primary" :loading="loading" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <el-button type="success" :loading="exporting" @click="download">导出 CSV</el-button>
        </span>
      </div>
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

      <!-- 趋势（使用视角：访问量/活跃用户；统计视角：主指标/次指标/失败数） -->
      <el-card class="mt-16" :header="trendTitle">
        <BaseChart :option="trendOption" height="340px" />
      </el-card>

      <template v-if="isStatView">
        <el-row :gutter="16" class="mt-16">
          <!-- 分布分桶（耗时 / 响应时间 / 登录类型） -->
          <el-col :xs="24" :sm="24" :md="10" :lg="10" :xl="10">
            <el-card :header="distributionTitle">
              <el-empty v-if="!distribution.length" description="该筛选条件下暂无数据" />
              <BaseChart v-else :option="distributionOption" height="420px" />
            </el-card>
          </el-col>
          <!-- 状态分布 -->
          <el-col :xs="24" :sm="24" :md="14" :lg="14" :xl="14">
            <el-card header="状态分布（成功 / 失败）">
              <BaseChart :option="statusOption" height="420px" />
            </el-card>
          </el-col>
        </el-row>

        <!-- 明细表（最近执行 / 异常登录 / Top 接口） -->
        <el-card class="mt-16" :header="detailsTitle">
          <el-empty v-if="!detailsRows.length" description="该筛选条件下暂无数据" />
          <el-table v-else :data="detailsRows" border stripe size="small" max-height="460">
            <el-table-column
              v-for="c in detailsColumns"
              :key="c.key"
              :prop="c.key"
              :label="c.label"
              show-overflow-tooltip
            />
          </el-table>
        </el-card>
      </template>

      <template v-else>
        <el-row :gutter="16" class="mt-16">
          <!-- 类别分布 -->
          <el-col :xs="24" :sm="24" :md="14" :lg="14" :xl="14">
            <el-card header="类别分布（模块使用频次 Top 10）">
              <el-empty v-if="!report?.categoryStats?.length" description="该筛选条件下暂无数据" />
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

// 统计视角元数据：主/次指标、失败口径、是否有耗时维度
const STAT_META = {
  JOB_EXEC: { main: '执行次数', secondary: '涉及任务数', fail: '失败次数', duration: true, dist: '耗时分布', details: '最近执行明细' },
  LOGIN: { main: '登录次数', secondary: '活跃登录用户', fail: '失败次数', duration: false, dist: '登录类型分布', details: '异常登录记录' },
  API_CALL: { main: '调用量', secondary: '涉及端点数', fail: '错误数', duration: true, dist: '响应时间分布', details: 'Top 接口（按调用量）' }
}
const KEYWORD_PLACEHOLDERS = {
  JOB_EXEC: '任务名称关键字',
  LOGIN: '用户名关键字',
  API_CALL: '请求路径关键字'
}

const types = ref([])
const type = ref('USAGE')
const rangeType = ref('LAST_7D')
const dateRange = ref([])
const module = ref('')
const status = ref('')
const sortBy = ref('COUNT')
const order = ref('DESC')
const keyword = ref('')

// 初始 loading=true：首渲染发生在 onMounted 之前，若为 false 会直接进入内容分支，
// 而 report 初始为 null，任何未防御的点访问都会让整页渲染崩溃（白屏）
const loading = ref(true)
const exporting = ref(false)
const error = ref('')
const report = ref(null)

const isStatView = computed(() => Object.prototype.hasOwnProperty.call(STAT_META, type.value))
const statMeta = computed(() => STAT_META[type.value] || null)
const keywordPlaceholder = computed(() => KEYWORD_PLACEHOLDERS[type.value] || '关键字')

const distribution = computed(() => report.value?.distribution || [])
const detailsColumns = computed(() => report.value?.details?.columns || [])
const detailsRows = computed(() => report.value?.details?.rows || [])

const trendTitle = computed(() =>
  isStatView.value
    ? `${statMeta.value.main}趋势（${statMeta.value.main} / ${statMeta.value.secondary} / ${statMeta.value.fail}）`
    : '访问趋势（访问量 / 活跃用户数）'
)
const distributionTitle = computed(() => statMeta.value?.dist || '分布')
const detailsTitle = computed(() => statMeta.value?.details || '明细')

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
  if (!isStatView.value) {
    if (module.value) params.module = module.value
    if (status.value !== '' && status.value !== null) params.success = status.value
  } else if (keyword.value && keyword.value.trim()) {
    params.keyword = keyword.value.trim()
  }
  return params
}

function fmtDuration(ms) {
  if (!ms) return '0'
  return ms >= 1000 ? (ms / 1000).toFixed(2) + ' s' : ms + ' ms'
}

const summaries = computed(() => {
  const s = report.value?.summary
  if (!s) return []
  if (isStatView.value) {
    const meta = statMeta.value
    const list = [
      { key: 'visits', label: meta.main, value: s.totalVisits, unit: '次' },
      { key: 'active', label: meta.secondary, value: s.activeUsers, unit: '人' },
      { key: 'success', label: '成功率', value: s.successRate, unit: '%' },
      { key: 'failed', label: meta.fail, value: s.failedVisits, unit: '次' }
    ]
    if (meta.duration) {
      list.push(
        { key: 'avg', label: '平均耗时', value: fmtDuration(s.avgDurationMs), unit: '' },
        { key: 'max', label: '最大耗时', value: fmtDuration(s.maxDurationMs), unit: '' },
        { key: 'p95', label: 'P95 耗时', value: fmtDuration(s.p95DurationMs), unit: '' }
      )
    }
    return list
  }
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
  const grid = { left: 48, right: 24, top: 40, bottom: 32 }
  if (!isStatView.value) {
    return {
      tooltip: { trigger: 'axis' },
      legend: { data: ['访问量', '活跃用户数'] },
      grid,
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
  }
  const meta = statMeta.value
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: [meta.main, meta.secondary, meta.fail] },
    grid,
    xAxis: { type: 'category', boundaryGap: false, data: trend.map((p) => p.bucket) },
    yAxis: { type: 'value' },
    series: [
      {
        name: meta.main, type: 'line', smooth: true, showSymbol: false,
        areaStyle: { opacity: 0.12 }, itemStyle: { color: '#409eff' },
        data: trend.map((p) => p.visits)
      },
      {
        name: meta.secondary, type: 'line', smooth: true, showSymbol: false,
        areaStyle: { opacity: 0.12 }, itemStyle: { color: '#67c23a' },
        data: trend.map((p) => p.activeUsers)
      },
      {
        name: meta.fail, type: 'line', smooth: true, showSymbol: false,
        areaStyle: { opacity: 0.08 }, itemStyle: { color: '#f56c6c' },
        data: trend.map((p) => p.failed)
      }
    ]
  }
})

const distributionOption = computed(() => {
  const dist = distribution.value
  const meta = dist.slice().reverse()
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const d = meta[params[0].dataIndex]
        return `${d.label}<br/>数量：${d.count}<br/>占比：${d.ratio}%`
      }
    },
    grid: { left: 96, right: 48, top: 16, bottom: 24 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: dist.map((d) => d.label).reverse() },
    series: [
      {
        type: 'bar', data: dist.map((d) => d.count).reverse(), barWidth: '55%',
        itemStyle: { color: '#e6a23c', borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right' }
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
function onTypeChange() {
  // 切换视角时清空关键字，避免跨类型残留造成空结果困惑
  keyword.value = ''
  load()
}
function reset() {
  type.value = types.value[0]?.code || 'USAGE'
  rangeType.value = 'LAST_7D'
  dateRange.value = []
  module.value = ''
  status.value = ''
  sortBy.value = 'COUNT'
  order.value = 'DESC'
  keyword.value = ''
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
.type-tabs { margin-bottom: 12px; }
/* 工具栏分区类（toolbar-filters / filter-group / filter-label / toolbar-actions / action-buttons）
   已沉淀至全局 styles.css，此处仅保留页面特有规则 */
.toolbar :deep(.el-card__body) { padding: 14px 18px; }
.filter-select { width: 140px; }
.keyword-input { width: 220px; }
.window-hint { color: #8a919f; font-size: 13px; }
.ml-8 { margin-left: 8px; }
.ml-12 { margin-left: 12px; }
.mt-12 { margin-top: 12px; }
.mt-16 { margin-top: 16px; }
.summary-row { margin-top: 16px; }
.summary-card { margin-bottom: 16px; }
.summary-label { color: #8a919f; font-size: 13px; }
.summary-value { margin-top: 8px; font-size: 26px; font-weight: 600; color: #303133; }
.summary-unit { font-size: 13px; font-weight: 400; color: #8a919f; margin-left: 4px; }
</style>
