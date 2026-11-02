<template>
  <div>
    <h2 class="page-title">{{ t('route.dataScope') }}</h2>

    <div class="toolbar" style="margin-bottom: 12px">
      <div class="toolbar-filters">
        <span class="filter-label">
          为指定角色配置各数据资源的可见范围。与菜单/接口权限相互独立，修改后立即生效，无需重新登录。
        </span>
      </div>
      <div class="toolbar-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadRows">{{ t('common.refresh') }}</el-button>
      </div>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="t('system.dataScopeTitle')"
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

      <el-table
        v-if="selectedRoleId"
        :data="rows"
        v-loading="loading"
        :empty-text="t('system.noConfigurableResource')"
        style="width: 100%"
      >
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
        <el-table-column :label="t('system.customDept')" min-width="200">
          <template #default="{ row }">
            <el-button v-if="row.scopeType === 'CUSTOM'" link type="primary" @click="openOrgDialog(row)">
              选择部门（{{ row.orgIds?.length || 0 }}）
            </el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.operation')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" :loading="row.saving" @click="saveRow(row)">{{ t('common.save') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

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
import { orgTree } from '@/api/org'

const roles = ref([])
const roleLoading = ref(false)
const selectedRoleId = ref(null)

const rows = ref([])
const loading = ref(false)
const error = ref('')

const orgTreeData = ref([])
const orgTreeRef = ref()
const orgDialogVisible = ref(false)
const dialogRow = ref(null)

// 与后端 DataScopeType 一一对应；label 供页面展示，value 直接作为接口入参
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
  await loadRows()
}

async function loadRows() {
  if (!selectedRoleId.value) {
    rows.value = []
    return
  }
  loading.value = true
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
    loading.value = false
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

async function saveRow(row) {
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
    await loadRows()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    row.saving = false
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
</style>
