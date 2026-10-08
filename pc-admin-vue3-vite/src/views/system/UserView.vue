<template>
  <div>
    <h2 class="page-title">用户管理</h2>
    <el-card class="page-card">
      <!-- 搜索筛选 -->
      <div class="toolbar">
        <el-input
          v-model="query.username"
          placeholder="用户名（模糊）"
          clearable
          style="width: 180px"
          @keyup.enter="load"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
        <span class="spacer" />
        <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
      </div>

      <!-- 列表 -->
      <el-table :data="rows" v-loading="loading" border stripe height="480" @sort-change="onSortChange">
        <el-table-column prop="id" label="ID" width="70" sortable="custom" />
        <el-table-column prop="username" label="用户名" width="130" sortable="custom" />
        <el-table-column prop="displayName" label="昵称" width="120" />
        <el-table-column prop="mobile" label="手机号" width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部门" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="(n, i) in (row.deptNames || [])" :key="'d' + i" class="role-tag" size="small" type="info">{{ n }}</el-tag>
            <span v-if="!(row.deptNames || []).length" class="text-muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-tag
              v-for="(n, i) in (row.roleNames || [])"
              :key="'r' + i"
              class="role-tag"
              size="small"
              :type="row.roleCodes && row.roleCodes[i] === 'SUPER_ADMIN' ? 'danger' : 'primary'"
            >{{ n }}</el-tag>
            <span v-if="!(row.roleNames || []).length" class="text-muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" sortable="custom" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" :icon="Key" @click="openReset(row)">重置密码</el-button>
            <!-- 硬删除：物理删除，需二次确认 -->
            <el-popconfirm title="确认物理删除该用户？此操作不可恢复" width="220" @confirm="onDelete(row)">
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

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="520px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位" />
        </el-form-item>
        <el-form-item label="昵称" prop="displayName">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.mobile" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门" prop="deptIds">
          <el-select v-model="form.deptIds" multiple filterable clearable style="width: 100%" placeholder="选择部门（可多选，至少 1 个）">
            <el-option v-for="o in orgOptions" :key="o.id" :label="o.orgName" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="主部门">
          <el-select v-model="form.primaryDeptId" clearable filterable style="width: 100%" placeholder="不选则取首个部门">
            <el-option v-for="o in selectedDeptOptions" :key="o.id" :label="o.orgName" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple filterable style="width: 100%" placeholder="选择角色（可多选，至少 1 个）">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="主角色">
          <el-select v-model="form.primaryRoleId" clearable filterable style="width: 100%" placeholder="不选则取首个角色">
            <el-option v-for="r in selectedRoleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetVisible" title="重置密码" width="420px">
      <el-form :model="resetForm" ref="resetRef" label-width="90px">
        <el-form-item
          label="新密码"
          prop="password"
          :rules="[{ required: true, min: 8, message: '至少 8 位', trigger: 'blur' }]"
        >
          <el-input v-model="resetForm.password" type="password" show-password placeholder="至少 8 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReset">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft, Plus, Edit, Key, Delete } from '@element-plus/icons-vue'
import { pageUsers, createUser, updateUser, deleteUser, resetPassword } from '@/api/user'
import { listRoles } from '@/api/role'
import { orgTree } from '@/api/org'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const roleOptions = ref([])
const orgOptions = ref([])
const query = reactive({ page: 1, size: 10, username: '', status: '', sortField: '', sortDirection: '' })

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = reactive({
  id: null, username: '', password: '', displayName: '', mobile: '', email: '',
  status: 'ACTIVE',
  roleIds: [], primaryRoleId: null,
  deptIds: [], primaryDeptId: null
})
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 8, message: '至少 8 位', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  roleIds: [{ required: true, type: 'array', min: 1, message: '至少绑定一个角色', trigger: 'change' }],
  deptIds: [{ required: true, type: 'array', min: 1, message: '至少归属一个部门', trigger: 'change' }]
}
// 主角色 / 主部门的候选：仅从已选角色 / 部门中挑选（mock 与真实后端语义一致）
const selectedRoleOptions = computed(() => roleOptions.value.filter((r) => form.roleIds.includes(r.id)))
const selectedDeptOptions = computed(() => orgOptions.value.filter((o) => form.deptIds.includes(o.id)))

const resetVisible = ref(false)
const resetRef = ref()
const resetForm = reactive({ id: null, password: '' })

async function load() {
  loading.value = true
  try {
    const data = await pageUsers({ ...query })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/**
 * 表头排序：走服务端白名单，前端只提交字段名（id/username/createdAt），
 * 不拼 SQL。order 为空表示取消排序，回到服务端默认（id 倒序）。
 */
function onSortChange({ prop, order }) {
  query.sortField = order ? prop : ''
  query.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  query.page = 1
  load()
}

function resetQuery() {
  query.username = ''
  query.status = ''
  query.sortField = ''
  query.sortDirection = ''
  query.page = 1
  load()
}

/** 拍平部门树为下拉选项（带缩进前缀）。 */
function flattenOrgs(nodes, depth = 0, acc = []) {
  ;(nodes || []).forEach((n) => {
    acc.push({ id: n.id, orgName: `${'　'.repeat(depth)}${n.orgName}` })
    if (n.children) flattenOrgs(n.children, depth + 1, acc)
  })
  return acc
}

async function ensureRoles() {
  if (roleOptions.value.length === 0) roleOptions.value = await listRoles()
  if (orgOptions.value.length === 0) orgOptions.value = flattenOrgs(await orgTree())
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, {
    id: null, username: '', password: '', displayName: '', mobile: '', email: '',
    status: 'ACTIVE', roleIds: [], primaryRoleId: null, deptIds: [], primaryDeptId: null
  })
  ensureRoles()
  dialogVisible.value = true
}

async function openEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id, username: row.username, password: '', displayName: row.displayName,
    mobile: row.mobile, email: row.email, status: row.status,
    roleIds: [...(row.roleIds || [])],
    primaryRoleId: row.primaryRoleId ?? (row.roleIds && row.roleIds[0] != null ? row.roleIds[0] : null),
    deptIds: [...(row.deptIds || [])],
    primaryDeptId: row.primaryDeptId ?? (row.deptIds && row.deptIds[0] != null ? row.deptIds[0] : null)
  })
  ensureRoles()
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value) {
      await updateUser(form.id, {
        displayName: form.displayName, mobile: form.mobile, email: form.email,
        status: form.status,
        roleIds: form.roleIds, primaryRoleId: form.primaryRoleId || undefined,
        deptIds: form.deptIds, primaryDeptId: form.primaryDeptId || undefined
      })
      ElMessage.success('更新成功')
    } else {
      await createUser({
        username: form.username, password: form.password, displayName: form.displayName,
        mobile: form.mobile, email: form.email,
        roleIds: form.roleIds, primaryRoleId: form.primaryRoleId || undefined,
        deptIds: form.deptIds, primaryDeptId: form.primaryDeptId || undefined
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function openReset(row) {
  resetForm.id = row.id
  resetForm.password = ''
  resetVisible.value = true
}

async function submitReset() {
  const valid = await resetRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await resetPassword(resetForm.id, resetForm.password)
    ElMessage.success('密码已重置')
    resetVisible.value = false
  } finally {
    saving.value = false
  }
}

// 物理删除（硬删除）：调用 DELETE /api/system/users/{id}
async function onDelete(row) {
  try {
    await deleteUser(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>
