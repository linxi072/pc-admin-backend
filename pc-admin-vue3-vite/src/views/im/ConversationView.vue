<template>
  <div>
    <h2 class="page-title">{{ t('route.imConversation') }}</h2>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="keyword"
            :placeholder="t('im.conversation.keywordPlaceholder')"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="filterType" :placeholder="t('im.conversation.typePlaceholder')" clearable style="width: 140px">
            <el-option :label="t('im.type.p2p')" value="P2P" />
            <el-option :label="t('im.type.group')" value="GROUP" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="onSearch">{{ t('common.search') }}</el-button>
        </div>
        <div class="actions">
          <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('im.conversation.create') }}</el-button>
          <el-button :icon="Refresh" @click="load">{{ t('common.refresh') }}</el-button>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column :label="t('im.conversation.type')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.conversationType === 'GROUP' ? 'warning' : 'primary'">
              {{ row.conversationType === 'GROUP' ? t('im.type.group') : t('im.type.p2p') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" :label="t('im.conversation.title')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.title || t('im.conversation.unnamed') }}</template>
        </el-table-column>
        <el-table-column prop="memberCount" :label="t('im.conversation.memberCount')" width="90" align="center" />
        <el-table-column prop="unreadCount" :label="t('im.conversation.unread')" width="90" align="center">
          <template #default="{ row }">
            <el-badge :value="row.unreadCount" :hidden="!row.unreadCount" type="danger">
              <span>{{ row.unreadCount ?? 0 }}</span>
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column prop="lastMessagePreview" :label="t('im.conversation.lastMessage')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="lastMessageAt" :label="t('im.conversation.lastMessageAt')" width="170">
          <template #default="{ row }">{{ formatTime(row.lastMessageAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.action')" width="270" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="ChatDotRound" @click="goMessage(row)">{{ t('im.conversation.goMessage') }}</el-button>
            <el-button link type="primary" :icon="Plus" @click="openAddMember(row)">{{ t('im.conversation.addMember') }}</el-button>
            <el-button link type="warning" @click="onQuit(row)">{{ t('im.conversation.quit') }}</el-button>
            <el-button link type="danger" :icon="Delete" @click="onDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建会话弹窗 -->
    <el-dialog v-model="createVisible" :title="t('im.conversation.create')" width="520px" destroy-on-close>
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="90px">
        <el-form-item :label="t('im.conversation.type')" prop="conversationType">
          <el-radio-group v-model="createForm.conversationType">
            <el-radio value="P2P">{{ t('im.type.p2p') }}</el-radio>
            <el-radio value="GROUP">{{ t('im.type.group') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('im.conversation.title')" prop="title">
          <el-input v-model="createForm.title" :placeholder="t('im.conversation.titlePlaceholder')" maxlength="255" />
        </el-form-item>
        <el-form-item :label="t('im.conversation.members')" prop="memberIds">
          <el-input
            v-model="memberIdsText"
            type="textarea"
            :rows="2"
            :placeholder="t('im.conversation.membersPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="onCreateSubmit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 添加成员弹窗 -->
    <el-dialog v-model="addMemberVisible" :title="t('im.conversation.addMember')" width="420px" destroy-on-close>
      <el-form :model="addMemberForm" :rules="addMemberRules" ref="addMemberFormRef" label-width="90px">
        <el-form-item :label="t('im.conversation.memberUserId')" prop="userId">
          <el-input-number v-model="addMemberForm.userId" :min="1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addMemberVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="onAddMemberSubmit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
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
  conversationType: [{ required: true, message: t('im.conversation.typeRequired'), trigger: 'change' }],
  title: [{ required: true, message: t('im.conversation.titleRequired'), trigger: 'blur' }]
}
const addMemberRules = {
  userId: [{ required: true, message: t('im.conversation.userIdRequired'), trigger: 'blur' }]
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
      ElMessage.warning(t('im.conversation.membersRequired'))
      return
    }
    if (createForm.conversationType === 'P2P' && memberIds.length !== 1) {
      ElMessage.warning(t('im.conversation.p2pSingleMember'))
      return
    }
    submitting.value = true
    try {
      await createConversation({
        conversationType: createForm.conversationType,
        title: createForm.title,
        memberIds
      })
      ElMessage.success(t('common.createSuccess'))
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
      ElMessage.success(t('im.conversation.memberAdded'))
      addMemberVisible.value = false
      await load()
    } finally {
      submitting.value = false
    }
  })
}

async function onQuit(row) {
  const confirmed = await ElMessageBox.confirm(t('im.conversation.quitConfirm'), t('common.warning'), {
    type: 'warning',
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel')
  }).catch(() => false)
  if (!confirmed) return
  await quitConversation(row.id)
  ElMessage.success(t('im.conversation.quitSuccess'))
  await load()
}

async function onDelete(row) {
  const confirmed = await ElMessageBox.confirm(t('im.conversation.deleteConfirm'), t('common.warning'), {
    type: 'warning',
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel')
  }).catch(() => false)
  if (!confirmed) return
  await deleteConversation(row.id)
  ElMessage.success(t('common.deleteSuccess'))
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
