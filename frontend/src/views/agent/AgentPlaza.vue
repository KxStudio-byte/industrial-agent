<template>
  <div>
    <PageHeader title="Agent广场" desc="按业务域浏览全部可用 Agent，查看能力与适用边界。">
      <el-input v-model="keyword" placeholder="搜索 Agent 名称" clearable style="width: 220px" />
    </PageHeader>

    <el-row :gutter="16">
      <el-col v-for="agent in filtered" :key="agent.id" :span="8">
        <div class="agent-card">
          <div class="agent-card__head">
            <div class="agent-card__avatar">{{ agent.name.slice(0, 1) }}</div>
            <div class="agent-card__title">
              <span class="agent-card__name">{{ agent.name }}</span>
              <span class="agent-card__type">{{ agent.type }}</span>
            </div>
            <StatusTag :text="agent.status" :map="RUN_STATUS_CLASS" />
          </div>

          <p class="agent-card__desc">{{ agent.desc }}</p>

          <div class="agent-card__meta">
            <span class="agent-card__meta-item">执行权限：{{ agent.permission }}</span>
            <span class="agent-card__meta-item">今日调用：{{ agent.id * 37 + 12 }} 次</span>
          </div>

          <div class="agent-card__actions">
            <el-button size="small" @click="openDetail(agent)">查看能力</el-button>
            <el-button size="small" type="primary" @click="run(agent)">立即运行</el-button>
          </div>
        </div>
      </el-col>
    </el-row>

    <DetailDrawer v-model="drawerVisible" title="智能体详情" subtitle="能力、适用场景、输入输出与标准执行流程。">
      <div class="scenes">
        <span class="scenes__title">适用场景</span>
        <div class="scenes__grid">
          <div class="scenes__item">
            <div class="scenes__label">覆盖工厂</div>
            <div class="scenes__value">4 家</div>
            <div class="scenes__sub">华东智造集团</div>
          </div>
          <div class="scenes__item">
            <div class="scenes__label">接入设备</div>
            <div class="scenes__value">86</div>
            <div class="scenes__sub">台设备</div>
          </div>
          <div class="scenes__item">
            <div class="scenes__label">诊断准确率</div>
            <div class="scenes__value">91.4%</div>
            <div class="scenes__sub">近 30 天</div>
          </div>
          <div class="scenes__item">
            <div class="scenes__label">平均响应时间</div>
            <div class="scenes__value">2.6 小时</div>
            <div class="scenes__sub">从触发到工单</div>
          </div>
        </div>
      </div>

      <h4 class="section-title">能力说明</h4>
      <p class="para">{{ current?.desc }}</p>
      <p class="para">
        本 Agent 可直接读取产线实时振动、温度、电流和历史故障案例，按「即时诊断 → 根因 → 建议动作」三段输出，
        并在「人工确认后执行」场景下自动生成维修工单。
      </p>

      <h4 class="section-title">典型应用</h4>
      <ul class="list">
        <li>夜间无人值守时段的数据采集与异常标记。</li>
        <li>关键部件劣化趋势判断，在故障前生成维护计划。</li>
        <li>与 MES / IoT / 知识库打通，形成「发现—诊断—执行」闭环。</li>
      </ul>

      <h4 class="section-title">输入 / 输出</h4>
      <div class="detail-list">
        <span class="detail-list__label">输入数据</span>
        <span class="detail-list__value">IoT 实时振动、温度、电流；工艺参数；历史维修案例</span>
        <span class="detail-list__label">知识来源</span>
        <span class="detail-list__value">设备手册、SOP、质量规范、历史维修案例</span>
        <span class="detail-list__label">调用工具</span>
        <span class="detail-list__value">查询设备、查询工单、查询知识库、创建工单、发起消息</span>
        <span class="detail-list__label">执行权限</span>
        <span class="detail-list__value">{{ current?.permission }}（需人工确认后执行）</span>
      </div>

      <h4 class="section-title">标准执行流程</h4>
      <ol class="flow">
        <li v-for="(step, i) in flow" :key="step">
          <span class="flow__idx">{{ i + 1 }}</span>
          <span class="flow__text">{{ step }}</span>
        </li>
      </ol>

      <template #footer>
        <el-button @click="drawerVisible = false">关闭</el-button>
        <el-button type="primary" @click="run(current)">立即运行此 Agent</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { agents } from '../../mock'
import { RUN_STATUS_CLASS } from '../../utils/dict'
import type { Agent } from '../../types'

const router = useRouter()
const keyword = ref('')
const drawerVisible = ref(false)
const current = ref<Agent | null>(null)

const flow = [
  '设备数据',
  '异常检测',
  '根因分析',
  '知识检索',
  '措施生成',
  '生成解决方案',
  '创建维修工单',
  '执行反馈'
]

const filtered = computed(() =>
  agents.filter((a) => a.status === '运行中' && (!keyword.value || a.name.includes(keyword.value)))
)

function openDetail(agent: Agent) {
  current.value = agent
  drawerVisible.value = true
}

function run(agent: Agent | null) {
  drawerVisible.value = false
  ElMessage.success(`${agent?.name ?? 'Agent'} 已启动，结果将出现在 Agent 任务中`)
  router.push('/agent/tasks')
}
</script>

<style scoped>
.agent-card {
  height: 100%;
  background: #fff;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 16px 18px;
  margin-bottom: 16px;
  transition: box-shadow 0.2s, transform 0.2s;
  display: flex;
  flex-direction: column;
}
.agent-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-1px);
}
.agent-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.agent-card__avatar {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, #1677ff, #4096ff);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.agent-card__title {
  display: flex;
  flex-direction: column;
  margin-right: auto;
}
.agent-card__name {
  font-size: 14px;
  font-weight: 600;
}
.agent-card__type {
  font-size: 12px;
  color: var(--text-3);
}
.agent-card__desc {
  flex: 1;
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 21px;
  color: var(--text-2);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.agent-card__meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--text-3);
  margin-bottom: 12px;
}
.agent-card__actions {
  display: flex;
  gap: 8px;
}

.scenes__title {
  font-size: 13px;
  font-weight: 600;
}
.scenes__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 10px 0 4px;
}
.scenes__item {
  border-left: 3px solid var(--c-blue);
  background: #fafbfc;
  border-radius: 4px;
  padding: 8px 10px;
}
.scenes__label {
  font-size: 12px;
  color: var(--text-3);
}
.scenes__value {
  font-size: 18px;
  font-weight: 600;
}
.scenes__sub {
  font-size: 11px;
  color: var(--text-3);
}

.para {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 22px;
  color: var(--text-2);
}
.list {
  margin: 0 0 8px;
  padding-left: 18px;
  font-size: 13px;
  line-height: 24px;
  color: var(--text-2);
}
.flow {
  list-style: none;
  margin: 0;
  padding: 0;
}
.flow li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  line-height: 30px;
  color: var(--text-2);
}
.flow__idx {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--brand-light);
  color: var(--brand);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
</style>