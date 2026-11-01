<template>
  <div>
    <PageHeader title="生产计划" desc="计划下达、达成跟踪与延期原因分析。">
      <el-button type="primary" @click="openCreate">新建计划</el-button>
      <AiButton label="AI 排产建议" type="primary" plain @click="suggest" />
    </PageHeader>

    <FilterPanel
      v-model:model="filters"
      :fields="[
        { key: 'line', label: '产线', type: 'select', options: lineOptions },
        { key: 'status', label: '计划状态', type: 'select', options: statusOptions },
        { key: 'keyword', label: '产品', type: 'input', placeholder: '请输入产品名称' }
      ]"
      @search="onSearch"
      @reset="reset"
    />

    <div class="panel">
      <el-table :data="rows" v-loading="loading" style="width: 100%">
        <el-table-column prop="planDate" label="计划日期" width="120" />
        <el-table-column prop="lineName" label="产线" width="90" />
        <el-table-column prop="product" label="产品" min-width="150" />
        <el-table-column prop="targetQty" label="计划量" width="100" />
        <el-table-column prop="actualQty" label="实际量" width="100">
          <template #default="{ row }">{{ row.actualQty ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="达成率" width="110">
          <template #default="{ row }">
            <span :style="{ color: achieveColor(row.achieve) }">
              {{ row.achieve == null ? '—' : row.achieve + '%' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :text="row.status" :map="PLAN_STATUS_CLASS" />
          </template>
        </el-table-column>
        <el-table-column prop="aiExplanation" label="AI 说明" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.aiExplanation || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <TableFooter v-model:page="page" :total="total" :page-size="size" />
    </div>

    <DetailDrawer v-model="visible" title="计划详情" subtitle="达成情况、延期原因与建议动作。">
      <div v-loading="detailLoading">
        <div class="detail-list">
          <span class="detail-list__label">计划日期</span>
          <span class="detail-list__value">{{ detail?.plan.planDate }}</span>
          <span class="detail-list__label">产线 / 产品</span>
          <span class="detail-list__value">{{ detail?.plan.lineName }} · {{ detail?.plan.product }}</span>
          <span class="detail-list__label">计划 / 实际</span>
          <span class="detail-list__value">
            {{ detail?.plan.targetQty }} / {{ detail?.plan.actualQty ?? '—' }}
          </span>
          <span class="detail-list__label">达成率</span>
          <span class="detail-list__value">
            {{ detail?.plan.achieve == null ? '—' : detail.plan.achieve + '%' }}
          </span>
          <span class="detail-list__label">班次 / 工时</span>
          <span class="detail-list__value">
            {{ detail?.plan.shiftName || '—' }} · {{ detail?.plan.planHours ?? '—' }} 小时
          </span>
          <span class="detail-list__label">状态</span>
          <span class="detail-list__value">
            <StatusTag v-if="detail" :text="detail.plan.status" :map="PLAN_STATUS_CLASS" />
          </span>
          <span class="detail-list__label">AI 说明</span>
          <span class="detail-list__value">{{ detail?.plan.aiExplanation || '—' }}</span>
        </div>

        <p class="section-title">生产批次（{{ detail?.batches.length ?? 0 }}）</p>
        <el-table :data="detail?.batches ?? []" size="small" style="width: 100%">
          <el-table-column prop="batchNo" label="批次" width="80" />
          <el-table-column prop="equipmentName" label="设备" min-width="110" />
          <el-table-column prop="goodQty" label="良品" width="80" />
          <el-table-column prop="defectQty" label="不良" width="70" />
          <el-table-column label="不良率" width="80">
            <template #default="{ row }">{{ row.defectRate == null ? '—' : row.defectRate + '%' }}</template>
          </el-table-column>
          <el-table-column label="时段" min-width="130">
            <template #default="{ row }">
              {{ shortTime(row.startTime) }} ~ {{ shortTime(row.endTime) }}
            </template>
          </el-table-column>
        </el-table>

        <p class="section-title">损失明细（{{ detail?.losses.length ?? 0 }}）</p>
        <el-table :data="detail?.losses ?? []" size="small" style="width: 100%">
          <el-table-column prop="lossType" label="类型" width="90" />
          <el-table-column prop="trigger" label="触发原因" width="110" />
          <el-table-column prop="minutes" label="时长(分)" width="80" />
          <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
          <el-table-column prop="equipment" label="设备" width="110" />
        </el-table>
        <el-empty
          v-if="detail && detail.losses.length === 0"
          description="该计划无损失记录"
          :image-size="60"
        />
      </div>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" @click="gotoOee">查看 OEE 影响</el-button>
      </template>
    </DetailDrawer>

    <DetailDrawer v-model="createVisible" title="新建生产计划" subtitle="计划产量与节拍决定后续 OEE 口径，请按实际排产填写。">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="112px">
        <el-form-item label="计划日期" prop="planDate">
          <el-date-picker
            v-model="form.planDate"
            type="date"
            placeholder="请选择计划日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="产线" prop="lineId">
          <el-select v-model="form.lineId" style="width: 100%" placeholder="请选择产线">
            <el-option v-for="l in lineList" :key="l.id" :label="l.lineName" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input v-model="form.productName" maxlength="100" show-word-limit placeholder="例如：外壳组件 A-220" />
        </el-form-item>
        <el-form-item label="班次" prop="shiftName">
          <el-radio-group v-model="form.shiftName">
            <el-radio-button value="白班">白班</el-radio-button>
            <el-radio-button value="夜班">夜班</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="计划产量" prop="targetQty">
          <el-input-number v-model="form.targetQty" :min="1" :max="1000000" :step="100" style="width: 100%" />
          <span class="form-hint">件 · 达成率分母</span>
        </el-form-item>
        <el-form-item label="计划工时" prop="planHours">
          <el-input-number v-model="form.planHours" :min="1" :max="24" :step="0.5" :precision="1" style="width: 100%" />
          <span class="form-hint">小时 · 开动率分母</span>
        </el-form-item>
        <el-form-item label="标准节拍" prop="standardQtyPerHour">
          <el-input-number v-model="form.standardQtyPerHour" :min="1" :max="1000000" :step="10" style="width: 100%" />
          <span class="form-hint">件/小时 · 性能效率分母</span>
        </el-form-item>

        <div class="capacity-hint">
          按当前填写，理论产能 {{ capacityQty.toLocaleString() }} 件
          <span v-if="capacityQty < form.targetQty" class="capacity-hint__warn">
            ，低于计划产量 {{ (form.targetQty - capacityQty).toLocaleString() }} 件，性能效率将无法达标
          </span>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">创建计划</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import AiButton from '../../components/AiButton.vue'
import StatusTag from '../../components/StatusTag.vue'
import FilterPanel from '../../components/FilterPanel.vue'
import TableFooter from '../../components/TableFooter.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { PLAN_STATUS_CLASS } from '../../utils/dict'
import {
  createPlan,
  fetchPlanDetail,
  fetchPlans,
  fetchProductionOptions,
  type PlanDetail,
  type PlanRow,
  type ShiftName,
} from '../../api/production'

const router = useRouter()

const filters = reactive({ line: '全部', status: '全部', keyword: '' })
const page = ref(1)
const size = ref(10)
const total = ref(0)
const rows = ref<PlanRow[]>([])
const loading = ref(false)

const visible = ref(false)
const detailLoading = ref(false)
const detail = ref<PlanDetail | null>(null)

const createVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{
  planDate: string
  lineId: number | null
  productName: string
  shiftName: ShiftName
  targetQty: number
  planHours: number
  standardQtyPerHour: number
}>({
  planDate: new Date().toISOString().slice(0, 10),
  lineId: null,
  productName: '',
  shiftName: '白班',
  targetQty: 5000,
  planHours: 10,
  standardQtyPerHour: 500,
})

/** 理论产能 = 节拍 × 工时，用于提前提示「节拍不够」。 */
const capacityQty = computed(() => Math.round(form.standardQtyPerHour * form.planHours))

const rules: FormRules = {
  planDate: [{ required: true, message: '请选择计划日期', trigger: 'change' }],
  lineId: [{ required: true, message: '请选择产线', trigger: 'change' }],
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  shiftName: [{ required: true, message: '请选择班次', trigger: 'change' }],
  targetQty: [{ required: true, message: '请输入计划产量', trigger: 'blur' }],
  planHours: [{ required: true, message: '请输入计划工时', trigger: 'blur' }],
  standardQtyPerHour: [{ required: true, message: '请输入标准节拍', trigger: 'blur' }],
}

/* ---------- 选项 ---------- */

const lineList = ref<Array<{ id: number; lineName: string }>>([])
const statusList = ref<string[]>([])

/** FilterPanel 的下拉要带「全部」兜底 */
const lineOptions = computed(() => ['全部', ...lineList.value.map((l) => l.lineName)])
const statusOptions = computed(() => ['全部', ...statusList.value])

async function loadOptions() {
  try {
    const o = await fetchProductionOptions()
    lineList.value = o.lines
    statusList.value = o.status
  } catch {
    // 选项加载失败不阻塞列表，下拉退化成只有「全部」
    lineList.value = []
    statusList.value = []
  }
}

/* ---------- 列表 ---------- */

function achieveColor(v: number | null): string {
  if (v == null) return '#86909c'
  return v >= 100 ? '#00b42a' : v >= 90 ? '#ff7d00' : '#f53f3f'
}

/** 「2026-10-03 08:00:00」→「08:00」 */
function shortTime(v: string | null): string {
  if (!v) return '—'
  const t = v.split(' ')[1]
  return t ? t.slice(0, 5) : v
}

async function load() {
  loading.value = true
  try {
    const res = await fetchPlans({
      lineName: filters.line === '全部' ? undefined : filters.line,
      status: filters.status === '全部' ? undefined : filters.status,
      keyword: filters.keyword || undefined,
      page: page.value,
      size: size.value,
      sortField: 'planDate',
      sortOrder: 'desc',
    })
    rows.value = res.list
    total.value = res.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '生产计划加载失败')
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function reset() {
  Object.assign(filters, { line: '全部', status: '全部', keyword: '' })
  page.value = 1
  load()
}

/* ---------- 详情 ---------- */

async function openDetail(row: PlanRow) {
  visible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await fetchPlanDetail(row.id)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '计划详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function gotoOee() {
  visible.value = false
  router.push('/production/oee')
}

function suggest() {
  ElMessage.success('AI 排产建议已生成：建议 A产线 08:00 开班前完成轴承检修')
}

/* ---------- 新建 ---------- */

function openCreate() {
  // 每次打开重置表单，避免带着上一条的残留值提交
  form.planDate = new Date().toISOString().slice(0, 10)
  form.lineId = null
  form.productName = ''
  form.shiftName = '白班'
  form.targetQty = 5000
  form.planHours = 10
  form.standardQtyPerHour = 500
  formRef.value?.clearValidate()
  createVisible.value = true
}

async function submit() {
  if (!formRef.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  // 兜底：el-input-number 清空时可能是 null，类型上不允许
  if (form.lineId == null) {
    ElMessage.error('请选择产线')
    return
  }

  submitting.value = true
  try {
    const created = await createPlan({
      planDate: form.planDate,
      lineId: form.lineId,
      productName: form.productName.trim(),
      targetQty: form.targetQty,
      planHours: form.planHours,
      standardQtyPerHour: form.standardQtyPerHour,
      shiftName: form.shiftName,
    })
    createVisible.value = false
    ElMessage.success(
      `生产计划已创建：${created.plan.lineName} ${created.plan.planDate} ${created.plan.shiftName} ${created.plan.product}`,
    )
    page.value = 1
    await load()
  } catch (e) {
    // 后端 BusinessException 的 msg（如「A产线 在 2026-11-02 白班 已有生产计划」）直接展示
    ElMessage.error(e instanceof Error ? e.message : '生产计划创建失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadOptions()
  await load()
})

// 翻页时重新拉数据（TableFooter 只发 update:page，没有 change 事件）
watch(page, () => {
  load()
})
</script>

<style scoped>
.section-title {
  margin: 18px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-2);
}
.form-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-3);
}
.capacity-hint {
  margin: 4px 0 0 112px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
  background: #fafbfc;
  border-radius: 4px;
  padding: 8px 10px;
}
.capacity-hint__warn {
  color: var(--c-red);
}
</style>