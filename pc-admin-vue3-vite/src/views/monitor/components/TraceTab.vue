<template>
  <div>
    <!-- 403 降级空态：可观测性权限（system:observability:read）未授予时友好提示 -->
    <el-empty v-if="denied" description="无「可观测性读取」权限（system:observability:read），请联系管理员授权后重试">
      <el-button type="primary" @click="load">重新加载</el-button>
    </el-empty>

    <template v-else>
      <!-- 运行态摘要卡 -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ summary.retained ?? '--' }}</div>
            <div class="stat-label">当前留存 span</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ summary.capacity ?? '--' }}</div>
            <div class="stat-label">留存容量</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-ok">{{ summary.okCount ?? '--' }}</div>
            <div class="stat-label">累计 OK</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-danger">{{ summary.errorCount ?? '--' }}</div>
            <div class="stat-label">累计 ERROR</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ summary.unsetCount ?? '--' }}</div>
            <div class="stat-label">累计 UNSET</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num" :class="summary.exporterEnabled ? 'stat-ok' : 'stat-danger'">
              {{ summary.exporterEnabled ? '已启用' : '未启用' }}
            </div>
            <div class="stat-label">OTLP 导出链路</div>
          </div>
        </el-col>
      </el-row>

      <!-- span 列表 -->
      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <div class="filter-group">
              <span class="filter-label">状态</span>
              <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
                <el-option label="OK" value="OK" />
                <el-option label="ERROR" value="ERROR" />
                <el-option label="UNSET" value="UNSET" />
              </el-select>
            </div>
            <div class="filter-group">
              <span class="filter-label">TraceId</span>
              <el-input v-model="query.traceId" placeholder="32 位 hex，精确匹配" clearable style="width: 260px" @keyup.enter="onSearch" />
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
              <el-button type="danger" :icon="Delete" @click="onClear">清空留存</el-button>
            </div>
          </div>
        </div>

        <el-table :data="spans" v-loading="loading" border stripe>
          <el-table-column prop="startEpochMillis" label="开始时间" width="170">
            <template #default="{ row }">{{ fmtEpoch(row.startEpochMillis) }}</template>
          </el-table-column>
          <el-table-column prop="name" label="Span" min-width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ERROR' ? 'danger' : row.status === 'OK' ? 'success' : 'info'" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="durationMillis" label="耗时" width="110" sortable>
            <template #default="{ row }">{{ fmtDuration(row.durationMillis) }}</template>
          </el-table-column>
          <el-table-column prop="traceId" label="TraceId" width="230" show-overflow-tooltip />
          <el-table-column prop="spanId" label="SpanId" width="150" show-overflow-tooltip />
          <el-table-column prop="statusMessage" label="错误说明" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.statusMessage" class="error-text">{{ row.statusMessage }}</span>
              <span v-else class="muted">--</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="hint">
          留存为有界环形（默认 200 条，超出淘汰最旧）；累计口径含已被淘汰的 span。
          清空只影响本地留存，不影响 OTLP Collector 导出链路（需 system:observability:manage）。
        </div>
      </el-card>

      <!-- 指标快照 -->
      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <span class="card-title">Micrometer 指标快照</span>
            <div class="filter-group">
              <span class="filter-label">名称前缀</span>
              <el-input v-model="metricPrefix" placeholder="如 app.security" clearable style="width: 220px" @keyup.enter="loadMetrics" />
            </div>
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button :icon="Refresh" @click="loadMetrics">刷新指标</el-button>
            </div>
          </div>
        </div>
        <el-table :data="metrics" v-loading="metricLoading" border stripe max-height="420">
          <el-table-column prop="name" label="指标名" min-width="240" show-overflow-tooltip />
          <el-table-column prop="type" label="类型" width="150" />
          <el-table-column prop="value" label="当前值" width="130" sortable>
            <template #default="{ row }">{{ row.value == null ? '--' : row.value }}</template>
          </el-table-column>
          <el-table-column prop="baseUnit" label="单位" width="100">
            <template #default="{ row }">{{ row.baseUnit || '--' }}</template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, Refresh, Delete } from '@element-plus/icons-vue'
import { listSpans, getTraceSummary, listMetrics, clearSpans } from '@/api/observability'
import { fmtEpoch, fmtDuration, isForbidden } from './format'

const spans = ref([])
const summary = ref({})
const metrics = ref([])
const loading = ref(false)
const metricLoading = ref(false)
const denied = ref(false)
const metricPrefix = ref('')
const query = reactive({ status: '', traceId: '', limit: 50 })

async function load() {
  loading.value = true
  try {
    const [list, sum] = await Promise.all([
      listSpans({ status: query.status || undefined, traceId: query.traceId || undefined, limit: query.limit }),
      getTraceSummary()
    ])
    spans.value = list || []
    summary.value = sum || {}
    denied.value = false
  } catch (e) {
    if (isForbidden(e)) {
      denied.value = true
      return
    }
    spans.value = []
  } finally {
    loading.value = false
  }
}

async function loadMetrics() {
  metricLoading.value = true
  try {
    metrics.value = (await listMetrics({ prefix: metricPrefix.value || undefined })) || []
  } catch (e) {
    metrics.value = []
  } finally {
    metricLoading.value = false
  }
}

function onSearch() {
  load()
  loadMetrics()
}

function resetQuery() {
  query.status = ''
  query.traceId = ''
  query.limit = 50
  load()
}

async function onClear() {
  try {
    await ElMessageBox.confirm('确认清空本地 span 留存？不影响 OTLP 导出链路与已导出数据。', '清空确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await clearSpans()
    ElMessage.success('已清空本地 span 留存')
    load()
  } catch (e) {
    // 拦截器已提示（含 403 无 manage 权限）
  }
}

onMounted(() => {
  load()
  loadMetrics()
})
</script>

<style scoped>
.error-text {
  color: #f56c6c;
}
.muted {
  color: #a8abb2;
}
</style>
