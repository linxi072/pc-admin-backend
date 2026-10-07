<template>
  <div>
    <h2 class="page-title">我的待办</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <span class="spacer" />
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="taskId" label="任务ID" width="100" />
        <el-table-column prop="name" label="任务名称" width="160" />
        <el-table-column prop="processInstanceId" label="流程实例" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PENDING' ? 'warning' : 'success'">
              {{ row.status === 'PENDING' ? '待处理' : '已完成' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dueAt" label="截止时间" width="170" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Select" @click="openApprove(row)">审批</el-button>
            <el-button link type="danger" :icon="Close" @click="openReject(row)">驳回</el-button>
            <el-button link type="warning" :icon="Share" @click="openTransfer(row)">转办</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="approveVisible" :title="rejectMode ? '驳回任务' : '审批任务'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="任务">
          <span>{{ current?.name }}（{{ current?.taskId }}）</span>
        </el-form-item>
        <el-form-item label="意见">
          <el-input v-model="opinion" type="textarea" :rows="3" placeholder="审批意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button :type="rejectMode ? 'danger' : 'primary'" :loading="acting" @click="submitApprove">
          确定{{ rejectMode ? '驳回' : '通过' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" title="转办任务" width="460px">
      <el-form label-width="90px">
        <el-form-item label="任务">
          <span>{{ current?.name }}（{{ current?.taskId }}）</span>
        </el-form-item>
        <el-form-item label="转给">
          <el-select v-model="transferTo" style="width: 100%" placeholder="选择处理人">
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.displayName + '(' + u.username + ')'"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="意见">
          <el-input v-model="opinion" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="warning" :loading="acting" @click="submitTransfer">确定转办</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Select, Close, Share, Refresh } from '@element-plus/icons-vue'
import { myTasks, completeTask, transferTask } from '@/api/workflow'
import { pageUsers } from '@/api/user'

function genOpId() {
  return 'op-' + Date.now() + '-' + Math.floor(Math.random() * 1000)
}

const loading = ref(false)
const rows = ref([])
const userOptions = ref([])
const approveVisible = ref(false)
const transferVisible = ref(false)
const rejectMode = ref(false)
const acting = ref(false)
const current = ref(null)
const opinion = ref('')
const transferTo = ref(null)

async function load() {
  loading.value = true
  try {
    const [tasks, users] = await Promise.all([myTasks(), pageUsers({ page: 1, size: 50 })])
    rows.value = tasks
    userOptions.value = users.records
  } finally {
    loading.value = false
  }
}

function openApprove(row) {
  current.value = row
  rejectMode.value = false
  opinion.value = ''
  approveVisible.value = true
}
function openReject(row) {
  current.value = row
  rejectMode.value = true
  opinion.value = ''
  approveVisible.value = true
}

async function submitApprove() {
  acting.value = true
  try {
    await completeTask({
      taskId: current.value.taskId,
      action: rejectMode.value ? 'REJECT' : 'APPROVE',
      opinion: opinion.value,
      operationId: genOpId()
    })
    ElMessage.success(rejectMode.value ? '已驳回' : '已通过')
    approveVisible.value = false
    load()
  } finally {
    acting.value = false
  }
}

function openTransfer(row) {
  current.value = row
  opinion.value = ''
  transferTo.value = null
  transferVisible.value = true
}
async function submitTransfer() {
  if (!transferTo.value) {
    ElMessage.warning('请选择转办人')
    return
  }
  acting.value = true
  try {
    await transferTask({
      taskId: current.value.taskId,
      toUserId: transferTo.value,
      opinion: opinion.value,
      operationId: genOpId()
    })
    ElMessage.success('已转办')
    transferVisible.value = false
    load()
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>
