<template>
  <div v-loading="loading">
    <PageHeader title="OEE分析" desc="开动性 × 性能 × 质量，定位损失并给出改进建议。">
      <el-select v-model="days" size="default" style="width: 120px; margin-right: 12px" @change="reload">
        <el-option :label="'近 7 天'" :value="7" />
        <el-option :label="'近 30 天'" :value="30" />
        <el-option :label="'近 60 天'" :value="60" />
      </el-select>
      <AiButton label="AI 解释 OEE 下降" type="primary" @click="explain" />
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="6">
        <StatCard
          label="全厂 OEE"
          :value="pct(summary.oee) + '%'"
          :sub="summary.oeeDelta || ''"
          accent="red"
          value-color="var(--c-red)"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="开动性"
          :value="pct(summary.availability) + '%'"
          :sub="summary.availabilityNote"
          accent="blue"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="性能效率"
          :value="pct(summary.performance) + '%'"
          :sub="summary.performanceNote"
          accent="orange"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="质量指数"
          :value="pct(summary.qualityIndex) + '%'"
          :sub="summary.qualityNote"
          accent="green"
          value-color="var(--c-green)"
        />
      </el-col>
    </el-row>

    <p class="scope-hint">{{ summary.scope }} · {{ summary.note }}</p>

    <el-row :gutter="16" class="row-gap">
      <el-col :span="16">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">损失 TOP 榜（按时长）</span>
          </div>
          <div ref="barRef" class="chart" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">损失构成</span>
          </div>
          <div ref="pieRef" class="chart" />
        </div>
      </el-col>
    </el-row>

    <div class="panel row-gap">
      <div class="panel__header">
        <span class="panel__title">产线 OEE 明细</span>
      </div>
      <el-table :data="lines" style="width: 100%">
        <el-table-column prop="lineName" label="产线" width="100" />
        <el-table-column label="OEE" width="110">
          <template #default="{ row }">
            <span class="oee-val" :style="{ color: oeeColor(row.oee) }">{{ pct(row.oee) }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="开动性" width="100">
          <template #default="{ row }">{{ pct(row.availability) }}%</template>
        </el-table-column>
        <el-table-column label="性能" width="100">
          <template #default="{ row }">{{ pct(row.performance) }}%</template>
        </el-table-column>
        <el-table-column label="质量" width="100">
          <template #default="{ row }">{{ pct(row.qualityIndex) }}%</template>
        </el-table-column>
        <el-table-column prop="downtime" label="停机" width="110" />
        <el-table-column label="达成率" width="100">
          <template #default="{ row }">{{ pct(row.achieve) }}%</template>
        </el-table-column>
        <el-table-column prop="lossBottleneck" label="主要损失" min-width="150" />
      </el-table>
    </div>

    <div class="panel row-gap">
      <div class="panel__header">
        <span class="panel__title">损失明细</span>
      </div>
      <el-table :data="losses" v-loading="lossLoading" style="width: 100%">
        <el-table-column prop="lossType" label="损失类型" width="120" />
        <el-table-column prop="trigger" label="归因维度" width="130" />
        <el-table-column prop="duration" label="时长/数量" width="110" />
        <el-table-column label="影响" width="100">
          <template #default="{ row }">
            <span style="color: var(--c-red)">{{ row.impact ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lineName" label="产线" width="100" />
        <el-table-column prop="equipment" label="设备/工位" width="130" />
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="occurTime" label="发生时间" width="160" />
      </el-table>
      <el-empty v-if="!lossLoading && losses.length === 0" description="统计期内无损失记录" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import PageHeader from '../../components/PageHeader.vue'
import StatCard from '../../components/StatCard.vue'
import AiButton from '../../components/AiButton.vue'
import {
  fetchLossTypes,
  fetchOeeByLine,
  fetchOeeLosses,
  fetchOeeSummary,
  type OeeLine,
  type OeeLoss,
  type OeeSummary,
} from '../../api/production'

const router = useRouter()
const barRef = ref<HTMLElement | null>(null)
const pieRef = ref<HTMLElement | null>(null)
let barChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null

const loading = ref(false)
const lossLoading = ref(false)
const days = ref(30)

const summary = reactive<OeeSummary>({
  oee: null,
  value: null,
  oeeDelta: null,
  delta: null,
  target: 90,
  availability: null,
  availabilityNote: '',
  performance: null,
  performanceNote: '',
  qualityIndex: null,
  qualityNote: '',
  quality: null,
  note: '',
  scope: '',
})

const lines = ref<OeeLine[]>([])
const losses = ref<OeeLoss[]>([])
const lossTypes = ref<Array<{ lossType: string; minutes: number; impact: number; count: number }>>([])

function pct(v: number | null): string {
  return v == null ? '—' : v.toFixed(2).replace(/\.00$/, '')
}

function oeeColor(v: number | null): string {
  if (v == null) return '#86909c'
  return v >= 85 ? '#00b42a' : v >= 80 ? '#ff7d00' : '#f53f3f'
}

const PIE_COLORS = ['#f53f3f', '#ff7d00', '#165dff', '#86909c', '#00b42a', '#722ed1']

/** 损失 TOP 榜横向条形图：没有真正的时序 OEE 数据，就不画假趋势线 */
function renderBar() {
  if (!barRef.value) return
  if (!barChart) barChart = echarts.init(barRef.value)
  // 取前三类之外的归入「其他」，避免条形太细
  const top = lossTypes.value.slice(0, 5)
  top.sort((a, b) => a.minutes - b.minutes) // 横向条从下往上，反转后最大值在顶部
  barChart.setOption(
    {
      grid: { left: 8, right: 24, top: 16, bottom: 8, containLabel: true },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#f2f3f5' } },
        axisLabel: { color: '#86909c', fontSize: 11, formatter: '{value} 分' },
      },
      yAxis: {
        type: 'category',
        data: top.map((d) => d.lossType),
        axisLine: { lineStyle: { color: '#e5e6eb' } },
        axisLabel: { color: '#4e5969', fontSize: 12 },
      },
      series: [
        {
          type: 'bar',
          barWidth: 16,
          itemStyle: { color: '#f53f3f', borderRadius: [0, 3, 3, 0] },
          label: { show: true, position: 'right', color: '#4e5969', fontSize: 11, formatter: '{c} 分' },
          data: top.map((d) => d.minutes),
        },
      ],
    },
    true,
  )
}

function renderPie() {
  if (!pieRef.value) return
  if (!pieChart) pieChart = echarts.init(pieRef.value)
  pieChart.setOption(
    {
      tooltip: { trigger: 'item', formatter: '{b}: {c} 分钟 ({d}%)' },
      legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
      series: [
        {
          type: 'pie',
          radius: ['46%', '68%'],
          center: ['50%', '44%'],
          itemStyle: { borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          data: lossTypes.value.map((d, i) => ({
            name: d.lossType,
            value: d.minutes,
            itemStyle: { color: PIE_COLORS[i % PIE_COLORS.length] },
          })),
        },
      ],
    },
    true,
  )
}

function onResize() {
  barChart?.resize()
  pieChart?.resize()
}

async function reload() {
  loading.value = true
  lossLoading.value = true
  try {
    const [s, byLine, types] = await Promise.all([
      fetchOeeSummary(days.value),
      fetchOeeByLine(days.value),
      fetchLossTypes(days.value),
    ])
    Object.assign(summary, s)
    lines.value = byLine
    lossTypes.value = types as unknown as Array<{
      lossType: string
      minutes: number
      impact: number
      count: number
    }>

    const ls = await fetchOeeLosses(days.value, undefined, 20)
    losses.value = ls

    await nextTick()
    renderBar()
    renderPie()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : 'OEE 数据加载失败')
  } finally {
    loading.value = false
    lossLoading.value = false
  }
}

function explain() {
  if (!summary.note) {
    ElMessage.info('暂无足够数据生成解释')
    return
  }
  ElMessage.success(`AI 解读：${summary.note}`)
  router.push('/copilot/chat')
}

onMounted(async () => {
  window.addEventListener('resize', onResize)
  await reload()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  barChart?.dispose()
  pieChart?.dispose()
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
.oee-val {
  font-weight: 600;
}
.scope-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--text-3);
}
</style>