<template>
  <div>
    <PageHeader title="能耗趋势" desc="分介质、分时段能耗趋势拆解，定位异常时段。">
      <AiButton label="解释夜间电耗上升" type="primary" @click="explain" />
    </PageHeader>

    <FilterPanel
      v-model:model="filters"
      :fields="[
        { key: 'medium', label: '能耗介质', type: 'select', options: options.medium },
        { key: 'shift', label: '班次', type: 'select', options: options.shift }
      ]"
      @search="apply"
      @reset="reset"
    />

    <el-row :gutter="16">
      <el-col :span="16">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">分时电耗对比（今日 vs 昨日）</span>
          </div>
          <div ref="lineRef" class="chart" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">白班 / 夜班</span>
          </div>
          <div ref="shiftRef" class="chart" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap">
      <el-col :span="24">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">三车间分时电耗（kWh）</span>
          </div>
          <div ref="workshopRef" class="chart" />
        </div>
      </el-col>
    </el-row>

    <div class="panel row-gap">
      <div class="panel__header">
        <span class="panel__title">介质明细</span>
      </div>
      <el-table :data="mediumShare" style="width: 100%">
        <el-table-column prop="medium" label="介质" width="120" />
        <el-table-column prop="value" label="今日用量" width="180" />
        <el-table-column label="相对电耗占比">
          <template #default="{ row }">
            <el-progress :percentage="row.ratio" :stroke-width="8" color="#ff7d00" />
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import PageHeader from '../../components/PageHeader.vue'
import AiButton from '../../components/AiButton.vue'
import FilterPanel from '../../components/FilterPanel.vue'
import { hourlyPower, mediumShare, options, shiftEnergy, workshopHourly } from '../../mock'

const router = useRouter()
const lineRef = ref<HTMLElement | null>(null)
const shiftRef = ref<HTMLElement | null>(null)
const workshopRef = ref<HTMLElement | null>(null)
let lineChart: echarts.ECharts | null = null
let shiftChart: echarts.ECharts | null = null
let workshopChart: echarts.ECharts | null = null
const filters = reactive({ medium: '电', shift: '全部' })

function initLine() {
  if (!lineRef.value) return
  lineChart = echarts.init(lineRef.value)
  lineChart.setOption({
    grid: { left: 8, right: 16, top: 36, bottom: 8, containLabel: true },
    legend: { top: 0, right: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 11 } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: hourlyPower.map((h) => h.hour + '时'),
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: { color: '#86909c', fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#86909c', fontSize: 11 }
    },
    series: [
      {
        name: '今日',
        type: 'line',
        smooth: true,
        data: hourlyPower.map((h) => h.today),
        areaStyle: { color: 'rgba(22,93,255,0.12)' },
        itemStyle: { color: '#165dff' }
      },
      {
        name: '昨日',
        type: 'line',
        smooth: true,
        data: hourlyPower.map((h) => h.yesterday),
        lineStyle: { type: 'dashed' },
        itemStyle: { color: '#86909c' }
      }
    ]
  })
}

function initShift() {
  if (!shiftRef.value) return
  shiftChart = echarts.init(shiftRef.value)
  shiftChart.setOption({
    grid: { left: 8, right: 16, top: 36, bottom: 8, containLabel: true },
    legend: { top: 0, right: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 11 } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'category',
      data: shiftEnergy.map((s) => s.workshop),
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: { color: '#86909c', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#86909c', fontSize: 11 }
    },
    series: [
      { name: '白班', type: 'bar', barWidth: 16, data: shiftEnergy.map((s) => s.day), itemStyle: { color: '#165dff', borderRadius: [3, 3, 0, 0] } },
      { name: '夜班', type: 'bar', barWidth: 16, data: shiftEnergy.map((s) => s.night), itemStyle: { color: '#ff7d00', borderRadius: [3, 3, 0, 0] } }
    ]
  })
}

function initWorkshop() {
  if (!workshopRef.value) return
  workshopChart = echarts.init(workshopRef.value)
  workshopChart.setOption({
    grid: { left: 8, right: 16, top: 36, bottom: 8, containLabel: true },
    legend: { top: 0, right: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 11 } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: workshopHourly.map((w) => w.hour + '时'),
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: { color: '#86909c', fontSize: 10, interval: 1 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#86909c', fontSize: 11 }
    },
    series: [
      { name: 'A车间', type: 'line', smooth: true, data: workshopHourly.map((w) => w.a), itemStyle: { color: '#165dff' } },
      { name: 'B车间', type: 'line', smooth: true, data: workshopHourly.map((w) => w.b), itemStyle: { color: '#00b42a' } },
      { name: 'C车间', type: 'line', smooth: true, data: workshopHourly.map((w) => w.c), itemStyle: { color: '#ff7d00' } }
    ]
  })
}

function onResize() {
  lineChart?.resize()
  shiftChart?.resize()
  workshopChart?.resize()
}

function apply() {
  ElMessage.success(`已切换至「${filters.medium} · ${filters.shift}」趋势`)
}

function reset() {
  Object.assign(filters, { medium: '电', shift: '全部' })
}

function explain() {
  ElMessage.success('AI：夜间 22 时电耗 +590kWh，主因是 4号空压机低负载空转')
  router.push('/copilot/chat')
}

onMounted(() => {
  initLine()
  initShift()
  initWorkshop()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  lineChart?.dispose()
  shiftChart?.dispose()
  workshopChart?.dispose()
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
</style>