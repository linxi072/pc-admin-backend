<template>
  <div>
    <h2 class="page-title">我发起的流程</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <span class="spacer" />
        <el-button type="primary" :icon="Plus" @click="startVisible = true">发起流程</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="processInstanceId" label="流程实例" width="140" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="businessType" label="业务类型" width="110" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RUNNING' ? 'primary' : row.status === 'COMPLETED' ? 'success' : 'info'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="发起时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Document" @click="openRecords(row)">审批记录</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="startVisible" title="发起审批流程" width="520px">
      <el-form :model="startForm" label-width="90px">
        <el-form-item label="流程定义">
          <el-select v-model="startForm.processKey" style="width: 100%">
            <el-option label="请假审批（可编辑设计）" value="leaveApproval" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input v-model="startForm.businessType" placeholder="如 LEAVE" />
        </el-form-item>
        <el-form-item label="业务单号">
          <el-input v-model="startForm.businessId" placeholder="如 L001" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="startForm.title" />
        </el-form-item>
        <el-form-item label="审批人">
          <el-select v-model="startForm.assigneeUserIds" multiple style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.displayName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="主管">
          <el-select v-model="startForm.managerUserId" style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.displayName" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="submitStart">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="recordsVisible" title="审批记录" size="420px">
      <el-timeline v-loading="recordsLoading">
        <el-timeline-item
          v-for="r in records"
          :key="r.operationId"
          :timestamp="r.occurredAt"
          :type="r.action === 'APPROVE' ? 'success' : r.action === 'REJECT' ? 'danger' : 'primary'"
        >
          <div><b>{{ actionLabel(r.action) }}</b> · 操作人 #{{ r.operatorUserId }}</div>
          <div v-if="r.opinion">意见：{{ r.opinion }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="!recordsLoading && records.length === 0" description="暂无审批记录" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Document } from '@element-plus/icons-vue'
import { myInstances, instanceRecords, startProcess } from '@/api/workflow'
import { pageUsers } from '@/api/user'

const loading = ref(false)
const rows = ref([])
const userOptions = ref([])

const startVisible = ref(false)
const starting = ref(false)
const startForm = reactive({
  processKey: 'leaveApproval', businessType: 'LEAVE', businessId: '', title: '', assigneeUserIds: [], managerUserId: null
})

const recordsVisible = ref(false)
const recordsLoading = ref(false)
const records = ref([])

function statusLabel(s) {
  return s === 'RUNNING' ? '运行中' : s === 'COMPLETED' ? '已完成' : s === 'TERMINATED' ? '已终止' : s
}
function actionLabel(a) {
  return a === 'APPROVE' ? '通过' : a === 'REJECT' ? '驳回' : a === 'START' ? '发起' : a === 'TRANSFER' ? '转办' : a
}

async function load() {
  loading.value = true
  try {
    const [instances, users] = await Promise.all([myInstances(), pageUsers({ page: 1, size: 50 })])
    rows.value = instances
    userOptions.value = users.records
  } finally {
    loading.value = false
  }
}

async function submitStart() {
  starting.value = true
  try {
    const pid = await startProcess({ ...startForm })
    ElMessage.success('流程已发起：' + pid)
    startVisible.value = false
    load()
  } finally {
    starting.value = false
  }
}

async function openRecords(row) {
  recordsVisible.value = true
  recordsLoading.value = true
  records.value = []
  try {
    records.value = await instanceRecords(row.processInstanceId)
  } finally {
    recordsLoading.value = false
  }
}

onMounted(load)
</script>
