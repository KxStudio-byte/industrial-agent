<template>
  <div>
    <PageHeader title="风险预警" desc="按风险等级跟踪设备与产线隐患，处理闭环。">
      <AiButton label="生成预警简报" type="default" @click="report" />
      <AiButton label="问 Copilot" type="primary" @click="router.push('/copilot/chat')" />
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="6">
        <StatCard label="高" :value="stats.高" sub="需 24h 内处理" accent="red" value-color="var(--c-red)" />
      </el-col>
      <el-col :span="6">
        <StatCard label="中" :value="stats.中" sub="本周内处理" accent="orange" value-color="var(--c-orange)" />
      </el-col>
      <el-col :span="6">
        <StatCard label="低" :value="stats.低" sub="观察项" accent="green" value-color="var(--c-green)" />
      </el-col>
      <el-col :span="6">
        <StatCard label="待处理工单" :value="2" sub="已流转 AI 工单" accent="blue" />
      </el-col>
    </el-row>

    <FilterPanel
      v-model:model="filters"
      class="filter-top"
      :fields="[
        { key: 'riskLevel', label: '风险等级', type: 'select', options: options.riskLevel },
        { key: 'objectType', label: '对象类型', type: 'select', options: ['全部', '设备', '产线'] },
        { key: 'keyword', label: '关键词', type: 'input', placeholder: '请输入洞察标题' }
      ]"
      @search="page = 1"
      @reset="reset"
    />

    <div class="panel">
      <el-table :data="filtered" style="width: 100%">
        <el-table-column prop="title" label="预警标题" min-width="220" />
        <el-table-column label="对象" width="150">
          <template #default="{ row }">{{ row.objectName }}</template>
        </el-table-column>
        <el-table-column label="风险等级" width="100">
          <template #default="{ row }">
            <StatusTag :text="row.riskLevel" :map="RISK_CLASS" />
          </template>
        </el-table-column>
        <el-table-column label="概率" width="90">
          <template #default="{ row }">
            <span class="prob">{{ row.probability }}%</span>
          </template>
        </el-table-column>
        <el-table-column prop="aiJudgement" label="AI 判断" min-width="260" show-overflow-tooltip />
        <el-table-column prop="discoverTime" label="发现时间" width="150" />
        <el-table-column prop="agent" label="来源 Agent" width="130" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openInsight(row)">查看</el-button>
            <el-button size="small" type="primary" @click="handle(row)">转工单</el-button>
          </template>
        </el-table-column>
      </el-table>
      <TableFooter v-model:page="page" :total="filtered.length" />
    </div>

    <DetailDrawer v-model="drawerVisible" title="预警详情" subtitle="风险成因、影响范围与建议动作。">
      <div class="detail-list">
        <span class="detail-list__label">预警标题</span>
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
      </div>
      <template #footer>
        <el-button @click="drawerVisible = false">关闭</el-button>
        <el-button type="primary" @click="handle(current)">转维修工单</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatCard from '../../components/StatCard.vue'
import AiButton from '../../components/AiButton.vue'
import StatusTag from '../../components/StatusTag.vue'
import FilterPanel from '../../components/FilterPanel.vue'
import TableFooter from '../../components/TableFooter.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { insights, options } from '../../mock'
import { RISK_CLASS } from '../../utils/dict'
import type { Insight } from '../../types'

const router = useRouter()
const filters = reactive({ riskLevel: '全部', objectType: '全部', keyword: '' })
const page = ref(1)
const drawerVisible = ref(false)
const current = ref<Insight | null>(null)

const stats = computed(() => ({
  高: insights.filter((i) => i.riskLevel === '高').length,
  中: insights.filter((i) => i.riskLevel === '中').length,
  低: insights.filter((i) => i.riskLevel === '低').length
}))

const filtered = computed(() =>
  insights.filter((i) => {
    const okLevel = filters.riskLevel === '全部' || i.riskLevel === filters.riskLevel
    const okType = filters.objectType === '全部' || i.objectType === filters.objectType
    const okKw = !filters.keyword || i.title.includes(filters.keyword)
    return okLevel && okType && okKw
  })
)

function reset() {
  Object.assign(filters, { riskLevel: '全部', objectType: '全部', keyword: '' })
  page.value = 1
}

function openInsight(row: Insight) {
  current.value = row
  drawerVisible.value = true
}

function handle(row: Insight | null) {
  drawerVisible.value = false
  ElMessage.success(`已为「${row?.objectName ?? ''}」创建工单草稿`)
  router.push('/copilot/executions')
}

function report() {
  ElMessage.success('预警简报已生成，可在报表生成中查看')
  router.push('/copilot/reports')
}
</script>

<style scoped>
.filter-top {
  margin-top: 16px;
}
.prob {
  font-weight: 500;
  color: var(--text-1);
}
</style>