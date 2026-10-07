<template>
  <div>
    <h2 class="page-title">菜单管理</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索菜单名称 / 编码"
          clearable
          :prefix-icon="Search"
          style="width: 240px"
          @clear="keyword = ''"
        />
        <span class="spacer" />
        <el-button type="primary" :icon="Plus" @click="openCreate">新增菜单</el-button>
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
        <el-table-column prop="menuName" label="菜单名称" min-width="160" />
        <el-table-column prop="menuCode" label="菜单编码" width="150" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.menuType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="routePath" label="路由地址" min-width="170" show-overflow-tooltip />
        <el-table-column prop="permissionCode" label="权限标识" min-width="170" show-overflow-tooltip />
        <el-table-column prop="sortNo" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该菜单？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId !== null ? '编辑菜单' : '新增菜单'" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="上级菜单">
          <el-select v-model="form.parentId" clearable placeholder="顶级菜单" style="width: 100%">
            <el-option v-for="m in parentOptions" :key="m.id" :label="m.label" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单编码" prop="menuCode">
          <el-input v-model="form.menuCode" placeholder="如 sys:user" />
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" />
        </el-form-item>
        <el-form-item label="菜单类型" prop="menuType">
          <el-select v-model="form.menuType" style="width: 100%">
            <el-option label="目录 (C)" value="C" />
            <el-option label="菜单 (M)" value="M" />
            <el-option label="按钮 (B)" value="B" />
          </el-select>
        </el-form-item>
        <el-form-item label="路由地址">
          <el-input v-model="form.routePath" placeholder="/system/user" />
        </el-form-item>
        <el-form-item label="组件路径">
          <el-input v-model="form.componentPath" placeholder="views/system/User.vue" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="form.permissionCode" placeholder="system:menu:read" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="如 Setting（Element Plus 图标名）" />
        </el-form-item>
        <el-form-item label="是否显示">
          <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
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
import { menuTree, createMenu, updateMenu, deleteMenu } from '@/api/menu'

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const rawTree = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()

const form = reactive({
  parentId: null,
  menuCode: '',
  menuName: '',
  menuType: 'M',
  routePath: '',
  componentPath: '',
  permissionCode: '',
  icon: '',
  visible: 1,
  sortNo: 0,
  status: 'ENABLED'
})
const rules = {
  menuCode: [{ required: true, message: '请输入菜单编码', trigger: 'blur' }],
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }]
}

function typeLabel(t) {
  return t === 'C' ? '目录' : t === 'B' ? '按钮' : '菜单'
}

// 将嵌套菜单树拍平为「上级菜单」下拉选项，带层级缩进
const flatMenus = computed(() => {
  const out = []
  const walk = (nodes, depth) => {
    for (const n of nodes) {
      out.push({ id: n.id, label: (depth > 0 ? '　'.repeat(depth) : '') + n.menuName })
      if (n.children?.length) walk(n.children, depth + 1)
    }
  }
  walk(rawTree.value, 0)
  return out
})

// 编辑态时，从「上级菜单」下拉中排除当前节点及其全部子孙，避免选到会造成环路的父级
const parentOptions = computed(() => {
  if (editingId.value == null) return flatMenus.value
  const banned = subtreeIds(rawTree.value, editingId.value)
  return flatMenus.value.filter((m) => !banned.has(m.id))
})

// 收集节点及其全部子孙的 id 集合（在父级下拉中排除，防止层级环路）
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

// 关键词过滤（保留命中或命中子孙的祖先），与后端 MenuTreeVO 结构对齐
const treeData = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rawTree.value
  const filter = (nodes) => {
    const res = []
    for (const n of nodes) {
      const match =
        (n.menuName || '').toLowerCase().includes(kw) ||
        (n.menuCode || '').toLowerCase().includes(kw)
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
    rawTree.value = await menuTree()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    parentId: null,
    menuCode: '',
    menuName: '',
    menuType: 'M',
    routePath: '',
    componentPath: '',
    permissionCode: '',
    icon: '',
    visible: 1,
    sortNo: 0,
    status: 'ENABLED'
  })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    parentId: row.parentId || null,
    menuCode: row.menuCode,
    menuName: row.menuName,
    menuType: row.menuType,
    routePath: row.routePath || '',
    componentPath: row.componentPath || '',
    permissionCode: row.permissionCode || '',
    icon: row.icon || '',
    visible: row.visible == null ? 1 : row.visible,
    sortNo: row.sortNo || 0,
    status: row.status || 'ENABLED'
  })
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value != null) {
      await updateMenu(editingId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createMenu({ ...form })
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
    await deleteMenu(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

onMounted(load)
</script>
