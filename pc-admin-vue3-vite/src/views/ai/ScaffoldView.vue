<template>
  <div>
    <h2 class="page-title">{{ 'AI 脚手架' }}</h2>

    <el-card class="page-card" shadow="never">
      <el-form :model="form" label-width="90px">
        <el-form-item :label="'目标模块'">
          <el-input v-model="form.moduleName" :placeholder="'可选，留空则按提示词推断（如 order、user_point）'" clearable />
        </el-form-item>
        <el-form-item :label="'提示词'">
          <el-input v-model="form.prompt" type="textarea" :rows="5" :placeholder="'用自然语言描述要生成的表/功能，例如：用户积分流水表，含 user_id、变更积分、余额、备注、创建时间'" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" :icon="MagicStick" @click="onGenerate">
            {{ loading ? '生成中…' : '生成脚手架' }}
          </el-button>
          <el-button :icon="RefreshLeft" @click="onReset">{{ '重置' }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-empty v-if="!loading && !result" :description="'输入提示词并点击生成，查看表结构 / 建表 DDL / 代码草稿。'" />

    <template v-if="result">
      <el-alert :title="'以下为 AI 生成的脚手架草稿，请人工复核后再落地。'" type="warning" :closable="false" show-icon class="page-card" />

      <el-row :gutter="12" class="summary-row">
        <el-col :span="8">
          <div class="stat-card">
            <div class="stat-num">{{ result.provider || '--' }}</div>
            <div class="stat-label">{{ '提供方' }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card">
            <div class="stat-num">{{ result.estimatedTokens ?? '--' }}</div>
            <div class="stat-label">{{ '预估 Token' }}</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card">
            <el-tag :type="result.needsReview ? 'danger' : 'success'" size="small">
              {{ result.needsReview ? '是' : '否' }}
            </el-tag>
            <div class="stat-label">{{ '需人工复核' }}</div>
          </div>
        </el-col>
      </el-row>

      <el-card class="page-card" shadow="never" :header="'表结构'">
        <el-descriptions :column="2" border v-if="result.tableSpec">
          <el-descriptions-item :label="'表名'">{{ result.tableSpec.tableName }}</el-descriptions-item>
          <el-descriptions-item :label="'说明'">{{ result.tableSpec.comment || '--' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="result.tableSpec?.columns || []" border stripe class="block-top">
          <el-table-column prop="columnName" :label="'字段名'" width="180" />
          <el-table-column prop="dataType" :label="'数据类型'" width="120" />
          <el-table-column prop="javaField" :label="'Java 属性'" width="160" />
          <el-table-column prop="javaType" :label="'Java 类型'" width="160" />
          <el-table-column :label="'主键'" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.primaryKey" type="warning" size="small">{{ '是' }}</el-tag>
              <span v-else>{{ '否' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="comment" :label="'说明'" min-width="160" show-overflow-tooltip />
        </el-table>
      </el-card>

      <el-card class="page-card" shadow="never" :header="'建表 DDL'">
        <pre class="code-block">{{ result.ddl }}</pre>
      </el-card>

      <el-card class="page-card" shadow="never" :header="'生成文件'">
        <el-collapse v-if="result.files && result.files.length">
          <el-collapse-item v-for="(f, i) in result.files" :key="i" :name="i">
            <template #title>
              <span class="file-title">{{ f.path }}</span>
              <el-tag size="small" class="file-lang">{{ f.language }}</el-tag>
            </template>
            <pre class="code-block">{{ f.content }}</pre>
          </el-collapse-item>
        </el-collapse>
        <el-empty v-else :description="'无生成文件'" :image-size="60" />
      </el-card>
    </template>
  </div>
</template>

<script setup>

import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, RefreshLeft } from '@element-plus/icons-vue'
import { generateScaffold } from '@/api/ai'

const form = reactive({ moduleName: '', prompt: '' })
const loading = ref(false)
const result = ref(null)

async function onGenerate() {
  if (!form.prompt || !form.prompt.trim()) {
    ElMessage.warning('请输入提示词')
    return
  }
  loading.value = true
  result.value = null
  try {
    const data = await generateScaffold({ moduleName: form.moduleName || null, prompt: form.prompt.trim() })
    result.value = data || {}
    if (!result.value.tableSpec && !result.value.ddl) {
      ElMessage.warning('AI 未返回有效结果，请调整提示词后重试。')
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
