<template>
  <div>
    <el-empty v-if="denied" :description="t('monitor.noMonPerm')">
      <el-button type="primary" @click="load">{{ t('common.reload') }}</el-button>
    </el-empty>

    <template v-else>
      <!-- 探测运行态 -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ status.threshold ?? '--' }}</div>
            <div class="stat-label">{{ t('monitor.judgeThreshold') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ status.capacity ?? '--' }}</div>
            <div class="stat-label">{{ t('monitor.retentionCapacity') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ status.registrySize ?? '--' }}</div>
            <div class="stat-label">{{ t('monitor.currentCount') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num" :class="status.totalBreaches > 0 ? 'stat-danger' : ''">
              {{ status.totalBreaches ?? 0 }}
            </div>
            <div class="stat-label">{{ t('monitor.accBreach') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-time">{{ fmtEpoch(status.lastBreachAtEpochMillis) }}</div>
            <div class="stat-label">{{ t('monitor.lastBreach') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ suspects.length }}</div>
            <div class="stat-label">{{ t('monitor.shownCount') }}</div>
          </div>
        </el-col>
      </el-row>

      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <span class="card-title">{{ t('monitor.n1SuspectEvents') }}</span>
            <div class="filter-group">
              <span class="filter-label">{{ t('monitor.count') }}</span>
              <el-input-number v-model="limit" :min="1" :max="200" style="width: 110px" @change="load" />
            </div>
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button type="primary" :icon="Refresh" @click="load">{{ t('common.refresh') }}</el-button>
              <el-button type="danger" :icon="Delete" @click="onClear">{{ t('monitor.clearRetention') }}</el-button>
            </div>
          </div>
        </div>

        <el-table :data="suspects" v-loading="loading" border stripe>
          <el-table-column prop="timestampMillis" :label="t('monitor.registerTime')" width="170">
            <template #default="{ row }">{{ fmtEpoch(row.timestampMillis) }}</template>
          </el-table-column>
          <el-table-column prop="count" :label="t('monitor.execCount')" width="100" sortable />
          <el-table-column prop="threadName" :label="t('monitor.thread')" width="180" show-overflow-tooltip />
          <el-table-column prop="fingerprint" :label="t('monitor.sameShapeFingerprint')" min-width="280" show-overflow-tooltip />
          <el-table-column prop="sampleSql" :label="t('monitor.sqlSample')" min-width="320" show-overflow-tooltip />
        </el-table>
        <div class="hint">
          指纹为参数归一化后的同构 SQL（占位符化），可直接用于定位代码中的查询。
          清空只清事件留存，「累计越限次数」为进程启动以来单调计数、不清零。
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Delete } from '@element-plus/icons-vue'
import { listSuspects, getNPlusOneStatus, clearSuspects } from '@/api/nplusOne'
import { fmtEpoch, isForbidden } from './format'

const suspects = ref([])
const status = ref({})
const loading = ref(false)
const denied = ref(false)
const limit = ref(50)

async function load() {
  loading.value = true
  try {
    const [list, st] = await Promise.all([
      listSuspects({ limit: limit.value }),
      getNPlusOneStatus()
    ])
    suspects.value = list || []
    status.value = st || {}
    denied.value = false
  } catch (e) {
    if (isForbidden(e)) {
      denied.value = true
      return
    }
    suspects.value = []
  } finally {
    loading.value = false
  }
}

async function onClear() {
  try {
    await ElMessageBox.confirm(t('monitor.confirmClearN1'), t('monitor.clearConfirm'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await clearSuspects()
    ElMessage.success(t('monitor.suspectCleared'))
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>
