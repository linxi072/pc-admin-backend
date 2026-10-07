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
            <el-checkbox v-model="onlyUnread" @change="onSearch">仅看未读</el-checkbox>
            <span class="spacer" />
            <el-button :icon="Select" @click="onReadAll">全部标记已读</el-button>
            <el-button type="primary" :icon="Promotion" @click="openSend">发送站内信</el-button>
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

          <el-pagination
            class="pager"
            layout="total, sizes, prev, pager, next"
            :total="total"
            v-model:current-page="query.page"
            v-model:page-size="query.size"
            :page-sizes="[10, 20, 50]"
            @change="load"
          />
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

          <el-pagination
            class="pager"
            layout="total, prev, pager, next"
            :total="sentTotal"
            v-model:current-page="sentQuery.page"
            @change="loadSent"
          />
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
} from '../../api/message'

// 嵌入「消息中心」时由外层 Tab 提供标题，此处隐藏页内标题避免重复
defineProps({ embedded: { type: Boolean, default: false } })
import { listRoles } from '../../api/role'
import { orgTree } from '../../api/org'
import { pageUsers } from '../../api/user'

const activeTab = ref('mine')
const loading = ref(false)
const list = ref([])
const total = ref(0)
const unread = ref(0)
const onlyUnread = ref(false)
const query = reactive({ page: 1, size: 20 })

const sentLoading = ref(false)
const sentList = ref([])
const sentTotal = ref(0)
const sentQuery = reactive({ page: 1, size: 20 })

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

/** 拍平部门树为下拉选项（带缩进前缀）。 */
function flattenOrgs(nodes, depth = 0, acc = []) {
  ;(nodes || []).forEach((n) => {
    acc.push({ id: n.id, orgName: `${'　'.repeat(depth)}${n.orgName}` })
    if (n.children) flattenOrgs(n.children, depth + 1, acc)
  })
  return acc
}

async function load() {
  loading.value = true
  try {
    const data = await pageMyMessages({ page: query.page, size: query.size, onlyUnread: onlyUnread.value })
    list.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
  loadUnread()
}

async function loadUnread() {
  const data = await getUnreadCount()
  unread.value = data?.unread || 0
}

async function loadSent() {
  sentLoading.value = true
  try {
    const data = await pageSentMessages({ page: sentQuery.page, size: sentQuery.size })
    sentList.value = data.records || []
    sentTotal.value = data.total || 0
  } finally {
    sentLoading.value = false
  }
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
  await markMessageRead(row.messageId)
  ElMessage.success('已标记为已读')
  load()
}

async function onReadAll() {
  const res = await markAllMessagesRead()
  ElMessage.success(`已标记 ${res?.updated ?? 0} 条为已读`)
  load()
}

async function openDetail(row) {
  current.value = row
  detailVisible.value = true
  // 打开详情即视为已读，回执时间在此落库
  if (!row.isRead) {
    await markMessageRead(row.messageId)
    load()
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
    orgFlatOptions.value = flattenOrgs(tree)
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
  await sendFormRef.value.validate()
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

  const res = await sendMessage(body)
  ElMessage.success(`已发送给 ${res?.receiverCount ?? 0} 人`)
  sendVisible.value = false
  activeTab.value = 'sent'
  loadSent()
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
.text-muted {
  color: #c0c4cc;
}
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
