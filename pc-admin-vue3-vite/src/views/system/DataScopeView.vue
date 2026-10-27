<template>
  <div>
    <h2 class="page-title">数据权限</h2>

    <div class="toolbar" style="margin-bottom: 12px">
      <div class="toolbar-filters">
        <span class="filter-label">
          为指定角色配置各数据资源的可见范围。与菜单/接口权限相互独立，修改后立即生效，无需重新登录。
        </span>
      </div>
      <div class="toolbar-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadRows">刷新</el-button>
      </div>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="未配置的资源默认不限制（全部数据）。自定义部门范围需至少选择一个部门。"
      style="margin-bottom: 12px"
    />

    <el-card class="page-card section">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="角色">
          <el-select
            v-model="selectedRoleId"
            filterable
            placeholder="请选择角色"
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
        empty-text="暂无可配置资源"
        style="width: 100%"
      >
        <el-table-column prop="resourceName" label="资源" min-width="160" />
        <el-table-column prop="resourceCode" label="资源编码" min-width="160">
          <template #default="{ row }">
            <span class="muted">{{ row.resourceCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数据范围" width="240">
          <template #default="{ row }">
            <el-select v-model="row.scopeType" style="width: 200px" @change="onScopeChange(row)">
              <el-option v-for="o in SCOPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="自定义部门" min-width="200">
          <template #default="{ row }">
            <el-button v-if="row.scopeType === 'CUSTOM'" link type="primary" @click="openOrgDialog(row)">
              选择部门（{{ row.orgIds?.length || 0 }}）
            </el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" :loading="row.saving" @click="saveRow(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else description="请先选择角色" />
    </el-card>

    <el-dialog v-model="orgDialogVisible" title="选择可见部门" width="440px">
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
        <el-button @click="orgDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmOrgDialog">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
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
  { value: 'ALL', label: '全部数据' },
  { value: 'DEPT_AND_CHILD', label: '本部门及下级' },
  { value: 'DEPT', label: '仅本部门' },
  { value: 'SELF', label: '仅本人' },
  { value: 'CUSTOM', label: '自定义部门' }
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
    error.value = e?.message || '加载失败'
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
    ElMessage.warning('自定义范围必须至少选择一个部门')
    return
  }
  row.saving = true
  try {
    await saveRoleDataScope(selectedRoleId.value, {
      resourceCode: row.resourceCode,
      scopeType: row.scopeType,
      orgIds: row.scopeType === 'CUSTOM' ? row.orgIds : []
    })
    ElMessage.success('数据权限已保存')
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
