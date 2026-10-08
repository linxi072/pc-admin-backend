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
        <el-table-column label="数据权限" width="150">
          <template #default="{ row }">
            <el-tag v-if="scopeLabelOf(row.id)" size="small" type="warning">
              {{ scopeLabelOf(row.id) }}
            </el-tag>
            <el-tag v-else size="small" type="info">未配置（全部数据）</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" :icon="Lock" @click="openDataScope(row)">数据权限</el-button>
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

    <!-- 数据权限：按资源配置可见范围。当前「用户数据」「部门数据」已接入过滤，其余资源预留。 -->
    <el-dialog v-model="scopeVisible" title="数据权限" width="560px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="数据权限决定该角色能看到哪些数据，与菜单/接口权限相互独立。"
        description="未配置的资源默认不限制（全部数据）。修改后立即生效，无需重新登录。"
        style="margin-bottom: 16px"
      />
      <el-form label-width="110px">
        <el-form-item label="角色">
          <span>{{ scopeForm.roleName }}</span>
        </el-form-item>
        <el-form-item label="资源">
          <el-select v-model="scopeForm.resourceCode" style="width: 100%" @change="onResourceChange">
            <el-option v-for="r in SUPPORTED_RESOURCES" :key="r.code" :label="r.label" :value="r.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据范围">
          <el-radio-group v-model="scopeForm.scopeType">
            <el-radio v-for="opt in SCOPE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
          <div class="scope-hint">{{ scopeHint }}</div>
        </el-form-item>
        <el-form-item v-if="scopeForm.scopeType === 'CUSTOM'" label="可见部门">
          <el-tree
            ref="orgTreeRef"
            :data="orgTreeData"
            :props="{ label: 'orgName', children: 'children' }"
            node-key="id"
            show-checkbox
            class="scope-tree"
          />
          <div v-if="!orgTreeData.length" class="scope-hint">尚未维护部门，无法配置自定义范围。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scopeVisible = false">取消</el-button>
        <el-button type="primary" :loading="scopeSaving" @click="onSaveScope">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Edit, Delete, Lock } from '@element-plus/icons-vue'
import { listRoles, getRole, createRole, updateRole, deleteRole, listRoleDataScopes, saveRoleDataScope } from '@/api/role'
import { menuTree as fetchMenuTree } from '@/api/menu'
import { listApiResources } from '@/api/apiResource'
import { orgTree } from '@/api/org'

// 数据范围选项，与后端 DataScopeType 一一对应；label 供页面展示，value 直接作为接口入参
const SCOPE_OPTIONS = [
  { value: 'ALL', label: '全部数据', desc: '不限制，能看到该资源的全部数据。' },
  { value: 'DEPT_AND_CHILD', label: '本部门及下级', desc: '用户所在部门，以及该部门所有下级部门的数据。' },
  { value: 'DEPT', label: '仅本部门', desc: '仅用户所在部门的数据，不含下级部门。' },
  { value: 'SELF', label: '仅本人', desc: '仅能看自己的数据，与部门无关。' },
  { value: 'CUSTOM', label: '自定义部门', desc: '仅能看下方勾选部门的数据。' }
]

// 当前接入数据权限过滤的资源。前端不提供自由输入，避免配出后端未接线的资源编码导致「配了却不生效」。
const SUPPORTED_RESOURCES = [
  { code: 'system:user', label: '用户数据' },
  { code: 'system:org', label: '部门数据' }
]

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

// ---- 数据权限弹窗 ----
const scopeVisible = ref(false)
const scopeSaving = ref(false)
const orgTreeRef = ref()
const orgTreeData = ref([])
// roleId -> 已配置规则的 scopeType，用于列表页概览标签
const scopeMap = ref({})
// 当前角色已拉取的全部数据权限规则（跨资源），切换资源时复用，不重新请求
const currentRules = ref([])
const scopeForm = reactive({
  roleId: null, roleName: '', resourceCode: 'system:user', scopeType: 'ALL', orgIds: []
})

const scopeHint = computed(() => {
  const opt = SCOPE_OPTIONS.find((o) => o.value === scopeForm.scopeType)
  return opt?.desc || ''
})

/** 列表页展示已配置的资源（如「用户数据、部门数据」）；未配置返回空串（模板降级为「未配置」）。 */
function scopeLabelOf(roleId) {
  return scopeMap.value[roleId] || ''
}

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
    await loadScopeSummaries(roles)
  } finally {
    loading.value = false
  }
}

/**
 * 拉取每个角色的数据权限概览。
 * 单个角色查询失败不应让整个列表加载失败，故逐个 catch 并跳过。
 * 概览展示已配置的资源名（如「用户数据、部门数据」），多个资源用顿号连接。
 */
async function loadScopeSummaries(roles) {
  const entries = await Promise.all(
    roles.map(async (r) => {
      try {
        const rules = await listRoleDataScopes(r.id)
        if (!rules.length) return null
        const labels = rules
          .map((x) => SUPPORTED_RESOURCES.find((s) => s.code === x.resourceCode)?.label)
          .filter(Boolean)
        return labels.length ? [r.id, labels.join('、')] : null
      } catch (e) {
        return null
      }
    })
  )
  const map = {}
  entries.filter(Boolean).forEach(([id, label]) => {
    map[id] = label
  })
  scopeMap.value = map
}

/** 打开数据权限弹窗：载入该角色已保存的规则。 */
async function openDataScope(row) {
  Object.assign(scopeForm, {
    roleId: row.id,
    roleName: row.roleName,
    resourceCode: SUPPORTED_RESOURCES[0].code,
    scopeType: 'ALL',
    orgIds: []
  })
  scopeVisible.value = true
  await nextTick()
  orgTreeRef.value?.setCheckedKeys([])
  try {
    const [rules, tree] = await Promise.all([listRoleDataScopes(row.id), orgTree()])
    currentRules.value = rules || []
    orgTreeData.value = tree || []
    const rule = rules.find((x) => x.resourceCode === scopeForm.resourceCode)
    if (rule) {
      scopeForm.scopeType = rule.scopeType
      scopeForm.orgIds = [...(rule.orgIds || [])]
    }
    await nextTick()
    // 自定义范围需回显已勾选部门；非自定义时保持空选
    if (scopeForm.scopeType === 'CUSTOM') {
      orgTreeRef.value?.setCheckedKeys(scopeForm.orgIds)
    }
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

/** 切换资源时，从已拉取的规则中重新定位该资源的配置（不重新请求）。 */
function onResourceChange() {
  const rule = currentRules.value.find((x) => x.resourceCode === scopeForm.resourceCode)
  if (rule) {
    scopeForm.scopeType = rule.scopeType
    scopeForm.orgIds = [...(rule.orgIds || [])]
  } else {
    scopeForm.scopeType = 'ALL'
    scopeForm.orgIds = []
  }
  nextTick(() => {
    if (scopeForm.scopeType === 'CUSTOM') {
      orgTreeRef.value?.setCheckedKeys(scopeForm.orgIds)
    } else {
      orgTreeRef.value?.setCheckedKeys([])
    }
  })
}

/** 保存数据权限：覆盖式提交，CUSTOM 之外不带 orgIds。 */
async function onSaveScope() {
  if (scopeForm.scopeType === 'CUSTOM') {
    // 取勾选的叶子节点即可，避免把父节点也写进规则
    const checked = orgTreeRef.value?.getCheckedKeys(true) || []
    if (!checked.length) {
      ElMessage.warning('请至少勾选一个部门')
      return
    }
    scopeForm.orgIds = checked
  } else {
    scopeForm.orgIds = []
  }
  scopeSaving.value = true
  try {
    await saveRoleDataScope(scopeForm.roleId, {
      resourceCode: scopeForm.resourceCode,
      scopeType: scopeForm.scopeType,
      orgIds: scopeForm.orgIds
    })
    ElMessage.success('数据权限已保存')
    scopeVisible.value = false
    scopeMap.value = { ...scopeMap.value, [scopeForm.roleId]: scopeForm.scopeType }
  } catch (e) {
    /* 拦截器已统一提示 */
  } finally {
    scopeSaving.value = false
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
