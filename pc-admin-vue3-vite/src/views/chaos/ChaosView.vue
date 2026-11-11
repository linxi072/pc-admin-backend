<template>
  <div>
    <h2 class="page-title">{{ '混沌工程' }}</h2>

    <el-empty v-if="denied" :description="'混沌工程未启用（app.chaos.enabled=false），后端控制器未注册，暂不可操作。'">
      <el-button type="primary" @click="load">{{ '重新加载' }}</el-button>
    </el-empty>

    <template v-else>
      <el-card class="page-card" shadow="never">
        <div class="toolbar">
          <div class="toolbar-filters">
            <span class="card-title">{{ '故障注入' }}</span>
          </div>
          <div class="toolbar-actions">
            <div class="action-buttons">
              <el-button type="primary" :icon="Plus" @click="openInject">{{ '注入故障' }}</el-button>
              <el-button type="danger" :icon="Delete" :disabled="!faults.length" @click="onClearAll">{{ '全部清除' }}</el-button>
              <el-button :icon="Refresh" @click="load">{{ '刷新' }}</el-button>
            </div>
          </div>
        </div>

        <el-table :data="faults" v-loading="loading" border stripe>
          <el-table-column prop="type" :label="'类型'" width="110">
            <template #default="{ row }">{{ typeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column prop="pathPattern" :label="'路径匹配'" min-width="200" show-overflow-tooltip />
          <el-table-column prop="latencyMillis" :label="'延迟(ms)'" width="110" />
          <el-table-column prop="errorStatus" :label="'错误状态码'" width="110" />
          <el-table-column prop="probability" :label="'触发概率'" width="110">
            <template #default="{ row }">{{ formatProb(row.probability) }}</template>
          </el-table-column>
          <el-table-column prop="description" :label="'说明'" min-width="160" show-overflow-tooltip />
          <el-table-column :label="'操作'" width="110" fixed="right">
            <template #default="{ row }">
              <el-button type="danger" link :icon="CircleClose" @click="onRelease(row)">{{ '解除' }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && !faults.length" :description="'当前无注入中的故障'" :image-size="80" />
      </el-card>
    </template>

    <el-dialog v-model="dialogVisible" :title="'注入故障'" width="520px" @closed="resetForm">
      <el-form :model="form" label-width="100px">
        <el-form-item :label="'类型'">
          <el-select v-model="form.type" style="width: 100%">
            <el-option :label="'延迟'" value="LATENCY" />
            <el-option :label="'错误'" value="ERROR" />
            <el-option :label="'异常'" value="EXCEPTION" />
          </el-select>
        </el-form-item>
        <el-form-item :label="'路径匹配'">
          <el-input v-model="form.pathPattern" placeholder="/api/**" clearable />
        </el-form-item>
        <el-form-item v-if="form.type === 'LATENCY'" :label="'延迟(ms)'">
          <el-input-number v-model="form.latencyMillis" :min="0" :max="60000" :step="50" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.type === 'ERROR'" :label="'错误状态码'">
          <el-input-number v-model="form.errorStatus" :min="400" :max="599" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="'触发概率'">
          <el-input-number v-model="form.probability" :min="0" :max="1" :step="0.05" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="'说明'">
          <el-input v-model="form.description" type="textarea" :rows="3" :placeholder="'说明'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ '取消' }}</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">{{ '确认' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>

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
  if (type === 'LATENCY') return '延迟'
  if (type === 'ERROR') return '错误'
  if (type === 'EXCEPTION') return '异常'
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
    ElMessage.warning('请输入路径匹配')
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
    ElMessage.success('故障注入成功')
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
    await ElMessageBox.confirm('确认解除该故障注入？', '警告', { type: 'warning' })
  } catch {
    return
  }
  try {
    await releaseFault(row.id)
    ElMessage.success('已解除该故障')
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onClearAll() {
  try {
    await ElMessageBox.confirm('确认清除全部故障注入？此操作不可撤销。', '警告', { type: 'warning' })
  } catch {
    return
  }
  try {
    await clearFaults()
    ElMessage.success('已清除全部故障')
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>
