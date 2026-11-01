<template>
  <!-- 简易横向进度条，原型里大量「名称 —— 条 —— 百分比」的排列 -->
  <div class="bar-row" :class="{ 'bar-row--block': block }">
    <span v-if="label" class="bar-row__label">{{ label }}</span>
    <div class="bar-row__track">
      <div class="bar-row__fill" :style="{ width: pct + '%', background: color }" />
    </div>
    <span class="bar-row__value">{{ valueText }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    label?: string
    /** 百分比 0-100，也允许超过 100（超产场景会被裁到 100） */
    percent: number
    valueText?: string
    color?: string
    block?: boolean
  }>(),
  { color: 'var(--c-blue)', valueText: '' }
)

const pct = computed(() => Math.max(0, Math.min(100, props.percent)))
</script>

<style scoped>
.bar-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  line-height: 22px;
}
.bar-row__label {
  width: 68px;
  flex-shrink: 0;
  color: var(--text-2);
}
.bar-row__track {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: var(--line-light);
  overflow: hidden;
}
.bar-row__fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.4s ease;
}
.bar-row__value {
  min-width: 52px;
  text-align: right;
  color: var(--text-1);
}
</style>