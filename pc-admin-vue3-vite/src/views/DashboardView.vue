<template>
  <div>
    <h2 class="page-title">工作台</h2>

    <!-- 统计卡片：数据全部来自 GET /api/workbench/stats，前端不硬编码 -->
    <div v-if="loading" class="stat-grid">
      <div class="stat-card" v-for="n in 5" :key="n">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="text" style="width: 45%" />
            <el-skeleton-item variant="h1" style="width: 60%; margin-top: 12px" />
          </template>
        </el-skeleton>
      </div>
    </div>

    <el-alert
      v-else-if="error"
      class="stat-alert"
      type="error"
      show-icon
      :closable="false"
      :title="error"
    />

    <el-empty v-else-if="!cards.length" description="暂无统计数据" />

    <div v-else class="stat-grid">
      <div class="stat-card" v-for="c in cards" :key="c.key">
        <el-icon class="stat-icon" :class="trendClass(c.trend)"><component :is="iconFor(c.key)" /></el-icon>
        <div class="label">{{ c.label }}</div>
        <div class="value">
          {{ c.value }}<span class="unit">{{ c.unit }}</span>
        </div>
        <div class="trend" :class="trendClass(c.trend)">
          <span>{{ trendText(c) }}</span>
          <span v-if="c.trend !== 'FLAT'" class="prev">
            较昨日 {{ c.previousValue }}{{ c.unit }}
          </span>
        </div>
      </div>
    </div>

    <!-- 下半区：最近动态 + 快捷入口/系统信息 -->
    <el-row :gutter="16" class="lower-row">
      <el-col :xs="24" :sm="24" :md="16" :lg="16" :xl="16">
        <el-card class="page-card" header="最近动态">
          <el-skeleton v-if="recentLoading" :rows="4" animated />
          <el-alert v-else-if="recentError" type="error" show-icon :closable="false" :title="recentError" />
          <el-empty v-else-if="!recent.length" description="暂无操作记录" :image-size="60" />
          <ul v-else class="recent-list">
            <li v-for="(r, i) in recent" :key="i" class="recent-item">
              <el-icon class="recent-dot" :class="r.success ? 'is-ok' : 'is-fail'">
                <CircleCheck v-if="r.success" /><CircleClose v-else />
              </el-icon>
              <span class="recent-name">{{ r.operationName || '操作' }}</span>
              <el-tag size="small" type="info" effect="plain">{{ r.moduleCode || '—' }}</el-tag>
              <span class="recent-time">{{ fmtTime(r.occurredAt) }}</span>
            </li>
          </ul>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="8" :lg="8" :xl="8">
        <el-card class="page-card" header="快捷入口">
          <div class="quick">
            <el-button type="primary" @click="go('user')">用户管理</el-button>
            <el-button type="primary" @click="go('role')">角色管理</el-button>
            <el-button type="primary" @click="go('task')">我的待办</el-button>
            <el-button type="primary" @click="go('instance')">我发起的流程</el-button>
            <el-button type="success" @click="go('jobList')">
              <el-icon class="btn-icon"><Timer /></el-icon>定时任务
            </el-button>
          </div>
        </el-card>
        <el-card class="page-card" header="系统信息">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="前端框架">Vue 3 + Element Plus + Vite</el-descriptions-item>
            <el-descriptions-item label="后端服务">java-admin-framework（jOOQ / JobRunr / Flowable）</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { CircleCheck, CircleClose, DataAnalysis, Timer } from '@element-plus/icons-vue'
import { workbenchStats, recentActivities } from '@/api/workbench'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const cards = ref([])

const recentLoading = ref(false)
const recentError = ref('')
const recent = ref([])

// 统计卡片图标（Element Plus 图标全局注册，按 key 映射）
const ICONS = {
  NEW_USER_TODAY: 'User',
  NEW_INSTANCE_TODAY: 'Document',
  MY_TODO_TASKS: 'Tickets',
  RUNNING_INSTANCE: 'Switch',
  ACTIVE_API: 'Connection'
}
function iconFor(key) {
  return ICONS[key] || 'Odometer'
}

async function loadStats() {
  loading.value = true
  error.value = ''
  try {
    const res = await workbenchStats()
    cards.value = Array.isArray(res?.cards) ? res.cards : []
  } catch (e) {
    cards.value = []
    error.value = e.message || '统计数据加载失败'
  } finally {
    loading.value = false
  }
}

async function loadRecent() {
  recentLoading.value = true
  recentError.value = ''
  try {
    const list = await recentActivities(8)
    recent.value = Array.isArray(list) ? list : []
  } catch (e) {
    recent.value = []
    recentError.value = e.message || '动态加载失败'
  } finally {
    recentLoading.value = false
  }
}

function trendClass(trend) {
  if (trend === 'UP') return 'is-up'
  if (trend === 'DOWN') return 'is-down'
  return 'is-flat'
}

function trendText(c) {
  if (c.trend === 'FLAT') return '持平'
  return `${c.trend === 'UP' ? '↑' : '↓'} ${Math.abs(c.changeRate)}%`
}

function fmtTime(iso) {
  if (!iso) return ''
  // 形如 2026-10-15T14:30:00 → 10-15 14:30
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso)
  if (!m) return iso
  return `${m[2]}-${m[3]} ${m[4]}:${m[5]}`
}

function go(name) {
  router.push({ name })
}

onMounted(() => {
  loadStats()
  loadRecent()
})
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}
.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 18px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  position: relative;
}
.stat-icon {
  font-size: 22px;
  margin-bottom: 8px;
}
.stat-icon.is-up { color: #67c23a; }
.stat-icon.is-down { color: #f56c6c; }
.stat-icon.is-flat { color: #409eff; }
.label {
  color: #8a919f;
  font-size: 13px;
}
.value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 600;
  color: #303133;
}
.unit {
  font-size: 13px;
  font-weight: 400;
  color: #8a919f;
  margin-left: 4px;
}
.trend {
  margin-top: 8px;
  font-size: 12px;
  display: flex;
  gap: 8px;
  align-items: baseline;
}
.trend.is-up { color: #67c23a; }
.trend.is-down { color: #f56c6c; }
.trend.is-flat { color: #8a919f; }
.trend .prev { color: #a8abb2; }

.lower-row {
  margin-top: 16px;
}
.page-card {
  margin-bottom: 16px;
}
.quick {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.btn-icon {
  margin-right: 4px;
  vertical-align: -2px;
}
.recent-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.recent-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid #f2f3f5;
  font-size: 13px;
}
.recent-item:last-child {
  border-bottom: none;
}
.recent-dot {
  font-size: 16px;
}
.recent-dot.is-ok { color: #67c23a; }
.recent-dot.is-fail { color: #f56c6c; }
.recent-name {
  font-weight: 500;
  color: #303133;
}
.recent-time {
  margin-left: auto;
  color: #a8abb2;
}
.stat-alert {
  margin-bottom: 16px;
}
</style>
