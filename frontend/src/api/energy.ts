import { api } from './request'

/**
 * 能源中心接口。
 *
 * 契约来源：后端 EnergyController 实际实现（/api/energy/**）。
 * 后端返回 { code, msg, data }，request.ts 的拦截器已经把 code !== 200 转成 reject，
 * 所以这里拿到的 data 一定是成功结果。
 *
 * ⚠️ 后端 Jackson 约定：BigDecimal → number，Long → string（如 "1"）。
 * 凡是后端为 Long 的字段（id / equipmentId）都用 num() 归一化成 number。
 */

/**
 * 能源介质。
 * <p>
 * 「综合」是<b>虚拟介质</b>：后端把四种介质折标煤后相加（t标煤），
 * 只支持在 /trend 上使用，用来画「综合能耗」曲线，与 summary.total 同口径。
 */
export type EnergyMedium = '电' | '水' | '气' | '蒸汽'

/** 趋势接口支持的综合介质（折标煤口径） */
export type EnergyTrendMedium = EnergyMedium | '综合'

/** 车间名 */
export type EnergyWorkshop = 'A车间' | 'B车间' | 'C车间'

/** 查询参数（与后端 EnergyQuery 字段一一对应，全部可选） */
export interface EnergyQuery {
  /**
   * 介质：电/水/气/蒸汽；不传 = 后端按电耗统计。
   * 传「综合」= 四介质折标煤相加（仅 /trend 支持）。
   */
  medium?: EnergyTrendMedium | null
  /** 车间名；不传 = 全厂 */
  workshop?: EnergyWorkshop | null
  /** 统计基准日 yyyy-MM-dd，默认今天 */
  date?: string
  /** 统计天数（含 date 当天），默认 7 */
  days?: number
  /** 趋势粒度：hour / day，默认 day。注：趋势接口不传 medium 时按电耗统计 */
  granularity?: 'hour' | 'day'
  /** 设备排行取前 N，默认 10 */
  topN?: number
  /** 方案查询：状态 */
  status?: string | null
  /** 方案查询：页码（不传返回全量） */
  page?: number
  /** 方案查询：每页条数 */
  size?: number
  /** 方案查询：关键字 */
  keyword?: string | null
}

/** 介质明细一行（今日 vs 昨日） */
export interface EnergyMediumRow {
  medium: string
  today: number
  yesterday: number
  unit: string
  /** 环比文案，如 "+1.2%" / "—" */
  delta: string
}

/** 能耗总览 KPI */
export interface EnergySummary {
  /** 综合能耗（t标煤） */
  total: number
  totalUnit: string
  totalNote: string
  /** 用电（千kWh，后端已除 1000） */
  electricity: number
  electricityNote: string
  /** 用水（t） */
  water: number
  waterNote: string
  /** 用气（Nm³） */
  gas: number
  gasNote: string
  /** 蒸汽（t） */
  steam: number
  steamNote: string
  /** 能耗成本（元） */
  cost: number
  costNote: string
  /** 单位产品能耗（kWh/件） */
  unitConsumption: number
  /** 较上期变化，如 "+0.03" / "—" */
  unitConsumptionNote: string
  /** 每日可节约潜力（元） */
  dailySavingPotential: number
  dailySavingNote: string
  /** AI 提示（规则引擎生成，非大模型） */
  aiHint: string
  aiHintBasis: string
  /** 统计口径说明 */
  scope: string
  mediums: EnergyMediumRow[]
}

/** 趋势一个点 */
export interface EnergyTrendPoint {
  label: string
  current: number
  last: number
}

/** 车间能耗一行 */
export interface EnergyWorkshopRow {
  workshop: string
  /** 电耗（千kWh） */
  value: number
  /** 占比(%) */
  ratio: number
}

/** 能耗明细一行 */
export interface EnergyDetailRow {
  id: number
  recordDate: string
  workshop: string
  medium: string
  value: number
  unit: string
  /** 较前一天差值；无前一天数据时 null */
  change: number | null
}

/** 高耗能设备一行 */
export interface EnergyTopEquipmentRow {
  equipmentCode: string
  equipmentName: string
  workshop: string
  /** 窗口累计耗电（kWh） */
  value: number
  /** 后半段日均 vs 前半段日均变化(%)；窗口不足时为 null */
  change: number | null
  abnormal: boolean
}

/** 节能方案 */
export interface EnergySavingPlan {
  id: number
  planCode: string
  planName: string
  location: string
  medium: string
  /** 待执行 / 已完成 / 执行失败 */
  status: string
  /** 每月节约（元） */
  saving: number
  /** 每日节约（元） */
  dailySaving: number
  reason: string
  advice: string
  scope: string
  result: string | null
  execTime: string | null
  source: string
  createTime: string
}

/** 方案列表（带状态计数） */
export interface EnergySavingPlanList {
  list: EnergySavingPlan[]
  total: number
  pendingCount: number
  doneCount: number
  failedCount: number
  /** 待执行方案每月合计节约（元） */
  pendingSaving: number
}

/* ------------------------------------------------------------------ */

/** 后端数字字段兜底：可能是 number 也可能被序列化成 string */
function num(v: unknown, fallback = 0): number {
  if (typeof v === 'number' && Number.isFinite(v)) return v
  if (typeof v === 'string' && v.trim() !== '') {
    const n = Number(v)
    if (Number.isFinite(n)) return n
  }
  return fallback
}

/** 可空数字：null / undefined / 空串 → null */
function numOrNull(v: unknown): number | null {
  if (v === null || v === undefined || v === '') return null
  const n = num(v, NaN)
  return Number.isNaN(n) ? null : n
}

function str(v: unknown, fallback = ''): string {
  return v === null || v === undefined ? fallback : String(v)
}

function objArr(v: unknown): Record<string, unknown>[] {
  return Array.isArray(v) ? (v as Record<string, unknown>[]) : []
}

/** 把可选查询参数压成 axios 的 params（剔除 undefined / null / 空串） */
function params(q: EnergyQuery): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(q)) {
    if (v === undefined || v === null || v === '') continue
    out[k] = v
  }
  return out
}

/* ------------------------------------------------------------------ */

/** 能耗总览 KPI */
export async function fetchEnergySummary(query: EnergyQuery = {}): Promise<EnergySummary> {
  const raw = await api.get<Record<string, unknown>>('/api/energy/summary', params(query))
  return {
    total: num(raw.total),
    totalUnit: str(raw.totalUnit),
    totalNote: str(raw.totalNote),
    electricity: num(raw.electricity),
    electricityNote: str(raw.electricityNote),
    water: num(raw.water),
    waterNote: str(raw.waterNote),
    gas: num(raw.gas),
    gasNote: str(raw.gasNote),
    steam: num(raw.steam),
    steamNote: str(raw.steamNote),
    cost: num(raw.cost),
    costNote: str(raw.costNote),
    unitConsumption: num(raw.unitConsumption),
    unitConsumptionNote: str(raw.unitConsumptionNote, '—'),
    dailySavingPotential: num(raw.dailySavingPotential),
    dailySavingNote: str(raw.dailySavingNote),
    aiHint: str(raw.aiHint),
    aiHintBasis: str(raw.aiHintBasis),
    scope: str(raw.scope),
    mediums: objArr(raw.mediums).map((r) => ({
      medium: str(r.medium),
      today: num(r.today),
      yesterday: num(r.yesterday),
      unit: str(r.unit),
      delta: str(r.delta, '—')
    }))
  }
}

/** 能耗趋势。granularity=day 按天上期对比；=hour 24 点今日 vs 昨日 */
export async function fetchEnergyTrend(query: EnergyQuery = {}): Promise<EnergyTrendPoint[]> {
  const raw = await api.get<Record<string, unknown>[]>('/api/energy/trend', params(query))
  return objArr(raw).map((r) => ({
    label: str(r.label),
    current: num(r.current),
    last: num(r.last)
  }))
}

/** 车间能耗对比 */
export async function fetchEnergyWorkshops(query: EnergyQuery = {}): Promise<EnergyWorkshopRow[]> {
  const raw = await api.get<Record<string, unknown>[]>('/api/energy/workshops', params(query))
  return objArr(raw).map((r) => ({
    workshop: str(r.workshop),
    value: num(r.value),
    ratio: num(r.ratio)
  }))
}

/** 能耗明细 */
export async function fetchEnergyDetails(query: EnergyQuery = {}): Promise<EnergyDetailRow[]> {
  const raw = await api.get<Record<string, unknown>[]>('/api/energy/details', params(query))
  return objArr(raw).map((r) => ({
    id: num(r.id),
    recordDate: str(r.recordDate),
    workshop: str(r.workshop),
    medium: str(r.medium),
    value: num(r.value),
    unit: str(r.unit),
    change: numOrNull(r.change)
  }))
}

/** 高耗能设备排行 */
export async function fetchEnergyTopEquipment(
  query: EnergyQuery = {}
): Promise<EnergyTopEquipmentRow[]> {
  const raw = await api.get<Record<string, unknown>[]>('/api/energy/top-equipment', params(query))
  return objArr(raw).map((r) => ({
    equipmentCode: str(r.equipmentCode),
    equipmentName: str(r.equipmentName),
    workshop: str(r.workshop, '—'),
    value: num(r.value),
    change: numOrNull(r.change),
    abnormal: r.abnormal === true
  }))
}

/** AI 节能优化方案列表 */
export async function fetchEnergySavingPlans(
  query: EnergyQuery = {}
): Promise<EnergySavingPlanList> {
  const raw = await api.get<Record<string, unknown>>('/api/energy/saving-plans', params(query))
  return {
    list: objArr(raw.list).map(mapPlan),
    total: num(raw.total),
    pendingCount: num(raw.pendingCount),
    doneCount: num(raw.doneCount),
    failedCount: num(raw.failedCount),
    pendingSaving: num(raw.pendingSaving)
  }
}

/** 单个节能方案详情（抽屉用） */
export async function fetchEnergySavingPlanDetail(id: number): Promise<EnergySavingPlan> {
  const raw = await api.get<Record<string, unknown>>(`/api/energy/saving-plans/${id}`)
  return mapPlan(raw)
}

function mapPlan(r: Record<string, unknown>): EnergySavingPlan {
  return {
    id: num(r.id),
    planCode: str(r.planCode),
    planName: str(r.planName),
    location: str(r.location),
    medium: str(r.medium),
    status: str(r.status),
    saving: num(r.saving),
    dailySaving: num(r.dailySaving),
    reason: str(r.reason),
    advice: str(r.advice),
    scope: str(r.scope),
    result: r.result === null || r.result === undefined ? null : String(r.result),
    execTime: r.execTime === null || r.execTime === undefined ? null : String(r.execTime),
    source: str(r.source),
    createTime: str(r.createTime)
  }
}