<template>
  <div>
    <h2 class="page-title">{{ t('workflow.myTodo') }}</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <span class="spacer" />
        <el-button :icon="Refresh" :loading="loading" @click="load">{{ t('common.refresh') }}</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="taskId" :label="t('workflow.taskId')" width="100" />
        <el-table-column prop="name" :label="t('workflow.taskName')" width="160" />
        <el-table-column prop="processInstanceId" :label="t('common.processInstance')" width="140" />
        <el-table-column :label="t('common.status')" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.delegationState === 'PENDING'" type="danger">{{ t('workflow.delegatedTodo') }}</el-tag>
            <el-tag v-else-if="row.delegationState === 'RESOLVED'" type="warning">{{ t('workflow.delegationReturned') }}</el-tag>
            <el-tag v-else :type="row.status === 'PENDING' ? 'warning' : 'success'">
              {{ row.status === 'PENDING' ? t('common.pending') : t('common.completed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dueAt" :label="t('workflow.deadline')" width="170" />
        <el-table-column :label="t('common.operation')" width="340" fixed="right">
          <template #default="{ row }">
            <template v-if="row.delegationState === 'PENDING'">
              <el-button link type="success" :icon="Promotion" @click="openResolve(row)">{{ t('workflow.completeAndReturn') }}</el-button>
            </template>
            <template v-else>
              <el-button link type="primary" :icon="Select" @click="openApprove(row)">{{ t('workflow.approval') }}</el-button>
              <el-button link type="danger" :icon="Close" @click="openReject(row)">{{ t('common.reject') }}</el-button>
              <el-button link type="warning" :icon="Share" @click="openTransfer(row)">{{ t('workflow.transfer') }}</el-button>
              <el-button link type="info" :icon="Switch" @click="openDelegate(row)">{{ t('workflow.delegate') }}</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="approveVisible" :title="rejectMode ? t('workflow.rejectTask') : t('workflow.approveTask')" width="460px">
      <el-form label-width="90px">
        <el-form-item :label="t('common.task')">
          <span>{{ current?.name }}（{{ current?.taskId }}）</span>
        </el-form-item>
        <el-form-item :label="t('workflow.opinion')">
          <el-input v-model="opinion" type="textarea" :rows="3" :placeholder="t('workflow.approvalOpinion')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button :type="rejectMode ? 'danger' : 'primary'" :loading="acting" @click="submitApprove">
          确定{{ rejectMode ? t('common.reject') : t('common.approve') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" :title="t('workflow.transferTask')" width="460px">
      <el-form label-width="90px">
        <el-form-item :label="t('common.task')">
          <span>{{ current?.name }}（{{ current?.taskId }}）</span>
        </el-form-item>
        <el-form-item :label="t('workflow.transferTo')">
          <el-select v-model="transferTo" style="width: 100%" :placeholder="t('workflow.selectHandler')">
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.displayName + '(' + u.username + ')'"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('workflow.opinion')">
          <el-input v-model="opinion" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="warning" :loading="acting" @click="submitTransfer">{{ t('workflow.confirmTransfer') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="delegateVisible" :title="t('workflow.delegateTask')" width="460px">
      <el-form label-width="90px">
        <el-form-item :label="t('common.task')">
          <span>{{ current?.name }}（{{ current?.taskId }}）</span>
        </el-form-item>
        <el-form-item :label="t('workflow.delegateTo')">
          <el-select v-model="delegateTo" style="width: 100%" :placeholder="t('workflow.selectDelegatee')">
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.displayName + '(' + u.username + ')'"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('workflow.opinion')">
          <el-input v-model="opinion" type="textarea" :rows="2" :placeholder="t('workflow.delegateNote')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="delegateVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="acting" @click="submitDelegate">{{ t('workflow.confirmDelegate') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resolveVisible" :title="t('workflow.completeAndReturn')" width="460px">
      <el-form label-width="90px">
        <el-form-item :label="t('common.task')">
          <span>{{ current?.name }}（{{ current?.taskId }}）</span>
        </el-form-item>
        <el-form-item :label="t('workflow.handlingOpinion')">
          <el-input v-model="opinion" type="textarea" :rows="3" :placeholder="t('workflow.completionOpinion')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="success" :loading="acting" @click="submitResolve">{{ t('workflow.confirmReturn') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Select, Close, Share, Refresh, Switch, Promotion } from '@element-plus/icons-vue'
import { myTasks, completeTask, transferTask, delegateTask, resolveDelegateTask } from '@/api/workflow'
import { pageUsers } from '@/api/user'

function genOpId() {
  return 'op-' + Date.now() + '-' + Math.floor(Math.random() * 1000)
}

const loading = ref(false)
const rows = ref([])
const userOptions = ref([])
const approveVisible = ref(false)
const transferVisible = ref(false)
const delegateVisible = ref(false)
const resolveVisible = ref(false)
const rejectMode = ref(false)
const acting = ref(false)
const current = ref(null)
const opinion = ref('')
const transferTo = ref(null)
const delegateTo = ref(null)

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
    ElMessage.success(rejectMode.value ? t('common.rejected') : t('common.approved'))
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
    ElMessage.warning(t('workflow.pleaseSelectTransfer'))
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
    ElMessage.success(t('workflow.transferred'))
    transferVisible.value = false
    load()
  } finally {
    acting.value = false
  }
}

function openDelegate(row) {
  current.value = row
  opinion.value = ''
  delegateTo.value = null
  delegateVisible.value = true
}
async function submitDelegate() {
  if (!delegateTo.value) {
    ElMessage.warning(t('workflow.pleaseSelectDelegatee'))
    return
  }
  acting.value = true
  try {
    await delegateTask({
      taskId: current.value.taskId,
      toUserId: delegateTo.value,
      opinion: opinion.value,
      operationId: genOpId()
    })
    ElMessage.success(t('workflow.delegated'))
    delegateVisible.value = false
    load()
  } finally {
    acting.value = false
  }
}

function openResolve(row) {
  current.value = row
  opinion.value = ''
  resolveVisible.value = true
}
async function submitResolve() {
  acting.value = true
  try {
    await resolveDelegateTask({
      taskId: current.value.taskId,
      opinion: opinion.value,
      operationId: genOpId()
    })
    ElMessage.success(t('workflow.returnedToApprover'))
    resolveVisible.value = false
    load()
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>
