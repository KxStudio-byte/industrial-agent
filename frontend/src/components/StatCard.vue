<template>
  <!-- 原型里每张指标卡都是「左侧 3px 彩条 + 标签 + 大数字 + 灰色副标题」 -->
  <div class="stat-card" :style="{ '--accent': accentColor }">
    <div class="stat-card__label">{{ label }}</div>
    <div class="stat-card__value" :class="{ 'stat-card__value--sm': small }" :style="{ color: valueColor }">
      {{ value }}
    </div>
    <div v-if="sub" class="stat-card__sub">{{ sub }}</div>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/** accent 支持小程序里的语义名，转成对应的 CSS 变量 */
const props = withDefaults(
  defineProps<{
    label: string
    value: string | number
    sub?: string
    accent?: 'blue' | 'green' | 'orange' | 'red' | 'cyan' | 'purple'
    valueColor?: string
    small?: boolean
  }>(),
  { accent: 'blue', valueColor: 'var(--text-1)' }
)

const accentColor = computed(() => {
  const map = {
    blue: 'var(--c-blue)',
    green: 'var(--c-green)',
    orange: 'var(--c-orange)',
    red: 'var(--c-red)',
    cyan: 'var(--c-cyan)',
    purple: 'var(--c-purple)'
  } as const
  return map[props.accent]
})
</script>