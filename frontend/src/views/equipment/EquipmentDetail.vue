<template>
  <div>
    <PageHeader title="设备详情" desc="单台设备的实时运行、健康评分、故障预测与历史维修。">
      <el-button @click="back">返回列表</el-button>
      <el-button type="primary" @click="createWorkOrder">创建维修工单</el-button>
    </PageHeader>

    <div class="panel">
      <div class="head">
        <div class="head__name">{{ equip.name }}</div>
        <span class="head__code">{{ equip.code }}</span>
        <StatusTag :text="equip.status" :map="EQUIPMENT_STATUS_CLASS" />
        <span class="head__line">所属：{{ equip.lineName }}</span>
        <span class="head__line">型号：{{ equip.model }}</span>
        <span class="head__line">厂商：{{ equip.vendor }}</span>
      </div>

      <el-row :gutter="16" class="head-metrics">
        <el-col :span="6">
          <div class="metric metric--warn">
            <div class="metric__label">健康评分</div>
            <div class="metric__value">{{ equip.health }} / 100</div>
            <div class="metric__sub">近 7 天下降 9 分</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric metric--warn">
            <div class="metric__label">振动</div>
            <div class="metric__value">{{ equip.vibration }} mm/s</div>
            <div class="metric__sub">阈值 6.5</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="metric__label">温度</div>
            <div class="metric__value">{{ equip.temperature }} ℃</div>
            <div class="metric__sub">正常</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric">
            <div class="metric__label">电流</div>
            <div class="metric__value">{{ equip.current }} A</div>
            <div class="metric__sub">额定 12.5 A</div>
          </div>
        </el-col>
      </el-row>

      <div class="ai-box">
        <div class="ai-box__title">
          <SparkIcon :size="15" />
          <span>AI 设备诊断</span>
        </div>
        <p class="ai-box__text">{{ equip.diagnosis }}</p>
      </div>
    </div>

    <el-row :gutter="16" class="row-gap">
      <el-col :span="16">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">运行趋势（近 12 小时）</span>
          </div>
          <div ref="chartRef" class="chart" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">故障预测</span>
          </div>
          <div class="predict">
            <div v-for="p in predicts" :key="p.label" class="predict__item">
              <div class="predict__label">{{ p.label }}</div>
              <el-progress
                :percentage="p.value"
                :stroke-width="8"
                :color="p.value >= 60 ? '#f53f3f' : p.value >= 30 ? '#ff7d00' : '#00b42a'"
              >
                <span class="predict__pct">{{ p.value }}%</span>
              </el-progress>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="panel row-gap">
      <div class="panel__header">
        <span class="panel__title">历史维修记录</span>
      </div>
      <el-table :data="repairHistory" style="width: 100%">
        <el-table-column prop="time" label="时间" width="160" />
        <el-table-column prop="fault" label="故障现象" min-width="180" />
        <el-table-column prop="solution" label="处理措施" min-width="240" />
        <el-table-column prop="spare" label="更换备件" width="140" />
        <el-table-column prop="duration" label="停机时长" width="110" />
        <el-table-column prop="owner" label="处理人" width="110" />
      </el-table>
    </div>

    <DetailDrawer v-model="createVisible" title="创建维修工单" subtitle="为设备提交维修申请。">
      <el-form :model="form" label-width="88px">
        <el-form-item label="设备">
          <el-input v-model="form.equipment" disabled />
        </el-form-item>
        <el-form-item label="故障描述">
          <el-input v-model="form.desc" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-radio-group v-model="form.urgency">
            <el-radio value="高">高</el-radio>
            <el-radio value="中">中</el-radio>
            <el-radio value="低">低</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">提交工单</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import SparkIcon from '../../components/SparkIcon.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { equipments } from '../../mock'
import { EQUIPMENT_STATUS_CLASS } from '../../utils/dict'
import type { Equipment } from '../../types'

const route = useRoute()
const router = useRouter()
const chartRef = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null
const createVisible = ref(false)

const equip = computed<Equipment>(
  () => equipments.find((e) => e.name === route.query.name || e.code === route.query.code) ?? equipments[2] ?? equipments[0]
)

const predicts = computed(() => [
  { label: '24 小时故障概率', value: equip.value.predict?.h24 ?? 0 },
  { label: '7 天故障概率', value: equip.value.predict?.d7 ?? 0 },
  { label: '30 天故障概率', value: equip.value.predict?.d30 ?? 0 }
])

const repairHistory = [
  {
    time: '2026-08-12 09:20',
    fault: '主轴异响，振动偏高',
    solution: '更换 6208 轴承，复测动平衡至 1.6mm/s',
    spare: '轴承 6208',
    duration: '4.5h',
    owner: '李工'
  },
  {
    time: '2026-05-03 14:05',
    fault: '液压油温过高',
    solution: '清洗冷却器，更换液压油',
    spare: '冷却器密封圈',
    duration: '2.0h',
    owner: '王工'
  },
  {
    time: '2026-01-18 22:40',
    fault: '模具顶针划伤制品',
    solution: '更换顶针 4 支，抛光模具型腔',
    spare: '顶针 ×4',
    duration: '3.2h',
    owner: '赵工'
  }
]

const form = reactive({
  equipment: '',
  desc: '',
  urgency: '高'
})

function back() {
  router.push('/equipment/list')
}

function createWorkOrder() {
  form.equipment = `${equip.value.name}（${equip.value.code}）`
  form.desc = equip.value.diagnosis ?? ''
  createVisible.value = true
}

function submit() {
  createVisible.value = false
  ElMessage.success('维修工单已提交')
  router.push('/equipment/work-orders')
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  const hours = Array.from({ length: 12 }, (_, i) => `${String(i * 2).padStart(2, '0')}:00`)

  chart.setOption({
    grid: { left: 8, right: 12, top: 36, bottom: 8, containLabel: true },
    legend: { top: 0, right: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 11 } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: hours,
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: { color: '#86909c', fontSize: 11 }
    },
    yAxis: [
      {
        type: 'value',
        name: 'mm/s / A',
        nameTextStyle: { color: '#86909c', fontSize: 11 },
        splitLine: { lineStyle: { color: '#f2f3f5' } },
        axisLabel: { color: '#86909c', fontSize: 11 }
      },
      {
        type: 'value',
        name: '℃',
        nameTextStyle: { color: '#86909c', fontSize: 11 },
        splitLine: { show: false },
        axisLabel: { color: '#86909c', fontSize: 11 }
      }
    ],
    series: [
      {
        name: '振动',
        type: 'line',
        smooth: true,
        data: [4.1, 4.4, 4.9, 5.3, 5.8, 6.2, 6.6, 7.0, 7.3, 7.5, 7.7, 7.8],
        itemStyle: { color: '#f53f3f' },
        lineStyle: { width: 2 },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: '#ff7d00', type: 'dashed' },
          data: [{ yAxis: 6.5 }]
        }
      },
      {
        name: '电流',
        type: 'line',
        smooth: true,
        data: [9.1, 9.3, 9.2, 9.4, 9.6, 9.7, 9.9, 10.1, 10.0, 10.2, 10.1, 10.3],
        itemStyle: { color: '#165dff' },
        lineStyle: { width: 2 }
      },
      {
        name: '温度',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: [62, 64, 66, 68, 70, 71, 73, 74, 75, 76, 77, 78],
        itemStyle: { color: '#00b42a' },
        lineStyle: { width: 2 }
      }
    ]
  })
}

function onResize() {
  chart?.resize()
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
.head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.head__name {
  font-size: 18px;
  font-weight: 600;
}
.head__code {
  font-size: 12px;
  color: var(--text-3);
  background: #f2f3f5;
  border-radius: 4px;
  padding: 2px 6px;
}
.head__line {
  font-size: 12px;
  color: var(--text-3);
}
.head-metrics {
  margin-bottom: 14px;
}

.metric {
  border-left: 3px solid var(--c-blue);
  background: #fafbfc;
  border-radius: 4px;
  padding: 8px 10px;
}
.metric--warn {
  border-color: var(--c-red);
}
.metric__label {
  font-size: 12px;
  color: var(--text-3);
}
.metric__value {
  font-size: 16px;
  font-weight: 600;
}
.metric--warn .metric__value {
  color: var(--c-red);
}
.metric__sub {
  font-size: 11px;
  color: var(--text-3);
}

.ai-box {
  background: var(--brand-light);
  border-radius: var(--radius);
  padding: 12px 14px;
}
.ai-box__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--brand);
  margin-bottom: 6px;
}
.ai-box__text {
  margin: 0;
  font-size: 13px;
  line-height: 22px;
  color: var(--text-2);
}

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
.predict__item {
  margin-bottom: 18px;
}
.predict__label {
  font-size: 12px;
  color: var(--text-3);
  margin-bottom: 4px;
}
.predict__pct {
  font-size: 12px;
  color: var(--text-2);
}
</style>