<template>
  <!-- 统一的状态小标签：圆点 + 文字，样式由 dict.ts 里的映射决定 -->
  <span class="tag-dot" :class="styleClass">{{ text }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { cls } from '../utils/dict'

const props = defineProps<{
  text: string
  /** 直接指定样式类，优先级高于 map */
  tone?: 'green' | 'orange' | 'red' | 'blue' | 'gray'
  map?: Record<string, string>
}>()

const styleClass = computed(() => {
  if (props.tone) return `tag-dot--${props.tone}`
  if (props.map) return cls(props.map, props.text)
  return 'tag-dot--gray'
})
</script>