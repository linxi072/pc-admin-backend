<template>
  <div>
    <h2 class="page-title">{{ t('route.imMessage') }}</h2>

    <el-card class="page-card" shadow="never">
      <div class="im-layout">
        <!-- 左侧：会话列表 -->
        <div class="conv-list">
          <div class="conv-header">
            <span>{{ t('im.message.myConversations') }}</span>
            <el-badge :value="totalUnread" :hidden="!totalUnread" type="danger">
              <el-button link :icon="Refresh" @click="loadConversations" />
            </el-badge>
          </div>
          <el-scrollbar height="480px">
            <div
              v-for="conv in conversations"
              :key="conv.id"
              class="conv-item"
              :class="{ active: conv.id === activeConversationId }"
              @click="selectConversation(conv)"
            >
              <div class="conv-item-main">
                <div class="conv-item-title">
                  <el-tag size="small" :type="conv.conversationType === 'GROUP' ? 'warning' : 'primary'">
                    {{ conv.conversationType === 'GROUP' ? t('im.type.group') : t('im.type.p2p') }}
                  </el-tag>
                  <span class="title-text">{{ conv.title || t('im.conversation.unnamed') }}</span>
                </div>
                <div class="conv-item-preview">{{ conv.lastMessagePreview || t('im.message.noMessages') }}</div>
              </div>
              <div class="conv-item-side">
                <span class="conv-item-time">{{ shortTime(conv.lastMessageAt) }}</span>
                <el-badge v-if="conv.unreadCount" :value="conv.unreadCount" type="danger" />
              </div>
            </div>
            <el-empty v-if="!loadingConversations && conversations.length === 0" :description="t('im.message.noConversations')" />
          </el-scrollbar>
        </div>

        <el-divider direction="vertical" class="layout-divider" />

        <!-- 右侧：消息区 -->
        <div class="msg-panel">
          <template v-if="activeConversationId">
            <div class="msg-header">
              <span>{{ activeTitle }}</span>
              <el-pagination
                v-model:current-page="msgPage"
                :page-size="msgSize"
                :total="msgTotal"
                layout="prev, pager, next"
                small
                @current-change="loadMessages"
              />
            </div>
            <el-scrollbar ref="msgScrollbar" height="380px" class="msg-scroll">
              <div class="msg-list" v-loading="loadingMessages">
                <div
                  v-for="msg in messages"
                  :key="msg.id"
                  class="msg-row"
                  :class="{ mine: isMine(msg) }"
                >
                  <div class="msg-bubble-wrap">
                    <div class="msg-meta">
                      <span>{{ t('im.message.sender') }} #{{ msg.senderId }}</span>
                      <span>{{ formatTime(msg.createdAt) }}</span>
                    </div>
                    <div v-if="isRecalled(msg)" class="msg-recalled">{{ t('im.message.recalled') }}</div>
                    <div v-else class="msg-bubble">{{ msg.content }}</div>
                    <el-button
                      v-if="isMine(msg) && !isRecalled(msg)"
                      link
                      type="danger"
                      size="small"
                      class="msg-recall"
                      @click="onRecall(msg)"
                    >
                      {{ t('im.message.recall') }}
                    </el-button>
                  </div>
                </div>
                <el-empty v-if="!loadingMessages && messages.length === 0" :description="t('im.message.noMessages')" />
              </div>
            </el-scrollbar>
            <div class="msg-input">
              <el-input
                v-model="draft"
                type="textarea"
                :rows="2"
                :placeholder="t('im.message.inputPlaceholder')"
                maxlength="4000"
                show-word-limit
                @keydown.enter.exact.prevent="onSend"
              />
              <el-button type="primary" :loading="sending" @click="onSend">{{ t('im.message.send') }}</el-button>
            </div>
          </template>
          <el-empty v-else :description="t('im.message.pickConversation')" class="msg-empty" />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, computed, nextTick, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  listMyConversations,
  listConversationMessages,
  sendMessage,
  recallMessage,
  markConversationRead,
  unreadCount
} from '@/api/im'
import { getProfile } from '@/api/profile'

const route = useRoute()
const loadingConversations = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)
const conversations = ref([])
const messages = ref([])
const activeConversationId = ref(null)
const myUserId = ref(null)
const msgPage = ref(1)
const msgSize = ref(30)
const msgTotal = ref(0)
const draft = ref('')
const msgScrollbar = ref()

const totalUnread = computed(() =>
  conversations.value.reduce((sum, conv) => sum + (conv.unreadCount || 0), 0)
)
const activeTitle = computed(() => {
  const conv = conversations.value.find((c) => c.id === activeConversationId.value)
  return conv ? conv.title || t('im.conversation.unnamed') : ''
})

function isMine(msg) {
  return myUserId.value != null && Number(msg.senderId) === Number(myUserId.value)
}
function isRecalled(msg) {
  return msg.status === 'RECALLED' || msg.content == null
}
function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}
function shortTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(5, 16)
}

async function loadMyId() {
  try {
    const profile = await getProfile()
    myUserId.value = profile?.id ?? null
  } catch {
    myUserId.value = null
  }
}

async function loadConversations() {
  loadingConversations.value = true
  try {
    const data = await listMyConversations()
    conversations.value = Array.isArray(data) ? data : []
  } finally {
    loadingConversations.value = false
  }
}

async function selectConversation(conv) {
  activeConversationId.value = conv.id
  msgPage.value = 1
  await loadMessages()
  // 打开会话即标记已读，并同步列表未读数
  try {
    await markConversationRead(conv.id)
    conv.unreadCount = 0
  } catch {
    // 已读失败不阻断浏览
  }
}

async function loadMessages() {
  if (!activeConversationId.value) return
  loadingMessages.value = true
  try {
    const data = await listConversationMessages(activeConversationId.value, {
      page: msgPage.value,
      size: msgSize.value
    })
    messages.value = data.records || []
    msgTotal.value = data.total ?? 0
    await nextTick()
    // 消息按时间正序，滚动到底部展示最新
    msgScrollbar.value?.setScrollTop(999999)
  } finally {
    loadingMessages.value = false
  }
}

async function onSend() {
  const content = draft.value.trim()
  if (!content) return
  sending.value = true
  try {
    await sendMessage({ conversationId: activeConversationId.value, content, msgType: 'TEXT' })
    draft.value = ''
    await loadMessages()
    await loadConversations()
  } finally {
    sending.value = false
  }
}

async function onRecall(msg) {
  const confirmed = await ElMessageBox.confirm(t('im.message.recallConfirm'), t('common.tip'), {
    type: 'warning',
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel')
  }).catch(() => false)
  if (!confirmed) return
  await recallMessage(msg.id)
  ElMessage.success(t('im.message.recallSuccess'))
  await loadMessages()
}

onMounted(async () => {
  await Promise.all([loadMyId(), loadConversations()])
  // 支持从会话管理页跳转定位：/im/message?conversationId=x
  const targetId = Number(route.query.conversationId)
  if (targetId) {
    const conv = conversations.value.find((c) => Number(c.id) === targetId)
    if (conv) await selectConversation(conv)
  }
  // 顶部总未读（失败静默，不影响主流程）
  unreadCount().catch(() => {})
})
</script>

<style scoped>
.im-layout {
  display: flex;
  min-height: 520px;
}
.conv-list {
  width: 320px;
  flex-shrink: 0;
}
.conv-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  margin-bottom: 8px;
}
.conv-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}
.conv-item:hover {
  background: var(--el-fill-color-light);
}
.conv-item.active {
  background: var(--el-color-primary-light-9);
}
.conv-item-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.title-text {
  font-weight: 500;
}
.conv-item-preview {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-item-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
}
.conv-item-time {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
.layout-divider {
  height: auto;
  margin: 0 16px;
}
.msg-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.msg-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  margin-bottom: 8px;
}
.msg-list {
  padding: 8px 12px;
}
.msg-row {
  display: flex;
  margin-bottom: 14px;
}
.msg-row.mine {
  justify-content: flex-end;
}
.msg-bubble-wrap {
  max-width: 70%;
}
.msg-meta {
  display: flex;
  gap: 10px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  margin-bottom: 4px;
}
.msg-row.mine .msg-meta {
  justify-content: flex-end;
}
.msg-bubble {
  background: var(--el-fill-color);
  border-radius: 8px;
  padding: 8px 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-row.mine .msg-bubble {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.msg-recalled {
  color: var(--el-text-color-placeholder);
  font-style: italic;
  font-size: 12px;
}
.msg-recall {
  margin-top: 2px;
}
.msg-row.mine .msg-recall {
  display: flex;
  justify-content: flex-end;
}
.msg-input {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  margin-top: 10px;
}
.msg-empty {
  margin: auto;
}
</style>
