<template>
  <div>
    <h2 class="page-title">{{ 'IM 会话管理' }}</h2>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="keyword"
            :placeholder="'按会话标题/最近消息搜索'"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="filterType" :placeholder="'会话类型'" clearable style="width: 140px">
            <el-option :label="'单聊'" value="P2P" />
            <el-option :label="'群聊'" value="GROUP" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="onSearch">{{ '搜索' }}</el-button>
        </div>
        <div class="actions">
          <el-button type="primary" :icon="Plus" @click="openCreate">{{ '创建会话' }}</el-button>
          <el-button :icon="Refresh" @click="load">{{ '刷新' }}</el-button>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column :label="'类型'" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.conversationType === 'GROUP' ? 'warning' : 'primary'">
              {{ row.conversationType === 'GROUP' ? '群聊' : '单聊' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" :label="'会话标题'" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.title || '（未命名会话）' }}</template>
        </el-table-column>
        <el-table-column prop="memberCount" :label="'成员数'" width="90" align="center" />
        <el-table-column prop="unreadCount" :label="'未读'" width="90" align="center">
          <template #default="{ row }">
            <el-badge :value="row.unreadCount" :hidden="!row.unreadCount" type="danger">
              <span>{{ row.unreadCount ?? 0 }}</span>
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column prop="lastMessagePreview" :label="'最近消息'" min-width="200" show-overflow-tooltip />
        <el-table-column prop="lastMessageAt" :label="'最近消息时间'" width="170">
          <template #default="{ row }">{{ formatTime(row.lastMessageAt) }}</template>
        </el-table-column>
        <el-table-column :label="'操作'" width="270" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="ChatDotRound" @click="goMessage(row)">{{ '发消息' }}</el-button>
            <el-button link type="primary" :icon="Plus" @click="openAddMember(row)">{{ '加成员' }}</el-button>
            <el-button link type="warning" @click="onQuit(row)">{{ '退出' }}</el-button>
            <el-button link type="danger" :icon="Delete" @click="onDelete(row)">{{ '删除' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建会话弹窗 -->
    <el-dialog v-model="createVisible" :title="'创建会话'" width="520px" destroy-on-close>
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="90px">
        <el-form-item :label="'类型'" prop="conversationType">
          <el-radio-group v-model="createForm.conversationType">
            <el-radio value="P2P">{{ '单聊' }}</el-radio>
            <el-radio value="GROUP">{{ '群聊' }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="'会话标题'" prop="title">
          <el-input v-model="createForm.title" :placeholder="'请输入会话标题'" maxlength="255" />
        </el-form-item>
        <el-form-item :label="'对方成员'" prop="memberIds">
          <el-input
            v-model="memberIdsText"
            type="textarea"
            :rows="2"
            :placeholder="'输入用户 ID，逗号分隔（单聊仅 1 人，群聊至少 1 人）'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ '取消' }}</el-button>
        <el-button type="primary" :loading="submitting" @click="onCreateSubmit">{{ '确认' }}</el-button>
      </template>
    </el-dialog>

    <!-- 添加成员弹窗 -->
    <el-dialog v-model="addMemberVisible" :title="'加成员'" width="420px" destroy-on-close>
      <el-form :model="addMemberForm" :rules="addMemberRules" ref="addMemberFormRef" label-width="90px">
        <el-form-item :label="'成员 ID'" prop="userId">
          <el-input-number v-model="addMemberForm.userId" :min="1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addMemberVisible = false">{{ '取消' }}</el-button>
        <el-button type="primary" :loading="submitting" @click="onAddMemberSubmit">{{ '确认' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>

import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Refresh, Delete, ChatDotRound } from '@element-plus/icons-vue'
import {
  listMyConversations,
  createConversation,
  addMember,
  quitConversation,
  deleteConversation
} from '@/api/im'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const keyword = ref('')
const filterType = ref('')
const allRows = ref([])
const createVisible = ref(false)
const addMemberVisible = ref(false)
const createFormRef = ref()
const addMemberFormRef = ref()

const createForm = reactive({
  conversationType: 'P2P',
  title: '',
  memberIds: []
})
const memberIdsText = ref('')

const addMemberForm = reactive({ conversationId: null, userId: 1 })

const createRules = {
  conversationType: [{ required: true, message: '请选择会话类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入会话标题', trigger: 'blur' }]
}
const addMemberRules = {
  userId: [{ required: true, message: '请输入成员用户 ID', trigger: 'blur' }]
}

// 关键字 + 类型过滤（前端过滤，/my 返回全量会话）
const rows = computed(() =>
  allRows.value.filter((row) => {
    if (filterType.value && row.conversationType !== filterType.value) return false
    const kw = keyword.value.trim().toLowerCase()
    if (!kw) return true
    return (
      (row.title || '').toLowerCase().includes(kw) ||
      (row.lastMessagePreview || '').toLowerCase().includes(kw)
    )
  })
)

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

async function load() {
  loading.value = true
  try {
    const data = await listMyConversations()
    allRows.value = Array.isArray(data) ? data : []
  } finally {
    loading.value = false
  }
}

function onSearch() {
  // rows 为 computed 前端过滤，输入变化即自动重算，无需重新请求
}

function openCreate() {
  createForm.conversationType = 'P2P'
  createForm.title = ''
  createForm.memberIds = []
  memberIdsText.value = ''
  createVisible.value = true
}

function parseMemberIds(text) {
  return String(text || '')
    .split(/[,，\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map(Number)
    .filter((n) => Number.isFinite(n) && n > 0)
}

function onCreateSubmit() {
  createFormRef.value?.validate(async (valid) => {
    if (!valid) return
    const memberIds = parseMemberIds(memberIdsText.value)
    if (memberIds.length === 0) {
      ElMessage.warning('请至少输入一个成员 ID')
      return
    }
    if (createForm.conversationType === 'P2P' && memberIds.length !== 1) {
      ElMessage.warning('单聊仅允许 1 名对方成员')
      return
    }
    submitting.value = true
    try {
      await createConversation({
        conversationType: createForm.conversationType,
        title: createForm.title,
        memberIds
      })
      ElMessage.success('创建成功')
      createVisible.value = false
      await load()
    } finally {
      submitting.value = false
    }
  })
}

function goMessage(row) {
  router.push({ path: '/im/message', query: { conversationId: row.id } })
}

function openAddMember(row) {
  addMemberForm.conversationId = row.id
  addMemberForm.userId = 1
  addMemberVisible.value = true
}

function onAddMemberSubmit() {
  addMemberFormRef.value?.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await addMember(addMemberForm.conversationId, addMemberForm.userId)
      ElMessage.success('成员已添加')
      addMemberVisible.value = false
      await load()
    } finally {
      submitting.value = false
    }
  })
}

async function onQuit(row) {
  const confirmed = await ElMessageBox.confirm('确认退出该会话吗？退出后将不再接收该会话消息。', '警告', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  }).catch(() => false)
  if (!confirmed) return
  await quitConversation(row.id)
  ElMessage.success('已退出会话')
  await load()
}

async function onDelete(row) {
  const confirmed = await ElMessageBox.confirm('确认删除该会话吗？仅群主可删除，删除后成员不可见。', '警告', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  }).catch(() => false)
  if (!confirmed) return
  await deleteConversation(row.id)
  ElMessage.success('删除成功')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.filters {
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>
