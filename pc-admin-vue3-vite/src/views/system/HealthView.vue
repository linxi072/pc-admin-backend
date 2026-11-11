<template>
  <div>
    <h2 class="page-title">{{ '健康探针' }}</h2>
    <p class="scope-hint">
      {{ '存活探针（liveness）仅依赖 JVM，就绪探针（readiness）检查数据库等必需依赖。两者均为 k8s 风格根路径端点，实时反映实例健康态，仅供运维只读诊断。' }}
    </p>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <el-button type="primary" :loading="loading" @click="refresh">{{ '刷新' }}</el-button>
        <el-switch
          v-model="autoRefresh"
          :active-text="'自动刷新（10s）'"
          @change="onAutoToggle"
        />
        <span v-if="lastRefresh" class="muted">{{ '最近刷新' }}：{{ lastRefreshText }}</span>
      </div>

      <el-alert
        v-if="bothFailed"
        type="error"
        :closable="false"
        :title="'存活与就绪探针均不可达，请确认后端已启动且 vite 代理已转发 /livez、/readyz。'"
        show-icon
        style="margin-bottom: 12px"
      />

      <el-row :gutter="16">
        <el-col :span="12">
          <el-card shadow="hover" class="probe-card">
            <template #header>
              <div class="probe-header">
                <span>{{ '存活探针' }}</span>
                <el-tag :type="tagType(live)" effect="dark" size="small">{{ statusText(live) }}</el-tag>
              </div>
            </template>
            <div v-loading="liveLoading">
              <el-alert
                v-if="live && !live.ok"
                type="error"
                :closable="false"
                :title="tx(live.error || 'health.unreachable')"
                show-icon
              />
              <template v-else-if="live && live.ok">
                <p class="probe-msg">{{ live.message }}</p>
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item v-for="(val, key) in live.details" :key="key" :label="key">
                    {{ formatValue(val) }}
                  </el-descriptions-item>
                </el-descriptions>
              </template>
            </div>
          </el-card>
        </el-col>

        <el-col :span="12">
          <el-card shadow="hover" class="probe-card">
            <template #header>
              <div class="probe-header">
                <span>{{ '就绪探针' }}</span>
                <el-tag :type="tagType(ready)" effect="dark" size="small">{{ statusText(ready) }}</el-tag>
              </div>
            </template>
            <div v-loading="readyLoading">
              <el-alert
                v-if="ready && !ready.ok"
                type="error"
                :closable="false"
                :title="tx(ready.error || 'health.unreachable')"
                show-icon
              />
              <template v-else-if="ready && ready.ok">
                <p class="probe-msg">{{ ready.message }}</p>
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item v-for="(val, key) in ready.details" :key="key" :label="key">
                    {{ formatValue(val) }}
                  </el-descriptions-item>
                </el-descriptions>
              </template>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { getLiveness, getReadiness } from '@/api/health'
import { tx } from '@/locales/zh-cn'

// 两个探针各自独立加载（一个失败不阻塞另一个）；loading 与结果分别持有。
const live = ref(null)
const ready = ref(null)
const liveLoading = ref(false)
const readyLoading = ref(false)
const loading = ref(false)
const autoRefresh = ref(false)
const lastRefresh = ref(null)
let timer = null

const lastRefreshText = computed(() =>
  lastRefresh.value ? lastRefresh.value.toLocaleTimeString() : ''
)

const bothFailed = computed(
  () => live.value && ready.value && !live.value.ok && !ready.value.ok
)

function tagType(probe) {
  if (!probe || !probe.ok) return 'danger'
  return probe.status === 'UP' ? 'success' : 'danger'
}

function statusText(probe) {
  if (!probe) return ''
  if (!probe.ok) return ''
  return probe.status === 'UP' ? 'UP' : 'DOWN'
}

// details 值可能是字符串/数字/布尔；对象则序列化为 JSON，避免渲染 [object Object]。
function formatValue(val) {
  if (val === null || val === undefined) return '—'
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

async function refresh() {
  loading.value = true
  liveLoading.value = true
  readyLoading.value = true
  try {
    const [l, r] = await Promise.all([getLiveness(), getReadiness()])
    live.value = l
    ready.value = r
    lastRefresh.value = new Date()
  } finally {
    loading.value = false
    liveLoading.value = false
    readyLoading.value = false
  }
}

function onAutoToggle(on) {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (on) {
    timer = setInterval(refresh, 10000)
  }
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

// 首屏主动拉取一次
refresh()
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.probe-card {
  margin-bottom: 4px;
}
.probe-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}
.probe-msg {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
}
</style>
