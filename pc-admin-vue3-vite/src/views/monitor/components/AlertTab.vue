<template>
  <div>
    <el-empty v-if="denied" description="无「系统监控读取」权限（system:monitor:read），请联系管理员授权后重试">
      <el-button type="primary" @click="load">重新加载</el-button>
    </el-empty>

    <template v-else>
      <!-- 收件箱汇总 -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ stats.total ?? '--' }}</div>
            <div class="stat-label">当前留存 / 容量 {{ stats.capacity ?? '--' }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-danger">{{ stats.firingCount ?? 0 }}</div>
            <div class="stat-label">firing（未恢复）</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-ok">{{ stats.resolvedCount ?? 0 }}</div>
            <div class="stat-label">resolved（已恢复）</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-time">{{ fmtEpoch(stats.mostRecentEpochMillis) }}</div>
            <div class="stat-label">最新接收时间</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-time">{{ fmtEpoch(stats.oldestRetainedEpochMillis) }}</div>
            <div class="stat-label">最旧留存时间</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="severity-tags">
              <el-tag
                v-for="[sev, cnt] in severityEntries"
                :key="sev"
                :type="sev === 'critical' ? 'danger' : sev === 'warning' ? 'warning' : 'info'"
                size="small"
                style="margin: 2px"
              >
                {{ sev }} × {{ cnt }}
              </el-tag>
              <span v-if="severityEntries.length === 0" class="muted">--</span>
            </div>
            <div class="stat-label">severity 分布（降序）</div>
          </div>
        </el-col>
      </el-row>

      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <span class="card-title">告警列表（最新在前）</span>
            <div class="filter-group">
              <span class="filter-label">状态</span>
              <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
                <el-option label="firing" value="firing" />
                <el-option label="resolved" value="resolved" />
              </el-select>
            </div>
            <div class="filter-group">
              <span class="filter-label">级别</span>
              <el-input v-model="query.severity" placeholder="如 critical / warning" clearable style="width: 170px" @keyup.enter="onSearch" />
            </div>
            <div class="filter-group">
              <span class="filter-label">条数</span>
              <el-input-number v-model="query.limit" :min="1" :max="200" style="width: 110px" />
            </div>
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
              <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
              <el-button type="danger" :icon="Delete" @click="onClear">清空收件箱</el-button>
            </div>
          </div>
        </div>

        <el-table :data="alerts" v-loading="loading" border stripe>
          <el-table-column prop="receivedAtEpochMillis" label="接收时间" width="170">
            <template #default="{ row }">{{ fmtEpoch(row.receivedAtEpochMillis) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'firing' ? 'danger' : 'success'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="severity" label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="row.severity === 'critical' ? 'danger' : row.severity === 'warning' ? 'warning' : 'info'" size="small">
                {{ row.severity || 'unknown' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="告警名" width="200" show-overflow-tooltip />
          <el-table-column prop="instance" label="对象" width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.instance || '--' }}</template>
          </el-table-column>
          <el-table-column prop="summary" label="摘要" min-width="240" show-overflow-tooltip />
          <el-table-column prop="startsAt" label="生效时间" width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ row.startsAt || '--' }}</template>
          </el-table-column>
          <el-table-column prop="endsAt" label="消除时间" width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ row.endsAt || '--' }}</template>
          </el-table-column>
        </el-table>
        <div class="hint">
          收件箱为「最近 N 条」有界窗口（容量 monitoring.alert.inbox-capacity，默认 200），超出自动淘汰最旧。
          limit 未传时按容量自适应；清空为整体丢弃当前窗口并返回清理条数，不提供单条删除。
          机器向接收入口（Alertmanager webhook）与本页读写分离，互不影响。
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, Delete } from '@element-plus/icons-vue'
import { listAlerts, getAlertStats, clearAlerts } from '@/api/alertInbox'
import { fmtEpoch, isForbidden } from './format'

const alerts = ref([])
const stats = ref({})
const loading = ref(false)
const denied = ref(false)
const query = reactive({ severity: '', status: '', limit: 100 })

/** severityCounts 为普通对象（Map 序列化），按 value 降序转条目数组（后端已排序，此处兜底保序）。 */
const severityEntries = computed(() => {
  const map = stats.value.severityCounts || {}
  return Object.entries(map).sort((a, b) => b[1] - a[1])
})

async function load() {
  loading.value = true
  try {
    const [list, st] = await Promise.all([
      listAlerts({ severity: query.severity || undefined, status: query.status || undefined, limit: query.limit }),
      getAlertStats()
    ])
    alerts.value = list || []
    stats.value = st || {}
    denied.value = false
  } catch (e) {
    if (isForbidden(e)) {
      denied.value = true
      return
    }
    alerts.value = []
  } finally {
    loading.value = false
  }
}

function onSearch() {
  load()
}

function resetQuery() {
  query.severity = ''
  query.status = ''
  query.limit = 100
  load()
}

async function onClear() {
  try {
    await ElMessageBox.confirm('确认清空告警收件箱？将整体丢弃当前窗口（最多为容量条），已发布的事件不受影响。', '清空确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    const cleared = await clearAlerts()
    ElMessage.success(`已清空 ${cleared ?? 0} 条`)
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<style scoped>
.muted {
  color: #a8abb2;
}
.severity-tags {
  min-height: 32px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}
</style>
