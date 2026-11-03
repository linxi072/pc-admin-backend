<template>
  <div class="stat-card" :class="{ 'is-loading': loading }">
    <div v-if="icon || $slots.icon" class="stat-card__icon">
      <slot name="icon">
        <el-icon v-if="icon"><component :is="icon" /></el-icon>
      </slot>
    </div>
    <div class="stat-card__body">
      <div class="stat-card__label">{{ label }}</div>
      <div class="stat-card__value">
        <span v-if="loading" class="stat-card__skeleton" />
        <template v-else>
          <span class="stat-card__num">{{ formatted.text }}</span>
          <span v-if="formatted.unit" class="stat-card__unit">{{ formatted.unit }}</span>
        </template>
      </div>
      <div v-if="showTrend && !loading && delta" class="stat-card__trend" :class="trendClass">
        <el-icon class="stat-card__trend-icon"><component :is="trendIcon" /></el-icon>
        <span>{{ trendText }}</span>
      </div>
      <div v-if="$slots.footer" class="stat-card__footer">
        <slot name="footer" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatStat, trendOf } from '@/utils/stat'
import { ArrowUp, ArrowDown, Minus } from '@element-plus/icons-vue'

const props = defineProps({
  // 指标名称（如「今日订单」「平均响应耗时」）
  label: { type: String, default: '' },
  // 原始数值，支持 number / string / null（null 显示占位符 '-'）
  value: { type: [Number, String], default: null },
  // 单位后缀（如 '%'、'ms'、'人'）
  unit: { type: String, default: '' },
  // 小数位（默认 0，自动去尾零）
  decimals: { type: Number, default: 0 },
  // 对比基线（用于趋势箭头与环比）
  baseline: { type: [Number, String], default: null },
  // 是否展示趋势（需配合 baseline）
  showTrend: { type: Boolean, default: false },
  // 'positive'：升=绿/降=红；'finance'：升=红/降=绿（A 股习惯）
  trendColorMode: { type: String, default: 'positive' },
  // 左上方图标（element-plus icons 组件）
  icon: { type: [Object, Function], default: null },
  // 加载态：展示骨架占位
  loading: { type: Boolean, default: false }
})

const formatted = computed(() =>
  formatStat(props.value, {
    decimals: props.decimals,
    unit: props.unit || '',
    compact: true
  })
)

const trend = computed(() => trendOf(props.value, props.baseline))

const trendIcon = computed(
  () =>
    ({ up: ArrowUp, down: ArrowDown, flat: Minus })[trend.value]
)

const trendClass = computed(() => {
  const palette =
    props.trendColorMode === 'finance'
      ? { up: 'is-up-finance', down: 'is-down-finance', flat: 'is-flat' }
      : { up: 'is-up', down: 'is-down', flat: 'is-flat' }
  return palette[trend.value]
})

// 环比差值（基线存在且非零时计算百分比）
const delta = computed(() => {
  const cur = Number(props.value)
  const base = Number(props.baseline)
  if (props.baseline == null || Number.isNaN(cur) || Number.isNaN(base) || base === 0) {
    return null
  }
  return (cur - base) / base
})

const trendText = computed(() => {
  if (delta.value == null) return ''
  const pct = delta.value * 100
  return `${pct >= 0 ? '+' : ''}${pct.toFixed(1)}%`
})
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  box-sizing: border-box;
}
.stat-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  font-size: 20px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  flex: 0 0 auto;
}
.stat-card__body {
  flex: 1 1 auto;
  min-width: 0;
}
.stat-card__label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.stat-card__value {
  margin-top: 6px;
  line-height: 1.2;
}
.stat-card__num {
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}
.stat-card__unit {
  margin-left: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.stat-card__skeleton {
  display: inline-block;
  width: 96px;
  height: 22px;
  border-radius: 4px;
  background: linear-gradient(
    90deg,
    var(--el-fill-color) 25%,
    var(--el-fill-color-light) 37%,
    var(--el-fill-color) 63%
  );
  background-size: 400% 100%;
  animation: stat-skeleton 1.4s ease infinite;
  vertical-align: middle;
}
.stat-card__trend {
  margin-top: 6px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 2px;
}
.stat-card__trend-icon {
  font-size: 14px;
}
.is-up {
  color: var(--el-color-success);
}
.is-down {
  color: var(--el-color-danger);
}
.is-up-finance {
  color: var(--el-color-danger);
}
.is-down-finance {
  color: var(--el-color-success);
}
.is-flat {
  color: var(--el-text-color-secondary);
}
.stat-card__footer {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
@keyframes stat-skeleton {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}
</style>
