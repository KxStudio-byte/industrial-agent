<template>
  <div>
    <PageHeader title="Agent任务" desc="追踪 Agent 任务状态并确认执行。">
      <AiButton label="查看运行记录" type="primary" @click="router.push('/agent/run-logs')" />
    </PageHeader>

    <FilterPanel
      v-model:model="filters"
      :fields="[{ key: 'status', label: '当前状态', type: 'select', options: options.taskStatus }]"
      @search="page = 1"
      @reset="reset"
    />

    <div class="panel">
      <el-table :data="paged" style="width: 100%">
        <el-table-column prop="taskName" label="任务名称" min-width="160" />
        <el-table-column prop="agentName" label="所属Agent" width="150" />
        <el-table-column prop="triggerTime" label="触发时间" width="160" />
        <el-table-column label="当前状态" width="120">
          <template #default="{ row }">
            <StatusTag :text="row.status" :map="RUN_STATUS_CLASS" />
          </template>
        </el-table-column>
        <el-table-column prop="result" label="执行结果" min-width="150" />
        <el-table-column prop="duration" label="耗时" width="90" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <TableFooter v-model:page="page" :total="filtered.length" />
    </div>

    <!-- 任务详情（原型 drawer-agent-task-detail） -->
    <DetailDrawer v-model="visible" title="任务详情" subtitle="确认或查看单次 Agent 任务。">
      <div class="task-head">
        <span class="task-head__name">{{ current?.taskName }}</span>
        <StatusTag v-if="current" :text="current.status" :map="RUN_STATUS_CLASS" />
      </div>

      <div class="detail-list">
        <span class="detail-list__label">所属Agent</span>
        <span class="detail-list__value">{{ current?.agentName }}</span>
        <span class="detail-list__label">触发时间</span>
        <span class="detail-list__value">{{ current?.triggerTime }}</span>
        <span class="detail-list__label">输入</span>
        <span class="detail-list__value">{{ current?.input }}</span>
        <span class="detail-list__label">AI决策</span>
        <span class="detail-list__value">{{ current?.decision }}</span>
      </div>

      <template #footer>
        <template v-if="current?.status === '等待确认'">
          <el-button type="primary" @click="confirm">确认执行</el-button>
          <el-button type="danger" @click="reject">拒绝执行</el-button>
        </template>
        <template v-else-if="current?.status === '执行中'">
          <el-button type="danger" plain @click="reject">终止执行</el-button>
          <el-button @click="visible = false">关闭</el-button>
        </template>
        <el-button v-else @click="visible = false">关闭</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import AiButton from '../../components/AiButton.vue'
import StatusTag from '../../components/StatusTag.vue'
import FilterPanel from '../../components/FilterPanel.vue'
import TableFooter from '../../components/TableFooter.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { agentTasks, options } from '../../mock'
import { RUN_STATUS_CLASS } from '../../utils/dict'
import type { AgentTask } from '../../types'

const router = useRouter()
const filters = reactive({ status: '全部' })
const page = ref(1)
const visible = ref(false)
const current = ref<AgentTask | null>(null)

const filtered = computed(() =>
  agentTasks.filter((t) => filters.status === '全部' || t.status === filters.status)
)
const paged = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10))

function reset() {
  filters.status = '全部'
  page.value = 1
}

function openDetail(row: AgentTask) {
  current.value = row
  visible.value = true
}

function confirm() {
  if (current.value) current.value.status = '已完成'
  visible.value = false
  ElMessage.success('已确认执行，工单已创建')
}

function reject() {
  if (current.value) current.value.status = '执行失败'
  visible.value = false
  ElMessage.warning('已拒绝本次执行')
}
</script>

<style scoped>
.task-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.task-head__name {
  font-size: 14px;
  font-weight: 600;
}
</style>