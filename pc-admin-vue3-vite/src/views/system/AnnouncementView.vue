<template>
  <div>
    <h2 v-if="!embedded" class="page-title">{{ t('system.systemAnnouncement') }}</h2>

    <el-card class="page-card" shadow="never">
      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          状态说明：<b>{{ t('common.draft') }}</b>{{ t('system.adminOnlyEditable') }}<b>{{ t('common.published') }}</b>（到达生效时间后对所有人可见） →
          <b>{{ t('system.signedOut') }}</b>（停止展示）。已发布公告需先下线才能编辑，避免线上内容被静默篡改。
        </template>
      </el-alert>
    </el-card>

    <el-card class="page-card">
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('common.keyword') }}</span>
            <el-input
              v-model="query.keyword"
              :placeholder="t('system.searchByTitleContent')"
              clearable
              style="width: 240px"
              @keyup.enter="onSearch"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('common.status') }}</span>
            <el-select v-model="query.status" :placeholder="t('common.status')" clearable style="width: 140px">
              <el-option :label="t('common.draft')" value="DRAFT" />
              <el-option :label="t('common.published')" value="PUBLISHED" />
              <el-option :label="t('system.signedOut')" value="OFFLINE" />
            </el-select>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="onSearch">{{ t('common.query') }}</el-button>
          </div>
          <div class="action-buttons">
            <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('system.newAnnouncement') }}</el-button>
          </div>
        </div>
      </div>

      <el-table :data="list" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column :label="t('common.title')" min-width="220" show-overflow-tooltip prop="title" sortable="custom">
          <template #default="{ row }">
            <el-tag v-if="row.isTop === 1" type="danger" size="small" style="margin-right: 6px">{{ t('topbar.pinned') }}</el-tag>
            <a @click="openDetail(row)">{{ row.title }}</a>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <!-- 状态标签统一走 utils/status 映射 -->
            <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).label }}</el-tag>
            <el-tag v-if="row.status === 'PUBLISHED' && !row.effective" type="warning" size="small">
              未生效/已过期
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishAt" :label="t('monitor.effectiveTime')" width="160" sortable="custom" />
        <el-table-column prop="expireAt" :label="t('system.validUntil')" width="160" sortable="custom">
          <template #default="{ row }">{{ row.expireAt || t('topbar.longTerm') }}</template>
        </el-table-column>
        <el-table-column prop="viewCount" :label="t('system.browse')" width="80" />
        <el-table-column :label="t('system.readProgress')" width="150">
          <template #default="{ row }">
            <!-- audienceCount 为发布时活跃用户快照（已读率分母）；0 表示 V40 前存量公告，无分母不展示 -->
            <template v-if="row.audienceCount > 0">
              <el-progress
                :percentage="readPercent(row)"
                :stroke-width="8"
                :text-inside="true"
                style="margin-bottom: 2px"
              />
              <span class="receipt-count">{{ row.readCount ?? 0 }}/{{ row.audienceCount }} 人</span>
            </template>
            <span v-else class="receipt-count">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="380" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              link
              type="success"
              :icon="Promotion"
              @click="openPublish(row)"
            >{{ t('common.publish') }}</el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              link
              type="warning"
              :icon="VideoPause"
              @click="onOffline(row)"
            >{{ t('profile.offline') }}</el-button>
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="warning" :icon="Top" @click="onToggleTop(row)">
              {{ row.isTop === 1 ? t('system.unpin') : t('topbar.pinned') }}
            </el-button>
            <el-button link type="info" :icon="Reading" @click="openReceipts(row)">{{ t('system.readReceipt') }}</el-button>
            <el-popconfirm :title="t('system.deleteThisAnnouncement')" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">{{ t('common.delete') }}</el-button>
              </template>
            </el-popconfirm>
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

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="editingId !== null ? t('system.editAnnouncement') : t('system.newAnnouncement')" width="680px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item :label="t('common.title')" prop="title">
          <el-input v-model="form.title" :placeholder="t('system.enterAnnouncementTitle')" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('system.content')" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" :placeholder="t('system.enterAnnouncementBody')" />
        </el-form-item>
        <el-form-item :label="t('system.pinNow')">
          <el-switch v-model="form.isTop" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item :label="t('monitor.effectiveTime')">
          <el-date-picker
            v-model="form.publishAt"
            type="datetime"
            :placeholder="t('system.blankMeansEffectiveOnPublish')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('system.validUntil')">
          <el-date-picker
            v-model="form.expireAt"
            type="datetime"
            :placeholder="t('system.blankMeansPermanent')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">{{ t('system.saveAsDraft') }}</el-button>
      </template>
    </el-dialog>

    <!-- 发布（可定时生效） -->
    <el-dialog v-model="publishVisible" :title="t('system.publishAnnouncement')" width="520px">
      <el-form label-width="100px">
        <el-form-item :label="t('monitor.effectiveTime')">
          <el-date-picker
            v-model="publishForm.publishAt"
            type="datetime"
            :placeholder="t('system.blankMeansEffectiveImmediately')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('system.validUntil')">
          <el-date-picker
            v-model="publishForm.expireAt"
            type="datetime"
            :placeholder="t('system.blankMeansPermanent')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="publishing" @click="onPublish">{{ t('system.confirmPublish') }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" :title="t('topbar.announcementDetail')" width="680px">
      <h3 class="detail-title">{{ current?.title }}</h3>
      <div class="detail-meta">
        <el-tag :type="statusTag(current?.status).type" size="small">{{ statusTag(current?.status).label }}</el-tag>
        <span class="meta-item">生效：{{ current?.publishAt || t('system.now') }}</span>
        <span class="meta-item">有效期至：{{ current?.expireAt || t('topbar.longTerm') }}</span>
        <span class="meta-item">浏览：{{ current?.viewCount ?? 0 }}</span>
      </div>
      <el-divider />
      <div class="detail-content">{{ current?.content }}</div>
    </el-dialog>

    <!-- 已读回执中心（V40）：统计 + 回执分页 + 催办未读 -->
    <el-dialog v-model="receiptVisible" :title="t('system.readReceipt')" width="680px">
      <div v-if="receiptStats" class="receipt-summary">
        <el-progress
          :percentage="receiptStats.readRate ?? 0"
          :stroke-width="14"
          :text-inside="true"
          style="flex: 1"
        />
        <span class="receipt-figures">
          已读 {{ receiptStats.readCount ?? 0 }} / 未读 {{ receiptStats.unreadCount ?? 0 }} /
          受众 {{ receiptStats.audienceCount ?? 0 }} 人
        </span>
      </div>
      <div v-else class="receipt-figures">{{ t('system.loadingStats') }}</div>

      <div class="toolbar receipt-toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('system.reader') }}</span>
            <el-input
              v-model="receiptQuery.keyword"
              :placeholder="t('system.searchByNameAccount')"
              clearable
              style="width: 200px"
              @keyup.enter="loadReceipts"
            />
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button :icon="Search" @click="loadReceipts">{{ t('common.query') }}</el-button>
            <el-popconfirm
              :title="t('system.willSendAnInSiteNudgeToUnreadUsersContinue')"
              @confirm="onRemindUnread"
            >
              <template #reference>
                <el-button type="warning" :icon="Bell" :loading="reminding">{{ t('system.urgeUnread') }}</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </div>

      <el-table :data="receipts" v-loading="receiptLoading" border stripe max-height="360">
        <el-table-column :label="t('system.reader')" min-width="160">
          <template #default="{ row }">
            {{ row.displayName || (t('system.userPrefix') + row.userId) }}
          </template>
        </el-table-column>
        <el-table-column prop="readAt" :label="t('system.readAt')" min-width="180" />
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
import { t } from '@/i18n'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Edit, Delete, Search, Promotion, VideoPause, Top, Reading, Bell } from '@element-plus/icons-vue'
import {
  pageAnnouncements,
  createAnnouncement,
  updateAnnouncement,
  publishAnnouncement,
  offlineAnnouncement,
  toggleAnnouncementTop,
  deleteAnnouncement,
  getAnnouncement,
  markAnnouncementRead,
  pageAnnouncementReceipts,
  getAnnouncementReadStats,
  remindAnnouncementUnread
} from '@/api/announcement'
// 状态标签映射统一到全局（替换此前页面内的三元判断）
import { statusTag } from '@/utils/status'
import { validateOrWarn } from '@/utils/validate'

// 嵌入「消息中心」时由外层 Tab 提供标题，此处隐藏页内标题避免重复
defineProps({ embedded: { type: Boolean, default: false } })

const loading = ref(false)
// 提交防重：与 UserView/JobListView 同款写法，避免慢网络下重复创建/重复发布
const saving = ref(false)
const publishing = ref(false)
const list = ref([])
const total = ref(0)
// 默认每页条数统一为 10（与其他列表页一致）
const query = reactive({ page: 1, size: 10, keyword: '', status: '', sortField: '', sortDirection: '' })

const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({ title: '', content: '', isTop: 0, publishAt: null, expireAt: null })

const publishVisible = ref(false)
const publishTarget = ref(null)
const publishForm = reactive({ publishAt: null, expireAt: null })

const detailVisible = ref(false)
const current = ref(null)

// ---- 已读回执中心（V40）----
const receiptVisible = ref(false)
const receiptTarget = ref(null)
const receiptStats = ref(null)
const receipts = ref([])
const receiptTotal = ref(0)
const receiptLoading = ref(false)
const reminding = ref(false)
const receiptQuery = reactive({ page: 1, size: 10, keyword: '' })

const rules = {
  title: [{ required: true, message: t('system.enterAnnouncementTitle'), trigger: 'blur' }],
  content: [{ required: true, message: t('system.enterAnnouncementBody'), trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const data = await pageAnnouncements({ ...query })
    list.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    // 失败静默：http 拦截器已统一提示，此处只避免 Uncaught (in promise)
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 每页条数变化后回到第一页。 */
function onSizeChange(size) {
  query.size = size
  query.page = 1
  load()
}

/**
 * 表头排序：走服务端白名单（title/publishAt/expireAt 等），取消排序时回到
 * 服务端默认（置顶优先 + 发布时间倒序）。
 */
function onSortChange({ prop, order }) {
  query.sortField = order ? prop : ''
  query.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  query.page = 1
  load()
}

function onSearch() {
  query.page = 1
  load()
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { title: '', content: '', isTop: 0, publishAt: null, expireAt: null })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    title: row.title,
    content: row.content,
    isTop: row.isTop ?? 0,
    publishAt: row.publishAt || null,
    expireAt: row.expireAt || null
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function onSubmit() {
  // 校验失败时 validate() 会 reject，须捕获避免 Uncaught (in promise)
  if (!(await validateOrWarn(formRef.value))) return
  const body = {
    title: form.title,
    content: form.content,
    isTop: form.isTop,
    publishAt: form.publishAt,
    expireAt: form.expireAt
  }
  // 按钮 loading 防重：慢网络下重复点击曾创建出多条相同草稿/重复发布请求
  saving.value = true
  try {
    if (editingId.value !== null) {
      await updateAnnouncement(editingId.value, body)
      ElMessage.success(t('common.saved'))
    } else {
      await createAnnouncement(body)
      ElMessage.success(t('system.createdAsDraft'))
    }
    dialogVisible.value = false
    load()
  } catch (e) {
    // 拦截器已提示；保持对话框开启便于就地修正
  } finally {
    saving.value = false
  }
}

function openPublish(row) {
  publishTarget.value = row
  publishForm.publishAt = row.publishAt || null
  publishForm.expireAt = row.expireAt || null
  publishVisible.value = true
}

async function onPublish() {
  publishing.value = true
  try {
    await publishAnnouncement(publishTarget.value.id, {
      publishAt: publishForm.publishAt || undefined,
      expireAt: publishForm.expireAt || undefined
    })
    ElMessage.success(t('common.published'))
    publishVisible.value = false
    load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    publishing.value = false
  }
}

async function onOffline(row) {
  try {
    await offlineAnnouncement(row.id)
    ElMessage.success(t('system.signedOut'))
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onToggleTop(row) {
  try {
    await toggleAnnouncementTop(row.id)
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function onDelete(row) {
  try {
    await deleteAnnouncement(row.id)
    ElMessage.success(t('common.deleted'))
    load()
  } catch (e) {
    // 拦截器已提示
  }
}

/** 已读率百分比（列表列展示用）：无分母（存量公告）返回 0，由模板走「—」分支。 */
function readPercent(row) {
  if (!row.audienceCount || row.audienceCount <= 0) return 0
  const read = Math.min(Math.max(row.readCount ?? 0, 0), row.audienceCount)
  return Math.round((read * 1000) / row.audienceCount) / 10
}

// ---- 已读回执中心 ----

/** 打开回执对话框：拉统计 + 第一页回执。 */
async function openReceipts(row) {
  receiptTarget.value = row
  receiptStats.value = null
  receiptQuery.keyword = ''
  receiptQuery.page = 1
  receiptVisible.value = true
  try {
    receiptStats.value = await getAnnouncementReadStats(row.id)
  } catch (e) {
    // 拦截器已提示；统计缺失时对话框按「统计加载中…」降级
  }
  loadReceipts()
}

async function loadReceipts() {
  if (!receiptTarget.value) return
  receiptLoading.value = true
  try {
    const data = await pageAnnouncementReceipts(receiptTarget.value.id, {
      page: receiptQuery.page,
      size: receiptQuery.size,
      keyword: receiptQuery.keyword || undefined
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
    const res = await remindAnnouncementUnread(receiptTarget.value.id)
    const count = res?.reminded ?? res?.data?.reminded ?? 0
    ElMessage.success(t('system.urgedUnreadUsers', { count }))
  } catch (e) {
    // 拦截器已提示（如「当前没有未读用户」）
  } finally {
    reminding.value = false
  }
}

async function openDetail(row) {
  try {
    current.value = await getAnnouncement(row.id)
    detailVisible.value = true
    // 详情即阅读：已发布公告自动落已读回执（懒插入模型）。失败静默——
    // 回执是统计增强，不应阻断用户查看公告本体
    if (current.value?.status === 'PUBLISHED') {
      try {
        await markAnnouncementRead(row.id)
      } catch (e) {
        // 已读上报失败不影响详情展示
      }
    }
  } catch (e) {
    // 详情拉取失败不打开弹窗，避免展示上一条公告内容
  }
}

onMounted(load)
</script>

<style scoped>
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
.meta-item {
  white-space: nowrap;
}
.detail-content {
  white-space: pre-wrap;
  line-height: 1.7;
  color: var(--text);
}
.receipt-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
}
.receipt-figures {
  color: #606266;
  font-size: 13px;
  white-space: nowrap;
}
.receipt-count {
  color: #909399;
  font-size: 12px;
}
.receipt-toolbar {
  margin-bottom: 12px;
}
</style>
