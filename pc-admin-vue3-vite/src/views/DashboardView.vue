<template>
  <div>
    <h2 class="page-title">工作台</h2>

    <!-- 统计卡片：数据全部来自 GET /api/workbench/stats，前端不硬编码 -->
    <div v-if="loading" class="stat-grid">
      <div class="stat-card" v-for="n in 4" :key="n">
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

    <div class="two-col">
      <el-card class="page-card" header="快捷入口">
        <div class="quick">
          <el-button type="primary" @click="go('user')">用户管理</el-button>
          <el-button type="primary" @click="go('role')">角色管理</el-button>
          <el-button type="primary" @click="go('task')">我的待办</el-button>
          <el-button type="primary" @click="go('instance')">我发起的流程</el-button>
        </div>
      </el-card>
      <el-card class="page-card" header="系统信息">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="前端框架">Vue 3 + Element Plus + Vite</el-descriptions-item>
          <el-descriptions-item label="后端服务">java-admin-framework（jOOQ / JobRunr / Flowable）</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { workbenchStats } from '@/api/workbench'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const cards = ref([])

async function loadStats() {
  loading.value = true
  error.value = ''
  try {
    const res = await workbenchStats()
    cards.value = Array.isArray(res?.cards) ? res.cards : []
  } catch (e) {
    // 失败呈现错误态，不使用任何本地/默认数据兜底
    cards.value = []
    error.value = e.message || '统计数据加载失败'
  } finally {
    loading.value = false
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

function go(name) {
  router.push({ name })
}

onMounted(loadStats)
</script>

<style scoped>
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
.trend.is-up {
  color: #67c23a;
}
.trend.is-down {
  color: #f56c6c;
}
.trend.is-flat {
  color: #8a919f;
}
.trend .prev {
  color: #a8abb2;
}
.stat-alert {
  margin-bottom: 16px;
}
</style>
