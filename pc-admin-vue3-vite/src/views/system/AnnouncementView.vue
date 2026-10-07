<template>
  <div>
    <h2 v-if="!embedded" class="page-title">系统公告</h2>

    <el-card class="page-card" shadow="never">
      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          状态说明：<b>草稿</b>（仅管理员可见，可编辑） → <b>已发布</b>（到达生效时间后对所有人可见） →
          <b>已下线</b>（停止展示）。已发布公告需先下线才能编辑，避免线上内容被静默篡改。
        </template>
      </el-alert>
    </el-card>

    <el-card class="page-card">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="按标题/内容搜索"
          clearable
          style="width: 240px"
          @keyup.enter="onSearch"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已下线" value="OFFLINE" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <span class="spacer" />
        <el-button type="primary" :icon="Plus" @click="openCreate">新建公告</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column label="标题" min-width="220" show-overflow-tooltip prop="title" sortable="custom">
          <template #default="{ row }">
            <el-tag v-if="row.isTop === 1" type="danger" size="small" style="margin-right: 6px">置顶</el-tag>
            <a @click="openDetail(row)">{{ row.title }}</a>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            <el-tag v-if="row.status === 'PUBLISHED' && !row.effective" type="warning" size="small">
              未生效/已过期
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishAt" label="生效时间" width="160" sortable="custom" />
        <el-table-column prop="expireAt" label="有效期至" width="160" sortable="custom">
          <template #default="{ row }">{{ row.expireAt || '长期有效' }}</template>
        </el-table-column>
        <el-table-column prop="viewCount" label="浏览" width="80" />
        <el-table-column label="操作" width="290" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              link
              type="success"
              :icon="Promotion"
              @click="openPublish(row)"
            >发布</el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              link
              type="warning"
              :icon="VideoPause"
              @click="onOffline(row)"
            >下线</el-button>
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" :icon="Top" @click="onToggleTop(row)">
              {{ row.isTop === 1 ? '取消置顶' : '置顶' }}
            </el-button>
            <el-popconfirm title="确认删除该公告？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
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

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="editingId !== null ? '编辑公告' : '新建公告'" width="680px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入公告标题" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" placeholder="请输入公告内容" />
        </el-form-item>
        <el-form-item label="立即置顶">
          <el-switch v-model="form.isTop" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker
            v-model="form.publishAt"
            type="datetime"
            placeholder="留空表示发布即生效"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker
            v-model="form.expireAt"
            type="datetime"
            placeholder="留空表示长期有效"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存为草稿</el-button>
      </template>
    </el-dialog>

    <!-- 发布（可定时生效） -->
    <el-dialog v-model="publishVisible" title="发布公告" width="520px">
      <el-form label-width="100px">
        <el-form-item label="生效时间">
          <el-date-picker
            v-model="publishForm.publishAt"
            type="datetime"
            placeholder="留空表示立即生效"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker
            v-model="publishForm.expireAt"
            type="datetime"
            placeholder="留空表示长期有效"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" @click="onPublish">确认发布</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="公告详情" width="680px">
      <h3 class="detail-title">{{ current?.title }}</h3>
      <div class="detail-meta">
        <el-tag :type="statusType(current?.status)" size="small">{{ statusText(current?.status) }}</el-tag>
        <span class="meta-item">生效：{{ current?.publishAt || '立即' }}</span>
        <span class="meta-item">有效期至：{{ current?.expireAt || '长期有效' }}</span>
        <span class="meta-item">浏览：{{ current?.viewCount ?? 0 }}</span>
      </div>
      <el-divider />
      <div class="detail-content">{{ current?.content }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Edit, Delete, Search, Promotion, VideoPause, Top } from '@element-plus/icons-vue'
import {
  pageAnnouncements,
  createAnnouncement,
  updateAnnouncement,
  publishAnnouncement,
  offlineAnnouncement,
  toggleAnnouncementTop,
  deleteAnnouncement,
  getAnnouncement
} from '../../api/announcement'

// 嵌入「消息中心」时由外层 Tab 提供标题，此处隐藏页内标题避免重复
defineProps({ embedded: { type: Boolean, default: false } })

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', status: '', sortField: '', sortDirection: '' })

const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({ title: '', content: '', isTop: 0, publishAt: null, expireAt: null })

const publishVisible = ref(false)
const publishTarget = ref(null)
const publishForm = reactive({ publishAt: null, expireAt: null })

const detailVisible = ref(false)
const current = ref(null)

const rules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }]
}

const statusText = (s) =>
  ({ DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' }[s] || s)
const statusType = (s) =>
  ({ DRAFT: 'info', PUBLISHED: 'success', OFFLINE: 'warning' }[s] || 'info')

async function load() {
  loading.value = true
  try {
    const data = await pageAnnouncements({ ...query })
    list.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
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
  await formRef.value.validate()
  const body = {
    title: form.title,
    content: form.content,
    isTop: form.isTop,
    publishAt: form.publishAt,
    expireAt: form.expireAt
  }
  if (editingId.value !== null) {
    await updateAnnouncement(editingId.value, body)
    ElMessage.success('已保存')
  } else {
    await createAnnouncement(body)
    ElMessage.success('已创建为草稿')
  }
  dialogVisible.value = false
  load()
}

function openPublish(row) {
  publishTarget.value = row
  publishForm.publishAt = row.publishAt || null
  publishForm.expireAt = row.expireAt || null
  publishVisible.value = true
}

async function onPublish() {
  await publishAnnouncement(publishTarget.value.id, {
    publishAt: publishForm.publishAt || undefined,
    expireAt: publishForm.expireAt || undefined
  })
  ElMessage.success('已发布')
  publishVisible.value = false
  load()
}

async function onOffline(row) {
  await offlineAnnouncement(row.id)
  ElMessage.success('已下线')
  load()
}

async function onToggleTop(row) {
  await toggleAnnouncementTop(row.id)
  load()
}

async function onDelete(row) {
  await deleteAnnouncement(row.id)
  ElMessage.success('已删除')
  load()
}

async function openDetail(row) {
  current.value = await getAnnouncement(row.id)
  detailVisible.value = true
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
  color: #303133;
}
</style>
