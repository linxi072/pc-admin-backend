<template>
  <div>
    <h2 class="page-title">工作台</h2>
    <div class="stat-grid">
      <div class="stat-card" v-for="s in stats" :key="s.label">
        <div class="label">{{ s.label }}</div>
        <div class="value">{{ s.value }}</div>
      </div>
    </div>
    <div class="two-col">
      <el-card class="page-card" header="快捷入口">
        <div class="quick">
          <el-button type="primary" @click="go('user')">用户管理</el-button>
          <el-button type="primary" @click="go('role')">角色管理</el-button>
          <el-button type="primary" @click="go('task')">我的待办</el-button>
          <el-button type="primary" @click="go('instance')">我发起的流程</el-button>
        </div>
      </el-card>
      <el-card class="page-card" header="系统信息">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="前端框架">Vue 3 + Element Plus + Vite</el-descriptions-item>
          <el-descriptions-item label="后端服务">java-admin-framework（jOOQ / JobRunr / Flowable）</el-descriptions-item>
          <el-descriptions-item label="数据模式">{{ USE_MOCK ? 'Mock 演示（前端内置）' : '对接真实后端' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { USE_MOCK } from '@/api/config'

const router = useRouter()
const stats = ref([
  { label: '今日新增用户', value: 12 },
  { label: '待办审批', value: 2 },
  { label: '运行中流程', value: 5 },
  { label: '在线接口', value: 36 }
])
function go(name) {
  router.push({ name })
}
</script>
