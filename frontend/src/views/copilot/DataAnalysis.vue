<template>
  <div>
    <PageHeader title="数据分析" desc="用自然语言追问，Copilot 自动选择数据集、拆解原因并给出结论。">
      <el-select v-model="scope" style="width: 150px">
        <el-option v-for="s in ['全厂', 'A产线', 'B产线', 'C产线']" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button @click="exportResult">导出分析结果</el-button>
    </PageHeader>

    <div class="panel ask">
      <div class="ask__label">你想问什么？</div>
      <div class="ask__row">
        <el-input
          v-model="question"
          size="large"
          placeholder="例如：为什么今天 OEE 下降？"
          @keydown.enter="analyze"
        />
        <el-button type="primary" size="large" :loading="analyzing" @click="analyze">开始分析</el-button>
      </div>
      <div class="ask__topics">
        <span class="ask__topics-label">推荐问题</span>
        <button v-for="t in analysisTopics" :key="t" class="topic" @click="pickTopic(t)">{{ t }}</button>
      </div>
    </div>

    <div class="analysis-grid">
      <div class="panel">
        <div class="panel__header">
          <span class="panel__title">分析结论</span>
          <span class="panel__extra">{{ scope }} · 近 24 小时</span>
        </div>
        <div class="conclusion__head">
          <SparkIcon :size="16" />
          <span>{{ question }}</span>
        </div>
        <p class="conclusion__text">
          今日全厂 OEE 87.6%，较昨日下降 4.8pct，为近 7 日最低。<span class="hl">设备停机贡献 42%</span>，其中
          5号注塑机电控柜过热保护停机 38 分钟；<span class="hl">换线时间贡献 28%</span>，集中在 B产线 14:00—15:30；
          质量损失 19%，主要来自 C产线外观划伤。
        </p>
        <div class="steps">
          <div v-for="(s, i) in steps" :key="i" class="steps__item">
            <span class="steps__idx">{{ i + 1 }}</span>
            <span>{{ s }}</span>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel__header">
          <span class="panel__title">损失构成</span>
          <span class="panel__extra">OEE 下降 4.8pct</span>
        </div>
        <div ref="pieRef" class="chart chart--pie" />
      </div>
    </div>

    <div class="panel">
      <div class="panel__header">
        <span class="panel__title">产线对比</span>
        <span class="panel__extra">计划达成率 / OEE / 良品率</span>
      </div>
      <div ref="barRef" class="chart chart--bar" />
    </div>

    <div class="panel">
      <div class="panel__header">
        <span class="panel__title">明细数据</span>
        <span class="panel__extra">数据源：MES · IoT · 质量 SPC</span>
      </div>
      <el-table :data="productionLines" style="width: 100%">
        <el-table-column prop="name" label="产线" width="110" />
        <el-table-column prop="product" label="产品" min-width="160" />
        <el-table-column prop="target" label="计划产量" width="110" />
        <el-table-column prop="actual" label="实际产量" width="110" />
        <el-table-column label="达成率" width="110">
          <template #default="{ row }">{{ row.achieve }}%</template>
        </el-table-column>
        <el-table-column label="OEE" width="110">
          <template #default="{ row }">{{ row.oee }}%</template>
        </el-table-column>
        <el-table-column label="良品率" width="110">
          <template #default="{ row }">{{ row.quality }}%</template>
        </el-table-column>
        <el-table-column prop="aiHint" label="AI 提示" min-width="240" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import PageHeader from '../../components/PageHeader.vue'
import SparkIcon from '../../components/SparkIcon.vue'
import { analysisTopics, oeeLossBreakdown, productionLines } from '../../mock'

const scope = ref('全厂')
const question = ref('为什么今天 OEE 下降？')
const analyzing = ref(false)
const pieRef = ref<HTMLElement | null>(null)
const barRef = ref<HTMLElement | null>(null)
let pie: echarts.ECharts | null = null
let bar: echarts.ECharts | null = null

const steps = [
  '识别意图：OEE 下降归因分析（时间范围 = 今日 vs 昨日）',
  '调用工具「查询生产」：拉取三条产线 OEE 与损失明细',
  '调用工具「查询设备」：关联 5号注塑机停机事件与 3号注塑机降速记录',
  '按损失类型归因排序，输出结论与建议动作'
]

function pickTopic(t: string) {
  question.value = t
  analyze()
}

function analyze() {
  if (!question.value.trim()) return
  analyzing.value = true
  window.setTimeout(() => {
    analyzing.value = false
    ElMessage.success('分析已完成')
  }, 800)
}

function exportResult() {
  ElMessage.success('分析结果已导出为 PDF')
}

function initCharts() {
  if (pieRef.value) {
    pie = echarts.init(pieRef.value)
    pie.setOption({
      tooltip: { trigger: 'item', formatter: '{b}：{c}%（{d}%）' },
      legend: {
        bottom: 0,
        icon: 'circle',
        itemWidth: 8,
        itemHeight: 8,
        textStyle: { fontSize: 12, color: '#4e5969' }
      },
      series: [
        {
          type: 'pie',
          radius: ['48%', '72%'],
          center: ['50%', '44%'],
          itemStyle: { borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          data: oeeLossBreakdown.map((d, i) => ({
            name: d.name,
            value: d.value,
            itemStyle: { color: ['#f53f3f', '#ff7d00', '#ffb400', '#86909c'][i] }
          }))
        }
      ]
    })
  }

  if (barRef.value) {
    bar = echarts.init(barRef.value)
    bar.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: {
        top: 0,
        right: 0,
        icon: 'roundRect',
        itemWidth: 10,
        itemHeight: 10,
        textStyle: { fontSize: 12, color: '#4e5969' }
      },
      grid: { left: 8, right: 16, top: 40, bottom: 8, containLabel: true },
      xAxis: {
        type: 'category',
        data: productionLines.map((l) => l.name),
        axisLine: { lineStyle: { color: '#e5e6eb' } },
        axisLabel: { color: '#86909c', fontSize: 12 }
      },
      yAxis: {
        type: 'value',
        max: 120,
        splitLine: { lineStyle: { color: '#f2f3f5' } },
        axisLabel: { color: '#86909c', fontSize: 11, formatter: '{value}%' }
      },
      series: [
        {
          name: '达成率',
          type: 'bar',
          barWidth: 18,
          data: productionLines.map((l) => l.achieve),
          itemStyle: { color: '#165dff', borderRadius: [3, 3, 0, 0] }
        },
        {
          name: 'OEE',
          type: 'bar',
          barWidth: 18,
          data: productionLines.map((l) => l.oee),
          itemStyle: { color: '#00b42a', borderRadius: [3, 3, 0, 0] }
        },
        {
          name: '良品率',
          type: 'bar',
          barWidth: 18,
          data: productionLines.map((l) => l.quality),
          itemStyle: { color: '#ff7d00', borderRadius: [3, 3, 0, 0] }
        }
      ]
    })
  }
}

function onResize() {
  pie?.resize()
  bar?.resize()
}

onMounted(() => {
  initCharts()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  pie?.dispose()
  bar?.dispose()
})
</script>

<style scoped>
.ask__label {
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 8px;
}
.ask__row {
  display: flex;
  gap: 10px;
}
.ask__topics {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.ask__topics-label {
  font-size: 12px;
  color: var(--text-3);
}
.topic {
  padding: 4px 10px;
  border-radius: 14px;
  border: 1px solid var(--border);
  background: #fff;
  font-size: 12px;
  color: var(--text-2);
  cursor: pointer;
  transition: all 0.15s;
}
.topic:hover {
  border-color: #bedaff;
  color: var(--c-blue);
  background: #f5f9ff;
}
.analysis-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
  gap: 16px;
}
.conclusion__head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}
.conclusion__text {
  margin: 10px 0 12px;
  font-size: 13px;
  line-height: 23px;
  color: var(--text-2);
}
.hl {
  color: var(--c-orange);
  font-weight: 600;
}
.steps {
  border-top: 1px dashed var(--border);
  padding-top: 10px;
}
.steps__item {
  display: flex;
  gap: 8px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-2);
  padding: 3px 0;
}
.steps__idx {
  flex: none;
  width: 16px;
  height: 16px;
  margin-top: 2px;
  border-radius: 50%;
  background: #e8f3ff;
  color: var(--c-blue);
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.chart {
  width: 100%;
}
.chart--pie {
  height: 250px;
}
.chart--bar {
  height: 260px;
}
</style>