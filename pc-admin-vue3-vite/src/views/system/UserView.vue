<template>
  <div>
    <h2 class="page-title">{{ t('route.user') }}</h2>
    <el-card class="page-card">
      <!-- 搜索筛选：条件区（上）/ 操作区（下）分区布局 -->
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('system.user.username') }}</span>
            <el-input
              v-model="query.username"
              :placeholder="t('system.user.usernameFuzzy')"
              clearable
              style="width: 180px"
              @keyup.enter="load"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('common.status') }}</span>
            <el-select v-model="query.status" :placeholder="t('common.status')" clearable style="width: 140px">
              <el-option :label="t('common.enabled')" value="ACTIVE" />
              <el-option :label="t('common.disabled')" value="DISABLED" />
            </el-select>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Search" @click="load">{{ t('system.common.query') }}</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">{{ t('common.reset') }}</el-button>
          </div>
          <div class="action-buttons">
            <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('system.user.addTitle') }}</el-button>
          </div>
        </div>
      </div>

      <!-- 列表 -->
      <!-- 不设 height：随内容自适应，与其他列表页一致 -->
      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <!-- 雪花 ID 为 19 位字符串，width=70 只能显示前 6 位（E2E F6） -->
        <el-table-column prop="id" label="ID" width="180" sortable="custom" show-overflow-tooltip />
        <el-table-column prop="username" :label="t('system.user.username')" width="130" sortable="custom" />
        <el-table-column prop="displayName" :label="t('system.user.nickname')" width="120" />
        <el-table-column prop="mobile" :label="t('system.user.mobile')" width="140" />
        <el-table-column prop="email" :label="t('system.user.email')" min-width="180" />
        <el-table-column :label="t('common.status')" width="90">
          <template #default="{ row }">
            <!-- 状态标签统一走 utils/status 映射 -->
            <el-tag :type="statusTag(row.status).type">{{ statusTag(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('system.department.label')" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="(n, i) in (row.deptNames || [])" :key="'d' + i" class="role-tag" size="small" type="info">{{ n }}</el-tag>
            <span v-if="!(row.deptNames || []).length" class="text-muted">{{ t('system.common.unassigned') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('system.role.label')" min-width="180">
          <template #default="{ row }">
            <el-tag
              v-for="(n, i) in (row.roleNames || [])"
              :key="'r' + i"
              class="role-tag"
              size="small"
              :type="row.roleCodes && row.roleCodes[i] === 'SUPER_ADMIN' ? 'danger' : 'primary'"
            >{{ n }}</el-tag>
            <span v-if="!(row.roleNames || []).length" class="text-muted">{{ t('system.common.unassigned') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('system.post.label')" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="(n, i) in (row.postNames || [])" :key="'p' + i" class="role-tag" size="small" type="warning">{{ n }}</el-tag>
            <span v-if="!(row.postNames || []).length" class="text-muted">{{ t('system.common.unassigned') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('system.common.createTime')" width="170" sortable="custom" />
        <el-table-column :label="t('common.operation')" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="warning" :icon="Key" @click="openReset(row)">{{ t('system.user.resetPassword') }}</el-button>
            <!-- 硬删除：物理删除，需二次确认 -->
            <el-popconfirm :title="t('system.user.confirmPhysicalDelete')" width="220" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">{{ t('common.delete') }}</el-button>
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
    <el-dialog v-model="dialogVisible" :title="isEdit ? t('system.user.editTitle') : t('system.user.addTitle')" width="520px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item :label="t('system.user.username')" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item v-if="!isEdit" :label="t('system.user.password')" prop="password">
          <el-input v-model="form.password" type="password" show-password :placeholder="t('system.common.atLeast8')" />
        </el-form-item>
        <el-form-item :label="t('system.user.nickname')" prop="displayName">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item :label="t('system.user.mobile')">
          <el-input v-model="form.mobile" />
        </el-form-item>
        <el-form-item :label="t('system.user.email')">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :label="t('common.enabled')" value="ACTIVE" />
            <el-option :label="t('common.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.department.label')" prop="deptIds">
          <el-select v-model="form.deptIds" multiple filterable clearable style="width: 100%" :placeholder="t('system.department.selectMulti')">
            <el-option v-for="o in orgOptions" :key="o.id" :label="o.orgName" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.department.primary')">
          <el-select v-model="form.primaryDeptId" clearable filterable style="width: 100%" :placeholder="t('system.department.defaultFirst')">
            <el-option v-for="o in selectedDeptOptions" :key="o.id" :label="o.orgName" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.role.label')" prop="roleIds">
          <el-select v-model="form.roleIds" multiple filterable style="width: 100%" :placeholder="t('system.role.selectMulti')">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.role.primary')">
          <el-select v-model="form.primaryRoleId" clearable filterable style="width: 100%" :placeholder="t('system.role.defaultFirst')">
            <el-option v-for="r in selectedRoleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.post.label')">
          <el-select v-model="form.postIds" multiple filterable clearable style="width: 100%" :placeholder="t('system.post.selectMulti')">
            <el-option v-for="p in postOptions" :key="p.id" :label="p.postName" :value="p.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetVisible" :title="t('system.user.resetPassword')" width="420px">
      <el-form :model="resetForm" ref="resetRef" label-width="90px">
        <el-form-item
          :label="t('system.user.newPassword')"
          prop="password"
          :rules="[{ required: true, min: 8, message: t('system.common.atLeast8'), trigger: 'blur' }]"
        >
          <el-input v-model="resetForm.password" type="password" show-password :placeholder="t('system.common.atLeast8')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="submitReset">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft, Plus, Edit, Key, Delete } from '@element-plus/icons-vue'
import { pageUsers, createUser, updateUser, deleteUser, resetPassword } from '@/api/user'
import { listRoles } from '@/api/role'
import { orgTree } from '@/api/org'
import { listPostOptions } from '@/api/post'
// 状态标签映射 / 部门树拍平均统一到 utils
import { statusTag } from '@/utils/status'
import { flattenTree } from '@/utils/tree'
import { validateOrWarn } from '@/utils/validate'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const roleOptions = ref([])
const orgOptions = ref([])
const postOptions = ref([])
const query = reactive({ page: 1, size: 10, username: '', status: '', sortField: '', sortDirection: '' })

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = reactive({
  id: null, username: '', password: '', displayName: '', mobile: '', email: '',
  status: 'ACTIVE',
  roleIds: [], primaryRoleId: null,
  deptIds: [], primaryDeptId: null,
  postIds: []
})
const rules = {
  username: [{ required: true, message: t('system.user.pleaseEnterUsername'), trigger: 'blur' }],
  password: [{ required: true, min: 8, message: t('system.common.atLeast8'), trigger: 'blur' }],
  displayName: [{ required: true, message: t('system.user.pleaseEnterNickname'), trigger: 'blur' }],
  roleIds: [{ required: true, type: 'array', min: 1, message: t('system.user.atLeastOneRole'), trigger: 'change' }],
  deptIds: [{ required: true, type: 'array', min: 1, message: t('system.user.atLeastOneDept'), trigger: 'change' }]
}
// 主角色 / 主部门的候选：仅从已选角色 / 部门中挑选
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

async function ensureRoles() {
  try {
    if (roleOptions.value.length === 0) roleOptions.value = await listRoles()
  } catch (e) {
    console.error('[UserView] load role options failed', e)
  }
  try {
    if (orgOptions.value.length === 0) {
      orgOptions.value = flattenTree(await orgTree(), { nameKey: 'orgName', labelKey: 'orgName' })
    }
  } catch (e) {
    console.error('[UserView] load org options failed', e)
  }
  try {
    if (postOptions.value.length === 0) postOptions.value = await listPostOptions()
  } catch (e) {
    console.error('[UserView] load post options failed', e)
  }
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, {
    id: null, username: '', password: '', displayName: '', mobile: '', email: '',
    status: 'ACTIVE', roleIds: [], primaryRoleId: null, deptIds: [], primaryDeptId: null, postIds: []
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
    primaryDeptId: row.primaryDeptId ?? (row.deptIds && row.deptIds[0] != null ? row.deptIds[0] : null),
    postIds: [...(row.postIds || [])]
  })
  ensureRoles()
  dialogVisible.value = true
}

async function onSubmit() {
  // 校验失败必须给出可见提示：只让字段变红时，用户在弹窗底部点「保存」看不到任何反应（E2E F8）。
  if (!(await validateOrWarn(formRef))) return
  saving.value = true
  try {
    if (isEdit.value) {
      await updateUser(form.id, {
        displayName: form.displayName, mobile: form.mobile, email: form.email,
        status: form.status,
        roleIds: form.roleIds, primaryRoleId: form.primaryRoleId || undefined,
        deptIds: form.deptIds, primaryDeptId: form.primaryDeptId || undefined,
        postIds: form.postIds
      })
      ElMessage.success(t('system.common.updateSuccess'))
    } else {
      await createUser({
        username: form.username, password: form.password, displayName: form.displayName,
        mobile: form.mobile, email: form.email,
        roleIds: form.roleIds, primaryRoleId: form.primaryRoleId || undefined,
        deptIds: form.deptIds, primaryDeptId: form.primaryDeptId || undefined,
        postIds: form.postIds
      })
      ElMessage.success(t('system.common.createSuccess'))
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
  if (!(await validateOrWarn(resetRef.value))) return
  saving.value = true
  try {
    await resetPassword(resetForm.id, resetForm.password)
    ElMessage.success(t('system.user.passwordReset'))
    resetVisible.value = false
  } finally {
    saving.value = false
  }
}

// 物理删除（硬删除）：调用 DELETE /api/system/users/{id}
async function onDelete(row) {
  try {
    await deleteUser(row.id)
    ElMessage.success(t('system.department.deleted'))
    load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>
