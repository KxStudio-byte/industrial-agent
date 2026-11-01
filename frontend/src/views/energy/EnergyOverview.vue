<template>
  <div>
    <PageHeader title="能耗概览" desc="电、水、气、蒸汽综合能耗监控与成本趋势。">
      <AiButton label="AI 节能建议" type="primary" @click="router.push('/energy/optimize')" />
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="6">
        <StatCard label="综合能耗" :value="energySummary.total" :sub="energySummary.totalNote" accent="blue" />
      </el-col>
      <el-col :span="6">
        <StatCard label="用电" :value="energySummary.electricity + ' 千kWh'" :sub="energySummary.electricityNote" accent="orange" value-color="var(--c-orange)" />
      </el-col>
      <el-col :span="6">
        <StatCard label="用水" :value="energySummary.water + ' t'" :sub="energySummary.waterNote" accent="green" value-color="var(--c-green)" />
      </el-col>
      <el-col :span="6">
        <StatCard label="天然气" :value="energySummary.gas + ' Nm³'" :sub="energySummary.gasNote" accent="red" />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap">
      <el-col :span="16">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">近 7 日综合能耗（t标煤）</span>
          </div>
          <div ref="trendRef" class="chart" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">今日 vs 昨日（分介质）</span>
          </div>
          <div ref="pieRef" class="chart" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap">
      <el-col :span="12">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">能耗成本</span>
          </div>
          <div class="cost">
            <div class="cost__main">
              <span class="cost__value">{{ energyCost.cost }}</span>
              <span class="cost__unit">{{ energyCost.costNote }}</span>
            </div>
            <div class="cost__grid">
              <div class="cost__item">
                <span class="cost__label">单位能耗</span>
                <span class="cost__num">{{ energyCost.unit }}</span>
                <span class="cost__note">{{ energyCost.unitNote }}</span>
              </div>
              <div class="cost__item">
                <span class="cost__label">可节约</span>
                <span class="cost__num">{{ energyCost.saving }}</span>
                <span class="cost__note">{{ energyCost.savingNote }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">今日分时电耗（kWh）</span>
          </div>
          <div ref="unitRef" class="chart chart--sm" />
        </div>
      </el-col>
    </el-row>

    <div class="panel row-gap">
      <div class="panel__header">
        <span class="panel__title">车间能耗排名</span>
      </div>
      <el-table :data="energyByWorkshop" style="width: 100%">
        <el-table-column prop="name" label="车间" width="110" />
        <el-table-column prop="value" label="能耗(t标煤)" width="120" />
        <el-table-column label="占比" width="180">
          <template #default="{ row }">
            <el-progress :percentage="row.ratio" :stroke-width="6" color="#165dff" :format="() => row.ratio + '%'" />
          </template>
        </el-table-column>
        <el-table-column prop="delta" label="环比" width="100" />
        <el-table-column prop="mix" label="介质构成" min-width="220" show-overflow-tooltip />
        <el-table-column prop="diagnosis" label="AI 诊断" min-width="150" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="router.push('/energy/optimize')">{{ row.action }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import PageHeader from '../../components/PageHeader.vue'
import StatCard from '../../components/StatCard.vue'
import AiButton from '../../components/AiButton.vue'
import {
  energyByMedium,
  energyByWorkshop,
  energyCost,
  energySummary,
  energyTrend7d,
  hourlyPower
} from '../../mock'

const router = useRouter()
const trendRef = ref<HTMLElement | null>(null)
const pieRef = ref<HTMLElement | null>(null)
const unitRef = ref<HTMLElement | null>(null)
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null
let unitChart: echarts.ECharts | null = null

function initTrend() {
  if (!trendRef.value) return
  trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    grid: { left: 8, right: 16, top: 36, bottom: 8, containLabel: true },
    legend: { top: 0, right: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 11 } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: energyTrend7d.map((d) => d.date),
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: { color: '#86909c', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#86909c', fontSize: 11 }
    },
    series: [
      {
        name: '本周',
        type: 'line',
        smooth: true,
        data: energyTrend7d.map((d) => d.current),
        areaStyle: { color: 'rgba(22,93,255,0.12)' },
        itemStyle: { color: '#165dff' }
      },
      { name: '上周', type: 'line', smooth: true, data: energyTrend7d.map((d) => d.last), itemStyle: { color: '#c9cdd4' } }
    ]
  })
}

function initPie() {
  if (!pieRef.value) return
  pieChart = echarts.init(pieRef.value)
  pieChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, right: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 11 } },
    grid: { left: 8, right: 16, top: 36, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: energyByMedium.map((d) => d.medium),
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: { color: '#86909c', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#86909c', fontSize: 11 }
    },
    series: [
      { name: '今日', type: 'bar', barWidth: 14, data: energyByMedium.map((d) => d.today), itemStyle: { color: '#165dff', borderRadius: [3, 3, 0, 0] } },
      { name: '昨日', type: 'bar', barWidth: 14, data: energyByMedium.map((d) => d.yesterday), itemStyle: { color: '#bedaff', borderRadius: [3, 3, 0, 0] } }
    ]
  })
}

function initUnit() {
  if (!unitRef.value) return
  unitChart = echarts.init(unitRef.value)
  unitChart.setOption({
    grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: hourlyPower.map((h) => h.hour + '时'),
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: { color: '#86909c', fontSize: 10, interval: 1 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#86909c', fontSize: 11 }
    },
    series: [
      {
        name: '今日',
        type: 'bar',
        data: hourlyPower.map((h) => h.today),
        barWidth: '55%',
        itemStyle: { color: '#165dff', borderRadius: [3, 3, 0, 0] }
      }
    ]
  })
}

function onResize() {
  trendChart?.resize()
  pieChart?.resize()
  unitChart?.resize()
}

onMounted(() => {
  initTrend()
  initPie()
  initUnit()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose()
  pieChart?.dispose()
  unitChart?.dispose()
})
</script>

<style scoped>
.row-gap {
  margin-top: 16px;
}
.panel--full {
  height: 100%;
}
.chart {
  width: 100%;
  height: 280px;
}
.chart--sm {
  height: 200px;
}
.cost__main {
  margin-bottom: 16px;
}
.cost__value {
  font-size: 30px;
  font-weight: 600;
  color: var(--text-1);
  margin-right: 8px;
}
.cost__unit {
  font-size: 12px;
  color: var(--text-3);
}
.cost__grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.cost__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: var(--radius);
  background: #f7f8fa;
  font-size: 13px;
}
.cost__label {
  color: var(--text-3);
  font-size: 12px;
}
.cost__num {
  color: var(--text-1);
  font-weight: 600;
}
.cost__note {
  color: var(--text-3);
  font-size: 12px;
}
</style>