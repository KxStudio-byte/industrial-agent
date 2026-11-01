<template>
  <!-- 全局只有 router-view；带侧边栏的那套外壳在 views/layout/MainLayout.vue 里，
       这样登录页才能独占整个视口。 -->
  <router-view />
</template>

<script setup lang="ts">
// 无需逻辑：布局与页面的分工完全交给路由表决定
</script>