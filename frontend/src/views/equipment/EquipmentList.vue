<template>
  <div>
    <PageHeader title="设备列表" desc="全厂设备台账、实时指标与健康评分。">
      <el-button :loading="exporting" @click="exportList">导出</el-button>
      <el-button type="primary" @click="create">新增设备</el-button>
    </PageHeader>

    <el-row :gutter="16" v-loading="summaryLoading">
      <el-col :span="6">
        <StatCard label="设备总数" :value="summary.total" sub="台" accent="blue" />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="运行中"
          :value="summary.online"
          sub="台"
          accent="green"
          value-color="var(--c-green)"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="离线"
          :value="summary.offline"
          sub="台"
          accent="orange"
          value-color="var(--c-orange)"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="异常"
          :value="summary.abnormal"
          sub="台"
          accent="red"
          value-color="var(--c-red)"
        />
      </el-col>
    </el-row>

    <FilterPanel
      v-model:model="filters"
      class="filter-top"
      :fields="[
        { key: 'status', label: '运行状态', type: 'select', options: options.equipmentStatus },
        { key: 'lineName', label: '产线', type: 'select', options: options.line },
        { key: 'riskLevel', label: '风险等级', type: 'select', options: options.riskLevel },
        { key: 'keyword', label: '关键词', type: 'input', placeholder: '设备名称 / 编号' }
      ]"
      @search="search"
      @reset="reset"
    />

    <div class="panel">
      <el-table
        v-loading="loading"
        :data="rows"
        style="width: 100%"
        empty-text="没有符合条件的设备"
        row-key="id"
      >
        <el-table-column prop="name" label="设备名称" min-width="150" />
        <el-table-column label="设备类型" width="120">
          <template #default="{ row }">{{ row.type || '—' }}</template>
        </el-table-column>
        <el-table-column label="所属产线" width="110">
          <template #default="{ row }">{{ row.lineName || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :text="row.status" :map="EQUIPMENT_STATUS_CLASS" />
          </template>
        </el-table-column>
        <el-table-column label="健康度" width="90">
          <template #default="{ row }">
            <span :style="{ color: healthColor(row.health), fontWeight: 500 }">{{ row.health }}</span>
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="100">
          <template #default="{ row }">
            <StatusTag :text="row.riskLevel" :map="RISK_CLASS" />
          </template>
        </el-table-column>
        <el-table-column label="最近异常" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lastException || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <TableFooter v-model:page="page" :total="total" :page-size="pageSize" />
    </div>

    <EquipmentDetailDrawer ref="drawerRef" @create="createWorkOrder" />

    <EquipmentCreateDialog v-model="createDeviceVisible" @success="onDeviceCreated" />

    <DetailDrawer v-model="createVisible" title="创建维修工单" subtitle="为设备提交维修申请，提交后进入工单池。">
      <el-form :model="form" label-width="88px">
        <el-form-item label="设备">
          <el-input v-model="form.equipment" disabled />
        </el-form-item>
        <el-form-item label="故障描述">
          <el-input v-model="form.desc" type="textarea" :rows="3" placeholder="请描述现象、发生时间与影响" />
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-radio-group v-model="form.urgency">
            <el-radio value="高">高</el-radio>
            <el-radio value="中">中</el-radio>
            <el-radio value="低">低</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理人">
          <el-select v-model="form.assignee" style="width: 100%">
            <el-option v-for="p in people" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitWorkOrder">提交工单</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatCard from '../../components/StatCard.vue'
import StatusTag from '../../components/StatusTag.vue'
import FilterPanel from '../../components/FilterPanel.vue'
import TableFooter from '../../components/TableFooter.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import EquipmentDetailDrawer from '../dashboard/EquipmentDetailDrawer.vue'
import EquipmentCreateDialog from './EquipmentCreateDialog.vue'
import { options } from '../../mock'
import { EQUIPMENT_STATUS_CLASS, RISK_CLASS, healthColor } from '../../utils/dict'
import {
  fetchEquipmentList,
  fetchEquipmentSummary,
  type EquipmentQuery,
  type EquipmentSummary
} from '../../api/equipment'
import type { Equipment } from '../../types'

const pageSize = 10

/**
 * 两组状态，别合并成一个：
 * - filters  ：用户正在编辑的筛选值，改了下拉框/输入框就变，但**不触发请求**
 * - applied  ：已经点过「查询」的条件，是真正发给后端的参数
 * 合并成一组的话，用户选一个下拉框就会立刻发请求，
 * 多选几个条件时连续打 3-4 次接口，还会出现后发先至的乱序结果。
 */
const filters = reactive<Record<string, string>>({
  status: '全部',
  lineName: '全部',
  riskLevel: '全部',
  keyword: ''
})
const applied = ref<EquipmentQuery>({})

const rows = ref<Equipment[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const exporting = ref(false)

const summary = reactive({
  total: 0,
  online: 0,
  offline: 0,
  abnormal: 0
})
const summaryLoading = ref(false)

const drawerRef = ref<InstanceType<typeof EquipmentDetailDrawer> | null>(null)
const createVisible = ref(false)
const createDeviceVisible = ref(false)
const people = ['设备主管 张工', '维修班 李工', '维修班 王工', '电气班 赵工']

const form = reactive({
  equipment: '',
  desc: '',
  urgency: '高',
  assignee: people[1]
})

/** 请求序号：只认最后一次的返回，避免快速切页/连续查询时旧结果覆盖新结果 */
let reqSeq = 0

/** 把「全部」/空串归一成 undefined，得到真正要发给后端的参数 */
function toQuery(f: Record<string, string>): EquipmentQuery {
  const q: EquipmentQuery = {}
  const status = (f.status ?? '').trim()
  const lineName = (f.lineName ?? '').trim()
  const riskLevel = (f.riskLevel ?? '').trim()
  const keyword = (f.keyword ?? '').trim()
  if (status && status !== '全部') q.status = status
  if (lineName && lineName !== '全部') q.lineName = lineName
  if (riskLevel && riskLevel !== '全部') q.riskLevel = riskLevel
  if (keyword) q.keyword = keyword
  return q
}

/** 加载列表。条件来自 applied，不读 filters */
async function load() {
  const seq = ++reqSeq
  loading.value = true
  try {
    const res = await fetchEquipmentList({ ...applied.value, page: page.value, size: pageSize })
    if (seq !== reqSeq) return // 已有更新的请求在路上，丢弃这次结果
    rows.value = res.list ?? []
    total.value = res.total ?? 0
  } catch (e) {
    if (seq !== reqSeq) return
    rows.value = []
    total.value = 0
    ElMessage.error(e instanceof Error ? e.message : '设备列表加载失败')
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

/** 加载顶部 KPI */
async function loadSummary() {
  summaryLoading.value = true
  try {
    const s: EquipmentSummary = await fetchEquipmentSummary()
    summary.total = s.total ?? 0
    summary.online = s.online ?? 0
    summary.offline = s.offline ?? 0
    summary.abnormal = (s.warning ?? 0) + (s.fault ?? 0)
  } catch {
    // KPI 失败不打断主流程：列表能看就行，错误不弹第二次
  } finally {
    summaryLoading.value = false
  }
}

/** 点「查询」：把当前编辑中的条件拍成 applied，回到第 1 页后请求 */
function search() {
  applied.value = toQuery(filters)
  page.value = 1
  load()
}

/** 点「重置」：清空关键词、所有下拉恢复「全部」，并重新加载 */
function reset() {
  filters.status = '全部'
  filters.lineName = '全部'
  filters.riskLevel = '全部'
  filters.keyword = ''
  applied.value = {}
  page.value = 1
  load()
  loadSummary()
}

/** 翻页时只换页码，条件沿用 applied */
watch(page, () => load())

onMounted(() => {
  load()
  loadSummary()
})

function openDetail(row: Equipment) {
  drawerRef.value?.open(row)
}

function createWorkOrder(row: Equipment) {
  form.equipment = `${row.name}（${row.code}）`
  form.desc = row.lastException ? `现场发现：${row.lastException}，请安排排查。` : ''
  createVisible.value = true
}

function submitWorkOrder() {
  createVisible.value = false
  ElMessage.success('维修工单已提交，已通知处理人')
}

function create() {
  createDeviceVisible.value = true
}

/**
 * 新增成功后的刷新策略。
 * <p>
 * 不回第 1 页、不强行清空筛选条件：用户可能正带着「A产线 + 预警」的条件在翻第 3 页，
 * 新增完把他弹回第 1 页且条件全丢，等于打断他的操作。只刷新当前视图 + 顶部 KPI，
 * 如果新设备恰好不符合当前筛选，它自然不出现，用户也可以直接搜编号找到它。
 */
function onDeviceCreated() {
  load()
  loadSummary()
}

function exportList() {
  exporting.value = true
  window.setTimeout(() => {
    exporting.value = false
    ElMessage.success('设备清单已导出')
  }, 400)
}
</script>

<style scoped>
.filter-top {
  margin-top: 16px;
}
</style>