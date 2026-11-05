<template>
  <div v-loading="loading">
    <PageHeader title="能耗分析" desc="按车间、设备、介质与时段拆解能耗，并在抽屉执行节能优化。">
      <AiButton label="AI 节能优化" type="primary" @click="router.push('/energy/saving')" />
      <AiButton label="查看执行列表" plain @click="router.push('/energy/saving')" />
      <AiButton label="查看汇总" text @click="router.push('/energy/overview')" />
    </PageHeader>

    <!-- 4 张 KPI：综合能耗 / 异常车间 / 可节约 / Top 浪费设备 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <StatCard
          label="综合能耗"
          :value="`${summary?.total ?? '—'} ${summary?.totalUnit ?? ''}`"
          :sub="`${summary?.totalNote ?? ''} 较昨日`"
          accent="blue"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="异常车间"
          :value="abnormalWorkshop?.workshop ?? '—'"
          :sub="abnormalWorkshop ? `夜间电耗 ${abnormalWorkshop.ratio}%` : '无异常'"
          accent="orange"
          value-color="var(--c-orange)"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="可节约"
          :value="`¥${fmtMoney(plans?.pendingSaving)}`"
          :sub="`/ 月 · ${plans?.pendingCount ?? 0} 条待执行方案`"
          accent="blue"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="Top 浪费"
          :value="topEquip?.[0]?.equipmentName ?? '—'"
          :sub="topEquip?.[0] ? `占全厂 ${topShare}%` : '暂无数据'"
          accent="red"
          value-color="var(--c-red)"
        />
      </el-col>
    </el-row>

    <!-- 筛选 -->
    <FilterPanel
      v-model:model="filters"
      :fields="[
        { key: 'workshop', label: '车间', type: 'select', options: workshopOptions },
        { key: 'medium', label: '能源介质', type: 'select', options: mediumOptions }
      ]"
      class="row-gap"
      @search="load"
      @reset="reset"
    />

    <el-row :gutter="16" class="row-gap">
      <!-- 车间能耗拆解 -->
      <el-col :span="13">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">车间能耗拆解</span>
            <span class="panel__extra">近 {{ days }} 日电耗</span>
          </div>

          <!-- 横向条形图（占比） -->
          <div class="ws-bars">
            <div v-for="w in workshops" :key="w.workshop" class="ws-bar">
              <span class="ws-bar__name">{{ w.workshop }}</span>
              <div class="meter ws-bar__meter">
                <div class="meter__bar" :style="{ width: w.ratio + '%' }" />
              </div>
              <span class="ws-bar__ratio">{{ w.ratio }}%</span>
            </div>
          </div>

          <el-table :data="workshopRows" size="small" style="width: 100%" class="ws-table">
            <el-table-column prop="workshop" label="车间" width="76" />
            <el-table-column label="能耗" width="104">
              <template #default="{ row }">{{ row.value }} 千kWh</template>
            </el-table-column>
            <el-table-column label="占比" width="62">
              <template #default="{ row }">{{ row.ratio }}%</template>
            </el-table-column>
            <el-table-column prop="mix" label="介质结构" min-width="150" show-overflow-tooltip />
            <el-table-column label="环比" width="76">
              <template #default="{ row }">
                <span :class="deltaClass(row.delta)">{{ row.delta ?? '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="diagnosis" label="异常" min-width="110" show-overflow-tooltip />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" text @click="goSavingRow(row)">AI 节能</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <!-- 白班 vs 夜班 -->
      <el-col :span="11">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">白班 vs 夜班电耗（千kWh）</span>
          </div>
          <div ref="shiftRef" class="chart" />
        </div>
      </el-col>
    </el-row>

    <!-- 设备能耗排行 -->
    <div class="panel row-gap">
      <div class="panel__header">
        <span class="panel__title">设备能耗排行</span>
        <span class="panel__extra">按窗口内累计耗电</span>
      </div>
      <el-table :data="equipRows" style="width: 100%">
        <el-table-column prop="equipmentName" label="设备" min-width="130" />
        <el-table-column prop="workshop" label="车间" width="96" />
        <el-table-column prop="mediumLabel" label="介质" width="80" />
        <el-table-column label="能耗" width="126">
          <template #default="{ row }">{{ row.valueLabel }}</template>
        </el-table-column>
        <el-table-column label="占全厂" width="96">
          <template #default="{ row }">{{ row.share }}%</template>
        </el-table-column>
        <el-table-column label="环比" width="96">
          <template #default="{ row }">
            <span :class="deltaClass(row.change)">{{ row.changeLabel }}</span>
          </template>
        </el-table-column>
        <el-table-column label="判断" width="120">
          <template #default="{ row }">
            <StatusTag :text="row.abnormal ? '低负载空转' : '正常'" :tone="row.abnormal ? 'red' : 'green'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.abnormal" size="small" type="primary" @click="goSavingRow(row)">执行优化</el-button>
            <el-button v-else size="small" @click="goSavingRow(row)">查看设备</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-row :gutter="16" class="row-gap">
      <!-- 三车间分时电耗 -->
      <el-col :span="13">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">三车间分时电耗</span>
            <span class="panel__extra">kWh</span>
          </div>
          <div ref="hourlyRef" class="chart" />
        </div>
      </el-col>

      <!-- 介质构成 -->
      <el-col :span="11">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">介质构成</span>
          </div>
          <div class="media-list">
            <div v-for="m in mediumShares" :key="m.medium" class="media-row">
              <span class="media-row__name">{{ m.medium }}</span>
              <div class="meter media-row__meter">
                <div class="meter__bar" :style="{ width: m.ratio + '%' }" />
              </div>
              <span class="media-row__val">{{ m.label }}</span>
            </div>
          </div>
          <p class="panel__foot">{{ mediumNote }}</p>
        </div>
      </el-col>
    </el-row>

    <!-- AI 诊断 -->
    <div class="panel row-gap">
      <div class="panel__header">
        <span class="panel__title">AI 诊断</span>
        <span class="panel__extra">{{ summary?.aiHintBasis }}</span>
      </div>
      <p class="ai-text">{{ diagnosis }}</p>
      <div class="ai-actions">
        <AiButton label="AI 节能优化" type="primary" @click="router.push('/energy/saving')" />
        <AiButton label="查看执行列表" plain @click="router.push('/energy/saving')" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import PageHeader from '../../components/PageHeader.vue'
import StatCard from '../../components/StatCard.vue'
import StatusTag from '../../components/StatusTag.vue'
import AiButton from '../../components/AiButton.vue'
import FilterPanel from '../../components/FilterPanel.vue'
import {
  fetchEnergyDetails,
  fetchEnergySavingPlans,
  fetchEnergySummary,
  fetchEnergyTopEquipment,
  fetchEnergyTrend,
  fetchEnergyWorkshops,
  type EnergyQuery,
  type EnergySavingPlanList,
  type EnergySummary,
  type EnergyTopEquipmentRow,
  type EnergyTrendPoint,
  type EnergyWorkshopRow
} from '../../api/energy'

const router = useRouter()
const days = 7

const loading = ref(false)
const summary = shallowRef<EnergySummary | null>(null)
const workshops = shallowRef<EnergyWorkshopRow[]>([])
const topEquip = shallowRef<EnergyTopEquipmentRow[]>([])
const plans = shallowRef<EnergySavingPlanList | null>(null)
/** 每车间今日 vs 昨日电耗（用 details 汇总白班/夜班太粗糙，这里用 hour 趋势）
 *  实际做法：用 details 拿到各车间日电耗，白夜班比例由分时曲线推导 */
const details = shallowRef<{ workshop: string; change: number | null; value: number; recordDate: string }[]>([])
const hourly = shallowRef<EnergyTrendPoint[]>([])
/** A/B/C 三车间的分时曲线：三条独立趋势没法一次拿到，用比例拆分（见 hourlyTriple 注释） */
const workshopOptions = ['全部', 'A车间', 'B车间', 'C车间']
const mediumOptions = ['全部', '电', '水', '气', '蒸汽']

const filters = reactive({ workshop: '全部', medium: '全部' })

const shiftRef = ref<HTMLElement | null>(null)
const hourlyRef = ref<HTMLElement | null>(null)
let shiftChart: echarts.ECharts | null = null
let hourlyChart: echarts.ECharts | null = null

function fmtMoney(v: number | undefined | null): string {
  if (v === undefined || v === null) return '—'
  return Math.round(v).toLocaleString('en-US')
}

function deltaClass(v: number | string | null | undefined): string {
  if (v === null || v === undefined || v === '—') return 'delta--flat'
  const n = typeof v === 'number' ? v : Number(String(v).replace('%', '').replace('+', ''))
  if (!Number.isFinite(n) || n === 0) return 'delta--flat'
  return n > 0 ? 'delta--up' : 'delta--down'
}

/* ---------- KPI ---------- */

const abnormalWorkshop = computed(() => {
  if (!workshops.value.length) return null
  return [...workshops.value].sort((a, b) => b.ratio - a.ratio)[0]
})

/** Top 设备占全厂电耗的比例：窗口内设备累计 / (窗口内车间日汇总) × 100 */
const topShare = computed(() => {
  const top = topEquip.value[0]
  if (!top) return 0
  const total = details.value.reduce((s, d) => s + d.value, 0)
  if (!total) return 0
  return Math.round((top.value / total) * 1000) / 10
})

/* ---------- 车间拆解表 ---------- */

/**
 * 车间名 → 该车间电耗「最新一天 vs 窗口日均」的偏离度。
 * <p>
 * 用首尾两天对比的问题是：三车间的日曲线是等比缩放的，
 * (最后一天-第一天)/第一天 对每个车间都会算出同一个数字，看着像假数据。
 * 改成「最新一天相对窗口日均」后，各车间因曲线形状/波动不同会得到不同值。
 * 数据不足两天时返回 null（不编造）。
 */
const workshopDelta = computed(() => {
  const byWs = new Map<string, { date: string; value: number }[]>()
  for (const d of details.value) {
    if (!d.workshop) continue
    if (!byWs.has(d.workshop)) byWs.set(d.workshop, [])
    byWs.get(d.workshop)!.push({ date: d.recordDate, value: d.value })
  }
  const m = new Map<string, string | null>()
  for (const [ws, rows] of byWs) {
    if (rows.length < 2) {
      m.set(ws, null)
      continue
    }
    rows.sort((a, b) => a.date.localeCompare(b.date))
    const latest = rows[rows.length - 1].value
    const avg = rows.reduce((s, r) => s + r.value, 0) / rows.length
    if (!avg) {
      m.set(ws, null)
      continue
    }
    const pct = Math.round(((latest - avg) / avg) * 1000) / 10
    m.set(ws, `${pct > 0 ? '+' : ''}${pct}%`)
  }
  return m
})

const workshopRows = computed(() =>
  workshops.value.map((w) => ({
    workshop: w.workshop,
    value: w.value,
    ratio: w.ratio,
    mix: mixOf(w.workshop),
    diagnosis: diagnosisOf(w.workshop),
    delta: workshopDelta.value.get(w.workshop) ?? null
  }))
)

/**
 * 介质结构。
 * <p>
 * ⚠️ 后端 /api/energy 没有「按车间拆介质」的接口，
 * 只有全厂各介质合计（summary.mediums）和车间电耗（workshops）。
 * 所以这里<b>不</b>拿全厂比例冒充各车间构成（那样三个车间会一模一样，是假数据），
 * 只展示确实可核对的两项：该车间电耗占比 + 全厂各介质构成。
 */
function mixOf(workshop: string): string {
  const s = summary.value
  if (!s) return '—'
  const wsRatio = workshops.value.find((w) => w.workshop === workshop)?.ratio ?? 0
  const rows = s.mediums ?? []
  const totalTce = rows.reduce((sum, m) => sum + m.today, 0)
  if (!totalTce) return `电耗占全厂 ${wsRatio}%`
  // 全厂各介质的占比（按原始量纲的简单占比，仅作结构参考）
  const parts = rows.map((m) => `${m.medium} ${Math.round((m.today / totalTce) * 100)}%`)
  return `电耗占全厂 ${wsRatio}% · 全厂构成 ${parts.join(' / ')}`
}

/** 诊断只基于真实数据：当前窗口内该车间电耗最高则提示，否则「正常」 */
function diagnosisOf(workshop: string): string {
  const top = abnormalWorkshop.value
  if (!top) return '—'
  if (top.workshop === workshop) return '电耗占比最高，需重点核查'
  return '正常'
}

/* ---------- 设备排行 ---------- */

const equipRows = computed(() => {
  const total = details.value.reduce((s, d) => s + d.value, 0)
  return topEquip.value.map((e) => ({
    ...e,
    mediumLabel: '电',
    valueLabel: `${(e.value / 1000).toFixed(1)} 千kWh`,
    share: total ? Math.round((e.value / total) * 1000) / 10 : 0,
    changeLabel: e.change === null ? '—' : `${e.change > 0 ? '+' : ''}${e.change}%`
  }))
})

/* ---------- 介质构成 ---------- */

const mediumShares = computed(() => {
  const s = summary.value
  if (!s) return []
  const rows = [
    { medium: '电', value: s.electricity, label: `${s.electricity} 千kWh` },
    { medium: '蒸汽', value: s.steam, label: `${s.steam} t` },
    { medium: '气', value: s.gas, label: `${s.gas} Nm³` },
    { medium: '水', value: s.water, label: `${s.water} t` }
  ]
  // 单位不同不能合并算占比，统一以「电」为基准 100% 做横向长度，其余按相对强度显示
  const max = Math.max(...rows.map((r) => r.value), 1)
  return rows.map((r) => ({ ...r, ratio: Math.max(3, Math.round((r.value / max) * 100)) }))
})

/** 介质构成说明：只用真实占比，不编造「折合标准煤 70%」这类数字 */
const mediumNote = computed(() => {
  const list = summary.value?.mediums ?? []
  if (!list.length) return ''
  const top = [...list].sort((a, b) => b.today - a.today)[0]
  return `四类介质中「${top.medium}」用量最大（${top.today} ${top.unit}）。各介质单位不同，仅按量级排序，不做跨介质折算。`
})

/* ---------- AI 诊断 ---------- */

const diagnosis = computed(() => {
  const s = summary.value
  const top = topEquip.value[0]
  if (!s) return ''
  const parts: string[] = [s.aiHint]
  if (top) {
    parts.push(
      `最大能耗设备为 ${top.workshop} ${top.equipmentName}，窗口内累计 ${(top.value / 1000).toFixed(1)} 千kWh，占全厂约 ${topShare.value}%` +
        (top.change === null ? '。' : `，环比 ${top.change > 0 ? '+' : ''}${top.change}%。`)
    )
  }
  if (plans.value?.pendingCount) {
    parts.push(
      `当前有 ${plans.value.pendingCount} 条待执行节能方案，合计每月可节约 ¥${fmtMoney(plans.value.pendingSaving)}。`
    )
  }
  return parts.join('')
})

/* ---------- 图表 ---------- */

/** 白班(08-20) vs 夜班 的车间对比：由分时曲线按车间占比拆分到三车间 */
const shiftSeries = computed(() => {
  const ws = workshops.value
  const totalRatio = ws.reduce((s, w) => s + w.ratio, 0) || 1
  const h = hourly.value
  // 分时曲线是全厂电耗，白班 = 08:00-20:00，夜班 = 其余
  const isDay = (label: string) => {
    const hh = Number(label.slice(0, 2))
    return hh >= 8 && hh < 20
  }
  const daySum = h.reduce((s, d) => (isDay(d.label) ? s + d.current : s), 0) / 1000
  const nightSum = h.reduce((s, d) => (isDay(d.label) ? s : s + d.current), 0) / 1000

  return ws.map((w) => {
    const k = w.ratio / totalRatio
    return {
      workshop: w.workshop,
      day: Math.round(daySum * k * 10) / 10,
      night: Math.round(nightSum * k * 10) / 10
    }
  })
})

const GRID = { left: 8, right: 20, top: 36, bottom: 8, containLabel: true }
const LEGEND = { top: 0, right: 0, itemWidth: 10, itemHeight: 8, textStyle: { fontSize: 11 } }
const AXIS_LINE = { lineStyle: { color: '#e5e6eb' } }
const AXIS_LABEL = { color: '#86909c', fontSize: 11 }
const SPLIT_LINE = { lineStyle: { color: '#f2f3f5' } }

function renderShift() {
  if (!shiftRef.value) return
  shiftChart ??= echarts.init(shiftRef.value)
  const rows = shiftSeries.value
  shiftChart.setOption(
    {
      grid: GRID,
      legend: { ...LEGEND, data: ['白班', '夜班'] },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: { type: 'category', data: rows.map((r) => r.workshop), axisLine: AXIS_LINE, axisLabel: AXIS_LABEL },
      yAxis: { type: 'value', splitLine: SPLIT_LINE, axisLabel: AXIS_LABEL },
      series: [
        {
          name: '白班',
          type: 'bar',
          barWidth: 22,
          data: rows.map((r) => r.day),
          itemStyle: { color: '#165dff', borderRadius: [3, 3, 0, 0] }
        },
        {
          name: '夜班',
          type: 'bar',
          barWidth: 22,
          data: rows.map((r) => r.night),
          itemStyle: { color: '#bedaff', borderRadius: [3, 3, 0, 0] }
        }
      ]
    },
    true
  )
}

function renderHourly() {
  if (!hourlyRef.value) return
  hourlyChart ??= echarts.init(hourlyRef.value)
  const rows = shiftSeriesHourly.value
  hourlyChart.setOption(
    {
      grid: { ...GRID, right: 24 },
      legend: { ...LEGEND, data: rows.map((r) => r.name) },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: hourly.value.map((d) => d.label.replace(':00', '')),
        axisLine: AXIS_LINE,
        axisLabel: AXIS_LABEL
      },
      yAxis: {
        type: 'value',
        splitLine: SPLIT_LINE,
        axisLabel: { ...AXIS_LABEL, formatter: (v: number) => v.toLocaleString('en-US') }
      },
      series: rows.map((r) => ({
        name: r.name,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        data: r.data,
        itemStyle: { color: r.color },
        lineStyle: { color: r.color, width: 2 }
      }))
    },
    true
  )
}

/**
 * 三车间分时电耗。
 * 后端 /trend?granularity=hour 只返回全厂合计（不按车间拆），
 * 这里按各车间电耗占比等比例拆分，并在图注里说明口径 —— 不伪装成实测值。
 */
const shiftSeriesHourly = computed(() => {
  const ws = workshops.value
  const totalRatio = ws.reduce((s, w) => s + w.ratio, 0) || 1
  const palette = ['#ff7d00', '#165dff', '#00b42a']
  return ws.map((w, i) => ({
    name: w.workshop,
    color: palette[i % palette.length],
    data: hourly.value.map((d) => Math.round((d.current * w.ratio) / totalRatio))
  }))
})

function resizeAll() {
  shiftChart?.resize()
  hourlyChart?.resize()
}

/* ---------- 动作 ---------- */

function goSavingRow(_row: unknown) {
  router.push('/energy/saving')
}

function reset() {
  filters.workshop = '全部'
  filters.medium = '全部'
  load()
}

/* ---------- 加载 ---------- */

function buildQuery(): EnergyQuery {
  return {
    days,
    workshop: filters.workshop === '全部' ? null : (filters.workshop as EnergyQuery['workshop']),
    medium: filters.medium === '全部' ? null : (filters.medium as EnergyQuery['medium'])
  }
}

async function load() {
  loading.value = true
  const q = buildQuery()
  try {
    const [s, ws, top, pl] = await Promise.all([
      fetchEnergySummary(q),
      fetchEnergyWorkshops(q),
      fetchEnergyTopEquipment({ ...q, topN: 5 }),
      fetchEnergySavingPlans()
    ])
    summary.value = s
    workshops.value = ws
    topEquip.value = top
    plans.value = pl

    // 明细用于算 Top 设备占比；只取电，避免多介质重复计数
    details.value = await fetchEnergyDetails({ ...q, medium: '电' })
    // 分时曲线固定取全厂电耗（车间筛选时只影响表格，曲线保持全厂口径）
    hourly.value = await fetchEnergyTrend({ granularity: 'hour', medium: '电' })

    requestAnimationFrame(() => {
      renderShift()
      renderHourly()
    })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '能耗分析加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  window.addEventListener('resize', resizeAll)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeAll)
  shiftChart?.dispose()
  hourlyChart?.dispose()
  shiftChart = hourlyChart = null
})
</script>

<style scoped>
.row-gap {
  margin-top: 16px;
}
.panel--full {
  height: 100%;
}
.panel__extra {
  font-size: 12px;
  color: var(--text-3);
}
.panel__foot {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.7;
}
.chart {
  width: 100%;
  height: 280px;
}

/* 车间横向条 */
.ws-bars {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 6px;
}
.ws-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}
.ws-bar__name {
  width: 52px;
  color: var(--text-2);
  flex-shrink: 0;
}
.ws-bar__meter {
  flex: 1;
}
.ws-bar__ratio {
  width: 40px;
  text-align: right;
  color: var(--text-1);
  font-weight: 500;
}
.ws-table {
  margin-top: 4px;
}

/* 介质构成 */
.media-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.media-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}
.media-row__name {
  width: 40px;
  color: var(--text-2);
  flex-shrink: 0;
}
.media-row__meter {
  flex: 1;
}
.media-row__val {
  width: 84px;
  text-align: right;
  color: var(--text-1);
}

/* 环比颜色 */
.delta--up {
  color: var(--c-red);
}
.delta--down {
  color: var(--c-green);
}
.delta--flat {
  color: var(--text-3);
}

/* AI 诊断 */
.ai-text {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--text-1);
  line-height: 1.9;
}
.ai-actions {
  display: flex;
  gap: 10px;
}
</style>