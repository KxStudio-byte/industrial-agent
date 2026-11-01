<template>
  <div>
    <PageHeader title="AI实时洞察" desc="Agent 持续扫描全厂数据，实时推送异常与机会点。">
      <AiButton label="刷新洞察" :type="'default'" @click="refresh" />
      <AiButton label="问 Copilot" type="primary" @click="router.push('/copilot/chat')" />
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="6">
        <StatCard label="今日洞察" :value="insights.length" sub="Agent 实时推送" accent="blue" />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="高风险洞察"
          :value="highRiskCount"
          sub="需人工确认"
          accent="red"
          value-color="var(--c-red)"
        />
      </el-col>
      <el-col :span="6">
        <StatCard label="已转工单" :value="2" sub="本周" accent="green" value-color="var(--c-green)" />
      </el-col>
      <el-col :span="6">
        <StatCard label="平均响应" value="38s" sub="Agent 首次产出" accent="cyan" />
      </el-col>
    </el-row>

    <div class="panel panel-top">
      <div class="panel__header">
        <span class="panel__title">实时洞察流</span>
        <span class="live">
          <i class="live__dot" />
          实时刷新中
        </span>
      </div>

      <div v-for="item in insights" :key="item.id" class="insight" :class="`insight--${item.riskLevel}`">
        <div class="insight__head">
          <span class="insight__title">{{ item.title }}</span>
          <StatusTag :text="item.riskLevel" :map="RISK_CLASS" />
          <span class="insight__time">{{ item.discoverTime }}</span>
        </div>
        <p class="insight__body">{{ item.aiJudgement }}</p>
        <div class="insight__foot">
          <span class="insight__meta">
            <span class="insight__meta-item">{{ item.objectType }}：{{ item.objectName }}</span>
            <span class="insight__meta-item">影响：{{ item.impact }}</span>
            <span class="insight__meta-item">来源：{{ item.agent }}</span>
          </span>
          <span class="insight__actions">
            <el-button size="small" @click="openInsight(item)">查看详情</el-button>
            <el-button size="small" type="primary" @click="toWorkOrder(item)">转工单</el-button>
          </span>
        </div>
      </div>
    </div>

    <DetailDrawer v-model="drawerVisible" title="洞察详情" subtitle="AI 判断依据与建议动作。">
      <div class="detail-list">
        <span class="detail-list__label">洞察标题</span>
        <span class="detail-list__value">{{ current?.title }}</span>
        <span class="detail-list__label">对象</span>
        <span class="detail-list__value">{{ current?.objectType }} · {{ current?.objectName }}</span>
        <span class="detail-list__label">风险等级</span>
        <span class="detail-list__value">
          <StatusTag v-if="current" :text="current.riskLevel" :map="RISK_CLASS" />
        </span>
        <span class="detail-list__label">概率</span>
        <span class="detail-list__value">{{ current?.probability }}%</span>
        <span class="detail-list__label">AI 判断</span>
        <span class="detail-list__value">{{ current?.aiJudgement }}</span>
        <span class="detail-list__label">影响</span>
        <span class="detail-list__value">{{ current?.impact }}</span>
        <span class="detail-list__label">发现时间</span>
        <span class="detail-list__value">{{ current?.discoverTime }}</span>
        <span class="detail-list__label">来源 Agent</span>
        <span class="detail-list__value">{{ current?.agent }}</span>
      </div>
      <template #footer>
        <el-button @click="drawerVisible = false">关闭</el-button>
        <el-button type="primary" @click="toWorkOrder(current)">转维修工单</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatCard from '../../components/StatCard.vue'
import AiButton from '../../components/AiButton.vue'
import StatusTag from '../../components/StatusTag.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { insights } from '../../mock'
import { RISK_CLASS } from '../../utils/dict'
import type { Insight } from '../../types'

const router = useRouter()
const drawerVisible = ref(false)
const current = ref<Insight | null>(null)

const highRiskCount = computed(() => insights.filter((i) => i.riskLevel === '高').length)

function openInsight(item: Insight) {
  current.value = item
  drawerVisible.value = true
}

function toWorkOrder(item: Insight | null) {
  drawerVisible.value = false
  ElMessage.success(`已为「${item?.objectName ?? ''}」创建工单草稿，请在操作执行中确认`)
  router.push('/copilot/executions')
}

function refresh() {
  ElMessage.success('已触发全部 Agent 重新扫描')
}
</script>

<style scoped>
.panel-top {
  margin-top: 16px;
}

.live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--c-green);
}
.live__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--c-green);
  box-shadow: 0 0 0 3px rgba(0, 180, 42, 0.15);
  animation: pulse 1.6s infinite;
}
@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}

.insight {
  position: relative;
  padding: 12px 14px 12px 16px;
  border: 1px solid var(--line-light);
  border-radius: var(--radius);
  margin-bottom: 10px;
  transition: box-shadow 0.2s;
}
.insight:hover {
  box-shadow: var(--shadow-hover);
}
.insight::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  border-radius: 3px 0 0 3px;
  background: var(--c-blue);
}
.insight--高::before {
  background: var(--c-red);
}
.insight--中::before {
  background: var(--c-orange);
}
.insight--低::before {
  background: var(--c-green);
}

.insight__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.insight__title {
  font-size: 14px;
  font-weight: 600;
}
.insight__time {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-3);
}
.insight__body {
  margin: 6px 0 8px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}
.insight__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.insight__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12px;
  color: var(--text-3);
}
.insight__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
</style>