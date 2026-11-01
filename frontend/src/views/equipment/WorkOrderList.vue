<template>
  <div class="page">
    <PageHeader title="维修工单" desc="设备故障处置全过程管理 · 工单来自 AI 异常诊断、人工报修与设备巡检">
      <AiButton label="AI 智能生成工单" :loading="aiLoading" @click="aiDraft" />
      <el-button type="primary" @click="openCreate()">新建工单</el-button>
    </PageHeader>

    <!-- KPI：全部来自 GET /api/work-orders/summary -->
    <div class="kpi-grid">
      <StatCard label="今日工单" :value="summary.today" sub="今日新增" accent="blue" />
      <StatCard label="待处理" :value="summary.pending" sub="待派工" accent="orange" value-color="var(--warn)" />
      <StatCard label="维修中" :value="summary.repairing" sub="处理中" accent="cyan" />
      <StatCard label="已完成" :value="summary.completed" sub="累计完成" accent="green" value-color="var(--ok)" />
    </div>

    <FilterPanel
      v-model:model="filters"
      :fields="filterFields"
      @search="search"
      @reset="reset"
    />

    <div class="card table-card">
      <el-table
        v-loading="loading"
        :data="rows"
        class="wo-table"
        table-layout="fixed"
        empty-text="暂无工单"
        @row-dblclick="(row: WorkOrderRow) => openDetail(row)"
      >
        <el-table-column prop="woNo" label="工单编号" width="170">
          <template #default="{ row }">
            <span class="wo-no">{{ row.woNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="设备名称" min-width="150">
          <template #default="{ row }">
            <div class="eq-name">{{ row.equipmentName ?? '—' }}</div>
            <div v-if="row.equipmentCode" class="eq-code">{{ row.equipmentCode }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="faultType" label="故障类型" width="110" />
        <el-table-column prop="description" label="故障描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="优先级" width="90" align="center">
          <template #default="{ row }">
            <span class="tag" :class="cls(PRIORITY_CLASS, row.priority)">{{ row.priority }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="tag" :class="cls(WORK_ORDER_STATUS_CLASS, row.status)">{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="负责人" width="100">
          <template #default="{ row }">{{ row.assigneeName ?? '未指派' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" :formatter="(r: WorkOrderRow) => r.createTime" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status !== '已完成'"
              link
              type="primary"
              @click="openDetail(row)"
            >
              {{ row.status === '待处理' ? '处理' : '推进' }}
            </el-button>
            <el-button v-else link disabled>已完成</el-button>
          </template>
        </el-table-column>
      </el-table>

      <TableFooter
        :total="total"
        :page="page"
        :page-size="pageSize"
        @update:page="(p: number) => goto(p)"
      />
    </div>

    <!-- ================= 详情抽屉 ================= -->
    <DetailDrawer v-model="detailVisible" title="工单详情" width="640px">
      <div v-if="detail" class="wo-detail">
        <!-- 工单信息 -->
        <section class="sec">
          <h4 class="sec__title">工单信息</h4>
          <div class="kv-2">
            <div class="kv"><span>工单编号</span><b>{{ detail.workOrder.woNo }}</b></div>
            <div class="kv"><span>工单状态</span>
              <span class="tag" :class="cls(WORK_ORDER_STATUS_CLASS, detail.workOrder.status)">{{ detail.workOrder.status }}</span>
            </div>
            <div class="kv"><span>故障类型</span><b>{{ detail.workOrder.faultType }}</b></div>
            <div class="kv"><span>优先级</span>
              <span class="tag" :class="cls(PRIORITY_CLASS, detail.workOrder.priority)">{{ detail.workOrder.priority }}</span>
            </div>
            <div class="kv"><span>负责人</span><b>{{ detail.workOrder.assigneeName ?? '未指派' }}</b></div>
            <div class="kv"><span>来源</span><b>{{ detail.workOrder.source }}</b></div>
            <div class="kv"><span>创建时间</span><b>{{ detail.workOrder.createTime }}</b></div>
            <div class="kv"><span>更新时间</span><b>{{ detail.workOrder.updateTime }}</b></div>
          </div>
          <div class="kv-desc">
            <span>故障描述</span>
            <p>{{ detail.workOrder.description }}</p>
          </div>
        </section>

        <!-- 设备信息 -->
        <section class="sec">
          <h4 class="sec__title">设备信息</h4>
          <template v-if="detail.equipment">
            <div class="kv-2">
              <div class="kv"><span>设备名称</span><b>{{ detail.equipment.name }}</b></div>
              <div class="kv"><span>设备编号</span><b>{{ detail.equipment.code }}</b></div>
              <div class="kv"><span>所属产线</span><b>{{ detail.equipment.lineName ?? '—' }}</b></div>
              <div class="kv"><span>设备状态</span>
                <span class="tag" :class="cls(EQUIPMENT_STATUS_CLASS, detail.equipment.status)">{{ detail.equipment.status }}</span>
              </div>
              <div class="kv"><span>健康评分</span><b :style="{ color: healthColor(detail.equipment.health ?? 0) }">{{ detail.equipment.health ?? '—' }}</b></div>
              <div class="kv"><span>风险等级</span>
                <span class="tag" :class="cls(RISK_CLASS, detail.equipment.riskLevel ?? '')">{{ detail.equipment.riskLevel ?? '—' }}</span>
              </div>
            </div>
            <div class="metric-row">
              <div class="metric"><span>温度</span><b>{{ fmt(detail.equipment.temperature) }}<i>℃</i></b></div>
              <div class="metric"><span>振动</span><b>{{ fmt(detail.equipment.vibration) }}<i>mm/s</i></b></div>
              <div class="metric"><span>电流</span><b>{{ fmt(detail.equipment.current) }}<i>A</i></b></div>
            </div>
            <div class="predict-row">
              <div class="predict">
                <span>24h 故障概率</span>
                <b :style="{ color: healthColor(100 - (detail.equipment.h24 ?? 0)) }">{{ pct(detail.equipment.h24) }}</b>
              </div>
              <div class="predict">
                <span>7天故障概率</span>
                <b :style="{ color: healthColor(100 - (detail.equipment.d7 ?? 0)) }">{{ pct(detail.equipment.d7) }}</b>
              </div>
              <div class="predict">
                <span>30天故障概率</span>
                <b :style="{ color: healthColor(100 - (detail.equipment.d30 ?? 0)) }">{{ pct(detail.equipment.d30) }}</b>
              </div>
            </div>
            <div v-if="detail.equipment.diagnosis" class="ai-box">
              <div class="ai-box__head"><SparkIcon :size="13" /> AI 诊断</div>
              <p>{{ detail.equipment.diagnosis }}</p>
            </div>
          </template>
          <div v-else class="empty-hint">关联设备已被删除，仅保留工单记录</div>
        </section>

        <!-- 处理 -->
        <section v-if="detail.workOrder.status !== '已完成'" class="sec">
          <h4 class="sec__title">工单处理</h4>
          <div class="form-2">
            <div class="fld">
              <label>推进状态</label>
              <el-select v-model="formStatus" placeholder="选择目标状态" style="width: 100%">
                <el-option v-for="s in nextStatuses" :key="s" :label="s" :value="s" />
              </el-select>
            </div>
            <div class="fld">
              <label>负责人</label>
              <el-select v-model="formAssignee" filterable placeholder="选择负责人" style="width: 100%">
                <el-option
                  v-for="u in woOptions.users"
                  :key="u.id"
                  :label="u.label"
                  :value="u.id"
                />
              </el-select>
            </div>
          </div>
          <div class="fld">
            <label>处理记录</label>
            <el-input v-model="formRemark" type="textarea" :rows="3" placeholder="填写维修过程、更换部件、处理结论等" />
          </div>
        </section>
        <section v-else class="sec">
          <div class="done-box">该工单已完成流转，不可再修改状态。</div>
        </section>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detail && detail.workOrder.status !== '已完成'"
          type="primary"
          :loading="submitting"
          @click="submitHandle"
        >
          提交处理
        </el-button>
      </template>
    </DetailDrawer>

    <!-- ================= 新建工单抽屉 ================= -->
    <DetailDrawer v-model="createVisible" title="新建工单" width="600px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="92px">
        <el-form-item label="设备" prop="equipmentId">
          <el-select v-model="createForm.equipmentId" filterable placeholder="选择设备" style="width: 100%">
            <el-option
              v-for="e in woOptions.equipments"
              :key="e.id"
              :label="e.label"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="故障类型" prop="faultType">
          <el-select v-model="createForm.faultType" placeholder="选择故障类型" style="width: 100%">
            <el-option v-for="t in woOptions.faultType" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="紧急程度" prop="priority">
          <el-radio-group v-model="createForm.priority">
            <el-radio-button v-for="p in woOptions.priority" :key="p" :value="p">{{ p }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="故障描述" prop="description">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="描述故障现象、发生时间、影响范围"
          />
        </el-form-item>
        <el-form-item label="负责人" prop="assigneeId">
          <el-select v-model="createForm.assigneeId" filterable placeholder="选择负责人" style="width: 100%">
            <el-option v-for="u in woOptions.users" :key="u.id" :label="u.label" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">提交工单</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import AiButton from '../../components/AiButton.vue'
import SparkIcon from '../../components/SparkIcon.vue'
import StatCard from '../../components/StatCard.vue'
import FilterPanel from '../../components/FilterPanel.vue'
import TableFooter from '../../components/TableFooter.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import type { FilterField } from '../../components/FilterPanel.vue'
import {
  changeWorkOrderStatus,
  createWorkOrder,
  fetchWorkOrderDetail,
  fetchWorkOrderList,
  fetchWorkOrderOptions,
  fetchWorkOrderSummary
} from '../../api/workOrder'
import type {
  WorkOrderCreatePayload,
  WorkOrderDetail,
  WorkOrderOptions,
  WorkOrderRow,
  WorkOrderSummary
} from '../../api/workOrder'
import {
  EQUIPMENT_STATUS_CLASS,
  PRIORITY_CLASS,
  RISK_CLASS,
  WORK_ORDER_STATUS_CLASS,
  cls,
  healthColor
} from '../../utils/dict'
import type { Priority, WorkOrderStatus } from '../../types'

/* ============================ 列表 ============================ */

const loading = ref(false)
const rows = ref<WorkOrderRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const filters = reactive<Record<string, string>>({
  keyword: '',
  status: '全部',
  priority: '全部'
})

const filterFields: FilterField[] = [
  { key: 'keyword', label: '关键词', type: 'input', placeholder: '工单编号 / 设备名称 / 设备编号' },
  { key: 'status', label: '工单状态', type: 'select', options: ['全部', '待处理', '维修中', '待验证', '已完成'] },
  { key: 'priority', label: '优先级', type: 'select', options: ['全部', '紧急', '高', '中', '低'] }
]

const summary = ref<WorkOrderSummary>({
  today: 0,
  pending: 0,
  repairing: 0,
  verifying: 0,
  completed: 0,
  total: 0
})

/** 拉列表。所有查询条件都真传给后端，分页也由后端做 */
async function loadList() {
  loading.value = true
  try {
    const res = await fetchWorkOrderList({
      keyword: filters.keyword,
      status: filters.status,
      priority: filters.priority,
      page: page.value,
      size: pageSize.value
    })
    rows.value = res.list
    total.value = res.total
    // 删掉最后一条后当前页可能越界，回退一页重查
    if (rows.value.length === 0 && page.value > 1) {
      page.value = Math.max(1, res.pages)
      await loadList()
    }
  } catch (e) {
    rows.value = []
    total.value = 0
    ElMessage.error((e as Error).message || '工单列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  try {
    summary.value = await fetchWorkOrderSummary()
  } catch {
    // KPI 失败不打断主流程，列表自己会报错
  }
}

function search() {
  page.value = 1
  loadList()
}

function reset() {
  filters.keyword = ''
  filters.status = '全部'
  filters.priority = '全部'
  page.value = 1
  loadList()
}

function goto(p: number) {
  page.value = p
  loadList()
}

/* ============================ 选项 ============================ */

const woOptions = ref<WorkOrderOptions>({
  status: [],
  priority: ['紧急', '高', '中', '低'],
  faultType: [],
  equipments: [],
  users: []
})

async function loadOptions() {
  try {
    woOptions.value = await fetchWorkOrderOptions()
  } catch (e) {
    ElMessage.error((e as Error).message || '下拉选项加载失败')
  }
}

/* ============================ 详情 ============================ */

const detailVisible = ref(false)
const detail = ref<WorkOrderDetail | null>(null)
const formStatus = ref<WorkOrderStatus | ''>('')
const formAssignee = ref<number | ''>('')
const formRemark = ref('')
const submitting = ref(false)

/** 后端合法流转：待处理→维修中→待验证→已完成，维修中可退回待处理 */
const NEXT_MAP: Record<string, WorkOrderStatus[]> = {
  待处理: ['维修中'],
  维修中: ['待验证', '待处理'],
  待验证: ['已完成', '维修中'],
  已完成: []
}

const nextStatuses = computed<WorkOrderStatus[]>(() =>
  detail.value ? NEXT_MAP[detail.value.workOrder.status] ?? [] : []
)

async function openDetail(row: WorkOrderRow) {
  detailVisible.value = true
  detail.value = null
  try {
    const d = await fetchWorkOrderDetail(row.id)
    detail.value = d
    formStatus.value = NEXT_MAP[d.workOrder.status]?.[0] ?? ''
    formAssignee.value = d.workOrder.assigneeId ?? ''
    formRemark.value = ''
  } catch (e) {
    detailVisible.value = false
    ElMessage.error((e as Error).message || '工单详情加载失败')
  }
}

async function submitHandle() {
  if (!detail.value) return
  if (!formStatus.value) {
    ElMessage.warning('请选择要推进的状态')
    return
  }
  submitting.value = true
  try {
    await changeWorkOrderStatus(detail.value.workOrder.id, formStatus.value, formRemark.value)
    ElMessage.success(`工单已流转到「${formStatus.value}」`)
    detailVisible.value = false
    await Promise.all([loadList(), loadSummary()])
  } catch (e) {
    ElMessage.error((e as Error).message || '状态推进失败')
  } finally {
    submitting.value = false
  }
}

/* ============================ 新建 ============================ */

const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()

const createForm = reactive<{
  equipmentId: number | ''
  faultType: string
  priority: Priority
  description: string
  assigneeId: number | ''
}>({
  equipmentId: '',
  faultType: '',
  priority: '中',
  description: '',
  assigneeId: ''
})

const createRules: FormRules = {
  equipmentId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  faultType: [{ required: true, message: '请选择故障类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择紧急程度', trigger: 'change' }],
  description: [
    { required: true, message: '请填写故障描述', trigger: 'blur' },
    { min: 5, max: 500, message: '故障描述需 5-500 字', trigger: 'blur' }
  ],
  assigneeId: [{ required: true, message: '请选择负责人', trigger: 'change' }]
}

function openCreate() {
  createForm.equipmentId = ''
  createForm.faultType = ''
  createForm.priority = '中'
  createForm.description = ''
  createForm.assigneeId = ''
  createFormRef.value?.clearValidate()
  createVisible.value = true
}

async function submitCreate() {
  const ok = await createFormRef.value?.validate().catch(() => false)
  if (!ok) return

  const payload: WorkOrderCreatePayload = {
    equipmentId: Number(createForm.equipmentId),
    faultType: createForm.faultType,
    priority: createForm.priority,
    description: createForm.description.trim(),
    assigneeId: Number(createForm.assigneeId)
  }
  creating.value = true
  try {
    const created = await createWorkOrder(payload)
    ElMessage.success(`工单 ${created.woNo} 已创建`)
    createVisible.value = false
    // 新工单是「待处理」，按当前筛选条件可能不在第一页，所以回第一页重查
    page.value = 1
    await Promise.all([loadList(), loadSummary()])
  } catch (e) {
    ElMessage.error((e as Error).message || '工单创建失败')
  } finally {
    creating.value = false
  }
}

/* ============================ AI 生成草稿 ============================ */

const aiLoading = ref(false)

/**
 * AI 智能生成工单。
 * <p>
 * 这一步只做「预填」：从设备列表里挑出风险最高的那台，把故障类型、优先级、
 * 描述按已有数据拼好填进新建抽屉，最终仍由人确认后走 createWorkOrder 落库。
 * 不从 AI 端直接建单 —— 无人确认的自动建单会污染真实工单数据。
 */
async function aiDraft() {
  if (woOptions.value.equipments.length === 0) {
    await loadOptions()
  }
  const risky = woOptions.value.equipments.find((e) => e.status === '故障')
    ?? woOptions.value.equipments.find((e) => e.status === '预警')
  if (!risky) {
    ElMessage.info('当前没有故障或预警设备，无需 AI 生成工单')
    return
  }

  aiLoading.value = true
  openCreate()
  createForm.equipmentId = risky.id
  createForm.faultType = woOptions.value.faultType[0] ?? '机械故障'
  createForm.priority = risky.status === '故障' ? '紧急' : '高'
  createForm.description = `【AI 生成】${risky.label} 当前状态为「${risky.status}」，系统检测到运行参数异常并持续劣化，建议尽快安排检修确认故障根因。`
  if (woOptions.value.users.length > 0) {
    createForm.assigneeId = woOptions.value.users[0].id
  }
  aiLoading.value = false
  ElMessage.success('AI 已根据设备状态生成工单草稿，请确认后提交')
}

/* ============================ 工具 ============================ */

function fmt(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : String(v)
}

function pct(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : `${v}%`
}

onMounted(async () => {
  await Promise.all([loadOptions(), loadSummary(), loadList()])
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.table-card {
  padding: 0;
  overflow: hidden;
}

.wo-table {
  width: 100%;
}

.wo-no {
  font-family: var(--font-mono, ui-monospace, monospace);
  font-size: 12.5px;
  color: var(--text-1);
}

.eq-name {
  font-size: 13px;
  color: var(--text-1);
}

.eq-code {
  font-size: 11.5px;
  color: var(--text-3);
}

.tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 18px;
  border: 1px solid transparent;
}

/* ---------- 详情抽屉 ---------- */

.wo-detail {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.sec__title {
  margin: 0 0 10px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
  padding-left: 8px;
  border-left: 3px solid var(--brand);
  line-height: 1.2;
}

.kv-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}

.kv {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.kv > span:first-child {
  color: var(--text-3);
  flex: 0 0 64px;
}

.kv > b {
  color: var(--text-1);
  font-weight: 500;
  word-break: break-all;
}

.kv-desc {
  margin-top: 10px;
  font-size: 13px;
}

.kv-desc > span {
  color: var(--text-3);
}

.kv-desc > p {
  margin: 4px 0 0;
  color: var(--text-1);
  line-height: 1.65;
  background: var(--bg-2);
  border-radius: 6px;
  padding: 8px 10px;
}

.metric-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 12px;
}

.metric {
  background: var(--bg-2);
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric span {
  font-size: 12px;
  color: var(--text-3);
}

.metric b {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
}

.metric b i {
  font-size: 11px;
  font-style: normal;
  color: var(--text-3);
  margin-left: 2px;
}

.predict-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 10px;
}

.predict {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.predict span {
  font-size: 12px;
  color: var(--text-3);
}

.predict b {
  font-size: 17px;
  font-weight: 600;
}

.ai-box {
  margin-top: 12px;
  border: 1px solid rgba(59, 130, 246, 0.28);
  background: rgba(59, 130, 246, 0.06);
  border-radius: 8px;
  padding: 10px 12px;
}

.ai-box__head {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--brand);
  margin-bottom: 5px;
}

.ai-box > p {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--text-2);
}

.form-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.fld {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.fld > label {
  font-size: 12.5px;
  color: var(--text-2);
}

.done-box,
.empty-hint {
  font-size: 12.5px;
  color: var(--text-3);
  background: var(--bg-2);
  border-radius: 8px;
  padding: 12px;
  text-align: center;
}
</style>