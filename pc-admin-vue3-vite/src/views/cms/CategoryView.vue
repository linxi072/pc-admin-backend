<template>
  <div>
    <h2 class="page-title">{{ 'CMS 栏目管理' }}</h2>

    <el-card class="page-card" shadow="never">
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="keyword"
            :placeholder="'按栏目名称/编码搜索'"
            clearable
            style="width: 220px"
            @keyup.enter="load"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" :icon="Search" @click="load">{{ '搜索' }}</el-button>
        </div>
        <div class="actions">
          <el-button type="primary" :icon="Plus" @click="openCreate">{{ '创建' }}</el-button>
          <el-button :icon="Refresh" @click="load">{{ '刷新' }}</el-button>
        </div>
      </div>

      <el-table
        :data="filteredTree"
        v-loading="loading"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="categoryName" :label="'栏目名称'" min-width="180" />
        <el-table-column prop="categoryCode" :label="'栏目编码'" min-width="140" />
        <el-table-column prop="description" :label="'描述'" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sortNo" :label="'排序'" width="90" align="center" />
        <el-table-column :label="'状态'" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type">{{ statusTag(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="'操作'" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Plus" @click="openCreate(row)">{{ '加子栏目' }}</el-button>
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ '编辑' }}</el-button>
            <el-button link type="danger" :icon="Delete" @click="onDelete(row)">{{ '删除' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item :label="'父栏目'" prop="parentId">
          <el-select v-model="form.parentId" :placeholder="'选择父栏目（可选）'" clearable style="width: 100%">
            <el-option :label="'根栏目'" :value="null" />
            <el-option
              v-for="item in parentOptions"
              :key="item.id"
              :label="item.categoryName"
              :value="item.id"
              :disabled="item.id === form.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="'栏目编码'" prop="categoryCode">
          <el-input v-model="form.categoryCode" :placeholder="'请输入栏目编码'" />
        </el-form-item>
        <el-form-item :label="'栏目名称'" prop="categoryName">
          <el-input v-model="form.categoryName" :placeholder="'请输入栏目名称'" />
        </el-form-item>
        <el-form-item :label="'描述'" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" :placeholder="'请输入栏目描述'" />
        </el-form-item>
        <el-form-item :label="'排序'" prop="sortNo">
          <el-input-number v-model="form.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item :label="'状态'" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">{{ '启用' }}</el-radio>
            <el-radio value="DISABLED">{{ '停用' }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ '取消' }}</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">{{ '确认' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>

import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete, Refresh } from '@element-plus/icons-vue'
import { listCategories, treeCategories, createCategory, updateCategory, deleteCategory } from '@/api/cms'
import { statusTag } from '@/utils/status'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const keyword = ref('')
const tree = ref([])
const flat = ref([])
const formRef = ref()

const form = reactive({
  id: null,
  parentId: null,
  categoryCode: '',
  categoryName: '',
  description: '',
  sortNo: 0,
  status: 'ACTIVE'
})

const rules = {
  categoryCode: [{ required: true, message: '请输入栏目编码', trigger: 'blur' }],
  categoryName: [{ required: true, message: '请输入栏目名称', trigger: 'blur' }]
}

const isEdit = computed(() => form.id != null)
const dialogTitle = computed(() => (isEdit.value ? '编辑栏目' : '新增栏目'))

// 父栏目下拉排除自身及其后代，避免形成环
const parentOptions = computed(() => {
  const excluded = new Set()
  const collectIds = (nodes) => {
    for (const node of nodes || []) {
      excluded.add(node.id)
      collectIds(node.children)
    }
  }
  if (isEdit.value) collectIds(tree.value.filter((n) => containsId(n, form.id)))
  return flat.value.filter((item) => !excluded.has(item.id))
})

function containsId(node, targetId) {
  if (node.id === targetId) return true
  return (node.children || []).some((child) => containsId(child, targetId))
}

const filteredTree = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return tree.value
  const lower = kw.toLowerCase()
  const match = (node) =>
    (node.categoryName || '').toLowerCase().includes(lower) || (node.categoryCode || '').toLowerCase().includes(lower)
  const filterNodes = (nodes) =>
    (nodes || [])
      .map((node) => {
        const children = filterNodes(node.children)
        if (match(node) || children.length > 0) return { ...node, children }
        return null
      })
      .filter(Boolean)
  return filterNodes(tree.value)
})

async function load() {
  loading.value = true
  try {
    const [treeData, listData] = await Promise.all([treeCategories(), listCategories()])
    tree.value = Array.isArray(treeData) ? treeData : []
    flat.value = Array.isArray(listData) ? listData : []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.id = null
  form.parentId = null
  form.categoryCode = ''
  form.categoryName = ''
  form.description = ''
  form.sortNo = 0
  form.status = 'ACTIVE'
}

function openCreate(parentRow) {
  resetForm()
  if (parentRow && parentRow.id != null) form.parentId = parentRow.id
  dialogVisible.value = true
}

function openEdit(row) {
  resetForm()
  form.id = row.id
  form.parentId = row.parentId ?? null
  form.categoryCode = row.categoryCode
  form.categoryName = row.categoryName
  form.description = row.description || ''
  form.sortNo = row.sortNo ?? 0
  form.status = row.status || 'ACTIVE'
  dialogVisible.value = true
}

function onSubmit() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const body = {
        id: form.id,
        parentId: form.parentId,
        categoryCode: form.categoryCode,
        categoryName: form.categoryName,
        description: form.description,
        sortNo: form.sortNo,
        status: form.status
      }
      if (isEdit.value) {
        await updateCategory(body)
        ElMessage.success('更新成功')
      } else {
        delete body.id
        await createCategory(body)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      await load()
    } finally {
      submitting.value = false
    }
  })
}

async function onDelete(row) {
  const confirmed = await ElMessageBox.confirm(`确认删除栏目「${row.categoryName}」吗？若存在子栏目或关联文章，删除将被拒绝。`, '警告', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  }).catch(() => false)
  if (!confirmed) return
  await deleteCategory(row.id)
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
