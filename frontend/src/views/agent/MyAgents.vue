<template>
  <div>
    <PageHeader title="我的Agent" desc="管理已启用的 Agent。行内「查看」或「运行详情」打开当前执行环节抽屉。">
      <AiButton label="创建 Agent" type="primary" @click="create" />
    </PageHeader>

    <FilterPanel
      v-model:model="filters"
      :fields="[{ key: 'status', label: '运行状态', type: 'select', options: options.agentStatus }]"
      @search="page = 1"
      @reset="reset"
    />

    <div class="panel">
      <el-table :data="paged" style="width: 100%">
        <el-table-column prop="name" label="Agent名称" min-width="160" />
        <el-table-column prop="type" label="Agent类型" width="120" />
        <el-table-column label="运行状态" width="120">
          <template #default="{ row }">
            <StatusTag :text="row.status" :map="RUN_STATUS_CLASS" />
          </template>
        </el-table-column>
        <el-table-column prop="permission" label="执行权限" width="160" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看</el-button>
            <el-button size="small" type="primary" plain @click="openRunning(row)">运行详情</el-button>
            <el-button
              size="small"
              :type="row.status === '运行中' ? 'danger' : 'success'"
              text
              @click="toggle(row)"
            >
              {{ row.status === '运行中' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <TableFooter v-model:page="page" :total="filtered.length" />
    </div>

    <!-- 执行环节抽屉：原型 drawer-agent-detail -->
    <DetailDrawer v-model="runningVisible" title="运行详情" subtitle="当前执行环节与工具调用链路。" :width="560">
      <div class="run-head">
        <span class="run-head__name">{{ current?.name }}</span>
        <StatusTag text="执行中" :map="RUN_STATUS_CLASS" />
        <span class="run-head__id">任务 ID：TASK-20260907-{{ current?.id ?? 1 }}</span>
      </div>

      <div class="run-kpis">
        <div class="run-kpi">
          <div class="run-kpi__label">已用时</div>
          <div class="run-kpi__value">18s</div>
        </div>
        <div class="run-kpi">
          <div class="run-kpi__label">已完成环节</div>
          <div class="run-kpi__value">3 / 6</div>
        </div>
        <div class="run-kpi">
          <div class="run-kpi__label">Token 消耗</div>
          <div class="run-kpi__value">4.2k</div>
        </div>
      </div>

      <h4 class="section-title">执行链路</h4>
      <el-timeline>
        <el-timeline-item
          v-for="(step, i) in steps"
          :key="step.name"
          :timestamp="step.time"
          :type="i < 3 ? 'success' : i === 3 ? 'primary' : 'info'"
          :hollow="i > 3"
        >
          <div class="step" :class="{ 'step--active': i === 3 }">
            <span class="step__name">{{ step.name }}</span>
            <span class="step__detail">{{ step.detail }}</span>
          </div>
        </el-timeline-item>
      </el-timeline>

      <h4 class="section-title">工具调用</h4>
      <el-table :data="toolCalls" size="small" style="width: 100%">
        <el-table-column prop="tool" label="工具" width="120" />
        <el-table-column prop="param" label="入参" />
        <el-table-column prop="result" label="结果" width="120" />
      </el-table>

      <template #footer>
        <el-button type="danger" plain @click="runningVisible = false">终止执行</el-button>
        <el-button @click="runningVisible = false">关闭</el-button>
      </template>
    </DetailDrawer>

    <!-- 能力详情抽屉：复用 Agent广场 的结构，这里精简展示 -->
    <DetailDrawer v-model="detailVisible" title="智能体详情" subtitle="能力、适用场景与执行权限。">
      <div class="detail-list">
        <span class="detail-list__label">Agent 名称</span>
        <span class="detail-list__value">{{ current?.name }}</span>
        <span class="detail-list__label">类型</span>
        <span class="detail-list__value">{{ current?.type }}</span>
        <span class="detail-list__label">执行权限</span>
        <span class="detail-list__value">{{ current?.permission }}</span>
        <span class="detail-list__label">能力说明</span>
        <span class="detail-list__value">{{ current?.desc }}</span>
      </div>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import AiButton from '../../components/AiButton.vue'
import StatusTag from '../../components/StatusTag.vue'
import FilterPanel from '../../components/FilterPanel.vue'
import TableFooter from '../../components/TableFooter.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { myAgents, options } from '../../mock'
import { RUN_STATUS_CLASS } from '../../utils/dict'
import type { Agent } from '../../types'

const filters = reactive({ status: '全部' })
const page = ref(1)
const runningVisible = ref(false)
const detailVisible = ref(false)
const current = ref<Agent | null>(null)

const steps = [
  { name: '读取设备实时数据', detail: '拉取 3号注塑机近 24 小时振动 / 温度 / 电流', time: '16:58:02' },
  { name: '异常检测', detail: '振动 7.8mm/s，超过 6.5 阈值，判定为异常', time: '16:58:11' },
  { name: '知识检索', detail: '命中《XX型号注塑机维修手册》第 4.2 节 轴承磨损', time: '16:58:19' },
  { name: '生成解决方案', detail: '正在生成：更换 6208 轴承 + 复测动平衡…', time: '16:58:26' },
  { name: '创建维修工单', detail: '等待人工确认', time: '—' },
  { name: '执行反馈', detail: '等待人工确认', time: '—' }
]

const toolCalls = [
  { tool: '查询设备', param: 'equipment_id=3', result: '成功' },
  { tool: '查询知识库', param: 'keyword=轴承磨损', result: '命中 3 条' },
  { tool: '创建工单', param: 'draft_no=AE-20260907-018', result: '待确认' }
]

const filtered = computed(() =>
  myAgents.filter((a) => filters.status === '全部' || a.status === filters.status)
)
const paged = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10))

function reset() {
  filters.status = '全部'
  page.value = 1
}

function openDetail(row: Agent) {
  current.value = row
  detailVisible.value = true
}

function openRunning(row: Agent) {
  current.value = row
  runningVisible.value = true
}

function toggle(row: Agent) {
  row.status = row.status === '运行中' ? '已停用' : '运行中'
  ElMessage.success(`${row.name} 已${row.status === '运行中' ? '启用' : '停用'}`)
}

function create() {
  ElMessage.success('已进入 Agent 创建向导')
}
</script>

<style scoped>
.run-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.run-head__name {
  font-size: 15px;
  font-weight: 600;
}
.run-head__id {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-3);
}
.run-kpis {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}
.run-kpi {
  border-left: 3px solid var(--c-blue);
  background: #fafbfc;
  border-radius: 4px;
  padding: 8px 10px;
}
.run-kpi__label {
  font-size: 12px;
  color: var(--text-3);
}
.run-kpi__value {
  font-size: 16px;
  font-weight: 600;
}
.step {
  display: flex;
  flex-direction: column;
  font-size: 13px;
}
.step__name {
  font-weight: 500;
}
.step__detail {
  color: var(--text-3);
  font-size: 12px;
}
.step--active .step__name {
  color: var(--brand);
}
</style>