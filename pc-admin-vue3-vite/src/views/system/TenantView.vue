<template>
  <div>
    <h2 class="page-title">租户管理</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">租户编码</span>
            <el-input
              v-model="query.tenantCode"
              placeholder="搜索租户编码"
              clearable
              :prefix-icon="Search"
              style="width: 220px"
              @keyup.enter="load"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">状态</span>
            <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
              <el-option label="启用" value="ENABLED" />
              <el-option label="禁用" value="DISABLED" />
            </el-select>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Plus" @click="openCreate">新增租户</el-button>
          </div>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="tenantCode" label="租户编码" width="180" sortable="custom" />
        <el-table-column prop="tenantName" label="租户名称" min-width="160" />
        <el-table-column prop="contactName" label="联系人" width="120" />
        <el-table-column prop="contactPhone" label="联系电话" width="150" />
        <el-table-column prop="contactEmail" label="联系邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="160">
          <template #default="{ row }">
            <span>{{ fmtDateTime(row.expireAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="170" sortable="custom" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              :type="row.status === 'ENABLED' ? 'warning' : 'success'"
              @click="onToggle(row)"
            >{{ row.status === 'ENABLED' ? '禁用' : '启用' }}</el-button>
            <el-popconfirm title="确认删除该租户？" width="220" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑租户' : '新增租户'" width="560px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="92px">
        <el-form-item label="租户编码" prop="tenantCode">
          <el-input
            v-model="form.tenantCode"
            :disabled="isEdit"
            placeholder="如 T-ACME（字母/数字/下划线/连字符，创建后不可变更）"
          />
        </el-form-item>
        <el-form-item label="租户名称" prop="tenantName">
          <el-input v-model="form.tenantName" placeholder="如 某某科技有限公司" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" placeholder="选填" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="联系邮箱">
          <el-input v-model="form.contactEmail" placeholder="选填" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="form.expireAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选填，留空表示长期有效"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  tenantPage,
  createTenant,
  updateTenant,
  removeTenant,
  toggleTenant
} from '@/api/tenant'
import { statusTag } from '@/utils/status'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, tenantCode: '', status: '', sortField: '', sortDirection: '' })

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  tenantCode: '',
  tenantName: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  status: 'ENABLED',
  expireAt: '',
  remark: ''
})
const rules = {
  tenantCode: [{ required: true, message: '请输入租户编码', trigger: 'blur' }],
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }]
}

/** LocalDateTime 字符串（"2026-10-26T14:30:00"）→ "2026-10-26 14:30"；空值回落 '长期'。 */
function fmtDateTime(v) {
  if (!v) return '长期'
  const s = String(v).replace('T', ' ').slice(0, 16)
  return s || '长期'
}

async function load() {
  loading.value = true
  try {
    const data = await tenantPage({ ...query })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function onSortChange({ prop, order }) {
  query.sortField = order ? prop : ''
  query.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  query.page = 1
  load()
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, {
    id: null,
    tenantCode: '',
    tenantName: '',
    contactName: '',
    contactPhone: '',
    contactEmail: '',
    status: 'ENABLED',
    expireAt: '',
    remark: ''
  })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    tenantCode: row.tenantCode,
    tenantName: row.tenantName,
    contactName: row.contactName || '',
    contactPhone: row.contactPhone || '',
    contactEmail: row.contactEmail || '',
    status: row.status || 'ENABLED',
    expireAt: row.expireAt ? String(row.expireAt).replace('T', ' ') : '',
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    // 后端 LocalDateTime 以 ISO 接收：空格分隔转 T 分隔，空值传 null
    const expireAt = form.expireAt ? String(form.expireAt).replace(' ', 'T') : null
    const payload = {
      tenantName: form.tenantName,
      contactName: form.contactName || null,
      contactPhone: form.contactPhone || null,
      contactEmail: form.contactEmail || null,
      status: form.status,
      expireAt,
      remark: form.remark || null
    }
    if (isEdit.value) {
      await updateTenant(form.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createTenant({ tenantCode: form.tenantCode, ...payload })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onToggle(row) {
  const enable = row.status === 'DISABLED'
  try {
    await toggleTenant(row.id, enable)
    ElMessage.success(enable ? '已启用' : '已禁用')
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

async function onDelete(row) {
  try {
    await removeTenant(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

onMounted(load)
</script>
