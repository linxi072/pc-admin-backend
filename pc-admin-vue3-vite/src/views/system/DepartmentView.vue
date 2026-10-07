<template>
  <div>
    <h2 class="page-title">部门管理</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索部门名称 / 编码"
          clearable
          :prefix-icon="Search"
          style="width: 240px"
          @clear="keyword = ''"
        />
        <span class="spacer" />
        <el-button type="primary" :icon="Plus" @click="openCreate">新增部门</el-button>
      </div>

      <el-table
        :data="treeData"
        v-loading="loading"
        row-key="id"
        :tree-props="{ children: 'children' }"
        border
        stripe
        default-expand-all
      >
        <el-table-column prop="orgName" label="部门名称" min-width="160" />
        <el-table-column prop="orgCode" label="部门编码" width="150" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.orgType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortNo" label="排序" width="80" />
        <el-table-column prop="leaderUserId" label="负责人ID" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该部门？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId !== null ? '编辑部门' : '新增部门'" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="上级部门">
          <el-select v-model="form.parentId" clearable placeholder="顶级部门" style="width: 100%">
            <el-option v-for="m in parentOptions" :key="m.id" :label="m.label" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门编码" prop="orgCode">
          <el-input v-model="form.orgCode" placeholder="如 tech-dept" />
        </el-form-item>
        <el-form-item label="部门名称" prop="orgName">
          <el-input v-model="form.orgName" />
        </el-form-item>
        <el-form-item label="部门类型" prop="orgType">
          <el-select v-model="form.orgType" style="width: 100%">
            <el-option label="公司 (COMPANY)" value="COMPANY" />
            <el-option label="部门 (DEPARTMENT)" value="DEPARTMENT" />
            <el-option label="小组 (TEAM)" value="TEAM" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" />
        </el-form-item>
        <el-form-item label="负责人ID">
          <el-input-number v-model="form.leaderUserId" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Search, Edit } from '@element-plus/icons-vue'
import { orgTree, createOrg, updateOrg, deleteOrg } from '@/api/org'

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const rawTree = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()

const form = reactive({
  parentId: null,
  orgCode: '',
  orgName: '',
  orgType: 'DEPARTMENT',
  sortNo: 0,
  leaderUserId: 0,
  status: 'ACTIVE'
})
const rules = {
  orgCode: [{ required: true, message: '请输入部门编码', trigger: 'blur' }],
  orgName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  orgType: [{ required: true, message: '请选择部门类型', trigger: 'change' }]
}

function typeLabel(t) {
  return t === 'COMPANY' ? '公司' : t === 'TEAM' ? '小组' : '部门'
}

const flatOrgs = computed(() => {
  const out = []
  const walk = (nodes, depth) => {
    for (const n of nodes) {
      out.push({ id: n.id, label: (depth > 0 ? '　'.repeat(depth) : '') + n.orgName })
      if (n.children?.length) walk(n.children, depth + 1)
    }
  }
  walk(rawTree.value, 0)
  return out
})

// 编辑态时排除当前节点及其全部子孙，避免选中造成层级环路
const parentOptions = computed(() => {
  if (editingId.value == null) return flatOrgs.value
  const banned = subtreeIds(rawTree.value, editingId.value)
  return flatOrgs.value.filter((m) => !banned.has(m.id))
})

function subtreeIds(nodes, id) {
  const set = new Set()
  const walk = (list) => {
    for (const n of list) {
      if (n.id === id) {
        set.add(n.id)
        const collect = (node) => {
          for (const c of node.children || []) {
            set.add(c.id)
            collect(c)
          }
        }
        collect(n)
        return true
      }
      if (n.children?.length && walk(n.children)) return true
    }
    return false
  }
  walk(nodes)
  return set
}

const treeData = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rawTree.value
  const filter = (nodes) => {
    const res = []
    for (const n of nodes) {
      const match =
        (n.orgName || '').toLowerCase().includes(kw) ||
        (n.orgCode || '').toLowerCase().includes(kw)
      const kids = n.children ? filter(n.children) : []
      if (match || kids.length) res.push({ ...n, children: kids })
    }
    return res
  }
  return filter(rawTree.value)
})

async function load() {
  loading.value = true
  try {
    rawTree.value = await orgTree()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    parentId: null,
    orgCode: '',
    orgName: '',
    orgType: 'DEPARTMENT',
    sortNo: 0,
    leaderUserId: 0,
    status: 'ACTIVE'
  })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    parentId: row.parentId || null,
    orgCode: row.orgCode,
    orgName: row.orgName,
    orgType: row.orgType || 'DEPARTMENT',
    sortNo: row.sortNo || 0,
    leaderUserId: row.leaderUserId || 0,
    status: row.status || 'ACTIVE'
  })
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value != null) {
      await updateOrg(editingId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createOrg({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    editingId.value = null
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await deleteOrg(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

onMounted(load)
</script>
