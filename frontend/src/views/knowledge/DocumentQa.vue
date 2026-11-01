<template>
  <div>
    <PageHeader title="文档问答" desc="基于知识库的检索增强问答，回答附原文出处。">
      <el-select v-model="scope" style="width: 180px">
        <el-option v-for="s in scopes" :key="s" :label="s" :value="s" />
      </el-select>
    </PageHeader>

    <div class="chat-layout">
      <div class="chat-panel">
        <div class="chat-toolbar">
          <el-button size="small" @click="resetSession">新建问答</el-button>
          <el-button size="small" text @click="showHistory = !showHistory">
            {{ showHistory ? '收起历史' : '历史记录' }}
          </el-button>
        </div>

        <div v-if="showHistory" class="history">
          <div v-for="h in history" :key="h.id" class="history__item">
            <span class="history__q">{{ h.q }}</span>
            <span class="history__t">{{ h.time }}</span>
          </div>
        </div>

        <div ref="scrollRef" class="messages">
          <div v-for="m in messages" :key="m.id" class="msg" :class="`msg--${m.role}`">
            <div class="msg__avatar">{{ m.role === 'user' ? '我' : 'KB' }}</div>
            <div class="msg__body">
              <div class="msg__bubble">
                <p v-for="(line, i) in m.content.split('\n')" :key="i" class="msg__line">{{ line }}</p>
              </div>

              <div v-if="m.sources?.length" class="sources">
                <div class="sources__title">引用原文</div>
                <div v-for="(s, i) in m.sources" :key="i" class="sources__item">
                  <span class="sources__idx">{{ i + 1 }}</span>
                  <div class="sources__body">
                    <div class="sources__file">
                      {{ s.fileName }}<span class="sources__grid"> · {{ s.location }}</span>
                    </div>
                    <div class="sources__text">{{ s.text }}</div>
                  </div>
                  <span class="sources__score">{{ s.score }}</span>
                </div>
              </div>
            </div>
          </div>

          <div v-if="sending" class="msg msg--assistant">
            <div class="msg__avatar">KB</div>
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
            placeholder="例如：注塑机轴承磨损的判定标准是什么？"
            @keydown.enter.exact.prevent="send"
          />
          <div class="composer__foot">
            <span class="composer__hint">Enter 发送 · Shift + Enter 换行</span>
            <el-button type="primary" :loading="sending" @click="send">发送</el-button>
          </div>
        </div>
      </div>

      <div class="side">
        <div class="panel">
          <div class="panel__header">
            <span class="panel__title">知识库概览</span>
          </div>
          <div>
            <div v-for="k in kbStats" :key="k.name" class="kb-row">
              <span>{{ k.name }}</span>
              <span class="kb-row__count">{{ k.count }} 篇</span>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel__header">
            <span class="panel__title">推荐问题</span>
          </div>
          <div class="suggest">
            <button v-for="q in suggests" :key="q" class="suggest__item" @click="ask(q)">{{ q }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import PageHeader from '../../components/PageHeader.vue'

interface Source {
  fileName: string
  location: string
  text: string
  score: string
}

interface QaMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  sources?: Source[]
}

const scopes = ['全部知识库', '设备维修手册库', '工艺规范库', '质量案例库']
const scope = ref('全部知识库')
const draft = ref('')
const sending = ref(false)
const showHistory = ref(false)
const scrollRef = ref<HTMLElement | null>(null)
let seq = 0

const history = [
  { id: 1, q: '注塑机轴承磨损的判定标准？', time: '今天 10:24' },
  { id: 2, q: '外观划伤的检验 SOP 有几条？', time: '今天 09:40' },
  { id: 3, q: '空压机冷却器清洗周期', time: '昨天 16:05' }
]

const kbStats = [
  { name: '设备维修手册库', count: 128 },
  { name: '工艺规范库', count: 86 },
  { name: '质量案例库', count: 214 },
  { name: '安全规程库', count: 42 }
]

const suggests = [
  '注塑机轴承磨损的判定标准是什么？',
  'C产线外观划伤应该怎么处理？',
  '空压机排气温度偏高的常见原因',
  '换线超时如何优化 SOP？'
]

const messages = ref<QaMessage[]>([
  {
    id: ++seq,
    role: 'assistant',
    content:
      '你好，我是工业知识库助手。我会在设备手册、工艺规范与质量案例中检索，并给出带出处的回答。\n你可以直接提问，例如「3号注塑机振动超标怎么处理」。',
    sources: []
  },
  {
    id: ++seq,
    role: 'user',
    content: '注塑机轴承磨损的判定标准是什么？'
  },
  {
    id: ++seq,
    role: 'assistant',
    content:
      '结论：当主轴振动有效值连续超过 6.5 mm/s，且 120~160 Hz 倍频能量上升 30% 以上时，判定为主轴轴承早期磨损。\n处置建议：72 小时内安排停机更换 6208 轴承，并对传动轴复测动平衡；更换后连续 3 天加密至每 2 小时采集一次振动数据。',
    sources: [
      {
        fileName: 'IM系列注塑机维修手册.pdf',
        location: '第 4 章 主轴系统 · P62',
        text: '当主轴振动有效值连续 3 次采样超过 6.5 mm/s，并伴随 120~160 Hz 倍频能量上升 30% 以上时，应按轴承磨损处理。',
        score: '0.92'
      },
      {
        fileName: '设备点检标准V3.docx',
        location: '2.3 振动监测',
        text: '注塑机主轴建议每 4 小时采集一次振动数据，超过阈值立即报修并记录工况。',
        score: '0.87'
      }
    ]
  }
])

const replyPool: { content: string; sources: Source[] }[] = [
  {
    content:
      '结论：外观划伤主要由夹具定位销偏差引起，先校正定位销并复核 15:00 后批次。\n处置建议：按 SOP 复检外观，划伤深度小于 0.1mm 可返修，超出则报废并登记质量案例。',
    sources: [
      {
        fileName: '外观检验SOP.docx',
        location: '第 2 节 划伤判定',
        text: '划伤深度 ≤0.1mm 的可抛光返修，深度 >0.1mm 的一律报废并录入不良品台账。',
        score: '0.90'
      }
    ]
  },
  {
    content:
      '结论：空压机排气温度偏高常见于冷却器结垢、冷却水流量不足与环境温度过高三类原因。\n处置建议：优先清洗换热器并检查冷却水流量，清洗周期建议不超过 3000 运行小时。',
    sources: [
      {
        fileName: 'GA系列空压机维护手册.pdf',
        location: '第 6 章 冷却系统 · P88',
        text: '冷却器建议每 3000 运行小时清洗一次，排气温度持续高于 95℃ 时应立即停机检查。',
        score: '0.88'
      }
    ]
  },
  {
    content:
      '结论：换线超时的核心原因是模具准备与首件确认串行执行。\n处置建议：将模具预热与工装准备并行，换线 SOP 增加「首件快速确认」环节，目标由 90 分钟压缩至 65 分钟。',
    sources: [
      {
        fileName: '换线作业指导书.docx',
        location: '第 3 节 换线流程',
        text: '换线流程包含模具准备、工装更换、参数设定与首件确认四个阶段，标准工时合计 90 分钟。',
        score: '0.85'
      }
    ]
  }
]

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
    const pick = replyPool[messages.value.length % replyPool.length]
    messages.value.push({ id: ++seq, role: 'assistant', content: pick.content, sources: pick.sources })
    scrollToBottom()
  }, 800)
}

function resetSession() {
  messages.value = [
    {
      id: ++seq,
      role: 'assistant',
      content: '新的问答已开始，请描述你要查询的设备、工序或质量问题。',
      sources: []
    }
  ]
  scrollToBottom()
}
</script>

<style scoped>
.chat-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}
.chat-panel {
  background: #fff;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  height: calc(100vh - 200px);
  min-height: 520px;
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
  max-width: 76%;
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
.sources {
  margin-top: 8px;
}
.sources__title {
  font-size: 12px;
  color: var(--text-3);
  margin-bottom: 6px;
}
.sources__item {
  display: flex;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f7f8fa;
  margin-bottom: 6px;
}
.sources__idx {
  width: 18px;
  height: 18px;
  flex: none;
  border-radius: 4px;
  background: var(--c-blue);
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.sources__body {
  flex: 1;
  min-width: 0;
}
.sources__file {
  font-size: 12px;
  color: var(--text-1);
  font-weight: 500;
}
.sources__grid {
  color: var(--text-3);
  font-weight: 400;
}
.sources__text {
  margin-top: 2px;
  font-size: 12px;
  line-height: 19px;
  color: var(--text-2);
}
.sources__score {
  flex: none;
  font-size: 11px;
  color: var(--c-green);
}
.composer {
  border-top: 1px solid var(--border);
  padding: 10px 14px 12px;
}
.composer__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.composer__hint {
  font-size: 12px;
  color: var(--text-3);
}
.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.kb-row {
  display: flex;
  justify-content: space-between;
  padding: 7px 0;
  font-size: 13px;
  color: var(--text-2);
  border-bottom: 1px dashed var(--border);
}
.kb-row:last-child {
  border-bottom: none;
}
.kb-row__count {
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}
.suggest {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.suggest__item {
  text-align: left;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: #fff;
  font-size: 12px;
  color: var(--text-2);
  cursor: pointer;
  transition: all 0.15s;
}
.suggest__item:hover {
  border-color: #bedaff;
  color: var(--c-blue);
  background: #f5f9ff;
}
</style>