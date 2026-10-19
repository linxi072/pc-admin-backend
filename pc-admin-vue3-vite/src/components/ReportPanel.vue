<template>
  <div>
    <h2 class="page-title">{{ title }}</h2>

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

        <!-- 关键字筛选：统计视角（任务名 / 用户名 / 请求路径） -->
        <div class="filter-group">
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

    <!-- 错误态：alert + 重试入口（重试与「查询」按钮等价，复用当前筛选条件） -->
    <div v-if="error" class="mt-12 error-row">
      <el-alert type="error" show-icon :closable="false" :title="error" class="error-alert" />
      <el-button :loading="loading" @click="load">重试</el-button>
    </div>
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

      <template>
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
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import BaseChart from '@/components/BaseChart.vue'
import { parseFileName } from '@/utils/download'
import { reportData, reportExport } from '@/api/report'

/**
 * 统计视角报表面板：承载单个统计报表的完整交互（筛选 / 汇总 / 趋势 / 分布 / 明细 / 导出）。
 *
 * <p>演进：原「报表管理」单页多 Tab（V24 拆分为独立菜单页）→ V30 移除 3 个公共统计页
 * （使用 / 类别 / 状态统计）后，本组件仅服务 3 个统计视角（JOB_EXEC / LOGIN / API_CALL），
 * 使用视角的筛选项（类别 / 状态 / 排序）与图表分支已随之删除；如后续恢复公共统计页，
 * 使用视角逻辑可从 git 历史（V30 提交前）取回，或按需重写。</p>
 *
 * <p>扩展方式：新增统计视角只需在 {@link STAT_META} 追加一条元数据（主/次指标、失败口径、
 * 是否有耗时维度、分布与明细标题），其余渲染与交互自动适配。</p>
 */
const props = defineProps({
  // 报表类型：仅统计视角 JOB_EXEC / LOGIN / API_CALL（V30 起）
  type: { type: String, required: true },
  // 页面标题（与菜单名一致）
  title: { type: String, required: true }
})

// 报表类型为页面固有属性（菜单导航取代页签切换），包一层 computed 供下方沿用 type.value 写法
const type = computed(() => props.type)

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

// 图表走 Canvas 渲染，无法解析 CSS 变量：运行时读取 :root 的品牌色（失败回落到原值）
const BRAND = (getComputedStyle(document.documentElement).getPropertyValue('--brand') || '').trim() || '#409eff'

const rangeType = ref('LAST_7D')
const dateRange = ref([])
const keyword = ref('')

// 初始 loading=true：首渲染发生在 onMounted 之前，若为 false 会直接进入内容分支，
// 而 report 初始为 null，任何未防御的点访问都会让整页渲染崩溃（白屏）
const loading = ref(true)
const exporting = ref(false)
const error = ref('')
const report = ref(null)

const statMeta = computed(() => STAT_META[type.value] || null)
const keywordPlaceholder = computed(() => KEYWORD_PLACEHOLDERS[type.value] || '关键字')

const distribution = computed(() => report.value?.distribution || [])
const detailsColumns = computed(() => report.value?.details?.columns || [])
const detailsRows = computed(() => report.value?.details?.rows || [])

const trendTitle = computed(() => {
  const meta = statMeta.value
  return meta
    ? `${meta.main}趋势（${meta.main} / ${meta.secondary} / ${meta.fail}）`
    : '趋势'
})
const distributionTitle = computed(() => statMeta.value?.dist || '分布')
const detailsTitle = computed(() => statMeta.value?.details || '明细')

function disableFuture(date) {
  return date.getTime() > Date.now()
}

function buildParams() {
  const params = { type: type.value, rangeType: rangeType.value }
  if (rangeType.value === 'CUSTOM') {
    if (dateRange.value && dateRange.value.length === 2) {
      params.start = dateRange.value[0]
      params.end = dateRange.value[1]
    }
  }
  if (keyword.value && keyword.value.trim()) {
    params.keyword = keyword.value.trim()
  }
  return params
}

function fmtDuration(ms) {
  if (ms == null) return '--'
  if (!ms) return '0'
  return ms >= 1000 ? (ms / 1000).toFixed(2) + ' s' : ms + ' ms'
}

const summaries = computed(() => {
  const s = report.value?.summary
  const meta = statMeta.value
  if (!s || !meta) return []
  const list = [
    { key: 'visits', label: meta.main, value: s.totalVisits ?? '--', unit: '次' },
    { key: 'active', label: meta.secondary, value: s.activeUsers ?? '--', unit: '人' },
    { key: 'success', label: '成功率', value: s.successRate ?? '--', unit: '%' },
    { key: 'failed', label: meta.fail, value: s.failedVisits ?? '--', unit: '次' }
  ]
  if (meta.duration) {
    list.push(
      { key: 'avg', label: '平均耗时', value: fmtDuration(s.avgDurationMs), unit: '' },
      { key: 'max', label: '最大耗时', value: fmtDuration(s.maxDurationMs), unit: '' },
      { key: 'p95', label: 'P95 耗时', value: fmtDuration(s.p95DurationMs), unit: '' }
    )
  }
  return list
})

const trendOption = computed(() => {
  const trend = report.value?.trend || []
  const meta = statMeta.value
  const grid = { left: 48, right: 24, top: 40, bottom: 32 }
  if (!meta) return { grid, series: [] }
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: [meta.main, meta.secondary, meta.fail] },
    grid,
    xAxis: { type: 'category', boundaryGap: false, data: trend.map((p) => p.bucket) },
    yAxis: { type: 'value' },
    series: [
      {
        name: meta.main, type: 'line', smooth: true, showSymbol: false,
        areaStyle: { opacity: 0.12 }, itemStyle: { color: BRAND },
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
  // 类型白名单：防止路由/菜单误配传入不支持的报表类型时静默展示空页
  if (!statMeta.value) {
    loading.value = false
    error.value = `不支持的报表类型：${type.value}（仅支持 JOB_EXEC / LOGIN / API_CALL）`
    return
  }
  loading.value = true
  error.value = ''
  try {
    const params = buildParams()
    if (rangeType.value === 'CUSTOM') {
      if (!params.start || !params.end) {
        error.value = '请选择自定义时间范围的开始与结束日期'
        return
      }
      // 前端预校验起止关系：后端 ReportQuery 会 fail-fast，此处提前给出可读提示，少一次无效请求
      if (params.start > params.end) {
        error.value = '自定义时间范围的开始日期不能晚于结束日期'
        return
      }
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
    // requestRaw 不拆包：业务错误时 content-type 为 application/json，需转文本提示。
    // 正常导出才是 CSV Blob——非 Blob 一律显式包装，杜绝 createObjectURL 类型不匹配。
    const raw = resp?.data
    if (raw == null) {
      error.value = '导出失败：服务端未返回文件内容'
      return
    }
    const blob = raw instanceof Blob ? raw : new Blob([raw])
    if (!blob.size) {
      error.value = '导出内容为空'
      return
    }
    if (blob.type && blob.type.includes('application/json')) {
      const text = await blob.text()
      let msg = '导出失败'
      try {
        msg = JSON.parse(text).message || msg
      } catch (e) { /* 非 JSON 错误体，保留通用文案 */ }
      error.value = msg
      return
    }
    // 复用 utils/download 的 parseFileName（支持 RFC 5987 filename*，中文报表名不回退硬编码名）
    const filename = parseFileName(resp, '报表管理', 'csv')
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出：${filename}`)
  } catch (e) {
    error.value = e.message || '导出失败'
  } finally {
    exporting.value = false
  }
}

function reset() {
  rangeType.value = 'LAST_7D'
  dateRange.value = []
  keyword.value = ''
  load()
}

onMounted(load)
</script>

<style scoped>
/* .page-title 已统一到全局 styles.css */
/* 工具栏分区类（toolbar-filters / filter-group / filter-label / toolbar-actions / action-buttons）
   已沉淀至全局 styles.css，此处仅保留页面特有规则 */
.toolbar :deep(.el-card__body) { padding: 14px 18px; }
.keyword-input { width: 220px; }
.window-hint { color: #8a919f; font-size: 13px; }
.error-row { display: flex; align-items: center; gap: 12px; }
.error-alert { flex: 1; }
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
