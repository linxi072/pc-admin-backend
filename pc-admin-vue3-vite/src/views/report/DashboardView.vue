<template>
  <div>
    <h2 class="page-title">报表大屏</h2>

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
        <el-button size="small" text type="primary" class="retry-btn" @click="loadOverview">重试</el-button>
      </template>
    </el-alert>
    <el-skeleton v-else-if="overviewLoading" :rows="6" animated />

    <template v-else>
      <!-- 运营概览 -->
      <el-card class="page-card section" header="运营概览">
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
      <el-card class="page-card section" header="可观测指标">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <div class="obs-subtitle">N+1 查询疑似</div>
            <div class="stat-grid">
              <div class="stat-card" v-for="s in n1Stats" :key="s.key">
                <div class="label">{{ s.label }}</div>
                <div class="value">{{ s.value }}</div>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :md="12">
            <div class="obs-subtitle">链路追踪</div>
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
    <el-card class="page-card section" :header="`时间序列趋势（近 ${days} 日）`">
      <div class="trend-toolbar">
        <span class="filter-label">回溯天数</span>
        <el-radio-group v-model="days" @change="loadTrend">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="14">近 14 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
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
          <el-button size="small" text type="primary" class="retry-btn" @click="loadTrend">重试</el-button>
        </template>
      </el-alert>
      <el-skeleton v-else-if="trendLoading" :rows="8" animated />
      <el-empty v-else-if="!trend" description="暂无趋势数据" />

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
    { key: 'tenantCount', label: '租户总数', value: o.tenantCount },
    { key: 'userCount', label: '用户总数', value: o.userCount },
    { key: 'roleCount', label: '角色总数', value: o.roleCount },
    { key: 'menuCount', label: '菜单总数', value: o.menuCount },
    { key: 'onlineSessionCount', label: '在线会话', value: o.onlineSessionCount },
    { key: 'idempotencyRecordCount', label: '幂等记录', value: o.idempotencyRecordCount },
    { key: 'operationLogCount', label: '操作日志', value: o.operationLogCount },
    { key: 'loginLogTodayCount', label: '今日登录', value: o.loginLogTodayCount }
  ]
})

const n1Stats = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { key: 'total', label: '累计越限', value: o.nPlusOneSuspectTotal },
    { key: 'retained', label: '当前留存', value: o.nPlusOneSuspectRetained },
    { key: 'lastBreach', label: '最近越限', value: o.lastBreachAtMillis ? formatDateTime(o.lastBreachAtMillis) : '从未' }
  ]
})

const traceStats = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { key: 'enabled', label: '记录器', value: o.traceRecorderEnabled ? '已启用' : '未启用' },
    { key: 'retained', label: '当前留存', value: o.traceRetained },
    { key: 'total', label: '累计记录', value: o.traceTotal },
    { key: 'errors', label: '错误数', value: o.traceErrorTotal }
  ]
})

const trendMetrics = [
  { key: 'newUsers', label: '新增用户', color: BRAND },
  { key: 'newTenants', label: '新增租户', color: '#67c23a' },
  { key: 'activeTenants', label: '活跃租户', color: '#e6a23c' },
  { key: 'loginLogs', label: '登录日志', color: '#909399' },
  { key: 'operationLogs', label: '操作日志', color: '#f56c6c' },
  { key: 'notifications', label: '通知发送', color: '#9254de' }
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
  NEW_USERS: '新增用户', NEW_TENANTS: '新增租户', ACTIVE_TENANTS: '活跃租户',
  LOGIN_LOGS: '登录日志', OPERATION_LOGS: '操作日志', NOTIFICATIONS: '通知发送'
}

const drillVisible = ref(false)
const drillMetric = ref('')
const drillDate = ref('')
const drillTitle = ref('下钻详情')

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
  openDashboardDrill(metric, p.name, `${m.label} · ${p.name} 逐小时分布`)
}

/** 概览统计卡点击：可下钻的指标（累计型）下钻其「今日」逐小时分布。 */
function onStatCardClick(s) {
  const metric = CARD_DRILL[s.key]
  if (!metric) return
  openDashboardDrill(metric, todayISO(), `${METRIC_LABEL[metric]} · 今日逐小时分布`)
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
    overviewError.value = e.message || '运营总览加载失败'
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
    trendError.value = e.message || '趋势数据加载失败'
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
  color: #303133;
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
