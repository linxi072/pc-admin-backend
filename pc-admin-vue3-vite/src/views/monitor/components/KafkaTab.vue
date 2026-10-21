<template>
  <div>
    <el-empty v-if="denied" description="无「Kafka 监控读取」权限（system:kafka:read），请联系管理员授权后重试">
      <el-button type="primary" @click="load">重新加载</el-button>
    </el-empty>

    <template v-else>
      <!-- 消费容器运行态 -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num" :class="status.enabled ? 'stat-ok' : 'stat-danger'">
              {{ status.enabled ? '已装配' : '未装配' }}
            </div>
            <div class="stat-label">消费容器</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num" :class="status.running ? 'stat-ok' : 'stat-danger'">
              {{ status.running ? '运行中' : '已停止' }}
            </div>
            <div class="stat-label">Worker 线程</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ status.handledCount ?? 0 }}</div>
            <div class="stat-label">累计已处理</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ status.staleCount ?? 0 }}</div>
            <div class="stat-label">旧版/重复丢弃</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-danger">{{ status.poisonCount ?? 0 }}</div>
            <div class="stat-label">累计毒消息</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num" :class="status.poisonPending > 0 ? 'stat-danger' : ''">
              {{ status.poisonPending ?? 0 }}
            </div>
            <div class="stat-label">待补偿（死信存量）</div>
          </div>
        </el-col>
      </el-row>

      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <span class="card-title">死信（毒消息）列表</span>
            <div class="filter-group">
              <span class="filter-label">Message Key</span>
              <el-input v-model="keyFilter" placeholder="精确匹配，可空" clearable style="width: 220px" @keyup.enter="onSearch" />
            </div>
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
              <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
            </div>
          </div>
        </div>

        <el-table :data="poison.records" v-loading="loading" border stripe>
          <el-table-column prop="topic" label="Topic" min-width="160" show-overflow-tooltip />
          <el-table-column prop="partition" label="分区" width="70" />
          <el-table-column prop="offset" label="位次" width="90" />
          <el-table-column prop="key" label="Key" width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.key || '--' }}</template>
          </el-table-column>
          <el-table-column prop="attempts" label="失败次数" width="90" sortable />
          <el-table-column prop="detectedAtEpochMillis" label="判定时间" width="170">
            <template #default="{ row }">{{ fmtEpoch(row.detectedAtEpochMillis) }}</template>
          </el-table-column>
          <el-table-column prop="lastError" label="末次异常" min-width="180" show-overflow-tooltip />
          <el-table-column prop="payloadPreview" label="负载预览" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="onRetry(row)">重试</el-button>
              <el-button type="danger" size="small" @click="onDiscard(row)">丢弃</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="poison.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="load"
            @size-change="onSizeChange"
          />
        </div>
        <div class="hint">
          未启用有序消费容器（acme.kafka.ordered-consumer.enabled=false）时全部计数为 0、死信为空——这是降级态而非故障。
          「重试」清除失败计数与版本表占位，允许上游重投后重新处理；「丢弃」视为已人工确认无需补偿（需 system:kafka:manage）。
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import { getConsumerStatus, pagePoison, retryPoison, discardPoison } from '@/api/kafkaMonitor'
import { fmtEpoch, isForbidden } from './format'

const status = ref({})
const poison = reactive({ records: [], total: 0 })
const loading = ref(false)
const denied = ref(false)
const keyFilter = ref('')
const page = ref(1)
const size = ref(10)

async function load() {
  loading.value = true
  try {
    const [st, pg] = await Promise.all([
      getConsumerStatus(),
      pagePoison({ page: page.value, size: size.value, key: keyFilter.value || undefined })
    ])
    status.value = st || {}
    poison.records = pg?.records || []
    poison.total = pg?.total || 0
    denied.value = false
  } catch (e) {
    if (isForbidden(e)) {
      denied.value = true
      return
    }
    status.value = {}
    poison.records = []
    poison.total = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function resetQuery() {
  keyFilter.value = ''
  page.value = 1
  load()
}

function onSizeChange(sz) {
  size.value = sz
  page.value = 1
  load()
}

async function onRetry(row) {
  try {
    await ElMessageBox.confirm(
      `确认重试毒消息 ${row.topic}-${row.partition}@${row.offset}？将清除失败计数与版本表占位。`,
      '重试确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await retryPoison(row.id)
    ElMessage.success('已允许重投，待上游重发后重新处理')
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onDiscard(row) {
  try {
    await ElMessageBox.confirm(
      `确认丢弃毒消息 ${row.topic}-${row.partition}@${row.offset}？丢弃后该消息不再参与补偿。`,
      '丢弃确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await discardPoison(row.id)
    ElMessage.success('已丢弃')
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>
