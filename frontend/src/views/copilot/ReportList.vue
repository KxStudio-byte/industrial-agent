<template>
  <div>
    <PageHeader title="报表生成" desc="由 Agent 自动汇总 MES、IoT、质量与能耗数据，生成可签发的日报、周报与分析报告。">
      <el-button class="white-btn" @click="exportPdf">导出</el-button>
    </PageHeader>

    <div class="panel">
      <div class="toolbar">
        <div class="toolbar__field">
          <span class="toolbar__label">关键词</span>
          <el-input
            v-model="filters.keyword"
            placeholder="请输入报表标题"
            clearable
            style="width: 240px"
            @keyup.enter="search"
          />
        </div>
        <span class="toolbar__divider" />
        <div class="toolbar__field">
          <span class="toolbar__label">报表类型</span>
          <el-select v-model="filters.type" style="width: 180px">
            <el-option v-for="t in options.reportType" :key="t" :label="t" :value="t" />
          </el-select>
        </div>
        <div class="toolbar__actions">
          <el-button type="primary" @click="search">查询</el-button>
          <el-button class="white-btn" @click="reset">重置</el-button>
          <AiButton label="生成报表" type="primary" @click="openCreate" />
        </div>
      </div>

      <el-table :data="rows" style="width: 100%">
        <el-table-column prop="title" label="报表标题" min-width="300">
          <template #default="{ row }">
            <div class="title-cell">
              <FileIcon format="PDF" :size="20" />
              <a class="title-cell__link" @click="openDetail(row)">{{ row.title }}</a>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="报表类型" width="130" />
        <el-table-column prop="scope" label="覆盖范围" width="170" />
        <el-table-column prop="createTime" label="生成时间" width="170" />
        <el-table-column prop="source" label="生成来源" width="150" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :text="row.status" :map="REPORT_STATUS_CLASS" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="primary" @click="download(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>

      <TableFooter
        :total="rows.length"
        :page="page"
        :page-size="pageSize"
        @update:page="(v: number) => (page = v)"
      />
    </div>

    <!-- 生成报表抽屉 -->
    <DetailDrawer v-model="createVisible" title="生成报表" subtitle="选择范围后由 Agent 自动汇总数据并生成正文" width="480px">
      <el-form label-position="top" class="create-form">
        <el-form-item label="报表标题" required>
          <el-input v-model="form.title" placeholder="请输入报表标题" />
        </el-form-item>
        <el-form-item label="报表类型" required>
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in options.reportType" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="覆盖范围" required>
          <el-select v-model="form.scope" style="width: 100%">
            <el-option v-for="s in ['全厂', 'A产线', 'B产线', 'C产线']" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <div class="ai-hint">
          生成时会自动读取同等时间范围的 <b>MES 产量</b>、<b>IoT 设备数据</b>、<b>质量 SPC</b> 与 <b>能耗计量</b>，
          约需 15 秒。
        </div>
      </el-form>
      <template #footer>
        <el-button style="flex: 1" @click="createVisible = false">取消</el-button>
        <el-button type="primary" style="flex: 1" :loading="generating" @click="generate">
          生成报表
        </el-button>
      </template>
    </DetailDrawer>

    <!-- 报表详情抽屉（报纸版式） -->
    <DetailDrawer
      v-model="detailVisible"
      :title="current?.title ?? '报表详情'"
      :subtitle="`${current?.type ?? ''} · ${current?.scope ?? ''} · 生成于 ${current?.createTime ?? ''}`"
      width="760px"
    >
      <article v-if="detail" class="paper">
        <div class="paper__masthead">
          <div class="paper__brand">{{ detail.factory }}</div>
          <div class="paper__meta-row">
            <span>范围：{{ detail.scope }}</span>
            <span>生成时间：{{ detail.createTime }}</span>
          </div>
        </div>

        <h1 class="paper__title">{{ detail.title }}</h1>
        <p class="paper__subtitle">{{ detail.subtitle }}</p>
        <div class="paper__byline">
          <span>签发：{{ detail.issuer }}</span>
          <span>覆盖范围：{{ detail.scope }}</span>
          <span>生成时间：{{ detail.createTime }}</span>
        </div>

        <h2 class="paper__section">一、今日结论</h2>
        <p class="paper__para">{{ detail.conclusion }}</p>
        <div class="paper__kpis">
          <div v-for="k in detail.kpis" :key="k.label" class="paper__kpi">
            <div class="paper__kpi-label">{{ k.label }}</div>
            <div class="paper__kpi-value" :style="{ color: k.color }">{{ k.value }}</div>
            <div class="paper__kpi-sub">{{ k.unit }}</div>
          </div>
        </div>

        <h2 class="paper__section">二、关键数据</h2>
        <table class="paper__table">
          <thead>
            <tr>
              <th>产线</th>
              <th>计划产量</th>
              <th>实际产量</th>
              <th>达成率</th>
              <th>OEE</th>
              <th>良品率</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in detail.keyData" :key="row.lineName">
              <td>{{ row.lineName }}</td>
              <td>{{ row.target }}</td>
              <td>{{ row.actual }}</td>
              <td>{{ row.achieve }}</td>
              <td>{{ row.oee }}</td>
              <td>{{ row.quality }}</td>
              <td>{{ row.status }}</td>
            </tr>
          </tbody>
        </table>

        <h2 class="paper__section">三、主要原因</h2>
        <p class="paper__para paper__para--note">{{ detail.reasonsNote }}</p>
        <div v-for="r in detail.reasons" :key="r.label" class="paper__reason">
          <div class="paper__reason-head">
            <span class="paper__reason-label">{{ r.label }}</span>
            <span class="paper__reason-value">{{ r.value }}</span>
          </div>
          <BarRow :percent="Number(r.value.replace('%', ''))" :value-text="r.value" :color="reasonColor(r.value)" />
          <p class="paper__reason-text">{{ r.text }}</p>
        </div>

        <h2 class="paper__section">四、建议与需决策事项</h2>
        <ol class="paper__list">
          <li v-for="s in detail.suggestions" :key="s">{{ s }}</li>
        </ol>

        <div class="paper__footer">{{ detail.footer }}</div>
      </article>

      <template #footer>
        <el-button style="flex: 1" @click="detailVisible = false">关闭</el-button>
        <el-button style="flex: 1" @click="archive">归档到知识库</el-button>
        <el-button type="primary" style="flex: 1" @click="exportPdf">导出 PDF</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import AiButton from '../../components/AiButton.vue'
import StatusTag from '../../components/StatusTag.vue'
import FileIcon from '../../components/FileIcon.vue'
import BarRow from '../../components/BarRow.vue'
import TableFooter from '../../components/TableFooter.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { options, productionDailyReport, reports } from '../../mock'
import type { Report } from '../../types'

/** 报表状态 -> 样式类 */
const REPORT_STATUS_CLASS: Record<string, string> = {
  已生成: 'tag-dot--green',
  生成中: 'tag-dot--orange',
  生成失败: 'tag-dot--red'
}

const page = ref(1)
const pageSize = 10

const filters = reactive({ keyword: '', type: '全部' })
const applied = reactive({ keyword: '', type: '全部' })

const rows = computed(() =>
  reports.filter((r) => {
    if (applied.keyword && !r.title.includes(applied.keyword)) return false
    if (applied.type !== '全部' && r.type !== applied.type) return false
    return true
  })
)

function search() {
  applied.keyword = filters.keyword.trim()
  applied.type = filters.type
  page.value = 1
}

function reset() {
  filters.keyword = ''
  filters.type = '全部'
  search()
}

/* ---------- 生成报表 ---------- */
const createVisible = ref(false)
const generating = ref(false)
const form = reactive({ title: '2026-09-07 生产日报', type: '生产日报', scope: '全厂' })

function openCreate() {
  createVisible.value = true
}

function today() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function generate() {
  if (!form.title || !form.type || !form.scope) {
    ElMessage.warning('请补全报表标题、类型与覆盖范围')
    return
  }
  generating.value = true
  window.setTimeout(() => {
    generating.value = false
    reports.unshift({
      id: Date.now(),
      title: form.title,
      type: form.type,
      scope: form.scope,
      createTime: today() + ' 18:30',
      source: '工业报告Agent',
      status: '已生成'
    })
    createVisible.value = false
    search()
    ElMessage.success('报表已生成')
  }, 900)
}

/* ---------- 详情 ---------- */
const detailVisible = ref(false)
const current = ref<Report | null>(null)
const detail = productionDailyReport

watch(detailVisible, (v) => {
  if (!v) current.value = null
})

function openDetail(row: Report) {
  current.value = row
  detailVisible.value = true
}

function reasonColor(value: string) {
  const n = Number(value.replace('%', ''))
  if (n >= 40) return 'var(--c-red)'
  if (n >= 25) return 'var(--c-orange)'
  return 'var(--c-blue)'
}

function exportPdf() {
  ElMessage.success('已导出 PDF')
}

function download(row: Report) {
  ElMessage.success(`正在下载《${row.title}》`)
}

function archive() {
  ElMessage.success('已归档到知识库')
  detailVisible.value = false
}
</script>

<style scoped>
.white-btn {
  background: #fff;
}

.toolbar {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.toolbar__field {
  display: flex;
  flex-direction: column;
}
.toolbar__label {
  font-size: 13px;
  color: var(--text-3);
  line-height: 20px;
  margin-bottom: 6px;
}
.toolbar__divider {
  width: 1px;
  align-self: stretch;
  background: var(--line-light);
  margin-bottom: 4px;
}
.toolbar__actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.title-cell__link {
  color: var(--text-1);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.title-cell__link:hover {
  color: var(--brand);
}

.create-form :deep(.el-form-item) {
  margin-bottom: 16px;
}
.create-form :deep(.el-form-item__label) {
  color: var(--text-3);
  font-size: 13px;
  padding-bottom: 4px;
}

/* ---------- 报纸版式 ---------- */
.paper {
  padding: 4px 4px 0;
}
.paper__masthead {
  border-bottom: 2px solid var(--text-1);
  padding-bottom: 8px;
}
.paper__brand {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
  color: var(--text-1);
}
.paper__meta-row {
  display: flex;
  gap: 20px;
  font-size: 12px;
  color: var(--text-3);
  margin-top: 4px;
}
.paper__title {
  margin: 16px 0 4px;
  font-size: 26px;
  font-weight: 700;
  text-align: center;
  letter-spacing: 2px;
  color: var(--text-1);
}
.paper__subtitle {
  margin: 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-3);
  letter-spacing: 1px;
}
.paper__byline {
  display: flex;
  justify-content: center;
  gap: 18px;
  margin: 10px 0 4px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  font-size: 12px;
  color: var(--text-2);
}
.paper__section {
  margin: 20px 0 10px;
  padding-left: 10px;
  border-left: 3px solid var(--text-1);
  font-size: 15px;
  font-weight: 700;
  color: var(--text-1);
}
.paper__para {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 24px;
  color: var(--text-2);
  text-align: justify;
}
.paper__para--note {
  color: var(--text-3);
  font-size: 12px;
}
.paper__kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.paper__kpi {
  border: 1px solid var(--line);
  border-radius: 4px;
  padding: 10px 12px;
  text-align: center;
}
.paper__kpi-label {
  font-size: 12px;
  color: var(--text-3);
}
.paper__kpi-value {
  font-size: 20px;
  font-weight: 700;
  line-height: 28px;
}
.paper__kpi-sub {
  font-size: 11px;
  color: var(--text-3);
}
.paper__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}
.paper__table th,
.paper__table td {
  border: 1px solid var(--line);
  padding: 7px 10px;
  text-align: center;
  color: var(--text-2);
}
.paper__table th {
  background: #fafbfc;
  color: var(--text-1);
  font-weight: 600;
}
.paper__table tbody tr:last-child td {
  background: #fafbfc;
  font-weight: 600;
  color: var(--text-1);
}
.paper__reason {
  margin-bottom: 12px;
}
.paper__reason-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 4px;
}
.paper__reason-label {
  font-weight: 600;
  color: var(--text-1);
}
.paper__reason-value {
  color: var(--text-2);
}
.paper__reason-text {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 21px;
  color: var(--text-3);
}
.paper__list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 24px;
  color: var(--text-2);
}
.paper__footer {
  margin-top: 20px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  font-size: 12px;
  color: var(--text-3);
  text-align: center;
}
</style>