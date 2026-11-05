<template>
  <div v-loading="loading">
    <PageHeader title="AI 节能优化" desc="查看节能优化执行列表与执行结果。">
      <AiButton label="执行优化" type="primary" :disabled="!nextPending" @click="openConfirm(nextPending!)" />
      <AiButton label="返回能耗分析" plain @click="router.push('/energy/analysis')" />
    </PageHeader>

    <!-- 筛选：执行状态 + 能源介质 -->
    <FilterPanel
      v-model:model="filters"
      :fields="[
        { key: 'status', label: '执行状态', type: 'select', options: statusOptions },
        { key: 'medium', label: '能源介质', type: 'select', options: mediumOptions }
      ]"
      @search="load"
      @reset="reset"
    />

    <div class="panel row-gap">
      <el-table :data="filtered" style="width: 100%" empty-text="没有符合条件的节能方案">
        <el-table-column prop="planCode" label="方案编号" width="150" />
        <el-table-column prop="planName" label="方案名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="location" label="位置" min-width="160" show-overflow-tooltip />
        <el-table-column prop="medium" label="介质" width="80" />
        <el-table-column label="执行状态" width="106">
          <template #default="{ row }">
            <StatusTag :text="row.status" :map="ENERGY_PLAN_STATUS_CLASS" />
          </template>
        </el-table-column>
        <el-table-column label="预计节约" width="126">
          <template #default="{ row }">¥{{ fmt(row.saving) }} / 月</template>
        </el-table-column>
        <el-table-column label="执行结果" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.result ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="执行时间" width="160">
          <template #default="{ row }">{{ row.execTime ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="130" />
        <el-table-column label="操作" width="168" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === '待执行'"
              size="small"
              type="primary"
              @click="openConfirm(row)"
            >
              执行优化
            </el-button>
            <el-button size="small" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-foot">
        <span>共 {{ filtered.length }} 条</span>
        <span>第 1 / 1 页</span>
      </div>
    </div>

    <!-- 执行确认抽屉（原型 drawer-energy-optimize） -->
    <DetailDrawer
      v-model="confirmVisible"
      title="AI 节能优化"
      subtitle="确认方案后下发执行，结果写入执行列表。"
    >
      <div v-if="current" class="detail-list">
        <span class="detail-list__label">来源</span>
        <span class="detail-list__value">{{ current.source }} · 方案编号 {{ current.planCode }}</span>

        <span class="detail-list__label">位置</span>
        <span class="detail-list__value">{{ current.location }}</span>

        <span class="detail-list__label">能源介质</span>
        <span class="detail-list__value">{{ current.medium }}</span>

        <span class="detail-list__label">异常判断</span>
        <span class="detail-list__value">{{ current.reason }}</span>

        <span class="detail-list__label">AI 建议</span>
        <span class="detail-list__value">{{ current.advice }}</span>

        <span class="detail-list__label">影响范围</span>
        <span class="detail-list__value">{{ current.scope }}</span>
      </div>
      <p class="drawer-foot-note">执行后记录进入「AI 节能优化」列表，可回看实际负荷与节约结果。</p>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="executing" @click="execute">执行优化</el-button>
      </template>
    </DetailDrawer>

    <!-- 只读详情抽屉 -->
    <DetailDrawer v-model="detailVisible" title="方案详情" subtitle="方案依据、执行范围与结果。">
      <div v-if="current" class="detail-list">
        <span class="detail-list__label">方案编号</span>
        <span class="detail-list__value">{{ current.planCode }}</span>

        <span class="detail-list__label">方案名称</span>
        <span class="detail-list__value">{{ current.planName }}</span>

        <span class="detail-list__label">执行状态</span>
        <span class="detail-list__value">
          <StatusTag :text="current.status" :map="ENERGY_PLAN_STATUS_CLASS" />
        </span>

        <span class="detail-list__label">位置 / 介质</span>
        <span class="detail-list__value">{{ current.location }} · {{ current.medium }}</span>

        <span class="detail-list__label">预计节约</span>
        <span class="detail-list__value">¥{{ fmt(current.saving) }} / 月（约 ¥{{ fmt(current.dailySaving) }} / 日）</span>

        <span class="detail-list__label">异常判断</span>
        <span class="detail-list__value">{{ current.reason }}</span>

        <span class="detail-list__label">AI 建议</span>
        <span class="detail-list__value">{{ current.advice }}</span>

        <span class="detail-list__label">影响范围</span>
        <span class="detail-list__value">{{ current.scope }}</span>

        <span class="detail-list__label">执行结果</span>
        <span class="detail-list__value">{{ current.result ?? '—' }}</span>

        <span class="detail-list__label">执行时间</span>
        <span class="detail-list__value">{{ current.execTime ?? '—' }}</span>

        <span class="detail-list__label">生成时间</span>
        <span class="detail-list__value">{{ current.createTime }}</span>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import AiButton from '../../components/AiButton.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import FilterPanel from '../../components/FilterPanel.vue'
import { ENERGY_PLAN_STATUS_CLASS } from '../../utils/dict'
import { fetchEnergySavingPlanDetail, fetchEnergySavingPlans, type EnergySavingPlan } from '../../api/energy'

const router = useRouter()

const loading = ref(false)
const executing = ref(false)
const plans = shallowRef<EnergySavingPlan[]>([])

const statusOptions = ['全部', '待执行', '已完成', '执行失败']
const mediumOptions = ['全部', '电', '水', '气', '蒸汽']
const filters = reactive({ status: '全部', medium: '全部' })

const confirmVisible = ref(false)
const detailVisible = ref(false)
const current = shallowRef<EnergySavingPlan | null>(null)

function fmt(v: number): string {
  return Math.round(v).toLocaleString('en-US')
}

const filtered = computed(() =>
  plans.value.filter(
    (p) =>
      (filters.status === '全部' || p.status === filters.status) &&
      (filters.medium === '全部' || p.medium === filters.medium)
  )
)

/** 顶部「执行优化」按钮针对第一条待执行方案 */
const nextPending = computed(() => plans.value.find((p) => p.status === '待执行') ?? null)

function openConfirm(row: EnergySavingPlan) {
  current.value = row
  confirmVisible.value = true
}

async function openDetail(row: EnergySavingPlan) {
  current.value = row
  detailVisible.value = true
  try {
    current.value = await fetchEnergySavingPlanDetail(row.id)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '方案详情加载失败')
  }
}

/**
 * 执行优化。
 * <p>
 * ⚠️ 后端目前<b>没有</b>「下发执行」接口（/api/energy 只有 7 个只读 GET），
 * 所以这里不能假装执行成功。只做本地状态提示并明确告知未接入。
 */
async function execute() {
  const plan = current.value
  if (!plan) return
  executing.value = true
  try {
    ElMessage.warning(`「${plan.planName}」下发接口尚未接入后端，未真正执行`)
    confirmVisible.value = false
  } finally {
    executing.value = false
  }
}

function reset() {
  filters.status = '全部'
  filters.medium = '全部'
  load()
}

async function load() {
  loading.value = true
  try {
    const res = await fetchEnergySavingPlans({
      status: filters.status === '全部' ? null : filters.status,
      medium: filters.medium === '全部' ? null : (filters.medium as '电' | '水' | '气' | '蒸汽')
    })
    plans.value = res.list
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '节能方案加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.row-gap {
  margin-top: 16px;
}
.table-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-3);
}
.drawer-foot-note {
  margin: 16px 0 0;
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.7;
}
</style>