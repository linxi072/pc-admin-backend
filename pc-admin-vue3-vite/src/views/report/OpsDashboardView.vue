<template>
  <div>
    <h2 class="page-title">{{ t('reportDashboard.title') }}</h2>

    <!-- 概览：错误态（拦截器已统一提示，此处仅提供重试入口） -->
    <el-alert
      v-if="overviewError"
      class="mb-12"
      type="error"
      show-icon
      :closable="false"
    >
      <template #title>
        <span>{{ overviewError }}</span>
        <el-button size="small" text type="primary" class="retry-btn" @click="loadOverview">{{ t('dashboard.retry') }}</el-button>
      </template>
    </el-alert>
    <el-skeleton v-else-if="overviewLoading" :rows="6" animated />

    <template v-else>
      <!-- 运营概览 -->
      <el-card class="page-card section" :header="t('reportDashboard.overview')">
        <div class="stat-grid">
          <div
            class="stat-card"
            v-for="s in systemStats"
            :key="s.key"
            :class="{ 'is-clickable': CARD_DRILL[s.key] }"
            @click="onStatCardClick(s)"
          >
            <div class="label">{{ s.label }}</div>
            <div class="value">{{ fmtCount(s.value) }}</div>
          </div>
        </div>
      </el-card>

      <!-- 可观测指标（跨模块端口接入：N+1 疑似 + 链路追踪） -->
      <el-card class="page-card section" :header="t('reportDashboard.observability')">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <div class="obs-subtitle">{{ t('reportDashboard.n1Suspect') }}</div>
            <div class="stat-grid">
              <div class="stat-card" v-for="s in n1Stats" :key="s.key">
                <div class="label">{{ s.label }}</div>
                <div class="value">{{ s.value }}</div>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :md="12">
            <div class="obs-subtitle">{{ t('reportDashboard.trace') }}</div>
            <div class="stat-grid">
              <div class="stat-card" v-for="s in traceStats" :key="s.key">
                <div class="label">{{ s.label }}</div>
                <div class="value">{{ s.value }}</div>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-card>
    </template>

    <!-- 时间序列趋势 -->
      <el-card class="page-card section" :header="t('reportDashboard.timeseries', { days })">
      <div class="trend-toolbar">
        <span class="filter-label">{{ t('reportDashboard.lookbackDays') }}</span>
        <el-radio-group v-model="days" @change="loadTrend">
          <el-radio-button :value="7">{{ t('reportDashboard.last7Days') }}</el-radio-button>
          <el-radio-button :value="14">{{ t('reportDashboard.last14Days') }}</el-radio-button>
          <el-radio-button :value="30">{{ t('reportDashboard.last30Days') }}</el-radio-button>
        </el-radio-group>
        <span class="trend-hint" v-if="overview">快照生成：{{ formatDateTime(overview.generatedAt) }}</span>
      </div>

      <el-alert
        v-if="trendError"
        class="mb-12"
        type="error"
        show-icon
        :closable="false"
      >
        <template #title>
          <span>{{ trendError }}</span>
          <el-button size="small" text type="primary" class="retry-btn" @click="loadTrend">{{ t('dashboard.retry') }}</el-button>
        </template>
      </el-alert>
      <el-skeleton v-else-if="trendLoading" :rows="8" animated />
      <el-empty v-else-if="!trend" :description="t('reportDashboard.noTrend')" />

      <el-row v-else :gutter="16">
        <el-col :xs="24" :md="8" v-for="m in trendMetrics" :key="m.key">
          <el-card class="chart-card" :header="m.label">
            <BaseChart :option="trendOption(m)" height="280px" @chart-click="(p) => onTrendClick(p, m)" />
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 下钻抽屉：点击趋势线 / 概览统计卡触发，复用通用 DrillDrawer（dashboard 模式） -->
    <DrillDrawer
      v-model="drillVisible"
      source="dashboard"
      :drill-metric="drillMetric"
      :drill-date="drillDate"
      :title="drillTitle"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import BaseChart from '@/components/BaseChart.vue'
import DrillDrawer from '@/components/DrillDrawer.vue'
import { dashboardOverview, dashboardTrend } from '@/api/dashboard'
import { t } from '@/i18n'

// 图表走 Canvas 渲染，无法解析 CSS 变量：运行时读取 :root 的品牌色（失败回落到原值）
const BRAND = (getComputedStyle(document.documentElement).getPropertyValue('--brand') || '').trim() || '#409eff'

const overviewLoading = ref(false)
const overviewError = ref('')
const overview = ref(null)

const trendLoading = ref(false)
const trendError = ref('')
const trend = ref(null)
const days = ref(30)

const systemStats = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { key: 'tenantCount', label: t('reportDashboard.tenantCount'), value: o.tenantCount },
    { key: 'userCount', label: t('reportDashboard.userCount'), value: o.userCount },
    { key: 'roleCount', label: t('reportDashboard.roleCount'), value: o.roleCount },
    { key: 'menuCount', label: t('reportDashboard.menuCount'), value: o.menuCount },
    { key: 'onlineSessionCount', label: t('reportDashboard.onlineSession'), value: o.onlineSessionCount },
    { key: 'idempotencyRecordCount', label: t('reportDashboard.idempotency'), value: o.idempotencyRecordCount },
    { key: 'operationLogCount', label: t('reportDashboard.operationLog'), value: o.operationLogCount },
    { key: 'loginLogTodayCount', label: t('reportDashboard.loginToday'), value: o.loginLogTodayCount }
  ]
})

const n1Stats = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { key: 'total', label: t('reportDashboard.totalBreach'), value: o.nPlusOneSuspectTotal },
    { key: 'retained', label: t('reportDashboard.retained'), value: o.nPlusOneSuspectRetained },
    { key: 'lastBreach', label: t('reportDashboard.lastBreach'), value: o.lastBreachAtMillis ? formatDateTime(o.lastBreachAtMillis) : t('reportDashboard.never') }
  ]
})

const traceStats = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { key: 'enabled', label: t('reportDashboard.recorder'), value: o.traceRecorderEnabled ? t('reportDashboard.enabled') : t('reportDashboard.disabled') },
    { key: 'retained', label: t('reportDashboard.retained'), value: o.traceRetained },
    { key: 'total', label: t('reportDashboard.cumulative'), value: o.traceTotal },
    { key: 'errors', label: t('reportDashboard.errorCount'), value: o.traceErrorTotal }
  ]
})

const trendMetrics = [
  { key: 'newUsers', label: t('dashboard.metricNewUsers'), color: BRAND },
  { key: 'newTenants', label: t('dashboard.metricNewTenants'), color: '#67c23a' },
  { key: 'activeTenants', label: t('dashboard.metricActiveTenants'), color: '#e6a23c' },
  { key: 'loginLogs', label: t('dashboard.metricLoginLogs'), color: '#909399' },
  { key: 'operationLogs', label: t('dashboard.metricOperationLogs'), color: '#f56c6c' },
  { key: 'notifications', label: t('dashboard.metricNotifications'), color: '#9254de' }
]

// 趋势线 / 概览卡 → 下钻指标映射；无对应每日序列的指标不启用下钻
const TREND_DRILL = {
  newUsers: 'NEW_USERS',
  newTenants: 'NEW_TENANTS',
  activeTenants: 'ACTIVE_TENANTS',
  loginLogs: 'LOGIN_LOGS',
  operationLogs: 'OPERATION_LOGS',
  notifications: 'NOTIFICATIONS'
}
const CARD_DRILL = {
  userCount: 'NEW_USERS',
  tenantCount: 'NEW_TENANTS',
  loginLogTodayCount: 'LOGIN_LOGS',
  operationLogCount: 'OPERATION_LOGS'
}
const METRIC_LABEL = {
  NEW_USERS: 'dashboard.metricNewUsers', NEW_TENANTS: 'dashboard.metricNewTenants', ACTIVE_TENANTS: 'dashboard.metricActiveTenants',
  LOGIN_LOGS: 'dashboard.metricLoginLogs', OPERATION_LOGS: 'dashboard.metricOperationLogs', NOTIFICATIONS: 'dashboard.metricNotifications'
}

const drillVisible = ref(false)
const drillMetric = ref('')
const drillDate = ref('')
const drillTitle = ref(t('dashboard.drillTitle'))

function todayISO() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function openDashboardDrill(metric, date, title) {
  drillMetric.value = metric
  drillDate.value = date
  drillTitle.value = title
  drillVisible.value = true
}

/** 趋势线点击：下钻该指标在点击日（p.name 为 ISO 日期）的逐小时分布。 */
function onTrendClick(p, m) {
  const metric = TREND_DRILL[m.key]
  if (!metric) return
  openDashboardDrill(metric, p.name, t('reportDashboard.hourlyTitle', { metric: m.label, date: p.name }))
}

/** 概览统计卡点击：可下钻的指标（累计型）下钻其「今日」逐小时分布。 */
function onStatCardClick(s) {
  const metric = CARD_DRILL[s.key]
  if (!metric) return
  openDashboardDrill(metric, todayISO(), `${t(METRIC_LABEL[metric])} · ${t('dashboard.todayHourly')}`)
}

function trendOption(m) {
  const series = (trend.value && trend.value[m.key]) || []
  const dates = series.map((p) => p.date)
  const values = series.map((p) => p.value)
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 20, top: 24, bottom: 32 },
    xAxis: { type: 'category', boundaryGap: false, data: dates, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value' },
    series: [
      {
        name: m.label,
        type: 'line',
        smooth: true,
        showSymbol: false,
        areaStyle: { opacity: 0.12 },
        itemStyle: { color: m.color },
        data: values
      }
    ]
  }
}

/** 千分位格式化：大数值可读性；非数值原样返回。 */
function fmtCount(v) {
  return typeof v === 'number' ? v.toLocaleString('zh-CN') : (v ?? '—')
}

/** epoch 毫秒 → YYYY-MM-DD HH:mm；空值返回 '—'。 */
function formatDateTime(ts) {
  if (!ts) return '—'
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return '—'
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function loadOverview() {
  overviewLoading.value = true
  overviewError.value = ''
  try {
    overview.value = await dashboardOverview()
  } catch (e) {
    overview.value = null
    overviewError.value = e.message || t('reportDashboard.overviewLoadFailed')
  } finally {
    overviewLoading.value = false
  }
}

async function loadTrend() {
  trendLoading.value = true
  trendError.value = ''
  try {
    trend.value = await dashboardTrend(days.value)
  } catch (e) {
    trend.value = null
    trendError.value = e.message || t('reportDashboard.trendLoadFailed')
  } finally {
    trendLoading.value = false
  }
}

onMounted(() => {
  loadOverview()
  loadTrend()
})
</script>

<style scoped>
.section {
  margin-bottom: 16px;
}
.obs-subtitle {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: var(--text);
}
.trend-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.filter-label {
  color: #8a919f;
  font-size: 13px;
  white-space: nowrap;
}
.trend-hint {
  margin-left: auto;
  color: #a8abb2;
  font-size: 12px;
}
.chart-card {
  margin-bottom: 16px;
}
/* 可下钻的概览统计卡：悬浮高亮 + 指针，提示可点击 */
.stat-card.is-clickable {
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.stat-card.is-clickable:hover {
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.25);
}
/* 错误态重试按钮：嵌入 alert 标题行，紧凑不抢焦点 */
.retry-btn {
  margin-left: 10px;
  padding: 0;
}
.mb-12 {
  margin-bottom: 12px;
}
</style>
