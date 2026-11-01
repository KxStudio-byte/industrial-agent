<template>
  <!--
    通用右侧抽屉。原型里所有「查看详情」都是右侧滑出：
    标题 + 一句灰色说明 + 右上角「关闭」。
  -->
  <el-drawer
    :model-value="modelValue"
    :size="width"
    :with-header="false"
    class="detail-drawer"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div class="drawer-head">
      <div class="drawer-head__left">
        <h3 class="drawer-head__title">{{ title }}</h3>
        <p v-if="subtitle" class="drawer-head__desc">{{ subtitle }}</p>
      </div>
      <el-button class="drawer-head__close" @click="emit('update:modelValue', false)">关闭</el-button>
    </div>

    <div class="drawer-body">
      <slot />
    </div>

    <div v-if="$slots.footer" class="drawer-footer">
      <slot name="footer" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    subtitle?: string
    /** 原型里详情抽屉宽度约 520px —— 百分比字符串也可 */
    width?: string | number
  }>(),
  { width: '520px' }
)

const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()
</script>

<style scoped>
.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line-light);
}
.drawer-head__title {
  margin: 0 0 4px;
  font-size: 17px;
  font-weight: 600;
  color: var(--text-1);
}
.drawer-head__desc {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
  line-height: 18px;
}
.drawer-head__close {
  background: #fff;
}
.drawer-body {
  padding-top: 16px;
}
.drawer-footer {
  margin-top: 20px;
  display: flex;
  gap: 10px;
}
</style>

<style>
/* 抽屉整体内边距交给内部结构控制 */
.detail-drawer .el-drawer__body {
  padding: 18px 22px !important;
  display: flex;
  flex-direction: column;
}
</style>