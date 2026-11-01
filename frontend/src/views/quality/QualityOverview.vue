<template>
  <div v-loading="loading">
    <PageHeader title="质量概览" desc="良品率、不合格与主要缺陷来源，聚焦质量改善。">
      <AiButton label="AI 根因分析" type="primary" @click="openRootCause" />
    </PageHeader>

    <!-- ============ KPI ============ -->
    <el-row :gutter="16">
      <el-col :span="6">
        <StatCard
          label="良品率"
          :value="summary ? fmtPct(summary.yieldRate) : '—'"
          :sub="summary?.yieldDelta || '—'"
          :accent="yieldAccent"
          :value-color="yieldColor"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="今日不良"
          :value="summary ? fmtNum(summary.defects) : '—'"
          :sub="summary?.defectsNote || '—'"
          accent="red"
          value-color="var(--c-red)"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="批次合格率"
          :value="summary ? fmtPct(summary.batchRate) : '—'"
          :sub="summary?.batchRateNote || '—'"
          accent="blue"
        />
      </el-col>
      <el-col :span="6">
        <StatCard
          label="本周客诉"
          :value="summary ? summary.complaints : '—'"
          :sub="summary?.complaintsNote || '—'"
          :accent="summary && summary.complaints > 0 ? 'orange' : 'green'"
          :value-color="summary && summary.complaints > 0 ? 'var(--c-orange)' : 'var(--c-green)'"
        />
      </el-col>
    </el-row>

    <!-- ============ AI 摘要：后端未接入时明确说明，不伪造 ============ -->
    <div v-if="summary" class="panel ai-panel">
      <div class="ai-panel__head">
        <span class="ai-panel__title">
          <SparkIcon />
          AI 质量归因
        </span>
        <el-tag v-if="!summary.aiSummary" size="small" type="info" effect="plain">未接入</el-tag>
      </div>
      <p v-if="summary.aiSummary" class="ai-panel__text">{{ summary.aiSummary }}</p>
      <p v-else class="ai-panel__empty">{{ summary.aiSummaryUnavailableReason }}</p>
    </div>

    <!-- ============ 缺陷 Top + 不良构成 ============ -->
    <el-row :gutter="16" class="row-gap">
      <el-col :span="14">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">主要缺陷 Top4</span>
            <span class="panel__hint">{{ defects.scope || '' }}</span>
          </div>
          <div v-show="hasDefects" ref="barRef" class="chart" />
          <el-empty v-if="!hasDefects" description="统计期内无缺陷记录" :image-size="80" />
        </div>
      </el-col>
      <el-col :span="10">
        <div class="panel panel--full">
          <div class="panel__header">
            <span class="panel__title">不良构成</span>
          </div>
          <div v-show="hasDefects" ref="pieRef" class="chart" />
          <el-empty v-if="!hasDefects" description="统计期内无缺陷记录" :image-size="80" />
        </div>
      </el-col>
    </el-row>

    <!-- ============ 三个维度 Tab ============ -->
    <div class="panel row-gap">
      <div class="panel__header">
        <span class="panel__title">缺陷维度分析</span>
        <div class="panel__actions">
          <el-tag v-if="defects.scope" size="small" effect="plain" type="info">{{ defects.scope }}</el-tag>
        </div>
      </div>

      <el-tabs v-model="activeTab">
        <!-- 产线 -->
        <el-tab-pane label="按产线" name="line">
          <el-table :data="defects.byLine" style="width: 100%">
            <el-table-column prop="lineName" label="产线" width="120" />
            <el-table-column label="良品率" width="120">
              <template #default="{ row }">
                <span :style="{ color: rateColor(row.rate) }">{{ fmtPct(row.rate) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="较昨日" width="110">
              <template #default="{ row }">
                <span v-if="row.delta === null || row.delta === undefined" class="text-muted">—</span>
                <span v-else :style="{ color: Number(row.delta) >= 0 ? 'var(--c-green)' : 'var(--c-red)' }">
                  {{ Number(row.delta) >= 0 ? '+' : '' }}{{ row.delta }}pct
                </span>
              </template>
            </el-table-column>
            <el-table-column label="批次合格率" width="130">
              <template #default="{ row }">{{ fmtPct(row.batchRate) }}</template>
            </el-table-column>
            <el-table-column prop="defectQty" label="缺陷数" width="110">
              <template #default="{ row }">{{ fmtNum(row.defectQty) }}</template>
            </el-table-column>
            <el-table-column label="主要缺陷" min-width="160">
              <template #default="{ row }">
                <span v-if="row.mainDefect && row.mainDefect !== '—'">
                  {{ row.mainDefect }}
                  <span class="text-muted">（{{ fmtNum(row.mainDefectQty) }} 件）</span>
                </span>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 产品 -->
        <el-tab-pane label="按产品" name="product">
          <el-table :data="defects.byProduct" style="width: 100%">
            <el-table-column prop="product" label="产品" min-width="180" />
            <el-table-column label="良品率" width="120">
              <template #default="{ row }">
                <span :style="{ color: rateColor(row.rate) }">{{ fmtPct(row.rate) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="产量" width="140">
              <template #default="{ row }">{{ fmtNum(row.qty) }}</template>
            </el-table-column>
            <el-table-column label="缺陷数" min-width="120">
              <template #default="{ row }">{{ fmtNum(row.defectQty) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 设备 -->
        <el-tab-pane label="按设备" name="equipment">
          <el-table :data="defects.byEquipment" style="width: 100%">
            <el-table-column prop="equipment" label="设备/工位" min-width="160" />
            <el-table-column prop="lineName" label="产线" width="110" />
            <el-table-column prop="defectType" label="主要缺陷" width="130" />
            <el-table-column label="缺陷数" width="110">
              <template #default="{ row }">{{ fmtNum(row.qty) }}</template>
            </el-table-column>
            <el-table-column label="不良贡献" width="140">
              <template #default="{ row }">
                <div class="share-cell">
                  <div class="share-cell__bar">
                    <i :style="{ width: Math.min(row.share, 100) + '%' }" />
                  </div>
                  <span>{{ fmtPct(row.share) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="健康度" width="110">
              <template #default="{ row }">
                <span :style="{ color: healthColor(row.health) }">{{ row.health }}</span>
              </template>
            </el-table-column>
            <el-table-column label="风险 / 状态" min-width="150">
              <template #default="{ row }">
                <StatusTag :text="row.riskLevel || '—'" :map="RISK_MAP" />
                <StatusTag :text="row.status || '—'" :map="STATUS_MAP" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="goEquipment(row.equipmentId)">查看设备</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- ============ 根因分析抽屉 ============ -->
    <DetailDrawer v-model="rcVisible" title="根因分析" subtitle="从异常指标出发，逐层定位到具体设备与工序。" width="620px">
      <div class="rc">
        <div class="rc__formula">
          <span class="rc__label">当前良品率</span>
          <span class="rc__value" :style="{ color: yieldColor }">{{ summary ? fmtPct(summary.yieldRate) : '—' }}</span>
          <span class="rc__delta text-muted">{{ summary?.yieldDelta || '' }}</span>
        </div>

        <div class="rc__section">
          <div class="rc__title">分析链路</div>
          <ol class="rc__steps">
            <li v-for="(s, i) in ROOT_CAUSE_STEPS" :key="i">
              <span class="rc__step-idx">{{ i + 1 }}</span>
              <div class="rc__step-body">
                <div class="rc__step-title">{{ s.title }}</div>
                <div class="rc__step-desc">{{ s.desc }}</div>
              </div>
            </li>
          </ol>
        </div>

        <div class="rc__section">
          <div class="rc__title">数据支撑（近 {{ FIXED_DAYS }} 天真实统计）</div>
          <div class="rc__facts">
            <div class="rc__fact">
              <span class="rc__fact-label">首要缺陷类型</span>
              <span class="rc__fact-value">
                {{ defects.top[0]?.type || '—' }}
                <span class="text-muted">{{ defects.top[0] ? `（${fmtNum(defects.top[0].qty)} 件，占 ${fmtPct(defects.top[0].rate)}）` : '' }}</span>
              </span>
            </div>
            <div class="rc__fact">
              <span class="rc__fact-label">最高贡献设备</span>
              <span class="rc__fact-value">
                {{ defects.byEquipment[0]?.equipment || '—' }}
                <span class="text-muted">{{ defects.byEquipment[0] ? `（${fmtNum(defects.byEquipment[0].qty)} 件，占 ${fmtPct(defects.byEquipment[0].share)}）` : '' }}</span>
              </span>
            </div>
            <div class="rc__fact">
              <span class="rc__fact-label">良品率最低产线</span>
              <span class="rc__fact-value">
                {{ lowestLine?.lineName || '—' }}
                <span class="text-muted">{{ lowestLine ? `（${fmtPct(lowestLine.rate)}）` : '' }}</span>
              </span>
            </div>
          </div>
        </div>

        <el-alert
          v-if="!summary?.aiSummary"
          type="info"
          :closable="false"
          show-icon
          class="rc__alert"
          title="第 5 步「AI 根因分析」暂不可用"
          :description="summary?.aiSummaryUnavailableReason || 'AI 服务未接入'"
        />
      </div>

      <template #footer>
        <el-button @click="rcVisible = false">关闭</el-button>
        <el-button type="primary" @click="goCopilot">去 Copilot 追问</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import PageHeader from '../../components/PageHeader.vue'
import StatCard from '../../components/StatCard.vue'
import StatusTag from '../../components/StatusTag.vue'
import AiButton from '../../components/AiButton.vue'
import SparkIcon from '../../components/SparkIcon.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { fetchQualityDefects, fetchQualitySummary } from '../../api/quality'
import type { QualityDefects, QualitySummary } from '../../api/quality'

defineOptions({ name: 'QualityOverview' })

const router = useRouter()

/** 缺陷统计固定窗口（与后端默认一致），页面上不提供切换，避免口径被随意改动 */
const FIXED_DAYS = 30

const loading = ref(false)
const summary = ref<QualitySummary | null>(null)
const defects = reactive<QualityDefects>({
  top: [],
  byLine: [],
  byProduct: [],
  byEquipment: [],
  scope: ''
})

const activeTab = ref<'line' | 'product' | 'equipment'>('line')
const barRef = ref<HTMLElement | null>(null)
const pieRef = ref<HTMLElement | null>(null)
let barChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null

const COLORS = ['#f53f3f', '#ff7d00', '#165dff', '#86909c', '#00b42a', '#722ed1']

/** StatusTag 的 map 值需要带 `tag-dot--` 前缀（见 utils/dict.ts 的 cls()） */
const RISK_MAP: Record<string, string> = {
  高: 'tag-dot--red',
  中: 'tag-dot--orange',
  低: 'tag-dot--green'
}
const STATUS_MAP: Record<string, string> = {
  正常: 'tag-dot--green',
  预警: 'tag-dot--orange',
  故障: 'tag-dot--red',
  离线: 'tag-dot--gray'
}

const ROOT_CAUSE_STEPS = [
  { title: '质量异常识别', desc: '从良品率偏离目标、批次不合格集中的指标切入。' },
  { title: '数据分析', desc: '按缺陷类型 / 产线 / 产品 / 设备四个维度交叉拆解，定位异常集中在哪一端。' },
  { title: '历史案例对比', desc: '与同期历史数据横向对照，判断是偶发波动还是持续性劣化。' },
  { title: 'AI 根因分析', desc: '结合设备运行参数与工艺设定，推导最可能的根因假设。' },
  { title: '解决方案建议', desc: '输出可执行的改善动作与验证指标。' }
]

const hasDefects = computed(() => defects.top.length > 0)

/** 良品率颜色：≥98 绿，≥96 橙，其余红 */
const yieldColor = computed(() => (summary.value ? rateColor(summary.value.yieldRate) : 'var(--text-1)'))

const yieldAccent = computed<'green' | 'orange' | 'red' | 'blue'>(() => {
  if (!summary.value) return 'blue'
  const r = summary.value.yieldRate
  if (r >= 98) return 'green'
  if (r >= 96) return 'orange'
  return 'red'
})

/** 良品率最低的产线，供根因分析抽屉展示 */
const lowestLine = computed(() => {
  const list = defects.byLine.filter((l) => Number.isFinite(l.rate))
  if (list.length === 0) return null
  return list.reduce((a, b) => (a.rate <= b.rate ? a : b))
})

const rcVisible = ref(false)

function rateColor(rate: number): string {
  if (!Number.isFinite(rate)) return 'var(--text-1)'
  if (rate >= 98) return 'var(--c-green)'
  if (rate >= 96) return 'var(--c-orange)'
  return 'var(--c-red)'
}

function healthColor(h: number): string {
  if (h >= 90) return 'var(--c-green)'
  if (h >= 80) return 'var(--c-orange)'
  return 'var(--c-red)'
}

function fmtPct(v: number): string {
  if (!Number.isFinite(v)) return '—'
  return `${v.toFixed(2).replace(/\.00$/, '')}%`
}

function fmtNum(v: number): string {
  if (!Number.isFinite(v)) return '—'
  return v.toLocaleString('zh-CN')
}

function renderBar() {
  if (!barRef.value || !hasDefects.value) return
  barChart = echarts.init(barRef.value)
  barChart.setOption({
    grid: { left: 8, right: 48, top: 16, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (ps: unknown) => {
        const p = (ps as Array<{ dataIndex: number }>)[0]
        const d = defects.top[p.dataIndex]
        if (!d) return ''
        return `${d.type}<br/>缺陷 ${fmtNum(d.qty)} 件<br/>构成占比 ${fmtPct(d.rate)}<br/>主要产线 ${d.lineName}`
      }
    },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#86909c', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: defects.top.map((d) => d.type),
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: { color: '#4e5969', fontSize: 12 }
    },
    series: [
      {
        name: '缺陷数',
        type: 'bar',
        data: defects.top.map((d, i) => ({
          value: d.qty,
          itemStyle: { color: COLORS[i] ?? '#86909c', borderRadius: [0, 3, 3, 0] }
        })),
        barWidth: 18,
        label: {
          show: true,
          position: 'right',
          color: '#4e5969',
          fontSize: 12,
          formatter: (p: { dataIndex: number }) => {
            const d = defects.top[p.dataIndex]
            return d ? `${fmtNum(d.qty)} (${fmtPct(d.rate)})` : ''
          }
        }
      }
    ]
  })
}

function renderPie() {
  if (!pieRef.value || !hasDefects.value) return
  pieChart = echarts.init(pieRef.value)
  pieChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (p: { name: string; value: number; percent: number }) =>
        `${p.name}<br/>${fmtNum(p.value)} 件（${p.percent}%）`
    },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
    series: [
      {
        type: 'pie',
        radius: ['46%', '68%'],
        center: ['50%', '44%'],
        label: { show: false },
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        data: defects.top.map((d, i) => ({
          name: d.type,
          value: d.qty,
          itemStyle: { color: COLORS[i] ?? '#86909c' }
        }))
      }
    ]
  })
}

function onResize() {
  barChart?.resize()
  pieChart?.resize()
}

/** 拉取真实数据。任何一个接口失败都不吞异常，由 request.ts 统一提示。 */
async function load() {
  loading.value = true
  try {
    const [s, d] = await Promise.all([
      fetchQualitySummary({ days: FIXED_DAYS }),
      fetchQualityDefects({ days: FIXED_DAYS })
    ])
    summary.value = s
    defects.top = d.top
    defects.byLine = d.byLine
    defects.byProduct = d.byProduct
    defects.byEquipment = d.byEquipment
    defects.scope = d.scope

    await nextTick()
    barChart?.dispose()
    pieChart?.dispose()
    barChart = null
    pieChart = null
    renderBar()
    renderPie()
  } finally {
    loading.value = false
  }
}

function openRootCause() {
  rcVisible.value = true
}

function goEquipment(id: number) {
  if (!id) return
  router.push(`/equipment/list`)
}

function goCopilot() {
  rcVisible.value = false
  router.push('/copilot/chat')
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  void load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  barChart?.dispose()
  pieChart?.dispose()
  barChart = null
  pieChart = null
})
</script>

<style scoped>
.row-gap {
  margin-top: 16px;
}
.panel--full {
  height: 100%;
}
.panel__hint {
  font-size: 12px;
  color: var(--text-3);
}
.panel__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chart {
  width: 100%;
  height: 260px;
}
.text-muted {
  color: var(--text-3);
}

/* ---------- AI 摘要 ---------- */
.ai-panel {
  margin-top: 16px;
}
.ai-panel__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ai-panel__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}
.ai-panel__text {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--text-2);
}
.ai-panel__empty {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--text-3);
}

/* ---------- 不良贡献进度条 ---------- */
.share-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.share-cell__bar {
  flex: 1;
  height: 6px;
  min-width: 40px;
  border-radius: 3px;
  background: var(--bg-3, #f2f3f5);
  overflow: hidden;
}
.share-cell__bar i {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #ff7d00, #f53f3f);
}

/* ---------- 根因分析抽屉 ---------- */
.rc__formula {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 8px;
  background: var(--bg-2, #f7f8fa);
}
.rc__label {
  font-size: 13px;
  color: var(--text-3);
}
.rc__value {
  font-size: 26px;
  font-weight: 600;
}
.rc__delta {
  font-size: 12px;
}
.rc__section {
  margin-top: 22px;
}
.rc__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 12px;
}
.rc__steps {
  margin: 0;
  padding: 0;
  list-style: none;
}
.rc__steps li {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-left: 2px solid var(--border-2, #e5e6eb);
  padding-left: 16px;
  position: relative;
}
.rc__step-idx {
  flex: none;
  width: 20px;
  height: 20px;
  margin-left: -27px;
  border-radius: 50%;
  background: var(--brand, #165dff);
  color: #fff;
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}
.rc__step-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
}
.rc__step-desc {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-3);
}
.rc__facts {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rc__fact {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}
.rc__fact-label {
  color: var(--text-3);
  flex: none;
}
.rc__fact-value {
  color: var(--text-1);
  text-align: right;
}
.rc__alert {
  margin-top: 20px;
}
</style>