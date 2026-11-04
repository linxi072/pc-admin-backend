<template>
  <div>
    <h2 class="page-title">{{ t('route.tenant') }}</h2>
    <el-card class="page-card">
      <div class="toolbar">
        <div class="toolbar-filters">
          <div class="filter-group">
            <span class="filter-label">{{ t('system.tenantCode') }}</span>
            <el-input
              v-model="query.tenantCode"
              :placeholder="t('system.searchTenantCode')"
              clearable
              :prefix-icon="Search"
              style="width: 220px"
              @keyup.enter="load"
            />
          </div>
          <div class="filter-group">
            <span class="filter-label">{{ t('common.status') }}</span>
            <el-select v-model="query.status" :placeholder="t('common.status')" clearable style="width: 140px">
              <el-option :label="t('common.enabled')" value="ENABLED" />
              <el-option :label="t('common.disabled')" value="DISABLED" />
            </el-select>
          </div>
        </div>
        <div class="toolbar-actions">
          <div class="action-buttons">
            <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('system.newTenant') }}</el-button>
            <el-button :icon="DataAnalysis" @click="openOverview">{{ t('system.operationsOverview') }}</el-button>
            <el-button :icon="Operation" @click="governanceVisible = true">{{ t('system.tenantGovernance') }}</el-button>
          </div>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe @sort-change="onSortChange">
        <el-table-column prop="tenantCode" :label="t('system.tenantCode')" width="180" sortable="custom" />
        <el-table-column prop="tenantName" :label="t('system.tenantName')" min-width="160" />
        <el-table-column prop="contactName" :label="t('system.contact')" width="120" />
        <el-table-column prop="contactPhone" :label="t('system.contactPhone')" width="150" />
        <el-table-column prop="contactEmail" :label="t('system.contactEmail')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('monitor.expireTime')" width="160">
          <template #default="{ row }">
            <span>{{ fmtDateTime(row.expireAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('job.remark')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createdAt" :label="t('system.createdAt')" width="170" sortable="custom" />
        <el-table-column :label="t('common.operation')" width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button
              link
              type="success"
              :icon="UserFilled"
              @click="openProvision(row)"
            >{{ t('system.provision') }}</el-button>
            <el-button
              link
              :type="row.status === 'ENABLED' ? 'warning' : 'success'"
              @click="onToggle(row)"
            >{{ row.status === 'ENABLED' ? t('common.disabled') : t('common.enabled') }}</el-button>
            <el-popconfirm :title="t('system.deleteThisTenant')" width="220" @confirm="onDelete(row)">
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

    <!-- 新增 / 编辑租户 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? t('system.editTenant') : t('system.newTenant')" width="560px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="92px">
        <el-form-item :label="t('system.tenantCode')" prop="tenantCode">
          <el-input
            v-model="form.tenantCode"
            :disabled="isEdit"
            :placeholder="t('system.eGTAcmeLettersDigitsUnderscoreHyphenImmutableAfterCreation')"
          />
        </el-form-item>
        <el-form-item :label="t('system.tenantName')" prop="tenantName">
          <el-input v-model="form.tenantName" :placeholder="t('system.eGSomeTechnologyCoLtd')" />
        </el-form-item>
        <el-form-item :label="t('system.contact')">
          <el-input v-model="form.contactName" :placeholder="t('system.optional')" />
        </el-form-item>
        <el-form-item :label="t('system.contactPhone')">
          <el-input v-model="form.contactPhone" :placeholder="t('system.optional')" />
        </el-form-item>
        <el-form-item :label="t('system.contactEmail')">
          <el-input v-model="form.contactEmail" :placeholder="t('system.optional')" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :label="t('common.enabled')" value="ENABLED" />
            <el-option :label="t('common.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('monitor.expireTime')">
          <el-date-picker
            v-model="form.expireAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            :placeholder="t('system.optionalBlankMeansPermanent')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('job.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" :placeholder="t('system.optional')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 开通租户（初始化管理员 / 角色 / 部门） -->
    <el-dialog v-model="provisionVisible" :title="t('system.provisionTenant')" width="560px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="t('system.willInitializeForThisTenantDefaultDepartmentTenantAdminRoleFullMenusApisFirstAdminUser')"
        style="margin-bottom: 16px"
      />
      <el-form :model="provisionForm" :rules="provisionRules" ref="provisionRef" label-width="96px">
        <el-form-item :label="t('system.targetTenant')">
          <el-input :model-value="provisionTenantName" disabled />
        </el-form-item>
        <el-form-item :label="t('system.adminAccount')" prop="adminUsername">
          <el-input v-model="provisionForm.adminUsername" :placeholder="t('system.optionalDefaultAdmin')" />
        </el-form-item>
        <el-form-item :label="t('system.loginPassword')" prop="adminPassword">
          <el-input
            v-model="provisionForm.adminPassword"
            type="password"
            show-password
            :placeholder="t('system.k864CharsUpperLowerCaseLettersDigitsSpecialChars')"
          />
        </el-form-item>
        <el-form-item :label="t('system.displayName')" prop="adminDisplayName">
          <el-input v-model="provisionForm.adminDisplayName" :placeholder="t('system.eGTenantAdministrator')" />
        </el-form-item>
        <el-form-item :label="t('common.phone')">
          <el-input v-model="provisionForm.adminMobile" :placeholder="t('system.optional')" />
        </el-form-item>
        <el-form-item :label="t('common.email')">
          <el-input v-model="provisionForm.adminEmail" :placeholder="t('system.optional')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="provisionVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="provisioning" @click="onProvision">{{ t('system.confirmProvision') }}</el-button>
      </template>
    </el-dialog>

    <!-- 跨租户运营总览 -->
    <el-dialog v-model="overviewVisible" :title="t('system.crossTenantOperationsOverview')" width="900px">
      <div v-loading="overviewLoading">
        <template v-if="overview">
          <!-- enforce 开关状态 -->
          <el-alert
            :type="overview.enforce ? 'success' : 'info'"
            :closable="false"
            show-icon
            :title="overview.enforce ? t('system.isolationOn') : t('system.isolationOff')"
          />

          <!-- 聚合统计卡 -->
          <div class="overview-stats">
            <div class="stat">
              <div class="stat-num">{{ overview.tenantCount }}</div>
              <div class="stat-label">{{ t('reportDashboard.tenantCount') }}</div>
            </div>
            <div class="stat">
              <div class="stat-num">{{ overview.enabledTenantCount }}</div>
              <div class="stat-label">{{ t('system.enableTenant') }}</div>
            </div>
            <div class="stat">
              <div class="stat-num">{{ overview.businessTableCount }}</div>
              <div class="stat-label">{{ t('system.businessTableCount') }}</div>
            </div>
            <div class="stat">
              <div class="stat-num" :class="{ danger: overview.isolationAudit && overview.isolationAudit.violatedTables > 0 }">
                {{ overview.isolationAudit ? overview.isolationAudit.violatedTables : '—' }}
              </div>
              <div class="stat-label">{{ t('system.problemTableCount') }}</div>
            </div>
            <div class="stat">
              <div class="stat-num" :class="{ danger: overview.isolationAudit && overview.isolationAudit.totalViolatedRows > 0 }">
                {{ overview.isolationAudit ? overview.isolationAudit.totalViolatedRows : '—' }}
              </div>
              <div class="stat-label">{{ t('system.unattributedRows') }}</div>
            </div>
            <div class="stat">
              <div class="stat-num">{{ overview.totalUserCount }}</div>
              <div class="stat-label">{{ t('system.totalUsers') }}</div>
            </div>
            <div class="stat">
              <div class="stat-num" :class="{ danger: overview.expiringSoonCount > 0 }">
                {{ overview.expiringSoonCount }}
              </div>
              <div class="stat-label">{{ t('system.expiringSoon') }}</div>
            </div>
            <div class="stat">
              <div class="stat-num" :class="{ danger: overview.disabledTenantCount > 0 }">
                {{ overview.disabledTenantCount }}
              </div>
              <div class="stat-label">{{ t('system.disableTenant') }}</div>
            </div>
          </div>

          <!-- 存量隔离自检结论（M45） -->
          <el-alert
            v-if="overview.isolationAudit"
            class="mt-12"
            :type="overview.isolationAudit.safeToEnable ? 'success' : 'warning'"
            :closable="false"
            show-icon
            :title="overview.isolationAudit.safeToEnable ? t('system.auditSafe') : t('system.auditRisk')"
            :description="overview.isolationAudit.message"
          />
          <el-alert
            v-else
            class="mt-12"
            type="info"
            :closable="false"
            show-icon
            :title="t('system.isolationSelfCheckUnavailableCheckDbConnectivity')"
          />

          <!-- 租户明细 -->
          <el-table
            :data="overview.tenants"
            class="mt-12"
            border
            stripe
            default-expand-all
            :empty-text="t('system.noTenantDataYet')"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <el-table :data="toPairs(row.tableCounts)" border size="small" style="margin: 8px 0">
                  <el-table-column prop="name" :label="t('system.businessTable')" min-width="240" />
                  <el-table-column prop="count" :label="t('formDesigner.rows')" width="140" align="right" />
                </el-table>
              </template>
            </el-table-column>
            <el-table-column prop="tenantName" :label="t('system.tenantName')" min-width="200" />
            <el-table-column prop="tenantId" :label="t('system.tenantId')" width="120" />
            <el-table-column :label="t('common.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
                  {{ row.status === 'ENABLED' ? t('common.enabled') : t('common.disabled') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('monitor.expireTime')" width="160">
              <template #default="{ row }">{{ fmtDateTime(row.expireAt) }}</template>
            </el-table-column>
            <el-table-column prop="userCount" :label="t('system.userCount')" width="110" align="right" />
            <el-table-column :label="t('system.warning')" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.expiringSoon" type="warning" size="small">{{ t('system.expiringSoon') }}</el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column prop="totalRows" :label="t('system.totalBusinessData')" width="160" align="right" />
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="overviewVisible = false">{{ t('common.close') }}</el-button>
        <el-button type="primary" :loading="overviewLoading" @click="loadOverview">{{ t('common.refresh') }}</el-button>
      </template>
    </el-dialog>

    <!-- 租户治理（M19：跨租户数据总览 / 存量归位 / 跨租户迁移） -->
    <TenantGovernanceDialog v-model="governanceVisible" />
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, Edit, Delete, DataAnalysis, UserFilled, Operation } from '@element-plus/icons-vue'
import {
  tenantPage,
  createTenant,
  updateTenant,
  removeTenant,
  toggleTenant,
  provisionTenant
} from '@/api/tenant'
import { getTenantOverview } from '@/api/tenantOverview'
import TenantGovernanceDialog from './TenantGovernanceDialog.vue'
import { statusTag } from '@/utils/status'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, tenantCode: '', status: '', sortField: '', sortDirection: '' })
const governanceVisible = ref(false)

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  tenantCode: '',
  tenantName: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  status: 'ENABLED',
  expireAt: '',
  remark: ''
})
const rules = {
  tenantCode: [{ required: true, message: t('system.enterTenantCode'), trigger: 'blur' }],
  tenantName: [{ required: true, message: t('system.enterTenantName'), trigger: 'blur' }]
}

// 开通租户
const provisionVisible = ref(false)
const provisioning = ref(false)
const provisionRef = ref()
const provisionTenantId = ref(null)
const provisionTenantName = ref('')
const provisionForm = reactive({
  adminUsername: '',
  adminPassword: '',
  adminDisplayName: '',
  adminMobile: '',
  adminEmail: ''
})
const provisionRules = {
  adminPassword: [{ required: true, message: t('system.enterInitialPassword'), trigger: 'blur' }],
  adminDisplayName: [{ required: true, message: t('system.enterAdminDisplayName'), trigger: 'blur' }]
}

// 跨租户运营总览
const overviewVisible = ref(false)
const overviewLoading = ref(false)
const overview = ref(null)

/** LocalDateTime 字符串（"2026-10-26T14:30:00"）→ "2026-10-26 14:30"；空值回落 t('system.permanent')。 */
function fmtDateTime(v) {
  if (!v) return t('system.permanent')
  const s = String(v).replace('T', ' ').slice(0, 16)
  return s || t('system.permanent')
}

async function load() {
  loading.value = true
  try {
    const data = await tenantPage({ ...query })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function onSortChange({ prop, order }) {
  query.sortField = order ? prop : ''
  query.sortDirection = order ? (order === 'ascending' ? 'ASC' : 'DESC') : ''
  query.page = 1
  load()
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, {
    id: null,
    tenantCode: '',
    tenantName: '',
    contactName: '',
    contactPhone: '',
    contactEmail: '',
    status: 'ENABLED',
    expireAt: '',
    remark: ''
  })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    tenantCode: row.tenantCode,
    tenantName: row.tenantName,
    contactName: row.contactName || '',
    contactPhone: row.contactPhone || '',
    contactEmail: row.contactEmail || '',
    status: row.status || 'ENABLED',
    expireAt: row.expireAt ? String(row.expireAt).replace('T', ' ') : '',
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    // 后端 LocalDateTime 以 ISO 接收：空格分隔转 T 分隔，空值传 null
    const expireAt = form.expireAt ? String(form.expireAt).replace(' ', 'T') : null
    const payload = {
      tenantName: form.tenantName,
      contactName: form.contactName || null,
      contactPhone: form.contactPhone || null,
      contactEmail: form.contactEmail || null,
      status: form.status,
      expireAt,
      remark: form.remark || null
    }
    if (isEdit.value) {
      await updateTenant(form.id, payload)
      ElMessage.success(t('system.updated'))
    } else {
      await createTenant({ tenantCode: form.tenantCode, ...payload })
      ElMessage.success(t('system.created'))
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function openProvision(row) {
  provisionTenantId.value = row.id
  provisionTenantName.value = row.tenantName || row.tenantCode
  Object.assign(provisionForm, {
    adminUsername: '',
    adminPassword: '',
    adminDisplayName: '',
    adminMobile: '',
    adminEmail: ''
  })
  provisionVisible.value = true
}

async function onProvision() {
  const valid = await provisionRef.value.validate().catch(() => false)
  if (!valid) return
  provisioning.value = true
  try {
    const payload = {
      adminUsername: provisionForm.adminUsername || null,
      adminPassword: provisionForm.adminPassword,
      adminDisplayName: provisionForm.adminDisplayName,
      adminMobile: provisionForm.adminMobile || null,
      adminEmail: provisionForm.adminEmail || null
    }
    const view = await provisionTenant(provisionTenantId.value, payload)
    ElMessage.success(t('system.tenantOpened', { username: view.adminUsername, role: view.roleCode }))
    provisionVisible.value = false
    load()
  } catch (e) {
    /* 拦截器已统一提示（如已开通会返回冲突错误） */
  } finally {
    provisioning.value = false
  }
}

async function onToggle(row) {
  const enable = row.status === 'DISABLED'
  try {
    await toggleTenant(row.id, enable)
    ElMessage.success(enable ? t('monitor.enabled') : t('system.disabled'))
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

async function onDelete(row) {
  try {
    await removeTenant(row.id)
    ElMessage.success(t('common.deleted'))
    load()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

async function openOverview() {
  overviewVisible.value = true
  await loadOverview()
}

async function loadOverview() {
  overviewLoading.value = true
  try {
    overview.value = await getTenantOverview()
  } catch (e) {
    /* 拦截器已统一提示 */
  } finally {
    overviewLoading.value = false
  }
}

/** Map<String,Long> → [{ name, count }]，按行数降序，便于阅读。 */
function toPairs(map) {
  if (!map) return []
  return Object.entries(map)
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
}

onMounted(load)
</script>

<style scoped>
.overview-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}
.stat {
  flex: 1 1 120px;
  min-width: 120px;
  text-align: center;
  padding: 14px 8px;
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 8px;
}
.stat-num {
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  line-height: 1.2;
}
.stat-num.danger {
  color: var(--el-color-danger, #f56c6c);
}
.stat-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
.mt-12 {
  margin-top: 12px;
}
</style>
