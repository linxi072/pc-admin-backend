<template>
  <div>
    <h2 class="page-title">岗位管理</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">关键字</span>
            <el-input
              v-model="query.keyword"
              placeholder="搜索岗位编码 / 名称"
              clearable
              :prefix-icon="Search"
              style="width: 240px"
              @keyup.enter="load"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">状态</span>
            <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Plus" @click="openCreate">新增岗位</el-button>
          </div>
        </div>
      </div>

      <!-- 不设 height：随内容自适应，与其他列表页一致 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="id" label="ID" width="90" sortable="custom" />
        <el-table-column prop="postCode" label="岗位编码" width="160" sortable="custom" />
        <el-table-column prop="postName" label="岗位名称" min-width="160" />
        <el-table-column prop="postSort" label="排序" width="90" align="center" sortable="custom" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <!-- 状态标签统一走 utils/status 映射 -->
            <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="170" sortable="custom" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该岗位？关联用户将被清空岗位" width="240" @confirm="onDelete(row)">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑岗位' : '新增岗位'" width="520px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="岗位编码" prop="postCode">
          <el-input v-model="form.postCode" placeholder="如 GM-001（字母/数字/下划线/连字符）" />
        </el-form-item>
        <el-form-item label="岗位名称" prop="postName">
          <el-input v-model="form.postName" placeholder="如 总经理" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.postSort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
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
import { pagePosts, createPost, updatePost, deletePost } from '@/api/post'
// 状态标签映射统一到 utils
import { statusTag } from '@/utils/status'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '', status: '', sortField: '', sortDirection: '' })

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = reactive({
  id: null, postCode: '', postName: '', postSort: 0, status: 'ACTIVE', remark: ''
})
const rules = {
  postCode: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }],
  postName: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const data = await pagePosts({ ...query })
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
  Object.assign(form, { id: null, postCode: '', postName: '', postSort: 0, status: 'ACTIVE', remark: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id, postCode: row.postCode, postName: row.postName,
    postSort: row.postSort || 0, status: row.status || 'ACTIVE', remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      postCode: form.postCode,
      postName: form.postName,
      postSort: form.postSort || 0,
      status: form.status,
      remark: form.remark || null
    }
    if (isEdit.value) {
      await updatePost(form.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createPost(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await deletePost(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

onMounted(load)
</script>
