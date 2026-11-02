<template>
  <div>
    <h2 class="page-title">{{ t('route.permissionPolicy') }}</h2>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="t('system.dataFieldPermDesc')"
      style="margin-bottom: 12px"
    />

    <el-card class="page-card section">
      <el-form :inline="true" @submit.prevent>
        <el-form-item :label="t('common.role')">
          <el-select
            v-model="selectedRoleId"
            filterable
            :placeholder="t('system.pleaseSelectRole')"
            style="width: 280px"
            :loading="roleLoading"
            @change="onRoleChange"
          >
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="error"
        type="error"
        :closable="false"
        :title="error"
        show-icon
        style="margin-bottom: 12px"
      />

      <el-tabs v-if="selectedRoleId" v-model="activeTab" class="policy-tabs">
        <!-- 行级：数据权限 -->
        <el-tab-pane :label="t('system.dataPermRow')" name="dataScope">
          <el-table :data="rows" v-loading="dataScopeLoading" :empty-text="t('system.noConfigurableResource')" style="width: 100%">
            <el-table-column prop="resourceName" :label="t('system.colResource')" min-width="160" />
            <el-table-column prop="resourceCode" :label="t('system.resourceCode')" min-width="160">
              <template #default="{ row }">
                <span class="muted">{{ row.resourceCode }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('system.dataScopeX')" width="240">
              <template #default="{ row }">
                <el-select v-model="row.scopeType" style="width: 200px" @change="onScopeChange(row)">
                  <el-option v-for="o in SCOPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('system.customDept')" min-width="160">
              <template #default="{ row }">
                <el-button v-if="row.scopeType === 'CUSTOM'" link type="primary" @click="openOrgDialog(row)">
                  选择部门（{{ row.orgIds?.length || 0 }}）
                </el-button>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.operation')" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" :loading="row.saving" @click="saveDataScopeRow(row)">{{ t('common.save') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 列级：字段权限 -->
        <el-tab-pane :label="t('system.fieldPermCol')" name="column">
          <el-table :data="columnRows" v-loading="columnLoading" :empty-text="t('system.noConfigurableField')" style="width: 100%">
            <el-table-column prop="resourceName" :label="t('system.colResource')" min-width="140" />
            <el-table-column prop="fieldLabel" :label="t('system.colField')" min-width="140" />
            <el-table-column prop="resourceCode" :label="t('system.resourceCode')" min-width="150">
              <template #default="{ row }">
                <span class="muted">{{ row.resourceCode }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="strategy" :label="t('system.maskStrategy')" width="120">
              <template #default="{ row }">
                <el-tag :type="row.strategy === 'HIDE' ? 'danger' : 'warning'" size="small">
                  {{ row.strategy === 'HIDE' ? t('system.hideSys') : t('system.maskSys') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('system.plaintextVisible')" width="120">
              <template #default="{ row }">
                <el-switch v-model="row.granted" @change="(val) => onColumnToggle(row, val)" />
              </template>
            </el-table-column>
          </el-table>
          <div class="footer-actions">
            <el-button type="primary" :loading="columnSaving" @click="saveColumnPermissions">{{ t('system.saveFieldPerm') }}</el-button>
            <span class="muted">{{ t('system.closePlaintextHint') }}</span>
          </div>
        </el-tab-pane>
      </el-tabs>

      <el-empty v-else :description="t('system.selectRoleFirst')" />
    </el-card>

    <el-dialog v-model="orgDialogVisible" :title="t('system.selectVisibleDept')" width="440px">
      <el-tree
        ref="orgTreeRef"
        :data="orgTreeData"
        :props="{ label: 'orgName', children: 'children' }"
        node-key="id"
        show-checkbox
        default-expand-all
        style="max-height: 360px; overflow: auto"
      />
      <template #footer>
        <el-button @click="orgDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="confirmOrgDialog">{{ t('system.confirmSys') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listRoles, listRoleDataScopes, saveRoleDataScope } from '@/api/role'
import { listDataScopeResources } from '@/api/dataScope'
import { listColumnPermissionResources, listRoleColumnPermissions, saveRoleColumnPermissions } from '@/api/columnPermission'
import { orgTree } from '@/api/org'

const roles = ref([])
const roleLoading = ref(false)
const selectedRoleId = ref(null)
const activeTab = ref('dataScope')

const rows = ref([])
const dataScopeLoading = ref(false)
const error = ref('')

const orgTreeData = ref([])
const orgTreeRef = ref()
const orgDialogVisible = ref(false)
const dialogRow = ref(null)

const columnResources = ref([])
const columnRows = ref([])
const columnLoading = ref(false)
const columnSaving = ref(false)

const SCOPE_OPTIONS = [
  { value: 'ALL', label: t('system.allData') },
  { value: 'DEPT_AND_CHILD', label: t('system.deptAndChildren') },
  { value: 'DEPT', label: t('system.onlyThisDept') },
  { value: 'SELF', label: t('system.onlySelf') },
  { value: 'CUSTOM', label: t('system.customDept') }
]

async function loadRoles() {
  roleLoading.value = true
  try {
    roles.value = (await listRoles()) || []
  } finally {
    roleLoading.value = false
  }
}

async function onRoleChange() {
  await Promise.all([loadDataScopeRows(), loadColumnRows()])
}

async function loadDataScopeRows() {
  if (!selectedRoleId.value) {
    rows.value = []
    return
  }
  dataScopeLoading.value = true
  error.value = ''
  try {
    const [resList, rules] = await Promise.all([
      listDataScopeResources(),
      listRoleDataScopes(selectedRoleId.value)
    ])
    const ruleMap = new Map((rules || []).map((r) => [r.resourceCode, r]))
    rows.value = (resList || []).map((res) => {
      const rule = ruleMap.get(res.resourceCode)
      return {
        resourceCode: res.resourceCode,
        resourceName: res.resourceName,
        scopeType: rule?.scopeType || 'ALL',
        orgIds: rule?.orgIds ? [...rule.orgIds] : [],
        saving: false
      }
    })
  } catch (e) {
    error.value = e?.message || t('system.loadFailedX')
    rows.value = []
  } finally {
    dataScopeLoading.value = false
  }
}

async function loadColumnRows() {
  if (!selectedRoleId.value) {
    columnResources.value = []
    columnRows.value = []
    return
  }
  columnLoading.value = true
  try {
    const [resList, codes] = await Promise.all([
      listColumnPermissionResources(),
      listRoleColumnPermissions(selectedRoleId.value)
    ])
    const codeSet = new Set(codes || [])
    const flat = []
    for (const res of resList || []) {
      for (const f of res.fields || []) {
        const code = `${res.resourceCode}:column:${f.field}`
        flat.push({
          resourceCode: res.resourceCode,
          resourceName: res.resourceName,
          field: f.field,
          fieldLabel: f.label || f.field,
          strategy: f.strategy || 'MASK',
          code,
          granted: codeSet.has(code)
        })
      }
    }
    columnResources.value = resList || []
    columnRows.value = flat
  } catch (e) {
    error.value = e?.message || t('system.loadFieldPermFailed')
    columnRows.value = []
  } finally {
    columnLoading.value = false
  }
}

function onScopeChange(row) {
  if (row.scopeType !== 'CUSTOM') {
    row.orgIds = []
  }
}

function openOrgDialog(row) {
  dialogRow.value = row
  orgDialogVisible.value = true
  nextTick(() => {
    orgTreeRef.value?.setCheckedKeys(row.orgIds || [])
  })
}

function confirmOrgDialog() {
  if (dialogRow.value) {
    dialogRow.value.orgIds = orgTreeRef.value?.getCheckedKeys() || []
  }
  orgDialogVisible.value = false
}

async function saveDataScopeRow(row) {
  if (row.scopeType === 'CUSTOM' && (!row.orgIds || row.orgIds.length === 0)) {
    ElMessage.warning(t('system.customScopeNeedDept'))
    return
  }
  row.saving = true
  try {
    await saveRoleDataScope(selectedRoleId.value, {
      resourceCode: row.resourceCode,
      scopeType: row.scopeType,
      orgIds: row.scopeType === 'CUSTOM' ? row.orgIds : []
    })
    ElMessage.success(t('system.dataPermissionSaved'))
    await loadDataScopeRows()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    row.saving = false
  }
}

function onColumnToggle(row, val) {
  // 仅切换本地状态，保存时统一提交
  row.granted = val
}

async function saveColumnPermissions() {
  columnSaving.value = true
  try {
    const codes = columnRows.value.filter((r) => r.granted).map((r) => r.code)
    await saveRoleColumnPermissions(selectedRoleId.value, codes)
    ElMessage.success(t('system.fieldPermSaved'))
    await loadColumnRows()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    columnSaving.value = false
  }
}

onMounted(() => {
  loadRoles()
  orgTree()
    .then((data) => {
      orgTreeData.value = data || []
    })
    .catch(() => {
      orgTreeData.value = []
    })
})
</script>

<style scoped>
.muted {
  color: #909399;
}
.page-title {
  margin: 0 0 12px;
  font-size: 20px;
  font-weight: 600;
}
.footer-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
