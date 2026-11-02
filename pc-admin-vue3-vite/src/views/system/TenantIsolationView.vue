<template>
  <div>
    <h2 class="page-title">{{ t('route.tenantIsolation') }}</h2>

    <div class="toolbar" style="margin-bottom: 12px">
      <div class="toolbar-filters">
        <span class="filter-label">{{ t('system.isolationDiagDesc') }}</span>
      </div>
      <div class="toolbar-actions">
        <div class="action-buttons">
          <el-button :icon="Refresh" :loading="loading" @click="load">{{ t('common.refresh') }}</el-button>
        </div>
      </div>
    </div>

    <el-alert
      v-if="error"
      type="error"
      :closable="false"
      :title="error"
      show-icon
      style="margin-bottom: 12px"
    />

    <template v-if="status">
      <el-card class="page-card section" :header="t('system.isolationOverview')">
        <div class="stat-grid">
          <div class="stat-card">
            <div class="label">{{ t('system.tenantIsolationSwitch') }}</div>
            <div class="value">
              <el-tag :type="boolTag(status.isolationEnabled).type" size="small">
                {{ boolTag(status.isolationEnabled).label }}
              </el-tag>
            </div>
          </div>
          <div class="stat-card">
            <div class="label">{{ t('system.enforceRuntime') }}</div>
            <div class="value">
              <el-tag :type="boolTag(status.enforceEnabled).type" size="small">
                {{ boolTag(status.enforceEnabled).label }}
              </el-tag>
            </div>
          </div>
          <div class="stat-card">
            <div class="label">{{ t('system.configSnapshotInjected') }}</div>
            <div class="value">
              <el-tag :type="boolTag(status.configured).type" size="small">
                {{ boolTag(status.configured).label }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="page-card section" :header="t('system.configStateSys')">
        <div class="stat-grid">
          <div class="stat-card">
            <div class="label">{{ t('system.defaultTenantId') }}</div>
            <div class="value">{{ status.defaultTenantId }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ t('system.platformAdminRoleCode') }}</div>
            <div class="value">{{ status.platformAdminRoleCode }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ t('system.tenantHeader') }}</div>
            <div class="value">{{ status.headerName }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ t('system.tokenClaimName') }}</div>
            <div class="value">{{ status.claimName }}</div>
          </div>
        </div>
        <div style="margin-top: 14px">
          <span class="filter-label">{{ t('system.platformSharedTable') }}</span>
          <div style="margin-top: 8px; display: flex; flex-wrap: wrap; gap: 8px">
            <el-tag v-for="t in status.platformTables || []" :key="t" size="small" effect="plain">{{ t }}</el-tag>
            <span v-if="!status.platformTables || !status.platformTables.length" class="filter-label">{{ t('system.empty') }}</span>
          </div>
        </div>
      </el-card>

      <el-card class="page-card section" :header="t('system.currentScope')">
        <div class="stat-grid">
          <div class="stat-card">
            <div class="label">{{ t('system.scopeSet') }}</div>
            <div class="value">
              <el-tag :type="boolTag(status.scopeSet).type" size="small">
                {{ boolTag(status.scopeSet).label }}
              </el-tag>
            </div>
          </div>
          <div class="stat-card">
            <div class="label">{{ t('system.currentTenantId') }}</div>
            <div class="value">{{ status.currentTenantId != null ? status.currentTenantId : t('workflow.notSet') }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ t('system.platformAdminScope') }}</div>
            <div class="value">
              <el-tag :type="boolTag(status.currentPlatformAdmin).type" size="small">
                {{ boolTag(status.currentPlatformAdmin).label }}
              </el-tag>
            </div>
          </div>
          <div class="stat-card">
            <div class="label">{{ t('system.crossTenantBypass') }}</div>
            <div class="value">
              <el-tag :type="boolTag(status.currentBypass).type" size="small">
                {{ boolTag(status.currentBypass).label }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="page-card section" :header="t('system.configValidation')">
        <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 10px">
          <span class="filter-label">{{ t('system.validationStatus') }}</span>
          <el-tag :type="validationTag(status.validationStatus).type" size="small">
            {{ validationTag(status.validationStatus).label }}
          </el-tag>
          <span class="filter-label">{{ status.validationStatus }}</span>
        </div>
        <el-alert
          :type="alertType(status.validationStatus)"
          :closable="false"
          :title="status.validationMessage"
          show-icon
        />
      </el-card>

      <el-card class="page-card section" :header="t('system.legacyIsolationCheck')">
        <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 10px">
          <el-button :icon="Refresh" :loading="auditLoading" @click="loadAudit">{{ t('system.recheck') }}</el-button>
          <span class="filter-label">{{ t('system.scanTenantOwnership') }}</span>
        </div>
        <el-alert
          v-if="audit"
          :type="audit.safeToEnable ? 'success' : 'warning'"
          :closable="false"
          :title="audit.message"
          show-icon
          style="margin-bottom: 12px"
        />
        <div v-if="audit" class="stat-grid">
          <div class="stat-card">
            <div class="label">{{ t('system.bizTableTotal') }}</div>
            <div class="value">{{ audit.totalBusinessTables }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ t('system.problemTableCount') }}</div>
            <div class="value">
              <el-tag :type="audit.violatedTables > 0 ? 'danger' : 'success'" size="small">
                {{ audit.violatedTables }}
              </el-tag>
            </div>
          </div>
          <div class="stat-card">
            <div class="label">{{ t('system.unattributedRows') }}</div>
            <div class="value">{{ audit.totalViolatedRows }}</div>
          </div>
        </div>
        <el-table
          v-if="audit && audit.byTable && audit.byTable.length"
          :data="audit.byTable"
          size="small"
          style="margin-top: 12px"
        >
          <el-table-column prop="tableName" :label="t('system.businessTable')" min-width="220" />
          <el-table-column :label="t('system.unattributedRows')" width="160">
            <template #default="{ row }">
              <el-tag v-if="row.violatedRows < 0" type="warning" size="small">{{ t('system.checkFailed') }}</el-tag>
              <span v-else>{{ row.violatedRows }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-card v-else-if="!loading" class="page-card section">
      <el-empty :description="t('system.noTenantIsolationData')" />
    </el-card>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getTenantIsolationStatus, auditTenantIsolation } from '@/api/tenantIsolation'

const loading = ref(false)
const error = ref('')
const status = ref(null)

const audit = ref(null)
const auditLoading = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    status.value = await getTenantIsolationStatus()
  } catch (e) {
    error.value = e?.message || t('system.loadFailedX')
  } finally {
    loading.value = false
  }
}

async function loadAudit() {
  auditLoading.value = true
  try {
    audit.value = await auditTenantIsolation()
  } catch (e) {
    // 自检失败不影响配置态势展示，仅清空上一次结果
    audit.value = null
    console.error(t('system.legacyIsolationCheckFailed'), e)
  } finally {
    auditLoading.value = false
  }
}

/** 布尔值 → 标签（开启/关闭）。 */
function boolTag(v) {
  return v ? { type: 'success', label: t('system.enableSys') } : { type: 'info', label: t('common.close') }
}

/** 校验状态 → 标签（OK/INVALID/DISABLED）。 */
function validationTag(s) {
  if (s === 'OK') return { type: 'success', label: t('system.configConsistent') }
  if (s === 'INVALID') return { type: 'danger', label: t('system.configInvalid') }
  return { type: 'info', label: t('system.validationOff') }
}

/** 校验状态 → el-alert 类型。 */
function alertType(s) {
  if (s === 'OK') return 'success'
  if (s === 'INVALID') return 'error'
  return 'info'
}

onMounted(() => {
  load()
  loadAudit()
})
</script>
