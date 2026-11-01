<template>
  <!-- 列表页通用的「共 N 条 / 第 1/1 页」底部条 -->
  <div class="table-footer">
    <span>共 {{ total }} 条</span>
    <span class="pager">
      <span class="pager__btn" :class="{ 'pager__btn--disabled': page <= 1 }" @click="prev">‹</span>
      <span class="pager__cur">第 {{ page }} / {{ pageCount }} 页</span>
      <span class="pager__btn" :class="{ 'pager__btn--disabled': page >= pageCount }" @click="next">›</span>
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{ total: number; page: number; pageSize?: number }>(),
  { pageSize: 10 }
)

const emit = defineEmits<{ (e: 'update:page', v: number): void }>()

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

function prev() {
  if (props.page > 1) emit('update:page', props.page - 1)
}
function next() {
  if (props.page < pageCount.value) emit('update:page', props.page + 1)
}
</script>

<style scoped>
.pager {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.pager__btn {
  width: 20px;
  height: 20px;
  line-height: 18px;
  text-align: center;
  border: 1px solid var(--line);
  border-radius: 4px;
  color: var(--text-2);
  cursor: pointer;
  user-select: none;
}
.pager__btn--disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>