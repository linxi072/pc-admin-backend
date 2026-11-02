<template>
  <div>
    <el-empty v-if="denied" :description="t('monitor.noMonPerm')">
      <el-button type="primary" @click="load">{{ t('common.reload') }}</el-button>
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
            <div class="stat-label">{{ t('monitor.firing') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-ok">{{ stats.resolvedCount ?? 0 }}</div>
            <div class="stat-label">{{ t('monitor.resolved') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-time">{{ fmtEpoch(stats.mostRecentEpochMillis) }}</div>
            <div class="stat-label">{{ t('monitor.latestReceive') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-time">{{ fmtEpoch(stats.oldestRetainedEpochMillis) }}</div>
            <div class="stat-label">{{ t('monitor.oldestRetain') }}</div>
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
            <div class="stat-label">{{ t('monitor.severityDist') }}</div>
          </div>
        </el-col>
      </el-row>

      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <span class="card-title">{{ t('monitor.alertList') }}</span>
            <div class="filter-group">
              <span class="filter-label">{{ t('common.status') }}</span>
              <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 130px">
                <el-option label="firing" value="firing" />
                <el-option label="resolved" value="resolved" />
              </el-select>
            </div>
            <div class="filter-group">
              <span class="filter-label">{{ t('monitor.level') }}</span>
              <el-input v-model="query.severity" :placeholder="t('monitor.egSeverity')" clearable style="width: 170px" @keyup.enter="onSearch" />
            </div>
            <div class="filter-group">
              <span class="filter-label">{{ t('monitor.count') }}</span>
              <el-input-number v-model="query.limit" :min="1" :max="200" style="width: 110px" />
            </div>
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button type="primary" :icon="Search" @click="onSearch">{{ t('common.query') }}</el-button>
              <el-button :icon="RefreshLeft" @click="resetQuery">{{ t('common.reset') }}</el-button>
              <el-button type="danger" :icon="Delete" @click="onClear">{{ t('monitor.clearInbox') }}</el-button>
            </div>
          </div>
        </div>

        <el-table :data="alerts" v-loading="loading" border stripe>
          <el-table-column prop="receivedAtEpochMillis" :label="t('monitor.receiveTime')" width="170">
            <template #default="{ row }">{{ fmtEpoch(row.receivedAtEpochMillis) }}</template>
          </el-table-column>
          <el-table-column prop="status" :label="t('common.status')" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'firing' ? 'danger' : 'success'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="severity" :label="t('monitor.level')" width="100">
            <template #default="{ row }">
              <el-tag :type="row.severity === 'critical' ? 'danger' : row.severity === 'warning' ? 'warning' : 'info'" size="small">
                {{ row.severity || 'unknown' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="name" :label="t('monitor.alertName')" width="200" show-overflow-tooltip />
          <el-table-column prop="instance" :label="t('monitor.object')" width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.instance || '--' }}</template>
          </el-table-column>
          <el-table-column prop="summary" :label="t('monitor.summary')" min-width="240" show-overflow-tooltip />
          <el-table-column prop="startsAt" :label="t('monitor.effectiveTime')" width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ row.startsAt || '--' }}</template>
          </el-table-column>
          <el-table-column prop="endsAt" :label="t('monitor.resolveTime')" width="170" show-overflow-tooltip>
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
import { t } from '@/i18n'
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
    await ElMessageBox.confirm(t('monitor.confirmClearInbox'), t('monitor.clearConfirm'), { type: 'warning' })
  } catch {
    return
  }
  try {
    const cleared = await clearAlerts()
    ElMessage.success(t('monitor.clearedN', { cleared: cleared ?? 0 }))
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
