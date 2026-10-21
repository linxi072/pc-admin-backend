<template>
  <div>
    <el-empty v-if="denied" description="无「系统监控读取」权限（system:monitor:read），请联系管理员授权后重试">
      <el-button type="primary" @click="load">重新加载</el-button>
    </el-empty>

    <template v-else>
      <!-- 探测运行态 -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ status.threshold ?? '--' }}</div>
            <div class="stat-label">判定阈值（同构 SQL 次数）</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ status.capacity ?? '--' }}</div>
            <div class="stat-label">留存容量</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ status.registrySize ?? '--' }}</div>
            <div class="stat-label">当前留存条数</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num" :class="status.totalBreaches > 0 ? 'stat-danger' : ''">
              {{ status.totalBreaches ?? 0 }}
            </div>
            <div class="stat-label">累计越限次数（不清零）</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-time">{{ fmtEpoch(status.lastBreachAtEpochMillis) }}</div>
            <div class="stat-label">最近越限时间</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ suspects.length }}</div>
            <div class="stat-label">本次展示条数</div>
          </div>
        </el-col>
      </el-row>

      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <span class="card-title">N+1 疑似事件（最新在前）</span>
            <div class="filter-group">
              <span class="filter-label">条数</span>
              <el-input-number v-model="limit" :min="1" :max="200" style="width: 110px" @change="load" />
            </div>
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button type="primary" :icon="Refresh" @click="load">刷新</el-button>
              <el-button type="danger" :icon="Delete" @click="onClear">清空留存</el-button>
            </div>
          </div>
        </div>

        <el-table :data="suspects" v-loading="loading" border stripe>
          <el-table-column prop="timestampMillis" label="登记时间" width="170">
            <template #default="{ row }">{{ fmtEpoch(row.timestampMillis) }}</template>
          </el-table-column>
          <el-table-column prop="count" label="执行次数" width="100" sortable />
          <el-table-column prop="threadName" label="线程" width="180" show-overflow-tooltip />
          <el-table-column prop="fingerprint" label="同构指纹" min-width="280" show-overflow-tooltip />
          <el-table-column prop="sampleSql" label="SQL 样本（截断）" min-width="320" show-overflow-tooltip />
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
    await ElMessageBox.confirm('确认清空 N+1 疑似事件留存？累计越限计数不清零。', '清空确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await clearSuspects()
    ElMessage.success('已清空疑似事件留存')
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>
