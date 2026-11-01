<template>
  <div>
    <PageHeader title="知识文档" desc="维修手册、SOP、故障案例与检验规范的统一入库、解析与召回管理。">
      <AiButton label="上传文档" type="primary" @click="uploadVisible = true" />
    </PageHeader>

    <!-- KPI -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <StatCard label="已上传文件" :value="total" sub="本工厂知识库" accent="blue" />
      </el-col>
      <el-col :span="6">
        <StatCard label="已解析" :value="parsedCount" sub="可供检索" accent="green" value-color="var(--c-green)" />
      </el-col>
      <el-col :span="6">
        <StatCard label="解析中" :value="parsingCount" sub="排队中" accent="orange" value-color="var(--c-orange)" />
      </el-col>
      <el-col :span="6">
        <StatCard label="本周调用" value="1,086" sub="由 Agent 召回" accent="cyan" />
      </el-col>
    </el-row>

    <FilterPanel
      v-model:model="filters"
      :fields="[
        { key: 'keyword', label: '关键词', type: 'input', placeholder: '请输入文件名称' },
        { key: 'docType', label: '文档类型', type: 'select', options: options.docType },
        { key: 'parseStatus', label: '解析状态', type: 'select', options: ['全部', '已解析', '解析中', '解析失败'] }
      ]"
      @search="page = 1"
      @reset="reset"
    />

    <div class="panel">
      <el-table :data="paged" style="width: 100%">
        <el-table-column label="文件名称" min-width="250">
          <template #default="{ row }">
            <div class="file-cell">
              <span class="file-badge" :class="`file-badge--${row.format.toLowerCase()}`">{{ row.format }}</span>
              <span class="file-cell__name">{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="format" label="格式" width="80" />
        <el-table-column prop="size" label="大小" width="96" />
        <el-table-column prop="uploader" label="上传人" width="104" />
        <el-table-column prop="uploadTime" label="上传时间" width="150" />
        <el-table-column label="解析状态" width="104">
          <template #default="{ row }">
            <StatusTag :text="row.parseStatus" :map="PARSE_CLASS" />
          </template>
        </el-table-column>
        <el-table-column prop="docType" label="知识类型" width="112" />
        <el-table-column prop="relatedEquipment" label="相关设备" width="120" />
        <el-table-column label="操作" width="168" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看文件清单</el-button>
            <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <TableFooter v-model:page="page" :total="filtered.length" />
    </div>

    <!-- 上传文件清单抽屉 -->
    <DetailDrawer v-model="visible" title="上传文件清单" :subtitle="current?.fileName">
      <div class="detail-list">
        <span class="detail-list__label">文件名称</span>
        <span class="detail-list__value">{{ current?.fileName }}</span>
        <span class="detail-list__label">文件格式</span>
        <span class="detail-list__value">{{ current?.format }} · {{ current?.size }}</span>
        <span class="detail-list__label">上传人</span>
        <span class="detail-list__value">{{ current?.uploader }}</span>
        <span class="detail-list__label">上传时间</span>
        <span class="detail-list__value">{{ current?.uploadTime }}</span>
        <span class="detail-list__label">知识类型</span>
        <span class="detail-list__value">{{ current?.docType }}</span>
        <span class="detail-list__label">相关设备</span>
        <span class="detail-list__value">{{ current?.relatedEquipment }}</span>
        <span class="detail-list__label">解析状态</span>
        <span class="detail-list__value">
          <StatusTag v-if="current" :text="current.parseStatus" :map="PARSE_CLASS" />
        </span>
      </div>

      <div class="panel panel--flat">
        <div class="panel__header">
          <span class="panel__title">包内文件</span>
          <span class="panel__extra">共 {{ kbFilesOfDoc.length }} 个文件</span>
        </div>
        <el-table :data="kbFilesOfDoc" size="small" style="width: 100%">
          <el-table-column prop="fileName" label="文件" min-width="180" />
          <el-table-column prop="format" label="格式" width="72" />
          <el-table-column prop="size" label="大小" width="88" />
          <el-table-column label="状态" width="88">
            <template #default="{ row }">
              <StatusTag :text="row.parseStatus" :map="PARSE_CLASS" />
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="ai-hint">
        <SparkIcon :size="14" />
        <span>解析完成后自动切分入索引，Agent 提问时将按相似度召回并标注出处。</span>
      </div>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <AiButton label="重新解析" type="primary" @click="reparse" />
      </template>
    </DetailDrawer>

    <!-- 上传文档抽屉 -->
    <DetailDrawer v-model="uploadVisible" title="上传文档" subtitle="上传后自动解析并加入当前工厂知识库。">
      <el-form label-position="top">
        <el-form-item label="文件名称" required>
          <el-input v-model="form.fileName" placeholder="请输入文件名称" />
        </el-form-item>
        <el-form-item label="知识类型" required>
          <el-select v-model="form.docType" style="width: 100%">
            <el-option v-for="t in options.docType.slice(1)" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="相关设备" required>
          <el-select v-model="form.relatedEquipment" style="width: 100%" placeholder="请选择相关设备">
            <el-option v-for="e in equipments" :key="e.id" :label="e.name" :value="e.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件格式" required>
          <el-select v-model="form.format" style="width: 100%">
            <el-option v-for="f in options.fileFormat" :key="f" :label="f" :value="f" />
          </el-select>
        </el-form-item>
        <el-form-item label="上传文件">
          <el-upload drag :auto-upload="false" :limit="1" style="width: 100%">
            <div class="upload-tip">将文件拖到此处，或点击选择文件</div>
            <template #tip>
              <div class="upload-hint">支持 PDF / Word / Excel / ZIP / CSV，单个不超过 50 MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <AiButton label="上传" type="primary" @click="submitUpload" />
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import FilterPanel from '../../components/FilterPanel.vue'
import TableFooter from '../../components/TableFooter.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import StatCard from '../../components/StatCard.vue'
import AiButton from '../../components/AiButton.vue'
import SparkIcon from '../../components/SparkIcon.vue'
import { equipments, kbDocuments, kbFilesOfDoc, options } from '../../mock'
import type { KbDocument } from '../../types'

const PARSE_CLASS: Record<string, string> = {
  已解析: 'tag-dot--green',
  解析中: 'tag-dot--orange',
  解析失败: 'tag-dot--red'
}

const filters = reactive({ keyword: '', docType: '全部', parseStatus: '全部' })
const page = ref(1)
const visible = ref(false)
const uploadVisible = ref(false)
const current = ref<KbDocument | null>(null)
const form = reactive({ fileName: '', docType: '维修手册', relatedEquipment: '', format: 'PDF' })

const total = computed(() => kbDocuments.length)
const parsedCount = computed(() => kbDocuments.filter((d) => d.parseStatus === '已解析').length)
const parsingCount = computed(() => kbDocuments.filter((d) => d.parseStatus === '解析中').length)

const filtered = computed(() =>
  kbDocuments.filter((d) => {
    const okKw = !filters.keyword || d.fileName.includes(filters.keyword)
    const okType = filters.docType === '全部' || d.docType === filters.docType
    const okStatus = filters.parseStatus === '全部' || d.parseStatus === filters.parseStatus
    return okKw && okType && okStatus
  })
)
const paged = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10))

function reset() {
  Object.assign(filters, { keyword: '', docType: '全部', parseStatus: '全部' })
  page.value = 1
}

function openDetail(row: KbDocument) {
  current.value = row
  visible.value = true
}

function reparse() {
  ElMessage.success('已提交重新解析任务')
  visible.value = false
}

function remove(row: KbDocument) {
  const idx = kbDocuments.findIndex((d) => d.id === row.id)
  if (idx >= 0) kbDocuments.splice(idx, 1)
  ElMessage.success('文档已删除')
}

function submitUpload() {
  if (!form.fileName.trim()) {
    ElMessage.warning('请填写文件名称')
    return
  }
  const id = Math.max(0, ...kbDocuments.map((d) => d.id)) + 1
  kbDocuments.push({
    id,
    fileName: form.fileName,
    format: form.format,
    size: '3.2 MB',
    uploader: '吴运成',
    uploadTime: '2026-09-07 17:42',
    parseStatus: '解析中',
    docType: form.docType,
    relatedEquipment: form.relatedEquipment || '—'
  })
  uploadVisible.value = false
  Object.assign(form, { fileName: '', docType: '维修手册', relatedEquipment: '', format: 'PDF' })
  ElMessage.success('文档已上传，正在解析')
}
</script>

<style scoped>
.kpi-row {
  margin-bottom: 16px;
}
.file-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.file-badge {
  flex: none;
  min-width: 40px;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  text-align: center;
  color: #fff;
  background: #86909c;
}
.file-badge--pdf {
  background: #f53f3f;
}
.file-badge--word {
  background: #165dff;
}
.file-badge--excel {
  background: #00b42a;
}
.file-badge--zip {
  background: #ff7d00;
}
.file-badge--csv {
  background: #722ed1;
}
.file-cell__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.panel--flat {
  border: 1px solid var(--line);
  box-shadow: none;
  padding: 12px;
}
.panel__extra {
  font-size: 12px;
  color: var(--text-3);
}
.upload-tip {
  font-size: 13px;
  color: var(--text-2);
  padding: 16px 0;
}
.upload-hint {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 6px;
}
</style>