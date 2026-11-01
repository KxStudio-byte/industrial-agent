<template>
  <DetailDrawer v-model="visible" title="设备详情" subtitle="基础信息、实时数据、健康评分与 AI 诊断。" :width="480">
    <div class="head-tags">
      <StatusTag :text="equip.status" :map="EQUIPMENT_STATUS_CLASS" />
      <span class="health">健康度 {{ fmtInt(equip.health) }} / 100</span>
      <span class="sep">·</span>
      <span class="risk">风险等级</span>
      <StatusTag :text="equip.riskLevel" :map="RISK_CLASS" />
    </div>

    <div class="detail-list">
      <span class="detail-list__label">设备名称</span>
      <span class="detail-list__value">{{ dash(equip.name) }}</span>
      <span class="detail-list__label">设备编号</span>
      <span class="detail-list__value">{{ dash(equip.code) }}</span>
      <span class="detail-list__label">型号</span>
      <span class="detail-list__value">{{ dash(equip.model) }}</span>
      <span class="detail-list__label">厂商</span>
      <span class="detail-list__value">{{ dash(equip.vendor) }}</span>
      <span class="detail-list__label">所属产线</span>
      <span class="detail-list__value">{{ dash(equip.lineName) }}</span>
      <span class="detail-list__label">温度</span>
      <span class="detail-list__value">{{ num(equip.temperature) }} ℃</span>
      <span class="detail-list__label">振动</span>
      <span class="detail-list__value">{{ num(equip.vibration) }} mm/s</span>
      <span class="detail-list__label">电流</span>
      <span class="detail-list__value">{{ num(equip.current) }} A</span>
    </div>

    <!-- 原型：故障预测与 AI 诊断并成一段文字，压在字段表下面 -->
    <p class="predict">
      <span class="predict__label">故障预测：</span>
      <template v-if="equip.predict">
        24小时 {{ pct(equip.predict.h24) }} · 7天 {{ pct(equip.predict.d7) }} · 30天
        {{ pct(equip.predict.d30) }}。
      </template>
      <template v-else>暂无预测数据。</template>
      <span class="predict__label">AI诊断：</span>
      <span>{{ equip.diagnosis || '暂无诊断结论。' }}</span>
    </p>

    <template #footer>
      <AiButton label="创建维修工单" type="primary" @click="createWorkOrder" />
    </template>
  </DetailDrawer>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import StatusTag from '../../components/StatusTag.vue'
import AiButton from '../../components/AiButton.vue'
import { EQUIPMENT_STATUS_CLASS, RISK_CLASS } from '../../utils/dict'
import type { Equipment } from '../../types'

const visible = ref(false)

const emptyEquip: Equipment = {
  id: 0,
  code: '',
  name: '',
  type: '',
  model: '',
  vendor: '',
  lineName: '',
  status: '正常',
  health: 0,
  riskLevel: '低',
  lastException: null,
  temperature: null,
  vibration: null,
  current: null,
  predict: null,
  diagnosis: null
}

// 抽屉打开前先给一份兜底数据，避免模板访问 undefined
const equip = reactive<Equipment>({ ...emptyEquip })

const emit = defineEmits<{ (e: 'create', equip: Equipment): void }>()

function open(data: Equipment) {
  // 用结构化展开而不是 JSON 往返：省一次序列化，也避免把 undefined 变成 null
  Object.assign(equip, emptyEquip, data)
  visible.value = true
}

/** 空值统一显示「—」，原型里没数据的地方就是这么画的 */
function dash(v: string | null | undefined): string {
  return v === null || v === undefined || v === '' ? '—' : v
}

/** 实时数值：保留后端给的小数，空值显示「—」 */
function num(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : String(v)
}

function fmtInt(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : String(Math.round(v))
}

/** 0.12 → 12% */
function pct(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : `${Math.round(v)}%`
}

/**
 * 「创建维修工单」：父页面可以用 @create 接管（比如设备列表页直接弹出新建工单抽屉），
 * 没人接管时退回到跳转维修工单列表，保证按钮永远有反馈。
 */
function createWorkOrder() {
  emit('create', equip)
}

defineExpose({ open })
</script>

<style scoped>
.head-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  font-size: 13px;
}
.health {
  color: var(--c-orange);
  font-weight: 500;
}
.sep {
  color: var(--text-4);
}
.risk {
  color: var(--text-3);
}
.predict {
  margin: 16px 0 0;
  font-size: 13px;
  line-height: 22px;
  color: var(--text-2);
}
.predict__label {
  color: var(--text-3);
}
</style>