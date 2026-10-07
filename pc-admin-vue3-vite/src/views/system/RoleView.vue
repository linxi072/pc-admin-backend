<template>
  <div>
    <h2 class="page-title">角色管理</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <span class="spacer" />
        <el-button type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="roleCode" label="角色编码" width="140" />
        <el-table-column prop="roleName" label="角色名称" width="140" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ row.roleType === 'SYSTEM' ? '系统角色' : '业务角色' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortNo" label="排序" width="80" />
        <el-table-column label="权限概览" min-width="200">
          <template #default="{ row }">
            <span>菜单 {{ row.menuIds?.length || 0 }} · 接口 {{ row.apiIds?.length || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该角色？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '新增角色'" width="640px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" :disabled="isEdit" placeholder="如 OPERATOR" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.roleType" style="width: 100%">
            <el-option label="业务角色" value="BUSINESS" />
            <el-option label="系统角色" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" />
        </el-form-item>

        <!-- 角色权限树联动：菜单树 + 接口资源树，保存时回填 menuIds / apiIds -->
        <el-form-item label="菜单权限">
          <div class="tree-panel">
            <el-tree
              ref="menuTreeRef"
              :data="menuTree"
              :props="{ label: 'menuName', children: 'children' }"
              node-key="id"
              show-checkbox
            />
          </div>
        </el-form-item>
        <el-form-item label="接口权限">
          <div class="tree-panel">
            <el-tree
              ref="apiTreeRef"
              :data="apiTree"
              :props="{ label: 'label', children: 'children' }"
              node-key="id"
              show-checkbox
            />
          </div>
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
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { listRoles, getRole, createRole, updateRole, deleteRole } from '@/api/role'
import { menuTree as fetchMenuTree } from '@/api/menu'
import { listApiResources } from '@/api/apiResource'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const menuTree = ref([])
const apiTree = ref([])
const menuTreeRef = ref()
const apiTreeRef = ref()

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = reactive({
  id: null, roleCode: '', roleName: '', roleType: 'BUSINESS', status: 'ACTIVE', sortNo: 0, menuIds: [], apiIds: []
})
const rules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

function buildApiTree(list) {
  return list.map((a) => ({ id: a.id, label: `${a.httpMethod} ${a.resourceName}` }))
}

function resetForm() {
  Object.assign(form, {
    id: null, roleCode: '', roleName: '', roleType: 'BUSINESS', status: 'ACTIVE', sortNo: 0, menuIds: [], apiIds: []
  })
}

async function load() {
  loading.value = true
  try {
    const [roles, menus, apis] = await Promise.all([listRoles(), fetchMenuTree(), listApiResources()])
    rows.value = roles
    menuTree.value = menus
    apiTree.value = buildApiTree(apis)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
  nextTick(() => {
    menuTreeRef.value?.setCheckedKeys([])
    apiTreeRef.value?.setCheckedKeys([])
  })
}

async function openEdit(row) {
  isEdit.value = true
  resetForm()
  const detail = await getRole(row.id)
  Object.assign(form, {
    id: detail.id, roleCode: detail.roleCode, roleName: detail.roleName,
    roleType: detail.roleType, status: detail.status, sortNo: detail.sortNo,
    menuIds: [...(detail.menuIds || [])], apiIds: [...(detail.apiIds || [])]
  })
  dialogVisible.value = true
  await nextTick()
  // 权限树联动回填：勾选该角色已分配的菜单 / 接口
  menuTreeRef.value?.setCheckedKeys(form.menuIds)
  apiTreeRef.value?.setCheckedKeys(form.apiIds)
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      roleCode: form.roleCode,
      roleName: form.roleName,
      roleType: form.roleType,
      status: form.status,
      sortNo: form.sortNo,
      menuIds: menuTreeRef.value.getCheckedKeys(),
      apiIds: apiTreeRef.value.getCheckedKeys()
    }
    if (isEdit.value) {
      await updateRole(form.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createRole(payload)
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
    await deleteRole(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>
