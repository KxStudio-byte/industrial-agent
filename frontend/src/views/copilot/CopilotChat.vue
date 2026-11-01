<template>
  <div>
    <PageHeader title="AI对话" desc="用自然语言驱动全厂数据、知识与 Agent 动作。" />

    <div class="chat-layout">
      <div class="chat-panel">
        <div class="chat-toolbar">
          <el-button size="small" @click="resetSession">新建会话</el-button>
          <el-button size="small" text @click="showHistory = !showHistory">
            {{ showHistory ? '收起历史' : '历史记录' }}
          </el-button>
        </div>

        <div v-if="showHistory" class="history">
          <div v-for="h in copilotHistory" :key="h.id" class="history__item">
            <span class="history__q">{{ h.q }}</span>
            <span class="history__t">{{ h.time }}</span>
          </div>
        </div>

        <div ref="scrollRef" class="messages">
          <div v-for="m in messages" :key="m.id" class="msg" :class="`msg--${m.role}`">
            <div class="msg__avatar">{{ m.role === 'user' ? '我' : 'AI' }}</div>
            <div class="msg__body">
              <div class="msg__bubble">
                <p v-for="(line, i) in m.content.split('\n')" :key="i" class="msg__line">{{ line }}</p>
              </div>

              <div v-if="m.thinking?.length" class="thinking">
                <div class="thinking__title">
                  <SparkIcon :size="14" />
                  <span>推理过程</span>
                </div>
                <div v-for="(t, i) in m.thinking" :key="i" class="thinking__step">
                  <span class="thinking__idx">{{ i + 1 }}</span>
                  <span>{{ t }}</span>
                </div>
              </div>

              <div v-if="m.actions?.length" class="actions">
                <span class="actions__label">建议动作</span>
                <el-button
                  v-for="a in m.actions"
                  :key="a.label"
                  size="small"
                  :type="a.primary ? 'primary' : 'default'"
                  @click="runAction(a)"
                >
                  {{ a.label }}
                </el-button>
              </div>
            </div>
          </div>

          <div v-if="sending" class="msg msg--assistant">
            <div class="msg__avatar">AI</div>
            <div class="msg__body">
              <div class="msg__bubble typing"><i /><i /><i /></div>
            </div>
          </div>
        </div>

        <div class="composer">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="例如：3号注塑机最近振动异常偏高，帮我看看下一步该处理什么？"
            @keydown.enter.exact.prevent="send"
          />
          <div class="composer__foot">
            <div class="quick">
              <button v-for="q in chatSuggestions" :key="q" class="quick__item" @click="ask(q)">{{ q }}</button>
            </div>
            <el-button type="primary" :loading="sending" @click="send">发送</el-button>
          </div>
        </div>
      </div>

      <div class="side">
        <div class="panel">
          <div class="panel__header">
            <span class="panel__title">数据看板</span>
            <span class="panel__extra">3号注塑机 · 主轴振动</span>
          </div>
          <div ref="chartRef" class="side__chart" />
          <div class="side__foot">
            <span>数据来源：IoT 网关 · 采样间隔 5min</span>
            <el-button size="small" @click="router.push('/equipment/detail/3')">查看设备详情</el-button>
          </div>
        </div>

        <div class="panel">
          <div class="panel__header">
            <span class="panel__title">当前上下文</span>
          </div>
          <div class="ctx">
            <div class="ctx__row"><span>角色</span><b>设备主管</b></div>
            <div class="ctx__row"><span>关注对象</span><b>3号注塑机</b></div>
            <div class="ctx__row"><span>关联工单</span><b>WO-20260907-018</b></div>
            <div class="ctx__row"><span>可用能力</span><b>7 个 Agent · 12 个工具</b></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import PageHeader from '../../components/PageHeader.vue'
import SparkIcon from '../../components/SparkIcon.vue'
import { chatSuggestions, copilotHistory } from '../../mock'

interface ChatAction {
  label: string
  primary?: boolean
}

interface ChatMsg {
  id: number
  role: 'user' | 'assistant'
  content: string
  thinking?: string[]
  actions?: ChatAction[]
}

const router = useRouter()
const draft = ref('')
const sending = ref(false)
const showHistory = ref(false)
const scrollRef = ref<HTMLElement | null>(null)
const chartRef = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null
let seq = 0

const messages = ref<ChatMsg[]>([
  {
    id: ++seq,
    role: 'assistant',
    content:
      '你好，我是设备主管 Copilot。我已接入 3 条产线、8 台设备的实时数据与知识库。\n你可以直接问：某台设备为什么异常、最近哪些风险要处理、某个工单进度如何，我会给出判断依据和建议动作。'
  },
  {
    id: ++seq,
    role: 'user',
    content: '3号注塑机最近振动异常偏高，帮我看看下一步该处理什么？'
  },
  {
    id: ++seq,
    role: 'assistant',
    content:
      '结论：3号注塑机主轴轴承存在早期点蚀，建议 72 小时内安排停机更换 6208 轴承，并对传动轴复测动平衡。\n判断依据：近 24h 振动有效值 7.8mm/s（阈值 6.5），120~160Hz 倍频能量上升 32%，温度正常，排除润滑与过载因素。',
    thinking: [
      '调用工具「查询设备」：equipment_id=3，拉取近 24h 振动/温度/电流',
      '异常判定：振动 RMS 7.8mm/s > 6.5mm/s，且 120~160Hz 频段能量上升',
      '调用工具「查询知识库」：命中《IM系列注塑机维修手册》4.2 轴承磨损判定',
      '结合历史案例：同型号 2 号设备在相似特征下 5~9 天内出现停机'
    ],
    actions: [{ label: '生成维修工单', primary: true }, { label: '加入今日重点任务' }, { label: '查看相似历史案例' }]
  }
])

function scrollToBottom() {
  nextTick(() => {
    if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  })
}

function ask(text: string) {
  draft.value = text
  send()
}

function send() {
  const text = draft.value.trim()
  if (!text || sending.value) return

  messages.value.push({ id: ++seq, role: 'user', content: text })
  draft.value = ''
  scrollToBottom()

  sending.value = true
  window.setTimeout(() => {
    sending.value = false
    messages.value.push({
      id: ++seq,
      role: 'assistant',
      content: `我已按「${text}」检索了实时数据与知识库。\n当前 3 条产线运行正常，但 3号注塑机（振动 7.8mm/s）与 5号注塑机（电气故障停机）处于关注状态。\n如果你希望，我可以把这两项整理成今日点检任务并派发给对应班组。`,
      thinking: ['解析意图：面向全厂的异常巡检', '调用工具「查询产线实时数据」「查询预警列表」', '汇总 Top2 风险对象并生成建议动作'],
      actions: [{ label: '派发点检任务', primary: true }, { label: '查看预警列表' }]
    })
    scrollToBottom()
  }, 900)
}

function runAction(a: ChatAction) {
  if (a.label.includes('工单')) {
    ElMessage.success('已生成维修工单草稿，请在操作执行中确认')
    router.push('/copilot/executions')
    return
  }
  if (a.label.includes('预警')) {
    router.push('/ai-dashboard/risk-alerts')
    return
  }
  ElMessage.success(`已执行：${a.label}`)
}

function resetSession() {
  messages.value = [
    { id: ++seq, role: 'assistant', content: '新的会话已开始。请告诉我你想了解哪台设备或哪条产线。' }
  ]
  scrollToBottom()
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  const hours = Array.from({ length: 12 }, (_, i) => `${String(i * 2).padStart(2, '0')}:00`)
  const data = [4.1, 4.3, 4.8, 5.2, 5.6, 6.1, 6.4, 6.9, 7.2, 7.5, 7.7, 7.8]

  chart.setOption({
    grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: hours,
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: { color: '#86909c', fontSize: 11, interval: 2 }
    },
    yAxis: {
      type: 'value',
      max: 10,
      name: 'mm/s',
      nameTextStyle: { color: '#86909c', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#86909c', fontSize: 11 }
    },
    series: [
      {
        name: '振动',
        type: 'line',
        smooth: true,
        data,
        symbol: 'circle',
        symbolSize: 5,
        itemStyle: { color: '#f53f3f' },
        lineStyle: { width: 2, color: '#f53f3f' },
        markLine: {
          silent: true,
          symbol: 'none',
          label: { formatter: '阈值 6.5', color: '#ff7d00', fontSize: 11 },
          lineStyle: { color: '#ff7d00', type: 'dashed' },
          data: [{ yAxis: 6.5 }]
        }
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
  scrollToBottom()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<style scoped>
.chat-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}
.chat-panel {
  background: #fff;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  height: calc(100vh - 180px);
  min-height: 560px;
}
.chat-toolbar {
  display: flex;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
}
.history {
  border-bottom: 1px solid var(--border);
  background: #fafbfc;
  max-height: 160px;
  overflow: auto;
}
.history__item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px;
  font-size: 12px;
  color: var(--text-2);
}
.history__q {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history__t {
  flex: none;
  color: var(--text-3);
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 14px;
}
.msg {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}
.msg--user {
  flex-direction: row-reverse;
}
.msg__avatar {
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
  background: var(--c-blue);
}
.msg--user .msg__avatar {
  background: #4e5969;
}
.msg__body {
  max-width: 78%;
  min-width: 0;
}
.msg--user .msg__body {
  display: flex;
  justify-content: flex-end;
}
.msg__bubble {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 22px;
  color: var(--text-1);
}
.msg--user .msg__bubble {
  background: #e8f3ff;
  border-color: #bedaff;
}
.msg__line {
  margin: 0;
}
.typing {
  display: flex;
  gap: 4px;
}
.typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-3);
  animation: blink 1.2s infinite ease-in-out;
}
.typing i:nth-child(2) {
  animation-delay: 0.2s;
}
.typing i:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.25;
  }
  40% {
    opacity: 1;
  }
}
.thinking {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f7f8fa;
}
.thinking__title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--c-blue);
  margin-bottom: 6px;
}
.thinking__step {
  display: flex;
  gap: 6px;
  font-size: 12px;
  line-height: 19px;
  color: var(--text-2);
}
.thinking__idx {
  flex: none;
  width: 16px;
  height: 16px;
  margin-top: 1px;
  border-radius: 50%;
  background: #e8f3ff;
  color: var(--c-blue);
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.actions__label {
  font-size: 12px;
  color: var(--text-3);
}
.composer {
  border-top: 1px solid var(--border);
  padding: 10px 14px 12px;
}
.composer__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}
.quick {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  flex: 1;
  min-width: 0;
}
.quick__item {
  flex: none;
  padding: 4px 10px;
  border-radius: 14px;
  border: 1px solid var(--border);
  background: #fff;
  font-size: 12px;
  color: var(--text-2);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}
.quick__item:hover {
  border-color: #bedaff;
  color: var(--c-blue);
  background: #f5f9ff;
}
.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.side__chart {
  height: 190px;
}
.side__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: var(--text-3);
}
.ctx__row {
  display: flex;
  justify-content: space-between;
  padding: 7px 0;
  font-size: 13px;
  color: var(--text-2);
  border-bottom: 1px dashed var(--border);
}
.ctx__row:last-child {
  border-bottom: none;
}
.ctx__row b {
  font-weight: 500;
  color: var(--text-1);
}
</style>