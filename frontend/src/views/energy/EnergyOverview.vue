<template>
  <div v-loading="loading">
    <PageHeader title="能耗总览" desc="查看电、水、气、蒸汽与综合能耗，并用折线、对比图分析。">
      <AiButton label="能耗分析" type="primary" @click="router.push('/energy/analysis')" />
      <AiButton label="查看执行列表" plain @click="router.push('/energy/saving')" />
    </PageHeader>

    <!-- 5 张 KPI 卡：综合能耗 / 电 / 水 / 气 / 蒸汽 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col v-for="k in kpiCards" :key="k.label" :span="24 / 5">
        <StatCard
          :label="k.label"
          :value="k.value"
          :sub="k.note"
          :accent="k.accent"
          :value-color="k.color"
        />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap">
      <!-- 近 7 日综合能耗 -->
      <el-col :span="13">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">近 7 日综合能耗</span>
            <span class="panel__extra">{{ trendUnit }}</span>
          </div>
          <div ref="trendRef" class="chart" />
        </div>
      </el-col>

      <!-- 今日 vs 昨日（分介质） -->
      <el-col :span="11">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">今日 vs 昨日（分介质）</span>
          </div>
          <div ref="mediumRef" class="chart" />
        </div>
      </el-col>
    </el-row>

    <!-- 今日分时电耗（全宽） -->
    <div class="panel row-gap">
      <div class="panel__header">
        <span class="panel__title">今日分时电耗（kWh）</span>
        <span class="panel__extra">{{ summary?.scope }}</span>
      </div>
      <div ref="hourlyRef" class="chart chart--tall" />
    </div>

    <el-row :gutter="16" class="row-gap">
      <!-- 成本与强度 -->
      <el-col :span="13">
        <div class="panel panel--full panel--pad">
          <div class="panel__header panel__header--plain">
            <span class="panel__title">成本与强度</span>
          </div>
          <div class="metric-boxes">
            <div class="metric-box">
              <span class="metric-box__label">能耗成本</span>
              <span class="metric-box__value">¥{{ fmtMoney(summary?.cost) }}</span>
              <span class="metric-box__note">今日 {{ summary?.costNote }}</span>
            </div>
            <div class="metric-box">
              <span class="metric-box__label">单位产品能耗</span>
              <span class="metric-box__value">{{ summary?.unitConsumption ?? '—' }} kWh</span>
              <span class="metric-box__note">件 · 较昨日 {{ summary?.unitConsumptionNote }}</span>
            </div>
          </div>
          <p class="panel__foot">{{ summary?.dailySavingNote }}</p>
        </div>
      </el-col>

      <!-- AI 提示 -->
      <el-col :span="11">
        <div class="panel panel--full panel--pad">
          <div class="panel__header panel__header--plain">
            <span class="panel__title">AI 提示</span>
          </div>
          <p class="ai-hint">{{ summary?.aiHint }}</p>
          <p class="ai-basis">{{ summary?.aiHintBasis }}</p>
          <div class="ai-actions">
            <AiButton label="能耗分析" type="primary" @click="router.push('/energy/analysis')" />
            <AiButton label="AI 节能优化" @click="router.push('/energy/saving')" />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import PageHeader from '../../components/PageHeader.vue'
import StatCard from '../../components/StatCard.vue'
import AiButton from '../../components/AiButton.vue'
import {
  fetchEnergySummary,
  fetchEnergyTrend,
  type EnergySummary,
  type EnergyTrendPoint
} from '../../api/energy'

const router = useRouter()

const loading = ref(false)
const summary = shallowRef<EnergySummary | null>(null)
const trend = shallowRef<EnergyTrendPoint[]>([])
const hourly = shallowRef<EnergyTrendPoint[]>([])

const trendRef = ref<HTMLElement | null>(null)
const mediumRef = ref<HTMLElement | null>(null)
const hourlyRef = ref<HTMLElement | null>(null)
let trendChart: echarts.ECharts | null = null
let mediumChart: echarts.ECharts | null = null
let hourlyChart: echarts.ECharts | null = null

/** 千分位金额，保留整数（原型是 ¥48,700 这种粒度） */
function fmtMoney(v: number | undefined): string {
  if (v === undefined) return '—'
  return Math.round(v).toLocaleString('en-US')
}

/** 近 7 日趋势的单位：综合能耗固定 t标煤；后端不返回时兜底 */
const trendUnit = 't标煤'

/**
 * 5 张 KPI。
 * 用电单独除以 1000 显示成「千kWh」，与原型一致；
 * mediums 里的电是原始 kWh，用于右侧柱状图。
 */
const kpiCards = computed(() => {
  const s = summary.value
  if (!s) return []
  return [
    { label: '综合能耗', value: `${s.total} ${s.totalUnit}`, note: s.totalNote, accent: 'blue' as const, color: 'var(--text-1)' },
    { label: '电', value: `${s.electricity} 千kWh`, note: `较昨日 ${s.electricityNote}`, accent: 'orange' as const, color: 'var(--c-orange)' },
    { label: '水', value: `${s.water} t`, note: `较昨日 ${s.waterNote}`, accent: 'blue' as const, color: 'var(--text-1)' },
    { label: '气', value: `${s.gas} Nm³`, note: `较昨日 ${s.gasNote}`, accent: 'orange' as const, color: 'var(--c-orange)' },
    { label: '蒸汽', value: `${s.steam} t`, note: `较昨日 ${s.steamNote}`, accent: 'orange' as const, color: 'var(--c-orange)' }
  ]
})

/* ---------------- 图表 ---------------- */

const GRID = { left: 8, right: 20, top: 36, bottom: 8, containLabel: true }
const LEGEND = { top: 0, right: 0, itemWidth: 10, itemHeight: 8, textStyle: { fontSize: 11 } }
const AXIS_LINE = { lineStyle: { color: '#e5e6eb' } }
const AXIS_LABEL = { color: '#86909c', fontSize: 11 }
const SPLIT_LINE = { lineStyle: { color: '#f2f3f5' } }

function renderTrend() {
  if (!trendRef.value) return
  trendChart ??= echarts.init(trendRef.value)
  trendChart.setOption(
    {
      grid: GRID,
      legend: { ...LEGEND, data: ['本期', '上期'] },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: trend.value.map((d) => d.label),
        axisLine: AXIS_LINE,
        axisLabel: AXIS_LABEL
      },
      yAxis: { type: 'value', splitLine: SPLIT_LINE, axisLabel: AXIS_LABEL },
      series: [
        {
          name: '本期',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 5,
          data: trend.value.map((d) => d.current),
          itemStyle: { color: '#165dff' }
        },
        {
          name: '上期',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 4,
          data: trend.value.map((d) => d.last),
          itemStyle: { color: '#bedaff' }
        }
      ]
    },
    true
  )
}

/** 今日 vs 昨日（分介质）：四种介质单位不同，用对数轴避免气体把其它压平 */
function renderMedium() {
  if (!mediumRef.value) return
  mediumChart ??= echarts.init(mediumRef.value)
  const rows = summary.value?.mediums ?? []
  mediumChart.setOption(
    {
      grid: GRID,
      legend: { ...LEGEND, data: ['今日', '昨日'] },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        valueFormatter: (v: number) => Number(v).toLocaleString('en-US')
      },
      xAxis: {
        type: 'category',
        data: rows.map((r) => r.medium),
        axisLine: AXIS_LINE,
        axisLabel: AXIS_LABEL
      },
      yAxis: {
        type: 'log',
        logBase: 10,
        min: 1,
        splitLine: SPLIT_LINE,
        axisLabel: { ...AXIS_LABEL, formatter: (v: number) => (v >= 1000 ? v / 1000 + 'k' : String(v)) }
      },
      series: [
        {
          name: '今日',
          type: 'bar',
          barWidth: 20,
          barGap: '10%',
          data: rows.map((r) => r.today),
          itemStyle: { color: '#165dff', borderRadius: [3, 3, 0, 0] }
        },
        {
          name: '昨日',
          type: 'bar',
          barWidth: 20,
          data: rows.map((r) => r.yesterday),
          itemStyle: { color: '#bedaff', borderRadius: [3, 3, 0, 0] }
        }
      ]
    },
    true
  )
}

/** 今日分时电耗：24 点，本期 vs 上期 */
function renderHourly() {
  if (!hourlyRef.value) return
  hourlyChart ??= echarts.init(hourlyRef.value)
  hourlyChart.setOption(
    {
      grid: { left: 8, right: 24, top: 36, bottom: 8, containLabel: true },
      legend: { ...LEGEND, data: ['今日', '昨日'] },
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
      series: [
        {
          name: '今日',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 5,
          data: hourly.value.map((d) => d.current),
          lineStyle: { width: 2, color: '#ff7d00' },
          itemStyle: { color: '#ff7d00' }
        },
        {
          name: '昨日',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 4,
          data: hourly.value.map((d) => d.last),
          lineStyle: { width: 2, color: '#bedaff' },
          itemStyle: { color: '#bedaff' }
        }
      ]
    },
    true
  )
}

function resizeAll() {
  trendChart?.resize()
  mediumChart?.resize()
  hourlyChart?.resize()
}

/* ---------------- 数据 ---------------- */

async function load() {
  loading.value = true
  try {
    const [s, t, h] = await Promise.all([
      fetchEnergySummary({ days: 7 }),
      // medium='综合' → 后端按折标煤系数汇总四介质，单位 t标煤，与 KPI 卡「综合能耗」同口径
      fetchEnergyTrend({ days: 7, granularity: 'day', medium: '综合' }),
      fetchEnergyTrend({ granularity: 'hour', medium: '电' })
    ])
    summary.value = s
    trend.value = t
    hourly.value = h
    // 等 DOM 的 v-loading 遮罩撤掉再画，避免宽高量到 0
    requestAnimationFrame(() => {
      renderTrend()
      renderMedium()
      renderHourly()
    })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '能耗总览加载失败')
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
  trendChart?.dispose()
  mediumChart?.dispose()
  hourlyChart?.dispose()
  trendChart = mediumChart = hourlyChart = null
})
</script>

<style scoped>
.row-gap {
  margin-top: 16px;
}
.kpi-row {
  margin-bottom: 0;
}
.panel--full {
  height: 100%;
}
.panel__extra {
  font-size: 12px;
  color: var(--text-3);
}
.chart {
  width: 100%;
  height: 300px;
}
.chart--tall {
  height: 340px;
}
/* 成本 / AI 提示两块用 .panel 的内边距即可，只需去掉 header 的下边距 */
.panel--pad {
  display: flex;
  flex-direction: column;
}
.panel__header--plain {
  margin-bottom: 12px;
}
.metric-boxes {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.metric-box {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}
.metric-box__label {
  font-size: 12px;
  color: var(--text-3);
}
.metric-box__value {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-1);
  line-height: 1.2;
}
.metric-box__note {
  font-size: 12px;
  color: var(--text-3);
}
.panel__foot {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.7;
}
.ai-hint {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--text-1);
  line-height: 1.8;
}
.ai-basis {
  margin: 0 0 14px;
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.7;
}
.ai-actions {
  display: flex;
  gap: 10px;
}
</style>