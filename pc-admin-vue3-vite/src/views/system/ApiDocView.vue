<template>
  <div class="api-doc">
    <h2 class="page-title">{{ 'API 文档中心' }}</h2>

    <!-- 工具栏：搜索 + 统计 + 分组筛选 -->
    <el-card class="page-card">
      <div class="toolbar">
        <div class="toolbar-filters">
          <el-input
            v-model="search"
            :placeholder="'搜索接口路径 / 描述 / 分组'"
            clearable
            :prefix-icon="Search"
            style="width: 360px"
          />
          <span class="endpoint-count">
            {{ '接口总数' }}：<b>{{ filtered.length }}</b> / {{ operations.length }}
          </span>
        </div>
        <div class="toolbar-actions">
          <el-button :icon="Refresh" :loading="loading" @click="load">{{ '刷新' }}</el-button>
        </div>
      </div>

      <!-- 分组 chips -->
      <div v-if="tags.length" class="tag-chips">
        <el-tag
          :effect="activeTag === '' ? 'dark' : 'plain'"
          class="tag-chip"
          @click="activeTag = ''"
        >
          {{ '全部' }}
        </el-tag>
        <el-tag
          v-for="tg in tags"
          :key="tg"
          :effect="activeTag === tg ? 'dark' : 'plain'"
          class="tag-chip"
          @click="activeTag = tg"
        >
          {{ tg }}
        </el-tag>
      </div>
    </el-card>

    <!-- 加载中 -->
    <el-skeleton v-if="loading" :rows="10" animated class="page-card" />

    <!-- 加载失败：优雅降级 + 重试 -->
    <el-alert
      v-else-if="error"
      type="warning"
      :closable="false"
      show-icon
      class="page-card"
      :title="error"
    >
      <template #default>
        <div class="alert-actions">
          <el-button size="small" type="primary" :loading="loading" @click="load">
            {{ '重试' }}
          </el-button>
          <span class="alert-hint">{{ '加载 OpenAPI 文档失败，请确认后端已启用 springdoc（开发/本地环境）' }}</span>
        </div>
      </template>
    </el-alert>

    <!-- 空态 -->
    <el-empty
      v-else-if="filtered.length === 0"
      :description="'未匹配到接口'"
      class="page-card"
    />

    <!-- 按分组渲染接口列表 -->
    <template v-else>
      <div v-for="(ops, tag) in grouped" :key="tag" class="tag-group">
        <div class="tag-group-title">
          <el-icon><FolderOpened /></el-icon>
          <span>{{ tag }}</span>
          <el-tag size="small" type="info" effect="plain">{{ ops.length }}</el-tag>
        </div>

        <el-card
          v-for="op in ops"
          :key="op.key"
          class="op-card"
          :class="{ 'is-expanded': expanded[op.key] }"
          shadow="hover"
        >
          <div class="op-head" @click="toggle(op.key)">
            <el-tag :type="methodType(op.method)" size="small" effect="dark" class="op-method">
              {{ op.method }}
            </el-tag>
            <span class="op-path" :title="op.path">{{ op.path }}</span>
            <span v-if="op.deprecated" class="op-deprecated">{{ '已废弃' }}</span>
            <span class="op-summary">{{ op.summary }}</span>
            <el-icon class="op-caret" :class="{ 'is-open': expanded[op.key] }">
              <ArrowRight />
            </el-icon>
          </div>

          <!-- 详情 -->
          <div v-show="expanded[op.key]" class="op-detail">
            <div class="detail-actions">
              <el-button size="small" :icon="DocumentCopy" @click="copyPath(op.path)">
                {{ '复制路径' }}
              </el-button>
            </div>

            <div v-if="op.description" class="detail-block">
              <div class="detail-label">{{ '描述' }}</div>
              <div class="detail-value">{{ op.description }}</div>
            </div>

            <div v-if="op.parameters.length" class="detail-block">
              <div class="detail-label">{{ '参数' }}</div>
              <el-table :data="op.parameters" border size="small" class="param-table">
                <el-table-column :label="'名称'" prop="name" min-width="140" />
                <el-table-column :label="'位置'" prop="in" width="120" />
                <el-table-column :label="'类型'" width="100">
                  <template #default="{ row }">{{ row.schema?.type || (row.schema?.$ref ? refName(row.schema.$ref) : '-') }}</template>
                </el-table-column>
                <el-table-column :label="'状态'" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.required ? 'danger' : 'info'" size="small">
                      {{ row.required ? '必填' : '可选' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="'描述'" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.description || '-' }}</template>
                </el-table-column>
              </el-table>
            </div>
            <div v-else class="detail-block">
              <div class="detail-label">{{ '参数' }}</div>
              <div class="detail-value muted">{{ '无参数' }}</div>
            </div>

            <div v-if="op.requestBody" class="detail-block">
              <div class="detail-label">{{ '请求体' }}</div>
              <div class="detail-value">
                <el-tag
                  v-for="(schema, ct) in requestBodyContent(op.requestBody)"
                  :key="ct"
                  size="small"
                  effect="plain"
                  class="rb-tag"
                >
                  {{ ct }}<template v-if="schema.$ref"> · {{ refName(schema.$ref) }}</template>
                  <template v-else-if="schema.type"> · {{ schema.type }}</template>
                </el-tag>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, ArrowRight, FolderOpened, DocumentCopy } from '@element-plus/icons-vue'

import { getApiDocs } from '@/api/apiDoc'

const loading = ref(false)
const error = ref('')
const raw = ref(null)
const search = ref('')
const activeTag = ref('')
const expanded = ref({})

/** 拉取 OpenAPI 文档；失败静默保留上一次结果，仅置错误态供重试。 */
async function load() {
  loading.value = true
  error.value = ''
  try {
    const doc = await getApiDocs()
    raw.value = doc
  } catch (e) {
    raw.value = null
    error.value = e?.response?.data?.message || e?.message || '加载 OpenAPI 文档失败，请确认后端已启用 springdoc（开发/本地环境）'
  } finally {
    loading.value = false
  }
}

/** 扁平化为操作列表，按 tag 排序。 */
const operations = computed(() => {
  const paths = raw.value?.paths || {}
  const HTTP = ['get', 'post', 'put', 'delete', 'patch', 'head', 'options']
  const list = []
  for (const [path, methods] of Object.entries(paths)) {
    for (const [method, op] of Object.entries(methods || {})) {
      if (!HTTP.includes(method) || typeof op !== 'object') continue
      list.push({
        key: method.toUpperCase() + ' ' + path,
        method: method.toUpperCase(),
        path,
        tag: (op.tags && op.tags[0]) || 'Default',
        summary: op.summary || '',
        description: op.description || '',
        operationId: op.operationId || '',
        deprecated: !!op.deprecated,
        parameters: op.parameters || [],
        requestBody: op.requestBody || null
      })
    }
  }
  list.sort((a, b) => a.tag.localeCompare(b.tag) || a.path.localeCompare(b.path))
  return list
})

const tags = computed(() => [...new Set(operations.value.map((o) => o.tag))])

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  return operations.value.filter((o) => {
    if (activeTag.value && o.tag !== activeTag.value) return false
    if (!q) return true
    return (o.path + ' ' + o.summary + ' ' + o.description + ' ' + o.tag + ' ' + o.operationId)
      .toLowerCase()
      .includes(q)
  })
})

/** 按 tag 分组供渲染（保持 operations 的排序）。 */
const grouped = computed(() => {
  const m = {}
  for (const o of filtered.value) (m[o.tag] ||= []).push(o)
  return m
})

function toggle(key) {
  expanded.value = { ...expanded.value, [key]: !expanded.value[key] }
}

function methodType(m) {
  return { GET: 'success', POST: 'warning', PUT: 'primary', DELETE: 'danger', PATCH: 'info' }[m] || 'info'
}

/** #/components/schemas/Xxx → Xxx */
function refName(ref) {
  if (typeof ref !== 'string') return ''
  const i = ref.lastIndexOf('/')
  return ref.slice(i + 1)
}

/** 请求体内容类型 → schema 映射，便于展示可接受的 Content-Type。 */
function requestBodyContent(rb) {
  return rb?.content || {}
}

async function copyPath(path) {
  try {
    await navigator.clipboard.writeText(path)
    ElMessage.success('已复制')
  } catch (e) {
    // 剪贴板不可用时静默降级（如非安全上下文）
  }
}

onMounted(load)
</script>

<style scoped>
.api-doc {
  padding: 4px;
}
.endpoint-count {
  color: #909399;
  font-size: 13px;
  margin-left: 8px;
}
.tag-chips {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tag-chip {
  cursor: pointer;
}
.tag-group {
  margin-top: 16px;
}
.tag-group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8px;
}
.op-card {
  margin-bottom: 10px;
}
.op-card.is-expanded {
  border-color: var(--el-color-primary);
}
.op-head {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  flex-wrap: wrap;
}
.op-method {
  font-weight: 700;
  font-family: monospace;
  min-width: 64px;
  text-align: center;
}
.op-path {
  font-family: monospace;
  color: #303133;
  font-size: 13px;
  word-break: break-all;
}
.op-deprecated {
  color: #e6a23c;
  font-size: 12px;
  border: 1px solid #e6a23c;
  border-radius: 4px;
  padding: 0 6px;
}
.op-summary {
  color: #909399;
  font-size: 13px;
  flex: 1;
  min-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.op-caret {
  transition: transform 0.2s;
  color: #c0c4cc;
}
.op-caret.is-open {
  transform: rotate(90deg);
}
.op-detail {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}
.detail-actions {
  margin-bottom: 8px;
}
.detail-block {
  margin-bottom: 12px;
}
.detail-label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}
.detail-value {
  font-size: 13px;
  color: #303133;
  line-height: 1.6;
  white-space: pre-wrap;
}
.detail-value.muted {
  color: #c0c4cc;
}
.param-table {
  font-size: 12px;
}
.rb-tag {
  margin-right: 6px;
}
.alert-actions {
  margin-top: 6px;
}
.alert-hint {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}
</style>
