<template>
  <div>
    <PageHeader title="设备总览" desc="全厂设备运行状态、健康度与预测性维护风险。">
      <el-select v-model="shift" style="width: 150px">
        <el-option v-for="s in shifts" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button @click="exportReport">导出设备台账</el-button>
    </PageHeader>

    <div class="kpi-grid">
      <StatCard label="设备总数" :value="factorySummary.equipmentTotal" :sub="factorySummary.equipmentNote" />
      <StatCard label="在线运行" :value="factorySummary.online" :sub="`在线率 ${factorySummary.onlineRate}`" accent="green" />
      <StatCard label="离线" :value="factorySummary.offline" :sub="factorySummary.offlineNote" accent="cyan" />
      <StatCard label="异常设备" :value="factorySummary.abnormal" :sub="factorySummary.abnormalNote" accent="orange" />
      <StatCard label="高风险" :value="factorySummary.highRisk" :sub="factorySummary.highRiskNote" accent="red" />
    </div>

    <div class="overview-grid">
      <div class="panel">
        <div class="panel__header">
          <span class="panel__title">设备状态分布</span>
          <span class="panel__extra">共 {{ equipments.length }} 台在册设备</span>
        </div>
        <div class="status-dist">
          <div ref="pieRef" class="status-dist__chart" />
          <div class="status-dist__legend">
            <div v-for="s in statusDist" :key="s.name" class="status-dist__row">
              <span class="dot" :style="{ background: s.color }" />
              <span class="status-dist__name">{{ s.name }}</span>
              <span class="status-dist__value">{{ s.value }} 台</span>
              <span class="status-dist__pct">{{ s.pct }}%</span>
            </div>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel__header">
          <span class="panel__title">产线健康度</span>
          <span class="panel__extra">按产线加权平均</span>
        </div>
        <div class="line-health">
          <div v-for="l in lineHealth" :key="l.name" class="line-health__item">
            <div class="line-health__head">
              <span class="line-health__name">{{ l.name }}</span>
              <span class="line-health__value">{{ l.health }}<i>分</i></span>
            </div>
            <div class="progress">
              <div class="progress__bar" :style="{ width: l.health + '%', background: healthColor(l.health) }" />
            </div>
            <div class="line-health__note">{{ l.note }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="panel">
      <div class="panel__header">
        <span class="panel__title">设备健康排行</span>
        <span class="panel__extra">按健康分升序，风险高的排在前面</span>
      </div>
      <el-table :data="sortedEquipments" style="width: 100%">
        <el-table-column prop="code" label="设备编号" width="120" />
        <el-table-column prop="name" label="设备名称" width="130" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="lineName" label="所在产线" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :text="row.status" :map="EQUIPMENT_STATUS_CLASS" />
          </template>
        </el-table-column>
        <el-table-column label="健康分" width="160">
          <template #default="{ row }">
            <div class="health-cell">
              <div class="progress progress--sm">
                <div class="progress__bar" :style="{ width: row.health + '%', background: healthColor(row.health) }" />
              </div>
              <span>{{ row.health }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="100">
          <template #default="{ row }">
            <StatusTag :text="row.riskLevel" :map="RISK_CLASS" />
          </template>
        </el-table-column>
        <el-table-column prop="lastException" label="最近异常" min-width="160" show-overflow-tooltip />
        <el-table-column label="7日故障概率" width="120">
          <template #default="{ row }">{{ row.predict.d7 }}%</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="router.push(`/equipment/detail/${row.id}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import PageHeader from '../../components/PageHeader.vue'
import StatCard from '../../components/StatCard.vue'
import StatusTag from '../../components/StatusTag.vue'
import { equipments, factorySummary } from '../../mock'
import { EQUIPMENT_STATUS_CLASS, RISK_CLASS } from '../../utils/dict'

const router = useRouter()
const shift = ref('当前班次')
const shifts = ['当前班次', '白班 08:00—20:00', '夜班 20:00—08:00']
const pieRef = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

const statusColors: Record<string, string> = {
  正常: '#00b42a',
  预警: '#ff7d00',
  故障: '#f53f3f',
  离线: '#86909c'
}

const statusDist = computed(() => {
  const map: Record<string, number> = {}
  equipments.forEach((e) => (map[e.status] = (map[e.status] ?? 0) + 1))
  return Object.entries(map).map(([name, value]) => ({
    name,
    value,
    pct: ((value / equipments.length) * 100).toFixed(0),
    color: statusColors[name] ?? '#86909c'
  }))
})

const sortedEquipments = computed(() => [...equipments].sort((a, b) => a.health - b.health))

const lineHealth = computed(() =>
  ['A产线', 'B产线', 'C产线', '公用工程'].map((name) => {
    const list = equipments.filter((e) => e.lineName === name)
    const health = list.length ? Math.round(list.reduce((s, e) => s + e.health, 0) / list.length) : 0
    return {
      name,
      health,
      note: list.length
        ? `${list.length} 台设备 · 其中异常 ${list.filter((e) => e.status !== '正常').length} 台`
        : '暂未接入设备'
    }
  })
)

function healthColor(v: number) {
  if (v >= 85) return '#00b42a'
  if (v >= 70) return '#ff7d00'
  return '#f53f3f'
}

function initChart() {
  if (!pieRef.value) return
  chart = echarts.init(pieRef.value)
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}：{c} 台（{d}%）' },
    series: [
      {
        type: 'pie',
        radius: ['54%', '78%'],
        center: ['50%', '50%'],
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { scaleSize: 4 },
        data: statusDist.value.map((s) => ({ name: s.name, value: s.value, itemStyle: { color: s.color } }))
      }
    ]
  })
}

function onResize() {
  chart?.resize()
}

function exportReport() {
  ElMessage.success('设备台账导出中，请稍后到下载中心查看')
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<style scoped>
.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.status-dist {
  display: flex;
  align-items: center;
  gap: 8px;
}
.status-dist__chart {
  width: 190px;
  height: 176px;
  flex: none;
}
.status-dist__legend {
  flex: 1;
  min-width: 0;
}
.status-dist__row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
}
.status-dist__name {
  flex: 1;
  color: var(--text-2);
}
.status-dist__value {
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}
.status-dist__pct {
  width: 44px;
  text-align: right;
  color: var(--text-3);
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
.line-health__item + .line-health__item {
  margin-top: 14px;
}
.line-health__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 6px;
}
.line-health__name {
  color: var(--text-1);
}
.line-health__value {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}
.line-health__value i {
  font-style: normal;
  font-size: 11px;
  color: var(--text-3);
  margin-left: 2px;
}
.line-health__note {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-3);
}
.progress {
  height: 6px;
  border-radius: 3px;
  background: #f2f3f5;
  overflow: hidden;
}
.progress--sm {
  width: 80px;
  height: 5px;
}
.progress__bar {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s;
}
.health-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  font-variant-numeric: tabular-nums;
}
</style>