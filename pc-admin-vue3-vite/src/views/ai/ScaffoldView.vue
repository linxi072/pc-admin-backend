<template>
  <div>
    <h2 class="page-title">{{ t('route.aiScaffold') }}</h2>

    <el-card class="page-card" shadow="never">
      <el-form :model="form" label-width="90px">
        <el-form-item :label="t('ai.moduleName')">
          <el-input v-model="form.moduleName" :placeholder="t('ai.moduleNamePlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="t('ai.prompt')">
          <el-input v-model="form.prompt" type="textarea" :rows="5" :placeholder="t('ai.promptPlaceholder')" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" :icon="MagicStick" @click="onGenerate">
            {{ loading ? t('ai.generating') : t('ai.generate') }}
          </el-button>
          <el-button :icon="RefreshLeft" @click="onReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-empty v-if="!loading && !result" :description="t('ai.emptyHint')" />

    <template v-if="result">
      <el-alert :title="t('ai.resultHint')" type="warning" :closable="false" show-icon class="page-card" />

      <el-row :gutter="12" class="summary-row">
        <el-col :span="8">
          <div class="stat-card">
            <div class="stat-num">{{ result.provider || '--' }}</div>
            <div class="stat-label">{{ t('ai.provider') }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card">
            <div class="stat-num">{{ result.estimatedTokens ?? '--' }}</div>
            <div class="stat-label">{{ t('ai.estimatedTokens') }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card">
            <el-tag :type="result.needsReview ? 'danger' : 'success'" size="small">
              {{ result.needsReview ? t('common.yes') : t('common.no') }}
            </el-tag>
            <div class="stat-label">{{ t('ai.needsReview') }}</div>
          </div>
        </el-col>
      </el-row>

      <el-card class="page-card" shadow="never" :header="t('ai.tableSpec')">
        <el-descriptions :column="2" border v-if="result.tableSpec">
          <el-descriptions-item :label="t('ai.tableName')">{{ result.tableSpec.tableName }}</el-descriptions-item>
          <el-descriptions-item :label="t('ai.comment')">{{ result.tableSpec.comment || '--' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="result.tableSpec?.columns || []" border stripe class="block-top">
          <el-table-column prop="columnName" :label="t('ai.columnName')" width="180" />
          <el-table-column prop="dataType" :label="t('ai.dataType')" width="120" />
          <el-table-column prop="javaField" :label="t('ai.javaField')" width="160" />
          <el-table-column prop="javaType" :label="t('ai.javaType')" width="160" />
          <el-table-column :label="t('ai.primaryKey')" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.primaryKey" type="warning" size="small">{{ t('common.yes') }}</el-tag>
              <span v-else>{{ t('common.no') }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="comment" :label="t('ai.comment')" min-width="160" show-overflow-tooltip />
        </el-table>
      </el-card>

      <el-card class="page-card" shadow="never" :header="t('ai.ddl')">
        <pre class="code-block">{{ result.ddl }}</pre>
      </el-card>

      <el-card class="page-card" shadow="never" :header="t('ai.files')">
        <el-collapse v-if="result.files && result.files.length">
          <el-collapse-item v-for="(f, i) in result.files" :key="i" :name="i">
            <template #title>
              <span class="file-title">{{ f.path }}</span>
              <el-tag size="small" class="file-lang">{{ f.language }}</el-tag>
            </template>
            <pre class="code-block">{{ f.content }}</pre>
          </el-collapse-item>
        </el-collapse>
        <el-empty v-else :description="t('ai.noFiles')" :image-size="60" />
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { t } from '@/i18n'
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, RefreshLeft } from '@element-plus/icons-vue'
import { generateScaffold } from '@/api/ai'

const form = reactive({ moduleName: '', prompt: '' })
const loading = ref(false)
const result = ref(null)

async function onGenerate() {
  if (!form.prompt || !form.prompt.trim()) {
    ElMessage.warning(t('ai.promptRequired'))
    return
  }
  loading.value = true
  result.value = null
  try {
    const data = await generateScaffold({ moduleName: form.moduleName || null, prompt: form.prompt.trim() })
    result.value = data || {}
    if (!result.value.tableSpec && !result.value.ddl) {
      ElMessage.warning(t('ai.emptyResult'))
    }
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

function onReset() {
  form.moduleName = ''
  form.prompt = ''
  result.value = null
}
</script>

<style scoped>
.summary-row { margin: 12px 0; }
.stat-card {
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 14px;
  text-align: center;
  min-height: 72px;
}
.stat-num { font-size: 18px; font-weight: 600; }
.stat-label { margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px; }
.block-top { margin-top: 12px; }
.code-block {
  background: var(--el-bg-color-page);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 420px;
  overflow: auto;
}
.file-title { font-family: ui-monospace, monospace; margin-right: 8px; }
.file-lang { margin-left: 8px; }
</style>
