<template>
  <div>
    <el-empty v-if="denied" :description="t('monitor.noKafkaPerm')">
      <el-button type="primary" @click="load">{{ t('common.reload') }}</el-button>
    </el-empty>

    <template v-else>
      <!-- 消费容器运行态 -->
      <el-row :gutter="16" class="summary-row">
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num" :class="status.enabled ? 'stat-ok' : 'stat-danger'">
              {{ status.enabled ? t('monitor.assembled') : t('monitor.notAssembled') }}
            </div>
            <div class="stat-label">{{ t('monitor.consumerContainer') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num" :class="status.running ? 'stat-ok' : 'stat-danger'">
              {{ status.running ? t('common.running') : t('monitor.stopped') }}
            </div>
            <div class="stat-label">{{ t('monitor.workerThreads') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ status.handledCount ?? 0 }}</div>
            <div class="stat-label">{{ t('monitor.accProcessed') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num">{{ status.staleCount ?? 0 }}</div>
            <div class="stat-label">{{ t('monitor.legacyDupDiscard') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num stat-danger">{{ status.poisonCount ?? 0 }}</div>
            <div class="stat-label">{{ t('monitor.accPoison') }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-num" :class="status.poisonPending > 0 ? 'stat-danger' : ''">
              {{ status.poisonPending ?? 0 }}
            </div>
            <div class="stat-label">{{ t('monitor.pendingCompensation') }}</div>
          </div>
        </el-col>
      </el-row>

      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <span class="card-title">{{ t('monitor.deadLetterList') }}</span>
            <div class="filter-group">
              <span class="filter-label">Message Key</span>
              <el-input v-model="keyFilter" :placeholder="t('monitor.exactMatchNullable')" clearable style="width: 220px" @keyup.enter="onSearch" />
            </div>
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button type="primary" :icon="Search" @click="onSearch">{{ t('common.query') }}</el-button>
              <el-button :icon="RefreshLeft" @click="resetQuery">{{ t('common.reset') }}</el-button>
            </div>
          </div>
        </div>

        <el-table :data="poison.records" v-loading="loading" border stripe>
          <el-table-column prop="topic" label="Topic" min-width="160" show-overflow-tooltip />
          <el-table-column prop="partition" :label="t('monitor.partition')" width="70" />
          <el-table-column prop="offset" :label="t('monitor.rank')" width="90" />
          <el-table-column prop="key" label="Key" width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.key || '--' }}</template>
          </el-table-column>
          <el-table-column prop="attempts" :label="t('monitor.failCount')" width="90" sortable />
          <el-table-column prop="detectedAtEpochMillis" :label="t('monitor.judgeTime')" width="170">
            <template #default="{ row }">{{ fmtEpoch(row.detectedAtEpochMillis) }}</template>
          </el-table-column>
          <el-table-column prop="lastError" :label="t('monitor.lastError')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="payloadPreview" :label="t('monitor.loadPreview')" min-width="200" show-overflow-tooltip />
          <el-table-column :label="t('common.operation')" width="160" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="onRetry(row)">{{ t('monitor.retry') }}</el-button>
              <el-button type="danger" size="small" @click="onDiscard(row)">{{ t('monitor.discard') }}</el-button>
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
import { t } from '@/i18n'
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
      t('monitor.confirmRetryPoison', { topic: row.topic, partition: row.partition, offset: row.offset }),
      t('monitor.retryConfirm'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await retryPoison(row.id)
    ElMessage.success(t('monitor.replayAllowed'))
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onDiscard(row) {
  try {
    await ElMessageBox.confirm(
      t('monitor.confirmDiscardPoison', { topic: row.topic, partition: row.partition, offset: row.offset }),
      t('monitor.discardConfirm'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await discardPoison(row.id)
    ElMessage.success(t('monitor.discarded'))
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>
