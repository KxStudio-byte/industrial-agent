<template>
  <div class="page-header">
    <div class="page-header__left">
      <h2 class="page-title">{{ title }}</h2>
      <p v-if="desc" class="page-desc">{{ desc }}</p>
    </div>
    <div class="page-header__actions">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ title: string; desc?: string }>()
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.page-header__left {
  min-width: 0;
}
.page-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding-top: 2px;
}
</style>