<template>
  <!--
    页面右上角的主操作按钮。
    原型里这类「AI 动作」按钮的右边永远挂着一颗小闪电，这里封装掉。
  -->
  <el-button
    :type="type"
    :size="size"
    :plain="plain"
    :class="['ai-btn', { 'ai-btn--text': text }]"
    @click="emit('click')"
  >
    <span class="ai-btn__label">{{ label }}</span>
    <SparkIcon :size="13" class="ai-btn__spark" />
  </el-button>
</template>

<script setup lang="ts">
import SparkIcon from './SparkIcon.vue'

withDefaults(
  defineProps<{
    label: string
    type?: 'primary' | 'default' | 'danger' | 'warning'
    size?: 'small' | 'default' | 'large'
    plain?: boolean
    /** 文本按钮（白色/无边框），用于「查看」「查看执行列表」这类次级动作 */
    text?: boolean
  }>(),
  { type: 'default', size: 'default', plain: false, text: false }
)

const emit = defineEmits<{ (e: 'click'): void }>()
</script>

<style scoped>
.ai-btn__label {
  margin-right: 4px;
}
.ai-btn--text {
  background: #fff;
  border-color: var(--line);
}
</style>