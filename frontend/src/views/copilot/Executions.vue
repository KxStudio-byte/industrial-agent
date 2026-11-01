<template>
  <div>
    <PageHeader title="操作执行" desc="Agent 生成的工单草稿与设备动作需人工确认后才真正下发，确保每一次 AI 决策可追溯。">
      <AiButton label="查看执行表" type="primary" @click="openExecutionTable" />
    </PageHeader>

    <el-row :gutter="16" class="kpi-row">
      <el-col v-for="k in kpis" :key="k.label" :span="6">
        <StatCard :label="k.label" :value="k.value" :sub="k.sub" :accent="k.accent" :value-color="k.color" />
      </el-col>
    </el-row>

    <div class="panel">
      <div class="toolbar">
        <div class="toolbar__field">
          <span class="toolbar__label">确认状态</span>
          <el-select v-model="filters.status" style="width: 150px">
            <el-option v-for="s in execStatusOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
        <span class="toolbar__divider" />
        <div class="toolbar__field">
          <span class="toolbar__label">来源智能体</span>
          <el-select v-model="filters.agentName" style="width: 190px">
            <el-option v-for="a in agentOptions" :key="a" :label="a" :value="a" />
          </el-select>
        </div>
        <span class="toolbar__divider" />
        <div class="toolbar__field">
          <span class="toolbar__label">关键词</span>
          <el-input
            v-model="filters.keyword"
            placeholder="草稿编号 / 设备名称"
            clearable
            style="width: 200px"
            @keyup.enter="search"
          />
        </div>
        <div class="toolbar__actions">
          <el-button type="primary" @click="search">查询</el-button>
          <el-button class="white-btn" @click="reset">重置</el-button>
        </div>
      </div>

      <el-table :data="rows" style="width: 100%">
        <el-table-column prop="execNo" label="草稿编号" width="170" />
        <el-table-column prop="agentName" label="来源智能体" width="150" />
        <el-table-column prop="equipmentName" label="设备名称" width="130" />
        <el-table-column prop="faultType" label="故障类型" width="110" />
        <el-table-column label="优先级" width="100">
          <template #default="{ row }">
            <StatusTag :text="row.priority" :map="PRIORITY_CLASS" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="确认状态" width="120">
          <template #default="{ row }">
            <StatusTag :text="row.status" :map="RUN_STATUS_CLASS" />
          </template>
        </el-table-column>
        <el-table-column prop="woNo" label="流转工单号" width="170" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button
              v-if="row.status === '待确认'"
              link
              type="primary"
              @click="confirmDraft(row)"
            >
              确认流转
            </el-button>
            <el-button
              v-else-if="row.status === '已确认'"
              link
              type="primary"
              @click="goWorkOrder"
            >
              前往维修工单
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <TableFooter :total="rows.length" :page="page" @update:page="(v: number) => (page = v)" />
    </div>

    <!-- 工单草稿详情 -->
    <DetailDrawer
      v-model="detailVisible"
      title="工单草稿"
      :subtitle="current ? `来源：${current.agentName} · ${current.draftNo}` : ''"
    >
      <div v-if="current" class="detail-list">
        <span class="detail-list__label">来源智能体</span>
        <span class="detail-list__value">{{ current.agentName }}</span>
        <span class="detail-list__label">草稿编号</span>
        <span class="detail-list__value">{{ current.draftNo }}</span>
        <span class="detail-list__label">设备名称</span>
        <span class="detail-list__value">{{ current.equipmentName }}</span>
        <span class="detail-list__label">故障类型</span>
        <span class="detail-list__value">{{ current.faultType }}</span>
        <span class="detail-list__label">优先级</span>
        <span class="detail-list__value">
          <StatusTag :text="current.priority" :map="PRIORITY_CLASS" />
        </span>
        <span class="detail-list__label">故障描述</span>
        <span class="detail-list__value">{{ current.description }}</span>
        <span class="detail-list__label">建议负责人</span>
        <span class="detail-list__value">{{ current.assignee }}</span>
        <span class="detail-list__label">创建时间</span>
        <span class="detail-list__value">{{ current.createTime }}</span>
        <span class="detail-list__label">确认状态</span>
        <span class="detail-list__value">
          <StatusTag :text="current.status" :map="RUN_STATUS_CLASS" />
        </span>
      </div>

      <div class="ai-hint drawer-hint">
        <SparkIcon :size="13" />
        <span>
          该草稿由 {{ current?.agentName }} 依据 IoT 实时数据与历史案例生成，确认后将自动创建维修工单并通知负责人。
        </span>
      </div>

      <template #footer>
        <el-button style="flex: 1" @click="reject">驳回</el-button>
        <el-button type="primary" style="flex: 1" @click="confirmDraft(current!)">确认并流转</el-button>
      </template>
    </DetailDrawer>

    <!-- 驳回 -->
    <el-dialog v-model="rejectVisible" title="驳回工单草稿" width="460px">
      <div class="reject-tip">
        驳回后 {{ current?.draftNo }} 将被标记为已驳回，Agent 会记录原因用于后续决策优化。
      </div>
      <el-form label-position="top">
        <el-form-item label="驳回原因">
          <el-input v-model="rejectReason" type="textarea" :rows="4" placeholder="请填写驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 执行表 -->
    <DetailDrawer v-model="execVisible" title="执行表" subtitle="所有 Copilot / Agent 动作的执行流水" width="640px">
      <el-table :data="executions" size="small" style="width: 100%">
        <el-table-column prop="execNo" label="编号" width="150" />
        <el-table-column prop="agentName" label="来源" width="140" />
        <el-table-column prop="actionType" label="动作" width="120" />
        <el-table-column prop="equipmentName" label="对象" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :text="row.status" :map="EXEC_STATUS_CLASS" />
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="执行人" width="90" />
      </el-table>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import AiButton from '../../components/AiButton.vue'
import StatCard from '../../components/StatCard.vue'
import StatusTag from '../../components/StatusTag.vue'
import TableFooter from '../../components/TableFooter.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import SparkIcon from '../../components/SparkIcon.vue'
import { executions, woDrafts } from '../../mock'
import { EXEC_STATUS_CLASS, PRIORITY_CLASS, RUN_STATUS_CLASS } from '../../utils/dict'
import type { WoDraft } from '../../types'

const router = useRouter()

const page = ref(1)
const execStatusOptions = ['全部', '待确认', '已确认', '已驳回']
const agentOptions = computed(() => ['全部', ...Array.from(new Set(woDrafts.map((d) => d.agentName)))])

const filters = reactive({ status: '全部', agentName: '全部', keyword: '' })
const applied = reactive({ status: '全部', agentName: '全部', keyword: '' })

const kpis = computed(() => [
  {
    label: '待确认草稿',
    value: woDrafts.filter((d) => d.status === '待确认').length,
    sub: '需人工确认后流转',
    accent: 'orange' as const,
    color: 'var(--c-orange)'
  },
  {
    label: '本周已确认',
    value: woDrafts.filter((d) => d.status === '已确认').length,
    sub: '9/1 至今累计 26 条',
    accent: 'green' as const,
    color: 'var(--c-green)'
  },
  {
    label: '已驳回',
    value: woDrafts.filter((d) => d.status === '已驳回').length,
    sub: '驳回原因已回流 Agent',
    accent: 'red' as const,
    color: 'var(--c-red)'
  },
  {
    label: '确认及时率',
    value: '94.2%',
    sub: '平均确认耗时 12 分钟',
    accent: 'blue' as const,
    color: 'var(--text-1)'
  }
])

const rows = computed(() =>
  woDrafts.filter((d) => {
    if (applied.status !== '全部' && d.status !== applied.status) return false
    if (applied.agentName !== '全部' && d.agentName !== applied.agentName) return false
    if (
      applied.keyword &&
      !d.draftNo.includes(applied.keyword) &&
      !d.equipmentName.includes(applied.keyword)
    )
      return false
    return true
  })
)

function search() {
  Object.assign(applied, filters)
  page.value = 1
}

function reset() {
  Object.assign(filters, { status: '全部', agentName: '全部', keyword: '' })
  search()
}

/* ---------- 详情 ---------- */
const detailVisible = ref(false)
const current = ref<WoDraft | null>(null)

function openDetail(row: WoDraft) {
  current.value = row
  detailVisible.value = true
}

function confirmDraft(row: WoDraft) {
  if (!row) return
  const target = woDrafts.find((d) => d.id === row.id)
  if (target) {
    target.status = '已确认'
    target.woNo = `WO-20260907-${String(target.id).padStart(3, '0')}`
  }
  detailVisible.value = false
  ElMessage.success('已确认并流转为维修工单')
}

/* ---------- 驳回 ---------- */
const rejectVisible = ref(false)
const rejectReason = ref('')

function reject() {
  rejectReason.value = ''
  rejectVisible.value = true
}

function submitReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  const target = woDrafts.find((d) => d.id === current.value?.id)
  if (target) target.status = '已驳回'
  rejectVisible.value = false
  detailVisible.value = false
  ElMessage.success('草稿已驳回')
}

/* ---------- 其它 ---------- */
const execVisible = ref(false)

function openExecutionTable() {
  execVisible.value = true
}

function goWorkOrder() {
  router.push('/equipment/work-orders')
}
</script>

<style scoped>
.kpi-row {
  margin-bottom: 16px;
}
.white-btn {
  background: #fff;
}
.toolbar {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.toolbar__field {
  display: flex;
  flex-direction: column;
}
.toolbar__label {
  font-size: 13px;
  color: var(--text-3);
  line-height: 20px;
  margin-bottom: 6px;
}
.toolbar__divider {
  width: 1px;
  align-self: stretch;
  background: var(--line-light);
  margin-bottom: 4px;
}
.toolbar__actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.drawer-hint {
  display: flex;
  gap: 6px;
  margin-top: 16px;
}
.reject-tip {
  font-size: 13px;
  color: var(--text-3);
  line-height: 20px;
  margin-bottom: 14px;
}
</style>