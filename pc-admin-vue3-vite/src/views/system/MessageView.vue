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
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="info" :icon="Reading" @click="openReceipts(row)">已读回执</el-button>
              </template>
            </el-table-column>
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

    <!-- 发送弹窗：渠道（站内信 / 邮件 / 短信）× 单条 / 批量 -->
    <el-dialog v-model="sendVisible" :title="sendDialogTitle" width="640px">
      <el-form :model="sendForm" :rules="sendRules" ref="sendFormRef" label-width="90px">
        <el-form-item label="通知渠道">
          <el-radio-group v-model="sendChannel">
            <el-radio label="IN_APP">站内信</el-radio>
            <el-radio label="EMAIL">邮件</el-radio>
            <el-radio label="SMS">短信</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 站内信：既有完整形态不变 -->
        <template v-if="sendChannel === 'IN_APP'">
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
        </template>

        <!-- 邮件：主题 + 正文 + 收件人地址列表 -->
        <template v-if="sendChannel === 'EMAIL'">
          <el-form-item label="邮件主题" prop="title">
            <el-input v-model="sendForm.title" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="邮件正文" prop="content">
            <el-input v-model="sendForm.content" type="textarea" :rows="5" />
          </el-form-item>
          <el-form-item label="收件人" prop="emails">
            <el-input
              v-model="sendForm.emails"
              type="textarea"
              :rows="3"
              placeholder="每行一个邮箱，或用逗号 / 分号分隔（单封上限 50）"
            />
          </el-form-item>
        </template>

        <!-- 短信：正文 + 接收手机号列表（无主题；渠道未启用时后端返回校验错误） -->
        <template v-if="sendChannel === 'SMS'">
          <el-form-item label="短信内容" prop="content">
            <el-input v-model="sendForm.content" type="textarea" :rows="4" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item label="接收手机" prop="mobiles">
            <el-input
              v-model="sendForm.mobiles"
              type="textarea"
              :rows="3"
              placeholder="每行一个手机号，或用逗号 / 分号分隔（单次上限 100）"
            />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="sendVisible = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="onSend">发送</el-button>
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

    <!-- 已读回执中心（M8）：统计 + 回执分页 + 催办未读（eager 投递模型，分母=回执总行数） -->
    <el-dialog v-model="receiptVisible" title="站内信已读回执" width="680px">
      <div v-if="receiptStats" class="receipt-summary">
        <el-progress
          :percentage="receiptStats.readRate ?? 0"
          :stroke-width="14"
          :text-inside="true"
          style="flex: 1"
        />
        <span class="receipt-figures">
          已读 {{ receiptStats.readCount ?? 0 }} / 未读 {{ receiptStats.unreadCount ?? 0 }} /
          接收人 {{ receiptStats.totalCount ?? 0 }} 人
        </span>
      </div>
      <div v-else class="receipt-figures">统计加载中…</div>

      <div class="toolbar receipt-toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">接收人</span>
            <el-input
              v-model="receiptQuery.keyword"
              placeholder="按姓名/账号搜索"
              clearable
              style="width: 200px"
              @keyup.enter="loadReceipts"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">状态</span>
            <el-select v-model="receiptQuery.readStatus" placeholder="全部" clearable style="width: 120px">
              <el-option label="已读" value="READ" />
              <el-option label="未读" value="UNREAD" />
            </el-select>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button :icon="Search" @click="loadReceipts">查询</el-button>
            <el-popconfirm
              title="将给未读用户发送站内信催办，确认继续？"
              @confirm="onRemindUnread"
            >
              <template #reference>
                <el-button type="warning" :icon="Bell" :loading="reminding">催办未读</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </div>

      <el-table :data="receipts" v-loading="receiptLoading" border stripe max-height="360">
        <el-table-column label="接收人" min-width="160">
          <template #default="{ row }">
            {{ row.displayName || ('用户#' + row.userId) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isRead ? 'success' : 'warning'" size="small">
              {{ row.isRead ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="readAt" label="已读时间" min-width="180">
          <template #default="{ row }">{{ row.readAt || '未读' }}</template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="receiptQuery.page"
          v-model:page-size="receiptQuery.size"
          :total="receiptTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadReceipts"
          @size-change="onReceiptSizeChange"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, Select, Reading, Bell, Search } from '@element-plus/icons-vue'
import {
  sendMessage,
  pageMyMessages,
  pageSentMessages,
  getUnreadCount,
  markMessageRead,
  markAllMessagesRead,
  getMessageReadStats,
  pageMessageReceipts,
  remindMessageUnread
} from '@/api/message'
import { sendNotification } from '@/api/notification'

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
// 发送防重：与公告页同款 loading 绑定
const sending = ref(false)
const sendFormRef = ref()
const sendMode = ref('batch')
// 通知渠道：IN_APP 站内信 / EMAIL 邮件 / SMS 短信（后两者走跨渠道统一端点）
const sendChannel = ref('IN_APP')
const sendForm = reactive({
  title: '', content: '', msgType: 'NOTICE', receiverIds: [], roleIds: [], orgIds: [],
  emails: '', mobiles: ''
})
// 短信无主题，标题不作为必填项；其余渠道标题必填
const sendRules = computed(() =>
  sendChannel.value === 'SMS'
    ? { content: [{ required: true, message: '请输入短信内容', trigger: 'blur' }] }
    : {
        title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
        content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
      }
)
const sendDialogTitle = computed(
  () => ({ IN_APP: '发送站内信', EMAIL: '发送邮件', SMS: '发送短信' }[sendChannel.value])
)
// 列表型文本域拆分：逗号 / 分号 / 换行分隔，trim 去空项
const splitList = (text) =>
  (text || '').split(/[,;，；\n]/).map((s) => s.trim()).filter((s) => s.length > 0)

const detailVisible = ref(false)
const current = ref(null)

const roleOptions = ref([])
const orgFlatOptions = ref([])
const userOptions = ref([])

// ---- 已读回执中心（M8）：eager 投递模型，分母=回执表行数 ----
const receiptVisible = ref(false)
const receiptTarget = ref(null)
const receiptStats = ref(null)
const receipts = ref([])
const receiptTotal = ref(0)
const receiptLoading = ref(false)
const reminding = ref(false)
const receiptQuery = reactive({ page: 1, size: 10, keyword: '', readStatus: '' })

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

// ---- 已读回执中心（M8）----

/** 打开回执对话框：拉统计 + 第一页回执。 */
async function openReceipts(row) {
  receiptTarget.value = row
  receiptStats.value = null
  receiptQuery.keyword = ''
  receiptQuery.readStatus = ''
  receiptQuery.page = 1
  receiptVisible.value = true
  try {
    receiptStats.value = await getMessageReadStats(row.messageId ?? row.id)
  } catch (e) {
    // 拦截器已提示；统计缺失时对话框按「统计加载中…」降级
  }
  loadReceipts()
}

async function loadReceipts() {
  if (!receiptTarget.value) return
  receiptLoading.value = true
  try {
    const data = await pageMessageReceipts(receiptTarget.value.messageId ?? receiptTarget.value.id, {
      page: receiptQuery.page,
      size: receiptQuery.size,
      keyword: receiptQuery.keyword || undefined,
      readStatus: receiptQuery.readStatus || undefined
    })
    receipts.value = data.records || []
    receiptTotal.value = data.total || 0
  } catch (e) {
    receipts.value = []
    receiptTotal.value = 0
  } finally {
    receiptLoading.value = false
  }
}

function onReceiptSizeChange(size) {
  receiptQuery.size = size
  receiptQuery.page = 1
  loadReceipts()
}

async function onRemindUnread() {
  if (!receiptTarget.value) return
  reminding.value = true
  try {
    const res = await remindMessageUnread(receiptTarget.value.messageId ?? receiptTarget.value.id)
    const count = res?.reminded ?? res?.data?.reminded ?? 0
    ElMessage.success(`已向 ${count} 位未读用户发送催办`)
    // 催办后未读数下降，刷新统计与回执
    receiptStats.value = await getMessageReadStats(receiptTarget.value.messageId ?? receiptTarget.value.id)
    loadReceipts()
  } catch (e) {
    // 拦截器已提示（如「当前没有未读用户」）
  } finally {
    reminding.value = false
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
    title: '', content: '', msgType: 'NOTICE', receiverIds: [], roleIds: [], orgIds: [],
    emails: '', mobiles: ''
  })
  sendMode.value = 'batch'
  sendChannel.value = 'IN_APP'
  sendVisible.value = true
  sendFormRef.value?.clearValidate()
  loadOptions()
}

/** 邮件 / 短信渠道：走跨渠道统一端点，目标列表由文本域拆分。 */
async function sendViaNotification() {
  if (sendChannel.value === 'EMAIL') {
    const emails = splitList(sendForm.emails)
    if (emails.length === 0) {
      ElMessage.warning('请至少填写一个收件人邮箱')
      return
    }
    const res = await sendNotification({
      channel: 'EMAIL', title: sendForm.title, content: sendForm.content,
      msgType: sendForm.msgType, emails
    })
    ElMessage.success(`已发送给 ${res?.delivered ?? 0} 个邮箱地址`)
    return
  }
  const mobiles = splitList(sendForm.mobiles)
  if (mobiles.length === 0) {
    ElMessage.warning('请至少填写一个接收手机号')
    return
  }
  const res = await sendNotification({
    // 短信无主题：title 仅作审计标识（后端不随短信发送），取内容前缀便于审计识别
    channel: 'SMS', title: sendForm.content.slice(0, 50) || '短信通知',
    content: sendForm.content, mobiles
  })
  ElMessage.success(`已发送给 ${res?.delivered ?? 0} 个手机号`)
}

async function onSend() {
  // 校验失败时 validate() 会 reject，须捕获避免 Uncaught (in promise)
  const valid = await sendFormRef.value.validate().catch(() => false)
  if (!valid) return

  // 邮件 / 短信渠道分支：目标校验与调用均不同于站内信
  if (sendChannel.value !== 'IN_APP') {
    sending.value = true
    try {
      await sendViaNotification()
      sendVisible.value = false
    } catch (e) {
      // 拦截器已提示；保持弹窗开启便于修正后重发
    } finally {
      sending.value = false
    }
    return
  }

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

  // 按钮 loading 防重：慢网络下重复点击曾重复发送同一条站内信
  sending.value = true
  try {
    const res = await sendMessage(body)
    ElMessage.success(`已发送给 ${res?.receiverCount ?? 0} 人`)
    sendVisible.value = false
    activeTab.value = 'sent'
    loadSent()
  } catch (e) {
    // 拦截器已提示；保持弹窗开启便于修正后重发
  } finally {
    sending.value = false
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
