<template>
  <!--
    筛选区。原型里所有列表页的表单项都是 label 在上、控件在下，
    控件之间用竖线分隔，最右是「查询 / 重置」两个按钮。
  -->
  <div class="filter-panel">
    <div class="filter-row">
      <div v-for="field in fields" :key="field.key" class="filter-item">
        <div class="filter-item__label">{{ field.label }}</div>

        <el-select
          v-if="field.type === 'select'"
          :model-value="model[field.key] ?? ''"
          :placeholder="field.placeholder ?? '全部'"
          style="width: 100%"
          @update:model-value="(v: string) => update(field.key, v)"
        >
          <el-option v-for="opt in field.options ?? []" :key="opt" :label="opt" :value="opt" />
        </el-select>

        <el-input
          v-else
          :model-value="model[field.key] ?? ''"
          :placeholder="field.placeholder ?? '请输入'"
          clearable
          @update:model-value="(v: string) => update(field.key, v)"
        />
      </div>

      <div class="filter-actions">
        <el-button type="primary" @click="emit('search')">查询</el-button>
        <el-button class="reset-btn" @click="emit('reset')">重置</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
export interface FilterField {
  key: string
  label: string
  type: 'select' | 'input'
  placeholder?: string
  options?: string[]
}

const props = defineProps<{
  fields: FilterField[]
  /**
   * 当前筛选值。
   * <p>
   * 父组件有两种写法：`v-model:model="filters"`（filters 是 reactive 对象）。
   * 注意：这里 **不能** 通过 emit 新对象的方式去更新它 —— 在 `v-model:model` 下
   * 组件内 emit('update:model', newObj) 会被编译成 `filters = newObj`，
   * 而 filters 是 `const` 的 reactive，赋值直接失败，表现为下拉框选了没反应、输入框打不进字。
   * 所以统一用「原地改属性」的方式通知父级，reactive 对象天然能收到。
   */
  model: Record<string, string>
}>()

const emit = defineEmits<{
  (e: 'update:model', v: Record<string, string>): void
  (e: 'search'): void
  (e: 'reset'): void
}>()

/**
 * 写回筛选值。
 * <p>
 * <b>为什么是原地赋值 + emit 同一个引用</b>：
 * `props.model` 在父组件是 reactive 对象，直接给它加/改键就能触发父组件的 computed 重算，
 * 不需要替换对象本身。emit 也把同一个引用传回去，这样父组件即使写的是
 * `@update:model="filters = $event"` 之类的形式也不会出错（引用没变）。
 */
function update(key: string, value: string) {
  if (props.model[key] === value) {
    return
  }
  // reactive 对象的属性赋值即可触发响应式；parent 的 filters 是同一引用
  ;(props.model as Record<string, string>)[key] = value
  emit('update:model', props.model)
}
</script>

<style scoped>
.filter-row {
  display: flex;
  align-items: flex-end;
  gap: 0;
  flex-wrap: wrap;
}
.filter-item {
  width: 220px;
  padding-right: 16px;
  border-right: 1px solid var(--line-light);
  margin-right: 16px;
  margin-bottom: 12px;
}
.filter-item:last-of-type {
  border-right: none;
}
.filter-item__label {
  font-size: 13px;
  color: var(--text-3);
  line-height: 20px;
  margin-bottom: 6px;
}
.filter-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.reset-btn {
  background: #fff;
}
</style>