<template>
  <div>
    <h2 class="page-title">可观测中心</h2>
    <p class="scope-hint">
      链路追踪 · Kafka 有序消费 · N+1 查询探测 · 幂等记录 · 告警收件箱——五个运维面的统一入口。
      各 Tab 数据均为进程内/库内实时口径，按后端权限最小授权：无对应权限的 Tab 展示降级空态而非报错。
    </p>

    <el-card class="page-card" shadow="never">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="链路追踪" name="trace">
          <TraceTab v-if="activeTab === 'trace'" />
        </el-tab-pane>
        <el-tab-pane label="Kafka 监控" name="kafka">
          <KafkaTab v-if="activeTab === 'kafka'" />
        </el-tab-pane>
        <el-tab-pane label="N+1 疑似" name="nplus1">
          <NPlusOneTab v-if="activeTab === 'nplus1'" />
        </el-tab-pane>
        <el-tab-pane label="幂等记录" name="idempotency">
          <IdempotencyTab v-if="activeTab === 'idempotency'" />
        </el-tab-pane>
        <el-tab-pane label="告警收件箱" name="alert">
          <AlertTab v-if="activeTab === 'alert'" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import TraceTab from './components/TraceTab.vue'
import KafkaTab from './components/KafkaTab.vue'
import NPlusOneTab from './components/NPlusOneTab.vue'
import IdempotencyTab from './components/IdempotencyTab.vue'
import AlertTab from './components/AlertTab.vue'

// 仅渲染当前 Tab（v-if）：未激活的 Tab 不发请求，避免进入页面即触发 5 组
// （其中 3 组对普通运维角色会 403）的无效调用；同时把 403 噪声限制在用户主动查看的 Tab。
const activeTab = ref('trace')
</script>
