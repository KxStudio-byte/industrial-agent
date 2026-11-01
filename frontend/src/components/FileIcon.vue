<template>
  <!--
    文件类型图标：原型里文档列表最左侧用不同底色的小方块快速区分格式，
    PDF 红、Word 蓝、Excel 绿、ZIP 橙、其余灰。
  -->
  <span class="file-icon" :class="`file-icon--${tone}`" :style="boxStyle">{{ label }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** 文件格式，如 PDF / DOCX / XLSX / CSV / ZIP / PNG，也可直接传 fileName */
  format?: string
  fileName?: string
  size?: number
}>()

const ext = computed(() => {
  if (props.format) return props.format.toUpperCase()
  const name = props.fileName ?? ''
  const dot = name.lastIndexOf('.')
  return dot >= 0 ? name.slice(dot + 1).toUpperCase() : 'FILE'
})

const label = computed(() => {
  if (['PDF'].includes(ext.value)) return 'PDF'
  if (['DOC', 'DOCX', 'WORD'].includes(ext.value)) return 'W'
  if (['XLS', 'XLSX', 'EXCEL', 'CSV'].includes(ext.value)) return 'X'
  if (['ZIP', 'RAR', '7Z'].includes(ext.value)) return 'ZIP'
  if (['PNG', 'JPG', 'JPEG', 'GIF'].includes(ext.value)) return 'IMG'
  if (['MD', 'MARKDOWN', 'TXT'].includes(ext.value)) return 'TXT'
  return 'FILE'
})

const tone = computed(() => {
  if (label.value === 'PDF') return 'red'
  if (label.value === 'W') return 'blue'
  if (label.value === 'X') return 'green'
  if (label.value === 'ZIP') return 'orange'
  return 'gray'
})

const boxStyle = computed(() => {
  const s = props.size ?? 22
  return { width: `${s}px`, height: `${s}px` }
})
</script>

<style scoped>
.file-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  color: #fff;
  flex: none;
  letter-spacing: -0.3px;
}
.file-icon--red {
  background: #f53f3f;
}
.file-icon--blue {
  background: #1677ff;
}
.file-icon--green {
  background: #00b42a;
}
.file-icon--orange {
  background: #ff7d00;
}
.file-icon--gray {
  background: #86909c;
}
</style>