<template>
  <div>
    <PageHeader title="Agent运行记录" desc="审计 Agent 调用链、决策、结果与成本。">
      <AiButton label="导出记录" type="default" @click="exportRecords" />
    </PageHeader>

    <div class="kpi-grid">
      <StatCard label="今日运行" :value="String(agentRunLogs.length)" unit="次" note="全部智能体合计" />
      <StatCard label="执行成功" :value="String(successCount)" unit="次" :note="successRateNote" />
      <StatCard label="Token 消耗" value="35.5k" note="今日累计" />
      <StatCard label="AI 成本" value="¥2.31" note="今日累计" />
    </div>

    <FilterPanel
      v-model:model="filters"
      :fields="[
        { key: 'agentName', label: '智能体', type: 'select', options: agentOptions },
        { key: 'result', label: '运行结果', type: 'select', options: ['全部', '执行成功', '等待确认', '执行失败'] },
        { key: 'keyword', label: '任务名称', type: 'input', placeholder: '请输入任务名称' }
      ]"
      @search="page = 1"
      @reset="reset"
    />

    <div class="panel">
      <div class="panel__header">
        <span class="panel__title">运行明细</span>
        <span class="panel__extra">点击行查看完整调用链</span>
      </div>
      <el-table :data="paged" style="width: 100%" class="clickable" @row-click="openDetail">
        <el-table-column prop="time" label="运行时间" width="150" />
        <el-table-column prop="agentName" label="智能体" width="140" />
        <el-table-column prop="taskName" label="任务名称" min-width="180" />
        <el-table-column prop="tools" label="调用工具" min-width="180" show-overflow-tooltip />
        <el-table-column label="结果" width="110">
          <template #default="{ row }">
            <StatusTag :text="row.result" :map="RUN_RESULT_CLASS" />
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="耗时" width="90" />
        <el-table-column prop="token" label="Token" width="90" />
        <el-table-column prop="cost" label="成本" width="90" />
      </el-table>
      <TableFooter v-model:page="page" :total="filtered.length" />
    </div>

    <DetailDrawer v-model="visible" title="运行调用链" subtitle="工具调用时序、模型输出与成本记账。">
      <template v-if="current">
        <div class="detail-list">
          <span class="detail-list__label">运行时间</span>
          <span class="detail-list__value">{{ current.time }}</span>
          <span class="detail-list__label">智能体</span>
          <span class="detail-list__value">{{ current.agentName }}</span>
          <span class="detail-list__label">任务名称</span>
          <span class="detail-list__value">{{ current.taskName }}</span>
          <span class="detail-list__label">结果</span>
          <span class="detail-list__value">
            <StatusTag :text="current.result" :map="RUN_RESULT_CLASS" />
          </span>
          <span class="detail-list__label">成本</span>
          <span class="detail-list__value">{{ current.duration }} · {{ current.token }} Token · {{ current.cost }}</span>
        </div>

        <div class="panel panel--flat">
          <div class="panel__header">
            <span class="panel__title">调用链</span>
          </div>
          <el-steps direction="vertical" :active="steps.length" finish-status="success">
            <el-step v-for="(s, i) in steps" :key="i" :title="s.title" :description="s.desc" />
          </el-steps>
        </div>
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" @click="rerun">重新运行</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import AiButton from '../../components/AiButton.vue'
import StatCard from '../../components/StatCard.vue'
import StatusTag from '../../components/StatusTag.vue'
import FilterPanel from '../../components/FilterPanel.vue'
import TableFooter from '../../components/TableFooter.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { agentRunLogs } from '../../mock'
import { RUN_RESULT_CLASS } from '../../utils/dict'
import type { AgentRunLog } from '../../types'

const filters = reactive({ agentName: '全部', result: '全部', keyword: '' })
const page = ref(1)
const visible = ref(false)
const current = ref<AgentRunLog | null>(null)

const agentOptions = computed(() => ['全部', ...Array.from(new Set(agentRunLogs.map((l) => l.agentName)))])
const successCount = computed(() => agentRunLogs.filter((l) => l.result === '执行成功').length)
const successRateNote = computed(() => `成功率 ${((successCount.value / agentRunLogs.length) * 100).toFixed(1)}%`)

const filtered = computed(() =>
  agentRunLogs.filter((l) => {
    const okAgent = filters.agentName === '全部' || l.agentName === filters.agentName
    const okResult = filters.result === '全部' || l.result === filters.result
    const okKw = !filters.keyword || l.taskName.includes(filters.keyword)
    return okAgent && okResult && okKw
  })
)
const paged = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10))

const steps = computed(() => {
  if (!current.value) return []
  const tools = current.value.tools.split(/[,，]\s*/).filter(Boolean)
  return [
    { title: '接收任务', desc: `${current.value.agentName} 于 ${current.value.time} 被触发` },
    ...tools.map((t) => ({ title: `调用工具 · ${t}`, desc: '工具返回结构化结果' })),
    { title: '模型推理', desc: `消耗 ${current.value.token} Token，耗时 ${current.value.duration}` },
    {
      title: current.value.result,
      desc:
        current.value.result === '等待确认'
          ? '等待人工确认后继续执行'
          : current.value.result === '执行失败'
            ? '执行失败，已记录错误信息'
            : '结果已写入业务库并通知相关人员'
    }
  ]
})

function reset() {
  Object.assign(filters, { agentName: '全部', result: '全部', keyword: '' })
  page.value = 1
}

function openDetail(row: AgentRunLog) {
  current.value = row
  visible.value = true
}

function rerun() {
  ElMessage.success(`已重新发起：${current.value?.taskName}`)
  visible.value = false
}

function exportRecords() {
  ElMessage.success('运行记录已导出')
}
</script>

<style scoped>
.panel--flat {
  border: 1px solid var(--border);
  box-shadow: none;
  padding: 12px;
  margin-bottom: 12px;
}
.clickable :deep(.el-table__row) {
  cursor: pointer;
}
</style>