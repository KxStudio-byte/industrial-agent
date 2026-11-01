<template>
  <div>
    <PageHeader title="节能优化" desc="AI 生成的节能方案清单，支持审核、下发与执行跟踪。">
      <AiButton label="AI 生成节能方案" type="primary" @click="generate" />
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="6">
        <StatCard label="待执行方案" :value="pendingCount" sub="条" accent="orange" value-color="var(--c-orange)" />
      </el-col>
      <el-col :span="6">
        <StatCard label="已完成" :value="doneCount" sub="条 · 本周" accent="green" value-color="var(--c-green)" />
      </el-col>
      <el-col :span="6">
        <StatCard label="执行失败" :value="failedCount" sub="条 · 需排查" accent="red" value-color="var(--c-red)" />
      </el-col>
      <el-col :span="6">
        <StatCard label="预期年节约" value="¥62.9 万" sub="按方案收益累加" accent="blue" />
      </el-col>
    </el-row>

    <div class="panel row-gap">
      <div class="panel__header">
        <span class="panel__title">优化方案</span>
        <span class="panel__extra">共 {{ energyPlans.length }} 条</span>
      </div>
      <el-table :data="energyPlans" style="width: 100%">
        <el-table-column prop="code" label="方案编号" width="150" />
        <el-table-column prop="name" label="方案名称" min-width="200" />
        <el-table-column prop="location" label="位置" min-width="160" />
        <el-table-column prop="medium" label="介质" width="80" />
        <el-table-column prop="saving" label="预期节约" width="130" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :text="row.status" :map="PLAN_EXEC_CLASS" />
          </template>
        </el-table-column>
        <el-table-column prop="execTime" label="执行时间" width="150" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <DetailDrawer v-model="visible" title="节能方案详情" subtitle="AI 生成依据、执行范围与结果跟踪。">
      <div class="detail-list">
        <span class="detail-list__label">方案编号</span>
        <span class="detail-list__value">{{ current?.code }}</span>
        <span class="detail-list__label">方案名称</span>
        <span class="detail-list__value">{{ current?.name }}</span>
        <span class="detail-list__label">生成来源</span>
        <span class="detail-list__value">{{ current?.source }}</span>
        <span class="detail-list__label">目标位置</span>
        <span class="detail-list__value">{{ current?.location }} · {{ current?.medium }}</span>
        <span class="detail-list__label">状态</span>
        <span class="detail-list__value">
          <StatusTag v-if="current" :text="current.status" :map="PLAN_EXEC_CLASS" />
        </span>
        <span class="detail-list__label">预期节约</span>
        <span class="detail-list__value">{{ current?.saving }}</span>
        <span class="detail-list__label">判断依据</span>
        <span class="detail-list__value">{{ current?.reason }}</span>
        <span class="detail-list__label">优化建议</span>
        <span class="detail-list__value">{{ current?.advice }}</span>
        <span class="detail-list__label">执行范围</span>
        <span class="detail-list__value">{{ current?.scope }}</span>
        <span class="detail-list__label">执行结果</span>
        <span class="detail-list__value">{{ current?.result }}</span>
      </div>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" :disabled="current?.status !== '待执行'" @click="execute">确认下发执行</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatCard from '../../components/StatCard.vue'
import StatusTag from '../../components/StatusTag.vue'
import AiButton from '../../components/AiButton.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import { energyPlans } from '../../mock'
import { PLAN_EXEC_CLASS } from '../../utils/dict'
import type { EnergyPlan } from '../../types'

const visible = ref(false)
const current = ref<EnergyPlan | null>(null)

const pendingCount = computed(() => energyPlans.filter((p) => p.status === '待执行').length)
const doneCount = computed(() => energyPlans.filter((p) => p.status === '已完成').length)
const failedCount = computed(() => energyPlans.filter((p) => p.status === '执行失败').length)

function openDetail(row: EnergyPlan) {
  current.value = row
  visible.value = true
}

function execute() {
  if (!current.value) return
  const target = energyPlans.find((p) => p.id === current.value!.id)
  if (target) {
    target.status = '已完成'
    target.execTime = '2026-09-07 20:00'
    target.result = '指令已下发，等待能耗数据验证'
  }
  visible.value = false
  ElMessage.success('方案已下发执行')
}

function generate() {
  ElMessage.success('AI 已生成 1 条新节能方案（蒸汽疏水阀排查）')
}
</script>

<style scoped>
.row-gap {
  margin-top: 16px;
}
.panel__extra {
  font-size: 12px;
  color: var(--text-3);
}
</style>