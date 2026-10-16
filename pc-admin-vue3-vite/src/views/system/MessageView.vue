<template>
  <div>
    <h2 v-if="!embedded" class="page-title">
      站内信
      <el-badge :value="unread" :hidden="unread === 0" type="danger" class="unread-badge">
        <span class="unread-label">未读 {{ unread }}</span>
      </el-badge>
    </h2>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- 收件箱 -->
      <el-tab-pane label="我的消息" name="mine">
        <el-card class="page-card">
          <div class="toolbar">
            <div class="toolbar-filters">
              <div class="filter-group">
                <el-checkbox v-model="onlyUnread" @change="onSearch">仅看未读</el-checkbox>
              </div>
            </div>
            <div class="toolbar-actions">
              <div class="action-buttons">
                <el-button :icon="Select" @click="onReadAll">全部标记已读</el-button>
              </div>
              <div class="action-buttons">
                <el-button type="primary" :icon="Promotion" @click="openSend">发送站内信</el-button>
              </div>
            </div>
          </div>

          <el-table :data="list" v-loading="loading" border stripe>
            <el-table-column label="标题" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <el-badge :is-dot="!row.isRead" class="dot-badge">
                  <a @click="openDetail(row)">{{ row.title }}</a>
                </el-badge>
              </template>
            </el-table-column>
            <el-table-column prop="msgType" label="类型" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.msgType === 'ALERT' ? 'danger' : 'info'">
                  {{ typeText(row.msgType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="senderName" label="发送人" width="120" />
            <el-table-column prop="sentAt" label="发送时间" width="160" />
            <el-table-column label="状态" width="150">
              <template #default="{ row }">
                <el-tag :type="row.isRead ? 'success' : 'warning'" size="small">
                  {{ row.isRead ? '已读' : '未读' }}
                </el-tag>
                <div v-if="row.isRead && row.readAt" class="read-at">回执 {{ row.readAt }}</div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-if="!row.isRead" link type="primary" @click="onRead(row)">标记已读</el-button>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页统一到全局形态：<div class="pager"> 包裹 + v-model + current/size 事件 + jumper -->
          <div class="pager">
            <el-pagination
              v-model:current-page="query.page"
              v-model:page-size="query.size"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="load"
              @size-change="onSizeChange"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 我发出的 -->
      <el-tab-pane label="我发出的" name="sent">
        <el-card class="page-card">
          <el-table :data="sentList" v-loading="sentLoading" border stripe>
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="msgType" label="类型" width="90">
              <template #default="{ row }">{{ typeText(row.msgType) }}</template>
            </el-table-column>
            <el-table-column prop="totalCount" label="接收人" width="90" />
            <el-table-column label="已读/未读" width="140">
              <template #default="{ row }">
                <el-progress
                  :percentage="readPercent(row)"
                  :stroke-width="12"
                  :format="() => `${row.readCount}/${row.totalCount}`"
                />
              </template>
            </el-table-column>
            <el-table-column prop="sentAt" label="发送时间" width="160" />
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="sentQuery.page"
              v-model:page-size="sentQuery.size"
              :total="sentTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="loadSent"
              @size-change="onSentSizeChange"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 发送弹窗：单条 / 批量 -->
    <el-dialog v-model="sendVisible" title="发送站内信" width="640px">
      <el-form :model="sendForm" :rules="sendRules" ref="sendFormRef" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="sendForm.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="sendForm.content" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="sendForm.msgType">
            <el-radio label="NOTICE">通知</el-radio>
            <el-radio label="ALERT">告警</el-radio>
            <el-radio label="SYSTEM">系统</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发送方式">
          <el-radio-group v-model="sendMode">
            <el-radio label="batch">按角色/部门批量</el-radio>
            <el-radio label="single">指定接收人</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="sendMode === 'batch'">
          <el-form-item label="按角色">
            <el-select v-model="sendForm.roleIds" multiple placeholder="选择角色（与部门为或关系）" style="width: 100%">
              <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="按部门">
            <el-select v-model="sendForm.orgIds" multiple placeholder="选择部门（与角色为或关系）" style="width: 100%">
              <el-option v-for="o in orgFlatOptions" :key="o.id" :label="o.orgName" :value="o.id" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item v-else label="接收人">
          <el-select v-model="sendForm.receiverIds" multiple filterable placeholder="选择接收人" style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="`${u.displayName}(${u.username})`" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendVisible = false">取消</el-button>
        <el-button type="primary" @click="onSend">发送</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="消息详情" width="640px">
      <h3 class="detail-title">{{ current?.title }}</h3>
      <div class="detail-meta">
        <span class="meta-item">发送人：{{ current?.senderName || '系统' }}</span>
        <span class="meta-item">时间：{{ current?.sentAt }}</span>
        <el-tag v-if="current?.isRead" type="success" size="small">已读 {{ current?.readAt }}</el-tag>
      </div>
      <el-divider />
      <div class="detail-content">{{ current?.content }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, Select } from '@element-plus/icons-vue'
import {
  sendMessage,
  pageMyMessages,
  pageSentMessages,
  getUnreadCount,
  markMessageRead,
  markAllMessagesRead
} from '@/api/message'

// 嵌入「消息中心」时由外层 Tab 提供标题，此处隐藏页内标题避免重复
defineProps({ embedded: { type: Boolean, default: false } })
import { listRoles } from '@/api/role'
import { orgTree } from '@/api/org'
import { pageUsers } from '@/api/user'
// 部门树拍平统一到 utils/tree
import { flattenTree } from '@/utils/tree'

const activeTab = ref('mine')
const loading = ref(false)
const list = ref([])
const total = ref(0)
const unread = ref(0)
const onlyUnread = ref(false)
// 默认每页条数统一为 10（与其他列表页一致）
const query = reactive({ page: 1, size: 10 })

const sentLoading = ref(false)
const sentList = ref([])
const sentTotal = ref(0)
const sentQuery = reactive({ page: 1, size: 10 })

const sendVisible = ref(false)
const sendFormRef = ref()
const sendMode = ref('batch')
const sendForm = reactive({
  title: '', content: '', msgType: 'NOTICE', receiverIds: [], roleIds: [], orgIds: []
})
const sendRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

const detailVisible = ref(false)
const current = ref(null)

const roleOptions = ref([])
const orgFlatOptions = ref([])
const userOptions = ref([])

const typeText = (t) => ({ NOTICE: '通知', ALERT: '告警', SYSTEM: '系统' }[t] || t)

const readPercent = (row) =>
  row.totalCount ? Math.round((row.readCount / row.totalCount) * 100) : 0

async function load() {
  loading.value = true
  try {
    const data = await pageMyMessages({ page: query.page, size: query.size, onlyUnread: onlyUnread.value })
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    // 失败静默：http 拦截器已统一提示，此处只避免 Uncaught (in promise)
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
  loadUnread()
}

/** 未读数为展示增强：失败静默降级，保留上一次计数，不打扰消息列表。 */
async function loadUnread() {
  try {
    const data = await getUnreadCount()
    unread.value = data?.unread || 0
  } catch (e) {
    // 静默降级：不改动 unread
  }
}

async function loadSent() {
  sentLoading.value = true
  try {
    const data = await pageSentMessages({ page: sentQuery.page, size: sentQuery.size })
    sentList.value = data.records || []
    sentTotal.value = data.total || 0
  } catch (e) {
    sentList.value = []
    sentTotal.value = 0
  } finally {
    sentLoading.value = false
  }
}

/** 每页条数变化后回到第一页。 */
function onSizeChange(size) {
  query.size = size
  query.page = 1
  load()
}

function onSentSizeChange(size) {
  sentQuery.size = size
  sentQuery.page = 1
  loadSent()
}

function onTabChange(name) {
  if (name === 'sent') loadSent()
  else load()
}

function onSearch() {
  query.page = 1
  load()
}

async function onRead(row) {
  try {
    await markMessageRead(row.messageId)
    ElMessage.success('已标记为已读')
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onReadAll() {
  try {
    const res = await markAllMessagesRead()
    ElMessage.success(`已标记 ${res?.updated ?? 0} 条为已读`)
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function openDetail(row) {
  current.value = row
  detailVisible.value = true
  // 打开详情即视为已读，回执时间在此落库
  if (!row.isRead) {
    try {
      await markMessageRead(row.messageId)
      load()
    } catch (e) {
      // 回执失败不影响详情阅读，拦截器已提示
    }
  }
}

async function loadOptions() {
  try {
    const [roles, tree, users] = await Promise.all([
      listRoles(),
      orgTree(),
      pageUsers({ page: 1, size: 200 })
    ])
    roleOptions.value = roles || []
    orgFlatOptions.value = flattenTree(tree, { nameKey: 'orgName', labelKey: 'orgName' })
    userOptions.value = users?.records || []
  } catch (e) {
    console.warn('加载收件人选项失败', e)
  }
}

function openSend() {
  Object.assign(sendForm, {
    title: '', content: '', msgType: 'NOTICE', receiverIds: [], roleIds: [], orgIds: []
  })
  sendMode.value = 'batch'
  sendVisible.value = true
  sendFormRef.value?.clearValidate()
  loadOptions()
}

async function onSend() {
  // 校验失败时 validate() 会 reject，须捕获避免 Uncaught (in promise)
  const valid = await sendFormRef.value.validate().catch(() => false)
  if (!valid) return
  // 校验：批量模式至少选一个角色或部门；单条模式至少选一个接收人
  if (sendMode.value === 'batch' && sendForm.roleIds.length === 0 && sendForm.orgIds.length === 0) {
    ElMessage.warning('请至少选择一个角色或一个部门')
    return
  }
  if (sendMode.value === 'single' && sendForm.receiverIds.length === 0) {
    ElMessage.warning('请至少选择一位接收人')
    return
  }

  const body =
    sendMode.value === 'batch'
      ? { title: sendForm.title, content: sendForm.content, msgType: sendForm.msgType,
          roleIds: sendForm.roleIds, orgIds: sendForm.orgIds }
      : { title: sendForm.title, content: sendForm.content, msgType: sendForm.msgType,
          receiverIds: sendForm.receiverIds }

  try {
    const res = await sendMessage(body)
    ElMessage.success(`已发送给 ${res?.receiverCount ?? 0} 人`)
    sendVisible.value = false
    activeTab.value = 'sent'
    loadSent()
  } catch (e) {
    // 拦截器已提示；保持弹窗开启便于修正后重发
  }
}

onMounted(load)
</script>

<style scoped>
.unread-badge {
  margin-left: 12px;
  vertical-align: middle;
}
.unread-label {
  font-size: 13px;
  font-weight: normal;
  color: #909399;
}
.dot-badge {
  margin-right: 4px;
}
.read-at {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
/* .text-muted 已统一到全局 styles.css */
.detail-title {
  margin: 0 0 8px;
  font-size: 18px;
}
.detail-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  color: #909399;
  font-size: 13px;
}
.detail-content {
  white-space: pre-wrap;
  line-height: 1.7;
  color: #303133;
}
</style>
