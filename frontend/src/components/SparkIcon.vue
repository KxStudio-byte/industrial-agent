<template>
  <!-- 侧边栏 / 按钮右侧那颗小闪电，原型里到处都是，统一成一个组件 -->
  <svg :width="size" :height="size" viewBox="0 0 16 16" class="spark">
    <defs>
      <linearGradient :id="gid" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stop-color="#4aa8ff" />
        <stop offset="100%" stop-color="#1677ff" />
      </linearGradient>
    </defs>
    <path
      d="M9.6 1L3.5 8.6h3.6L6.4 15l6.1-7.6H8.9z"
      :fill="`url(#${gid})`"
    />
  </svg>
</template>

<script setup lang="ts">
import { useId } from 'vue'

withDefaults(defineProps<{ size?: number }>(), { size: 14 })

// 每个实例需要独立的渐变 id，否则同一页面多个闪电会互相覆盖
const gid = 'spark-' + useId()
</script>

<style scoped>
.spark {
  flex-shrink: 0;
  display: inline-block;
  vertical-align: -2px;
}
</style>