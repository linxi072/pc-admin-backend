<template>
  <div>
    <h2 class="page-title">{{ t('route.chaos') }}</h2>

    <el-empty v-if="denied" :description="t('chaos.featureDisabled')">
      <el-button type="primary" @click="load">{{ t('common.reload') }}</el-button>
    </el-empty>

    <template v-else>
      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <span class="card-title">{{ t('chaos.faults') }}</span>
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button type="primary" :icon="Plus" @click="openInject">{{ t('chaos.injectFault') }}</el-button>
              <el-button type="danger" :icon="Delete" :disabled="!faults.length" @click="onClearAll">{{ t('chaos.clearAll') }}</el-button>
              <el-button :icon="Refresh" @click="load">{{ t('common.refresh') }}</el-button>
            </div>
          </div>
        </div>

        <el-table :data="faults" v-loading="loading" border stripe>
          <el-table-column prop="type" :label="t('chaos.type')" width="110">
            <template #default="{ row }">{{ typeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column prop="pathPattern" :label="t('chaos.pathPattern')" min-width="200" show-overflow-tooltip />
          <el-table-column prop="latencyMillis" :label="t('chaos.latency')" width="110" />
          <el-table-column prop="errorStatus" :label="t('chaos.errorStatus')" width="110" />
          <el-table-column prop="probability" :label="t('chaos.probability')" width="110">
            <template #default="{ row }">{{ formatProb(row.probability) }}</template>
          </el-table-column>
          <el-table-column prop="description" :label="t('chaos.description')" min-width="160" show-overflow-tooltip />
          <el-table-column :label="t('chaos.actions')" width="110" fixed="right">
            <template #default="{ row }">
              <el-button type="danger" link :icon="CircleClose" @click="onRelease(row)">{{ t('chaos.release') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && !faults.length" :description="t('chaos.noFaults')" :image-size="80" />
      </el-card>
    </template>

    <el-dialog v-model="dialogVisible" :title="t('chaos.injectFault')" width="520px" @closed="resetForm">
      <el-form :model="form" label-width="100px">
        <el-form-item :label="t('chaos.type')">
          <el-select v-model="form.type" style="width: 100%">
            <el-option :label="t('chaos.typeLatency')" value="LATENCY" />
            <el-option :label="t('chaos.typeError')" value="ERROR" />
            <el-option :label="t('chaos.typeException')" value="EXCEPTION" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('chaos.pathPattern')">
          <el-input v-model="form.pathPattern" placeholder="/api/**" clearable />
        </el-form-item>
        <el-form-item v-if="form.type === 'LATENCY'" :label="t('chaos.latency')">
          <el-input-number v-model="form.latencyMillis" :min="0" :max="60000" :step="50" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.type === 'ERROR'" :label="t('chaos.errorStatus')">
          <el-input-number v-model="form.errorStatus" :min="400" :max="599" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('chaos.probability')">
          <el-input-number v-model="form.probability" :min="0" :max="1" :step="0.05" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('chaos.description')">
          <el-input v-model="form.description" type="textarea" :rows="3" :placeholder="t('chaos.description')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Refresh, CircleClose } from '@element-plus/icons-vue'
import { listFaults, injectFault, releaseFault, clearFaults } from '@/api/chaos'

const faults = ref([])
const loading = ref(false)
const denied = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)

const form = reactive({
  type: 'LATENCY',
  pathPattern: '',
  latencyMillis: 500,
  errorStatus: 500,
  probability: 1,
  description: ''
})

function isForbidden(err) {
  return err?.response?.status === 403
}

function typeLabel(type) {
  if (type === 'LATENCY') return t('chaos.typeLatency')
  if (type === 'ERROR') return t('chaos.typeError')
  if (type === 'EXCEPTION') return t('chaos.typeException')
  return type || '--'
}

function formatProb(p) {
  if (p == null) return '--'
  return `${(Number(p) * 100).toFixed(0)}%`
}

function resetForm() {
  form.type = 'LATENCY'
  form.pathPattern = ''
  form.latencyMillis = 500
  form.errorStatus = 500
  form.probability = 1
  form.description = ''
}

async function load() {
  loading.value = true
  try {
    const data = await listFaults()
    faults.value = data || []
    denied.value = false
  } catch (e) {
    if (isForbidden(e)) {
      denied.value = true
      return
    }
    faults.value = []
  } finally {
    loading.value = false
  }
}

function openInject() {
  dialogVisible.value = true
}

async function onSubmit() {
  if (!form.pathPattern || !form.pathPattern.trim()) {
    ElMessage.warning(t('chaos.pathRequired'))
    return
  }
  submitting.value = true
  try {
    await injectFault({
      type: form.type,
      pathPattern: form.pathPattern.trim(),
      latencyMillis: form.type === 'LATENCY' ? form.latencyMillis : null,
      errorStatus: form.type === 'ERROR' ? form.errorStatus : null,
      probability: form.probability,
      description: form.description || null
    })
    ElMessage.success(t('chaos.injectSuccess'))
    dialogVisible.value = false
    load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function onRelease(row) {
  try {
    await ElMessageBox.confirm(t('chaos.confirmRelease'), t('common.warning'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await releaseFault(row.id)
    ElMessage.success(t('chaos.releaseSuccess'))
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onClearAll() {
  try {
    await ElMessageBox.confirm(t('chaos.confirmClearAll'), t('common.warning'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await clearFaults()
    ElMessage.success(t('chaos.clearSuccess'))
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>
