<template>
  <div v-loading="loading">
    <PageHeader title="生产总览" desc="按产线与班次跟踪计划达成、产量与 OEE。">
      <el-button @click="exportData">导出</el-button>
      <AiButton label="问 Copilot" type="primary" @click="router.push('/copilot/chat')" />
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="6">
        <StatCard
          label="计划产量"
          :value="kpi.planQty.toLocaleString()"
          :sub="kpi.planNote || '件 · 今日'"
          accent="blue"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="实际产量"
          :value="kpi.actualQty.toLocaleString()"
          :sub="`达成率 ${fmtPct(kpi.achieveRate)}%`"
          accent="orange"
          value-color="var(--c-orange)"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="全厂 OEE"
          :value="fmtPct(kpi.oee) + '%'"
          :sub="kpi.oeeNote"
          accent="red"
          value-color="var(--c-red)"
        />
      </el-col>
      <el-col :span="6">
        <StatCard label="延期计划" :value="kpi.delayedLines" sub="需重点跟进" accent="cyan" />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap">
      <el-col v-for="line in lines" :key="line.name" :span="8">
        <div class="line-card">
          <div class="line-card__head">
            <span class="line-card__name">{{ line.name }}</span>
            <StatusTag :text="line.status" :map="PLAN_STATUS_CLASS" />
            <span class="line-card__product">{{ line.product ?? '—' }}</span>
          </div>

          <div class="line-card__row">
            <span class="line-card__label">产量</span>
            <span class="line-card__value">{{ line.actual }} / {{ line.target }}</span>
            <span class="line-card__note">{{ line.achieveNote }}</span>
          </div>

          <el-progress
            :percentage="Math.min(line.planProgress, 100)"
            :stroke-width="8"
            :color="line.planProgress >= 100 ? '#00b42a' : line.planProgress >= 90 ? '#165dff' : '#ff7d00'"
          >
            <span class="line-card__pct">{{ line.planProgress }}%</span>
          </el-progress>

          <div class="line-card__grid">
            <div class="cell">
              <div class="cell__label">OEE</div>
              <div class="cell__value">{{ fmtPct(line.oee) }}%</div>
            </div>
            <div class="cell">
              <div class="cell__label">良品率</div>
              <div class="cell__value">{{ fmtPct(line.quality) }}%</div>
            </div>
            <div class="cell">
              <div class="cell__label">当前批次</div>
              <div class="cell__value">{{ line.batchProgress == null ? '—' : line.batchProgress + '%' }}</div>
            </div>
            <div class="cell">
              <div class="cell__label">换型</div>
              <div class="cell__value">{{ line.changeover == null ? '—' : line.changeover + ' 分' }}</div>
            </div>
          </div>

          <p class="line-card__ai">{{ line.aiHint }}</p>
        </div>
      </el-col>
      <el-col v-if="!loading && lines.length === 0" :span="24">
        <el-empty description="暂无产线数据" />
      </el-col>
    </el-row>

    <div class="panel row-gap">
      <div class="panel__header">
        <span class="panel__title">当日生产计划</span>
        <AiButton size="small" text label="查看计划达成分析" @click="router.push('/production/plan')" />
      </div>
      <el-table :data="plans" v-loading="planLoading" style="width: 100%">
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
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatCard from '../../components/StatCard.vue'
import AiButton from '../../components/AiButton.vue'
import StatusTag from '../../components/StatusTag.vue'
import { PLAN_STATUS_CLASS } from '../../utils/dict'
import {
  fetchPlans,
  fetchProductionOverview,
  type PlanRow,
  type ProductionKpi,
  type ProductionLineCard,
} from '../../api/production'

const router = useRouter()

const loading = ref(false)
const planLoading = ref(false)
const lines = ref<ProductionLineCard[]>([])
const plans = ref<PlanRow[]>([])

const kpi = reactive<ProductionKpi>({
  planQty: 0,
  planNote: '',
  actualQty: 0,
  actualNote: '',
  achieveRate: null,
  achieveNote: '',
  oee: null,
  oeeNote: '',
  lineCount: 0,
  onTrackLines: 0,
  delayedLines: 0,
  statDate: '',
})

/** 百分比统一两位小数，null 显示 — */
function fmtPct(v: number | null): string {
  return v == null ? '—' : v.toFixed(2).replace(/\.00$/, '')
}

function achieveColor(v: number | null): string {
  if (v == null) return '#86909c'
  return v >= 100 ? '#00b42a' : v >= 90 ? '#ff7d00' : '#f53f3f'
}

async function load() {
  loading.value = true
  try {
    const data = await fetchProductionOverview()
    Object.assign(kpi, data.kpi)
    lines.value = data.lines
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '生产总览加载失败')
  } finally {
    loading.value = false
  }
}

/** 当日计划：只取今天，最多 10 条 */
async function loadPlans() {
  planLoading.value = true
  try {
    const today = kpi.statDate || new Date().toISOString().slice(0, 10)
    const res = await fetchPlans({ startDate: today, endDate: today, page: 1, size: 10 })
    plans.value = res.list
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '生产计划加载失败')
  } finally {
    planLoading.value = false
  }
}

function exportData() {
  ElMessage.success('生产总览已导出')
}

onMounted(async () => {
  await load()
  await loadPlans()
})
</script>

<style scoped>
.row-gap {
  margin-top: 16px;
}

.line-card {
  background: #fff;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 14px 16px;
  height: 100%;
}
.line-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.line-card__name {
  font-size: 15px;
  font-weight: 600;
}
.line-card__product {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-3);
}
.line-card__row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 6px;
}
.line-card__label {
  font-size: 12px;
  color: var(--text-3);
}
.line-card__value {
  font-size: 18px;
  font-weight: 600;
}
.line-card__note {
  font-size: 12px;
  color: var(--text-3);
}
.line-card__pct {
  font-size: 12px;
  color: var(--text-2);
}
.line-card__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin: 14px 0 10px;
}
.cell {
  background: #fafbfc;
  border-radius: 4px;
  padding: 6px 8px;
  text-align: center;
}
.cell__label {
  font-size: 11px;
  color: var(--text-3);
}
.cell__value {
  font-size: 14px;
  font-weight: 600;
}
.line-card__ai {
  margin: 0;
  font-size: 12px;
  line-height: 19px;
  color: var(--text-2);
  background: var(--brand-light);
  border-radius: 4px;
  padding: 8px 10px;
}
</style>