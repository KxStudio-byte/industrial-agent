<template>
  <div>
    <PageHeader title="工厂总览" desc="全厂生产、质量、设备、能耗一屏掌握。">
      <AiButton label="打开 AI 对话" type="primary" @click="router.push('/copilot/chat')" />
    </PageHeader>

    <!-- 顶部 6 张指标卡 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <StatCard
          label="今日产量"
          :value="summary.actualQty.toLocaleString()"
          :sub="`计划 ${summary.planQty.toLocaleString()} 件 · 达成率 ${summary.achieve}%`"
          accent="blue"
          value-color="var(--c-orange)"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="全厂 OEE"
          :value="summary.oee + '%'"
          :sub="summary.oeeDelta"
          accent="orange"
          value-color="var(--c-red)"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="良品率"
          :value="summary.quality + '%'"
          :sub="summary.qualityDelta"
          accent="green"
          value-color="var(--c-orange)"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="待处理预警"
          :value="summary.alerts"
          :sub="summary.alertsNote"
          accent="red"
          value-color="var(--c-red)"
        />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap">
      <el-col :span="6">
        <StatCard :label="'设备总数'" :value="summary.equipmentTotal" :sub="summary.equipmentNote" accent="blue" />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="在线设备"
          :value="summary.online"
          :sub="summary.onlineRate"
          accent="green"
          value-color="var(--c-green)"
        />
      </el-col>
      <el-col :span="6">
        <StatCard label="离线设备" :value="summary.offline" :sub="summary.offlineNote" accent="orange" />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="异常设备"
          :value="summary.abnormal"
          :sub="summary.abnormalNote"
          accent="red"
          value-color="var(--c-red)"
        />
      </el-col>
    </el-row>

    <!-- AI 结论 -->
    <div class="panel ai-panel">
      <div class="ai-panel__head">
        <div class="ai-panel__title">
          <SparkIcon :size="16" />
          <span>AI 结论</span>
        </div>
        <span class="ai-panel__time">生成于 {{ app.currentTime }} · 生产分析Agent</span>
      </div>
      <p class="ai-panel__text">{{ summary.aiSummary }}</p>
      <div class="ai-panel__actions">
        <el-button size="small" @click="router.push('/production/oee')">查看 OEE 分析</el-button>
        <el-button size="small" @click="router.push('/equipment/list')">查看风险设备</el-button>
        <AiButton size="small" label="问 Copilot" type="primary" @click="router.push('/copilot/chat')" />
      </div>
    </div>

    <!-- 产线卡片 -->
    <div class="panel">
      <div class="panel__header">
        <span class="panel__title">产线实时状态</span>
        <AiButton size="small" text label="查看生产总览" @click="router.push('/production/overview')" />
      </div>

      <el-row :gutter="16">
        <el-col v-for="line in productionLines" :key="line.name" :span="8">
          <div class="line-card">
            <div class="line-card__head">
              <span class="line-card__name">{{ line.name }}</span>
              <StatusTag :text="line.status" :map="PLAN_STATUS_CLASS" />
              <span class="line-card__product">{{ line.product }}</span>
            </div>
            <el-row :gutter="10" class="line-card__metrics">
              <el-col :span="12">
                <div class="metric metric--blue">
                  <div class="metric__label">产量</div>
                  <div class="metric__value">{{ line.actual }} / {{ line.target }}</div>
                  <div class="metric__sub">{{ line.achieveNote }}</div>
                </div>
              </el-col>
              <el-col :span="12">
                <div class="metric metric--orange">
                  <div class="metric__label">OEE</div>
                  <div class="metric__value">{{ line.oee }}%</div>
                  <div class="metric__sub">{{ line.oeeDelta }}</div>
                </div>
              </el-col>
            </el-row>
            <el-row :gutter="10">
              <el-col :span="12">
                <div class="metric metric--green">
                  <div class="metric__label">良品率</div>
                  <div class="metric__value">{{ line.quality }}%</div>
                  <div class="metric__sub">{{ line.qualityNote }}</div>
                </div>
              </el-col>
              <el-col :span="12">
                <div class="metric metric--cyan">
                  <div class="metric__label">达成率</div>
                  <div class="metric__value">{{ line.achieve }}%</div>
                  <div class="metric__sub">当前批次 {{ line.currentBatchQty }}</div>
                </div>
              </el-col>
            </el-row>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 风险提示 + 能耗 -->
    <el-row :gutter="16" class="row-gap">
      <el-col :span="14">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">风险设备</span>
            <AiButton size="small" text label="查看设备列表" @click="router.push('/equipment/list')" />
          </div>
          <el-table :data="riskEquipments" style="width: 100%">
            <el-table-column prop="name" label="设备名称" min-width="120" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <StatusTag :text="row.status" :map="EQUIPMENT_STATUS_CLASS" />
              </template>
            </el-table-column>
            <el-table-column label="健康度" width="90">
              <template #default="{ row }">
                <span :style="{ color: healthColor(row.health), fontWeight: 500 }">{{ row.health }}</span>
              </template>
            </el-table-column>
            <el-table-column label="风险等级" width="90">
              <template #default="{ row }">
                <StatusTag :text="row.riskLevel" :map="RISK_CLASS" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button size="small" @click="openEquipment(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <el-col :span="10">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">今日能耗</span>
            <AiButton size="small" text label="能耗分析" @click="router.push('/energy/analysis')" />
          </div>
          <div class="energy-grid">
            <div v-for="item in energyItems" :key="item.label" class="energy-item">
              <span class="energy-item__label">{{ item.label }}</span>
              <span class="energy-item__value">{{ item.value }}</span>
              <span class="energy-item__sub" :class="{ 'energy-item__sub--up': item.up }">{{ item.note }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <EquipmentDrawer ref="equipDrawer" @create="goWorkOrder" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatCard from '../../components/StatCard.vue'
import AiButton from '../../components/AiButton.vue'
import StatusTag from '../../components/StatusTag.vue'
import SparkIcon from '../../components/SparkIcon.vue'
import EquipmentDrawer from './EquipmentDetailDrawer.vue'
import { useAppStore } from '../../stores/app'
import { productionLines, factorySummary, riskEquipments, energySummary } from '../../mock'
import { EQUIPMENT_STATUS_CLASS, RISK_CLASS, PLAN_STATUS_CLASS, healthColor } from '../../utils/dict'
import type { Equipment } from '../../types'

const router = useRouter()
const app = useAppStore()
const summary = factorySummary

const equipDrawer = ref<InstanceType<typeof EquipmentDrawer> | null>(null)

const energyItems = computed(() => [
  { label: '综合能耗', value: energySummary.total + ' t标煤', note: '今日', up: true },
  { label: '电', value: energySummary.electricity + ' 千kWh', note: '+12.4%', up: true },
  { label: '水', value: energySummary.water + ' t', note: '-3.1%', up: false },
  { label: '气', value: energySummary.gas + ' Nm³', note: '+6.8%', up: true }
])

function openEquipment(row: Equipment) {
  equipDrawer.value?.open(row)
}

/** 抽屉里点「创建维修工单」：总览页不做创建，直接把人送到工单列表 */
function goWorkOrder() {
  ElMessage.success('已为你带出设备信息，请在工单列表中提交')
  router.push('/equipment/work-orders')
}
</script>

<style scoped>
.row-gap {
  margin-top: 16px;
}

.ai-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.ai-panel__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
}
.ai-panel__time {
  font-size: 12px;
  color: var(--text-3);
}
.ai-panel__text {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 22px;
  color: var(--text-2);
}
.ai-panel__actions {
  display: flex;
  gap: 8px;
}
.ai-panel {
  margin-top: 16px;
}

.line-card {
  border: 1px solid var(--line-light);
  border-radius: var(--radius);
  padding: 12px 14px;
  height: 100%;
}
.line-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.line-card__name {
  font-size: 14px;
  font-weight: 600;
}
.line-card__product {
  font-size: 12px;
  color: var(--text-3);
  margin-left: auto;
}
.line-card__metrics {
  margin-bottom: 8px;
}

.metric {
  border-left: 3px solid var(--c-blue);
  background: #fafbfc;
  border-radius: 4px;
  padding: 8px 10px;
  height: 100%;
}
.metric--blue {
  border-color: var(--c-blue);
}
.metric--orange {
  border-color: var(--c-orange);
}
.metric--green {
  border-color: var(--c-green);
}
.metric--cyan {
  border-color: var(--c-cyan);
}
.metric__label {
  font-size: 12px;
  color: var(--text-3);
}
.metric__value {
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}
.metric__sub {
  font-size: 11px;
  color: var(--text-3);
}

.panel--full {
  height: 100%;
}

.energy-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.energy-item {
  border-left: 3px solid var(--c-blue);
  background: #fafbfc;
  border-radius: 4px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
}
.energy-item__label {
  font-size: 12px;
  color: var(--text-3);
}
.energy-item__value {
  font-size: 15px;
  font-weight: 600;
}
.energy-item__sub {
  font-size: 11px;
  color: var(--c-green);
}
.energy-item__sub--up {
  color: var(--c-orange);
}
</style>