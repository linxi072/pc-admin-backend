<template>
  <el-dialog v-model="visible" :title="'租户治理'" width="960px" @open="loadOverview">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      :title="'跨租户治理为破坏性操作：归位会改写历史数据的租户归属，迁移会移动整表数据。请务必确认目标租户无误，操作不可回滚。'"
      class="mb-12"
    />

    <el-tabs v-model="active">
      <el-tab-pane :label="'数据总览'" name="overview">
        <div v-loading="loading">
          <el-table :data="overview" border stripe default-expand-all :empty-text="'（空）'">
            <el-table-column type="expand">
              <template #default="{ row }">
                <el-table :data="toPairs(row.tableCounts)" border size="small" style="margin: 8px 0">
                  <el-table-column prop="name" :label="'业务表'" min-width="240" />
                  <el-table-column prop="count" :label="'行数'" width="140" align="right" />
                </el-table>
              </template>
            </el-table-column>
            <el-table-column prop="tenantName" :label="'租户名称'" min-width="200" />
            <el-table-column prop="tenantId" :label="'租户 ID'" width="120" />
            <el-table-column prop="totalRows" :label="'业务数据总量'" width="160" align="right" />
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="'存量归位'" name="backfill">
        <el-form :model="backfillForm" label-width="160px">
          <el-form-item :label="'归位目标租户 ID'" required>
            <el-input
              v-model.number="backfillForm.targetTenantId"
              type="number"
              :placeholder="'请输入目标租户 ID'"
            />
          </el-form-item>
          <el-popconfirm
            :title="`确认将全部业务表的历史数据归位到目标租户 ${backfillForm.targetTenantId || '?'}？此操作将改写数据租户归属且不可回滚。`"
            width="320"
            @confirm="onBackfill"
          >
            <template #reference>
              <el-button type="danger" :loading="backfillLoading">{{ '存量归位' }}</el-button>
            </template>
          </el-popconfirm>
        </el-form>
      </el-tab-pane>

      <el-tab-pane :label="'跨租户迁移'" name="reassign">
        <el-form :model="reassignForm" label-width="160px">
          <el-form-item :label="'迁移源租户 ID'">
            <el-input
              v-model.number="reassignForm.fromTenantId"
              type="number"
              :placeholder="'选填'"
            />
          </el-form-item>
          <el-form-item :label="'迁移目标租户 ID'" required>
            <el-input
              v-model.number="reassignForm.toTenantId"
              type="number"
              :placeholder="'请输入目标租户 ID'"
            />
          </el-form-item>
          <el-form-item :label="'迁移表（留空=全部业务表）'">
            <el-select
              v-model="reassignForm.tables"
              multiple
              filterable
              :placeholder="'留空表示迁移全部业务表'"
              style="width: 100%"
            >
              <el-option v-for="name in tableOptions" :key="name" :label="name" :value="name" />
            </el-select>
          </el-form-item>
          <el-popconfirm
            :title="`确认将源租户 ${reassignForm.fromTenantId || '?'} 的数据迁移到目标租户 ${reassignForm.toTenantId || '?'}？此操作不可回滚。`"
            width="320"
            @confirm="onReassign"
          >
            <template #reference>
              <el-button type="danger" :loading="reassignLoading">{{ '跨租户迁移' }}</el-button>
            </template>
          </el-popconfirm>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="visible = false">{{ '关闭' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>

import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchTenantDataOverview, backfillTenantData, reassignTenantData } from '@/api/tenantGovernance'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])
const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const active = ref('overview')
const loading = ref(false)
const overview = ref([])

const backfillForm = reactive({ targetTenantId: null })
const backfillLoading = ref(false)
const reassignForm = reactive({ fromTenantId: null, toTenantId: null, tables: [] })
const reassignLoading = ref(false)

// 业务表名候选：从总览中各租户 tableCounts 的 key 并集推导，驱动迁移表的下拉多选。
const tableOptions = computed(() => {
  const set = new Set()
  for (const o of overview.value || []) {
    if (o.tableCounts) Object.keys(o.tableCounts).forEach((k) => set.add(k))
  }
  return Array.from(set).sort()
})

function toPairs(map) {
  if (!map) return []
  return Object.entries(map)
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
}

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await fetchTenantDataOverview()
  } catch (e) {
    overview.value = []
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

async function onBackfill() {
  if (!backfillForm.targetTenantId) {
    ElMessage.warning('请输入目标租户 ID')
    return
  }
  backfillLoading.value = true
  try {
    const res = await backfillTenantData({ targetTenantId: backfillForm.targetTenantId })
    ElMessage.success(`存量归位完成，影响 ${res.affectedRows} 行`)
    await loadOverview()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    backfillLoading.value = false
  }
}

async function onReassign() {
  if (!reassignForm.toTenantId) {
    ElMessage.warning('请输入目标租户 ID')
    return
  }
  reassignLoading.value = true
  try {
    const res = await reassignTenantData({
      fromTenantId: reassignForm.fromTenantId || null,
      toTenantId: reassignForm.toTenantId,
      tables: reassignForm.tables && reassignForm.tables.length ? reassignForm.tables : null
    })
    ElMessage.success(`跨租户迁移完成，影响 ${res.affectedRows} 行`)
    await loadOverview()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    reassignLoading.value = false
  }
}
</script>

<style scoped>
.mb-12 {
  margin-bottom: 12px;
}
</style>
