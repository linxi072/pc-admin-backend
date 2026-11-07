<template>
  <div>
    <h2 class="page-title">{{ t('route.role') }}</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <span class="spacer" />
        <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('system.role.addTitle') }}</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="roleCode" :label="t('system.role.code')" width="140" />
        <el-table-column prop="roleName" :label="t('system.role.name')" width="140" />
        <el-table-column :label="t('system.common.type')" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ row.roleType === 'SYSTEM' ? t('system.role.sysRole') : t('system.role.bizRole') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="90">
          <template #default="{ row }">
            <!-- 状态标签统一走 utils/status 映射 -->
            <el-tag :type="statusTag(row.status).type">{{ statusTag(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortNo" :label="t('system.common.sort')" width="80" />
        <el-table-column :label="t('system.role.permissionOverview')" min-width="200">
          <template #default="{ row }">
            <span>菜单 {{ row.menuIds?.length || 0 }} · 接口 {{ row.apiIds?.length || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('system.role.dataPermission')" width="150">
          <template #default="{ row }">
            <el-tag v-if="scopeLabelOf(row.id)" size="small" type="warning">
              {{ scopeLabelOf(row.id) }}
            </el-tag>
            <el-tag v-else size="small" type="info">{{ t('system.common.notConfiguredAll') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="warning" :icon="Lock" @click="openDataScope(row)">{{ t('system.role.dataPermission') }}</el-button>
            <el-popconfirm :title="t('system.role.confirmDelete')" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">{{ t('common.delete') }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('system.role.editTitle') : t('system.role.addTitle')" width="640px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item :label="t('system.role.code')" prop="roleCode">
          <el-input v-model="form.roleCode" :disabled="isEdit" :placeholder="t('system.post.exampleCode')" />
        </el-form-item>
        <el-form-item :label="t('system.role.name')" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item :label="t('system.common.type')">
          <el-select v-model="form.roleType" style="width: 100%">
            <el-option :label="t('system.role.bizRole')" value="BUSINESS" />
            <el-option :label="t('system.role.sysRole')" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :label="t('common.enabled')" value="ACTIVE" />
            <el-option :label="t('common.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.common.sort')">
          <el-input-number v-model="form.sortNo" :min="0" />
        </el-form-item>

        <!-- 权限树：菜单与接口整合为单棵权限树，接口归入所属菜单之下。
             勾选菜单默认勾选其下接口权限；可单独取消某接口；保存时分别回填 menuIds / apiIds。 -->
        <el-form-item :label="t('system.role.menuApiPermission')">
          <div class="perm-toolbar" style="margin-bottom: 8px; display: flex; gap: 4px">
            <el-button size="small" text type="primary" @click="expandAll">{{ t('system.common.expandAll') }}</el-button>
            <el-button size="small" text type="primary" @click="collapseAll">{{ t('system.common.collapseAll') }}</el-button>
            <el-button size="small" text type="primary" @click="checkAll">{{ t('system.common.selectAll') }}</el-button>
            <el-button size="small" text @click="clearAll">{{ t('system.common.clear') }}</el-button>
          </div>
          <div class="tree-panel">
            <el-tree
              ref="permTreeRef"
              :data="permTree"
              :props="{ label: 'label', children: 'children' }"
              node-key="id"
              show-checkbox
              check-strictly
              :expand-on-click-node="false"
              default-expand-all
              @check-change="onPermCheckChange"
            />
          </div>
          <div style="color: #909399; font-size: 12px; margin-top: 6px">
            接口已归入对应菜单之下；勾选菜单将默认勾选其下接口权限，也可单独取消某个接口。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 数据权限：按资源配置可见范围。当前「用户数据」「部门数据」已接入过滤，其余资源预留。 -->
    <el-dialog v-model="scopeVisible" :title="t('system.role.dataPermission')" width="560px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="t('system.role.dataPermissionDesc')"
        :description="t('system.config.notConfiguredHint')"
        style="margin-bottom: 16px"
      />
      <el-form label-width="110px">
        <el-form-item :label="t('system.role.label')">
          <span>{{ scopeForm.roleName }}</span>
        </el-form-item>
        <el-form-item :label="t('system.common.resource')">
          <el-select v-model="scopeForm.resourceCode" style="width: 100%" @change="onResourceChange">
            <el-option v-for="r in SUPPORTED_RESOURCES" :key="r.code" :label="r.label" :value="r.code" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.role.dataScope')">
          <el-radio-group v-model="scopeForm.scopeType">
            <el-radio v-for="opt in SCOPE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
          <div class="scope-hint">{{ scopeHint }}</div>
        </el-form-item>
        <el-form-item v-if="scopeForm.scopeType === 'CUSTOM'" :label="t('system.department.visible')">
          <el-tree
            ref="orgTreeRef"
            :data="orgTreeData"
            :props="{ label: 'orgName', children: 'children' }"
            node-key="id"
            show-checkbox
            class="scope-tree"
          />
          <div v-if="!orgTreeData.length" class="scope-hint">{{ t('system.department.notMaintained') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scopeVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="scopeSaving" @click="onSaveScope">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Edit, Delete, Lock } from '@element-plus/icons-vue'
import { listRoles, getRole, createRole, updateRole, deleteRole, listRoleDataScopes, saveRoleDataScope } from '@/api/role'
import { menuTree as fetchMenuTree } from '@/api/menu'
import { listApiResources } from '@/api/apiResource'
import { orgTree } from '@/api/org'
// 状态标签映射统一到 utils
import { statusTag } from '@/utils/status'
import { validateOrWarn } from '@/utils/validate'

// 数据范围选项，与后端 DataScopeType 一一对应；label 供页面展示，value 直接作为接口入参
const SCOPE_OPTIONS = [
  { value: 'ALL', label: t('system.datascope.all'), desc: t('system.datascope.allDesc') },
  { value: 'DEPT_AND_CHILD', label: t('system.datascope.deptAndSub'), desc: t('system.datascope.userDept') },
  { value: 'DEPT', label: t('system.department.selfOnly'), desc: t('system.datascope.deptSelfNoSub') },
  { value: 'SELF', label: t('system.datascope.self'), desc: t('system.datascope.userSelf') },
  { value: 'CUSTOM', label: t('system.department.custom'), desc: t('system.datascope.deptCustom') }
]

// 当前接入数据权限过滤的资源。前端不提供自由输入，避免配出后端未接线的资源编码导致「配了却不生效」。
const SUPPORTED_RESOURCES = [
  { code: 'system:user', label: t('system.user.data') },
  { code: 'system:org', label: t('system.department.data') }
]

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const permTree = ref([])
const permTreeRef = ref()
// 权限树节点 key（前缀 M:/A:）→ 原始数值 id 映射，便于提交时拆分 menuIds / apiIds
const permMenuId = ref(new Map())
const permApiId = ref(new Map())

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
  roleCode: [{ required: true, message: t('system.role.pleaseEnterCode'), trigger: 'blur' }],
  roleName: [{ required: true, message: t('system.role.pleaseEnterName'), trigger: 'blur' }]
}

// ---- 菜单与接口权限整合为单棵权限树 ----
// 菜单与接口资源通过 permission_code 的「资源前缀」归属：
//   菜单 permission_code 形如 system:user:read，接口形如 system:user:create，
//   二者资源前缀均为 system:user，视为同一资源的「菜单」与「接口权限」，接口归入菜单之下。
// 历史种子数据存在两处命名不一致，用别名对齐：api-resource↔api、department↔org。
const RESOURCE_ALIASES = {
  'system:api-resource': 'system:api',
  'system:department': 'system:org'
}

/** 从 permission_code 提取资源前缀：system:user:read → system:user；无冒号则原样返回。 */
function resourceKeyOf(pc) {
  if (!pc) return null
  const idx = pc.lastIndexOf(':')
  return idx > 0 ? pc.substring(0, idx) : pc
}

// 权限树节点 key 统一加前缀，避免菜单 id 与接口 id 冲突，并用于提交时拆分：
//   菜单节点 key = 'M:' + menuId；接口节点 key = 'A:' + apiId
function buildPermissionTree(menus, apis) {
  permMenuId.value.clear()
  permApiId.value.clear()
  const apiByKey = new Map()
  for (const a of apis || []) {
    const k = resourceKeyOf(a.permissionCode)
    if (!k) continue
    const key = RESOURCE_ALIASES[k] || k
    if (!apiByKey.has(key)) apiByKey.set(key, [])
    apiByKey.get(key).push(a)
  }
  const usedApiIds = new Set()
  const makeApiNodes = (list) =>
    (list || []).map((a) => {
      const key = 'A:' + a.id
      permApiId.value.set(key, a.id)
      return { id: key, apiId: a.id, label: `${a.httpMethod} ${a.resourceName}`, permissionCode: a.permissionCode }
    })
  const walk = (nodes) =>
    (nodes || []).map((n) => {
      const k = resourceKeyOf(n.permissionCode)
      const key = k ? RESOURCE_ALIASES[k] || k : null
      let children = n.children && n.children.length ? walk(n.children) : []
      if (key && apiByKey.has(key)) {
        const matched = apiByKey.get(key)
        matched.forEach((a) => usedApiIds.add(a.id))
        children = children.concat(makeApiNodes(matched))
      }
      const mKey = 'M:' + n.id
      permMenuId.value.set(mKey, n.id)
      return { id: mKey, menuId: n.id, label: n.menuName, permissionCode: n.permissionCode, children }
    })
  const tree = walk(menus)
  // 未能归属到任何菜单的接口，归入「其他接口权限」分组，保证仍可勾选
  const orphans = (apis || []).filter((a) => !usedApiIds.has(a.id))
  if (orphans.length) {
    tree.push({ id: 'M:orphan', menuId: null, label: t('system.role.otherApiPermission'), children: makeApiNodes(orphans) })
  }
  return tree
}

// 权限树全部 key（菜单 + 接口）
const allPermKeys = computed(() => {
  const keys = []
  const walk = (ns) => ns.forEach((n) => { keys.push(n.id); if (n.children) walk(n.children) })
  walk(permTree.value)
  return keys
})

// 程序化回填（打开弹窗/初始化）期间抑制级联，避免回显时被联动改写
const programmatic = ref(false)
// 级联执行期间抑制子节点 check-change 重入
const cascading = ref(false)

/** 收集节点下所有后代节点 key（用于勾选菜单时级联其下接口与子菜单）。 */
function collectDescendantKeys(tn) {
  const keys = []
  const walk = (n) => {
    n.childNodes.forEach((c) => {
      keys.push(c.key)
      if (c.childNodes && c.childNodes.length) walk(c)
    })
  }
  walk(tn)
  return keys
}

/** 权限树 check-change：勾选/取消菜单时，级联其下全部接口与子菜单。接口节点不触发级联。 */
function onPermCheckChange(data, checked) {
  if (programmatic.value || cascading.value) return
  if (!String(data.id).startsWith('M:')) return
  const tree = permTreeRef.value
  const tn = tree && tree.getNode(data.id)
  if (!tn) return
  cascading.value = true
  collectDescendantKeys(tn).forEach((k) => tree.setChecked(k, checked, false))
  cascading.value = false
}

/** 从已勾选 key 中拆分出 menuIds / apiIds（排除虚拟分组节点）。 */
function collectCheckedIds(keys) {
  const menuIds = []
  const apiIds = []
  for (const k of keys || []) {
    const s = String(k)
    if (s.startsWith('A:')) {
      const id = permApiId.value.get(s)
      if (id != null) apiIds.push(id)
    } else if (s.startsWith('M:')) {
      const id = permMenuId.value.get(s)
      if (id != null) menuIds.push(id)
    }
  }
  return { menuIds, apiIds }
}

// ---- 权限树工具栏：展开/折叠/全选/清空 ----
function allTreeNodes() {
  const tree = permTreeRef.value
  if (!tree || !tree.store) return []
  if (typeof tree.store._getAllNodes === 'function') return tree.store._getAllNodes()
  return []
}
function expandAll() {
  allTreeNodes().forEach((n) => { n.expanded = true })
}
function collapseAll() {
  allTreeNodes().forEach((n) => { n.expanded = false })
}
function checkAll() {
  programmatic.value = true
  permTreeRef.value?.setCheckedKeys(allPermKeys.value)
  nextTick(() => { programmatic.value = false })
}
function clearAll() {
  programmatic.value = true
  permTreeRef.value?.setCheckedKeys([])
  nextTick(() => { programmatic.value = false })
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
    permTree.value = buildPermissionTree(menus, apis)
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
      ElMessage.warning(t('system.department.pleaseCheck'))
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
    ElMessage.success(t('system.role.dataPermissionSaved'))
    scopeVisible.value = false
    // 保存后按加载同口径刷新该角色概览标签：早期直接回写 scopeForm.scopeType（枚举值），
    // 列表会把 "CUSTOM"/"ALL" 原文当文案渲染，且单资源保存会覆盖掉多资源 joined 标签
    await refreshScopeSummary(scopeForm.roleId)
  } catch (e) {
    /* 拦截器已统一提示 */
  } finally {
    scopeSaving.value = false
  }
}

/** 按与 {@link loadScopeSummaries} 完全相同的口径刷新单个角色的数据权限概览标签。 */
async function refreshScopeSummary(roleId) {
  try {
    const rules = await listRoleDataScopes(roleId)
    const labels = rules
      .map((x) => SUPPORTED_RESOURCES.find((s) => s.code === x.resourceCode)?.label)
      .filter(Boolean)
    if (labels.length) {
      scopeMap.value = { ...scopeMap.value, [roleId]: labels.join('、') }
    } else {
      const next = { ...scopeMap.value }
      delete next[roleId]
      scopeMap.value = next
    }
  } catch (e) {
    /* 刷新失败时列表保留旧标签，不阻塞保存成功提示 */
  }
}

function openCreate() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
  nextTick(() => {
    programmatic.value = true
    permTreeRef.value?.setCheckedKeys([])
    nextTick(() => { programmatic.value = false })
  })
}

async function openEdit(row) {
  isEdit.value = true
  resetForm()
  let detail
  try {
    detail = await getRole(row.id)
  } catch (e) {
    ElMessage.error(t('system.loadFailedX'))
    return
  }
  Object.assign(form, {
    id: detail.id, roleCode: detail.roleCode, roleName: detail.roleName,
    roleType: detail.roleType, status: detail.status, sortNo: detail.sortNo,
    menuIds: [...(detail.menuIds || [])], apiIds: [...(detail.apiIds || [])]
  })
  dialogVisible.value = true
  await nextTick()
  // 权限树回填：勾选该角色已分配的菜单 / 接口（统一 key 前缀）
  // 先置 programmatic 抑制级联，避免「勾选菜单」事件把接口权限改写成全量
  programmatic.value = true
  const keys = [
    ...(detail.menuIds || []).map((id) => 'M:' + id),
    ...(detail.apiIds || []).map((id) => 'A:' + id)
  ].filter((k) => permMenuId.value.has(k) || permApiId.value.has(k))
  permTreeRef.value?.setCheckedKeys(keys)
  nextTick(() => { programmatic.value = false })
}

async function onSubmit() {
  if (!(await validateOrWarn(formRef.value))) return
  saving.value = true
  try {
    const checked = permTreeRef.value ? permTreeRef.value.getCheckedKeys() : []
    const { menuIds, apiIds } = collectCheckedIds(checked)
    const payload = {
      roleCode: form.roleCode,
      roleName: form.roleName,
      roleType: form.roleType,
      status: form.status,
      sortNo: form.sortNo,
      menuIds,
      apiIds
    }
    if (isEdit.value) {
      await updateRole(form.id, payload)
      ElMessage.success(t('system.common.updateSuccess'))
    } else {
      await createRole(payload)
      ElMessage.success(t('system.common.createSuccess'))
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
    ElMessage.success(t('system.department.deleted'))
    load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>
