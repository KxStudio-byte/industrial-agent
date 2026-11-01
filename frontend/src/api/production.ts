import { api } from './request'
import type { PageResult } from './equipment'

/**
 * 生产运营接口。
 * <p>
 * 契约来源：后端 `ProductionController`（/api/production/**）。
 * 后端返回 { code, msg, data }，request.ts 的拦截器已经把 code !== 200 转成 reject。
 * <p>
 * <b>⚠️ id 字段是字符串</b>：后端 JacksonConfig 把 Long 序列化成字符串（防 2^53 溢出）。
 * 这里统一用 `Number()` 归一，理由同 workOrder.ts —— el-select 的 v-model 严格比较，
 * 字符串 value 配数字初值会「选中了但框里没字」。
 */

/* ============================================================
 * 一、类型
 * ============================================================ */

/** 计划状态 */
export type PlanStatus = '待执行' | '执行中' | '已延期' | '已完成'

/** 班次 */
export type ShiftName = '白班' | '夜班'

/** 下拉选项（产线 / 状态 / 班次） */
export interface ProductionOptions {
  lines: Array<{ id: number; lineName: string; workshopId: number | null }>
  status: PlanStatus[]
  shiftName: ShiftName[]
}

/** 生产计划行（列表 / 详情主体，对应后端 PlanRowVO） */
export interface PlanRow {
  id: number
  planDate: string
  lineId: number | null
  lineName: string | null
  product: string
  targetQty: number
  actualQty: number | null
  /** 达成率百分比，target 为 0 或 actual 为空时是 null */
  achieve: number | null
  status: PlanStatus
  shiftName: ShiftName | null
  planHours: number | null
  standardQtyPerHour: number | null
  aiExplanation: string | null
  /** 该计划关联损失分钟合计 */
  lossMinutes: number
}

/** 生产批次（计划详情里的分批投料/产出） */
export interface PlanBatch {
  id: number
  planId: number
  batchNo: string
  equipmentId: number | null
  equipmentName: string | null
  goodQty: number
  defectQty: number
  totalQty: number
  defectRate: number | null
  changeoverMinutes: number
  startTime: string | null
  endTime: string | null
  durationMinutes: number | null
}

/** OEE 损失明细 */
export interface OeeLoss {
  id: number
  lossType: string
  /** 触发原因，如「卡料 / 卡模」 */
  trigger: string | null
  minutes: number
  duration: string | null
  impact: string | null
  impactValue: number | null
  lineId: number | null
  lineName: string | null
  equipment: string | null
  description: string | null
  occurTime: string | null
}

/** 计划详情（主体 + 批次 + 损失） */
export interface PlanDetail {
  plan: PlanRow
  batches: PlanBatch[]
  losses: OeeLoss[]
}

/** 生产总览 KPI */
export interface ProductionKpi {
  /** 计划产量（当日） */
  planQty: number
  planNote: string
  actualQty: number
  actualNote: string
  achieveRate: number | null
  achieveNote: string
  oee: number | null
  oeeNote: string
  lineCount: number
  onTrackLines: number
  delayedLines: number
  statDate: string
}

/** 产线卡片（生产总览三段式卡片） */
export interface ProductionLineCard {
  name: string
  lineId: number | null
  status: PlanStatus | string
  product: string | null
  workTime: string | null
  target: number
  actual: number
  achieve: number | null
  achieveNote: string
  oee: number | null
  oeeDelta: number | null
  quality: number | null
  qualityNote: string
  availability: number | null
  performance: number | null
  qualityIndex: number | null
  planProgress: number
  batchProgress: number | null
  currentBatch: string | null
  currentBatchQty: number | null
  changeover: number | null
  downtimeSum: number
  losses: Array<{ name: string; value: number }>
  aiHint: string
}

/** 生产总览整包 */
export interface ProductionOverview {
  kpi: ProductionKpi
  lines: ProductionLineCard[]
}

/** OEE 顶部 KPI */
export interface OeeSummary {
  oee: number | null
  value: number | null
  oeeDelta: string | null
  delta: string | null
  target: number
  availability: number | null
  availabilityNote: string
  performance: number | null
  performanceNote: string
  qualityIndex: number | null
  qualityNote: string
  quality: number | null
  note: string
  /** 统计范围文案，如「统计范围 2026-10-03 ~ 2026-11-01」 */
  scope: string
}

/** 分产线 OEE */
export interface OeeLine {
  lineId: number | null
  lineName: string
  oee: number | null
  delta: number | null
  availability: number | null
  performance: number | null
  qualityIndex: number | null
  downtime: string | null
  /** 瓶颈损失类型 */
  lossBottleneck: string | null
  targetQty: number | null
  actualQty: number | null
  achieve: number | null
}

/** 计划查询条件 */
export interface PlanQuery {
  status?: string
  lineId?: number
  lineName?: string
  shiftName?: string
  product?: string
  keyword?: string
  startDate?: string
  endDate?: string
  page?: number
  size?: number
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

/* ============================================================
 * 二、归一化
 * ============================================================ */

/** 后端 Long → 字符串，统一转 number（null 保持 null） */
function num(v: unknown): number | null {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isNaN(n) ? null : n
}

/** 必填数字：null 兜底成 0 */
function num0(v: unknown): number {
  return num(v) ?? 0
}

function normalizeLine(raw: Record<string, unknown>): ProductionLineCard {
  return {
    ...(raw as unknown as ProductionLineCard),
    lineId: num(raw.lineId),
    target: num0(raw.target),
    actual: num0(raw.actual),
    achieve: num(raw.achieve),
    oee: num(raw.oee),
    oeeDelta: num(raw.oeeDelta),
    quality: num(raw.quality),
    qualityIndex: num(raw.qualityIndex),
    availability: num(raw.availability),
    performance: num(raw.performance),
    planProgress: num0(raw.planProgress),
    batchProgress: num(raw.batchProgress),
    currentBatchQty: num(raw.currentBatchQty),
    changeover: num(raw.changeover),
    downtimeSum: num0(raw.downtimeSum),
    losses: Array.isArray(raw.losses) ? (raw.losses as Array<{ name: string; value: number }>) : [],
    aiHint: String(raw.aiHint ?? ''),
    achieveNote: String(raw.achieveNote ?? ''),
    qualityNote: String(raw.qualityNote ?? ''),
  } as ProductionLineCard
}

function normalizePlanRow(raw: Record<string, unknown>): PlanRow {
  return {
    ...(raw as unknown as PlanRow),
    id: num0(raw.id),
    lineId: num(raw.lineId),
    targetQty: num0(raw.targetQty),
    actualQty: num(raw.actualQty),
    achieve: num(raw.achieve),
    planHours: num(raw.planHours),
    standardQtyPerHour: num(raw.standardQtyPerHour),
    lossMinutes: num0(raw.lossMinutes),
  } as PlanRow
}

function normalizeBatch(raw: Record<string, unknown>): PlanBatch {
  return {
    ...(raw as unknown as PlanBatch),
    id: num0(raw.id),
    planId: num0(raw.planId),
    equipmentId: num(raw.equipmentId),
    goodQty: num0(raw.goodQty),
    defectQty: num0(raw.defectQty),
    totalQty: num0(raw.totalQty),
    defectRate: num(raw.defectRate),
    changeoverMinutes: num0(raw.changeoverMinutes),
    durationMinutes: num(raw.durationMinutes),
  } as PlanBatch
}

function normalizeLoss(raw: Record<string, unknown>): OeeLoss {
  return {
    ...(raw as unknown as OeeLoss),
    id: num0(raw.id),
    minutes: num0(raw.minutes),
    impactValue: num(raw.impactValue),
    lineId: num(raw.lineId),
  } as OeeLoss
}

/* ============================================================
 * 三、接口
 * ============================================================ */

/** 生产总览（KPI + 三条产线卡片） */
export async function fetchProductionOverview(): Promise<ProductionOverview> {
  const data = await api.get<{ kpi: Record<string, unknown>; lines: Array<Record<string, unknown>> }>(
    '/api/production/overview',
  )
  const kpiRaw = data.kpi ?? {}
  const kpi: ProductionKpi = {
    ...(kpiRaw as unknown as ProductionKpi),
    planQty: num0(kpiRaw.planQty),
    actualQty: num0(kpiRaw.actualQty),
    achieveRate: num(kpiRaw.achieveRate),
    oee: num(kpiRaw.oee),
    lineCount: num0(kpiRaw.lineCount),
    onTrackLines: num0(kpiRaw.onTrackLines),
    delayedLines: num0(kpiRaw.delayedLines),
    planNote: String(kpiRaw.planNote ?? ''),
    actualNote: String(kpiRaw.actualNote ?? ''),
    achieveNote: String(kpiRaw.achieveNote ?? ''),
    oeeNote: String(kpiRaw.oeeNote ?? ''),
    statDate: String(kpiRaw.statDate ?? ''),
  }
  return { kpi, lines: (data.lines ?? []).map(normalizeLine) }
}

/** 生产计划列表（传 page/size 返回分页对象） */
export async function fetchPlans(query: PlanQuery): Promise<PageResult<PlanRow>> {
  const data = await api.get<PageResult<Record<string, unknown>>>(
    '/api/production/plans',
    query as unknown as Record<string, unknown>,
  )
  return {
    ...data,
    list: (data.list ?? []).map(normalizePlanRow),
  }
}

/** 新建生产计划的提交体（与后端 PlanCreateRequest 一一对应） */
export interface PlanCreatePayload {
  planDate: string
  lineId: number
  productName: string
  targetQty: number
  planHours: number
  standardQtyPerHour: number
  shiftName: ShiftName
}

/**
 * 新建生产计划。
 * 后端固定 actualQty=0、status=待执行、aiExplanation=null，不接受前端传入。
 */
export async function createPlan(payload: PlanCreatePayload): Promise<PlanDetail> {
  const data = await api.post<{
    plan: Record<string, unknown>
    batches: Array<Record<string, unknown>>
    losses: Array<Record<string, unknown>>
  }>('/api/production/plans', payload)
  return {
    plan: normalizePlanRow(data.plan ?? {}),
    batches: (data.batches ?? []).map(normalizeBatch),
    losses: (data.losses ?? []).map(normalizeLoss),
  }
}

/** 生产计划详情（主体 + 批次 + 损失） */
export async function fetchPlanDetail(id: number): Promise<PlanDetail> {
  const data = await api.get<{
    plan: Record<string, unknown>
    batches: Array<Record<string, unknown>>
    losses: Array<Record<string, unknown>>
  }>(`/api/production/plans/${id}`)
  return {
    plan: normalizePlanRow(data.plan ?? {}),
    batches: (data.batches ?? []).map(normalizeBatch),
    losses: (data.losses ?? []).map(normalizeLoss),
  }
}

/** 下拉选项（产线 / 计划状态 / 班次） */
export async function fetchProductionOptions(): Promise<ProductionOptions> {
  const data = await api.get<{
    lines: Array<Record<string, unknown>>
    status: string[]
    shiftName: string[]
  }>('/api/production/options')
  return {
    lines: (data.lines ?? []).map((l) => ({
      id: num0(l.id),
      lineName: String(l.lineName ?? ''),
      workshopId: num(l.workshopId),
    })),
    status: (data.status ?? []) as PlanStatus[],
    shiftName: (data.shiftName ?? []) as ShiftName[],
  }
}

/** OEE 顶部 KPI；lineId 为空表示全厂 */
export async function fetchOeeSummary(days = 30, lineId?: number): Promise<OeeSummary> {
  const data = await api.get<Record<string, unknown>>('/api/production/oee/summary', { days, lineId })
  return {
    ...(data as unknown as OeeSummary),
    oee: num(data.oee),
    value: num(data.value),
    target: num0(data.target),
    availability: num(data.availability),
    performance: num(data.performance),
    qualityIndex: num(data.qualityIndex),
    quality: num(data.quality),
  } as OeeSummary
}

/** 分产线 OEE；lineId 为空表示全部产线 */
export async function fetchOeeByLine(days = 7, lineId?: number): Promise<OeeLine[]> {
  const data = await api.get<Array<Record<string, unknown>>>('/api/production/oee/by-line', { days, lineId })
  return (data ?? []).map(
    (raw) =>
      ({
        ...(raw as unknown as OeeLine),
        lineId: num(raw.lineId),
        oee: num(raw.oee),
        delta: num(raw.delta),
        availability: num(raw.availability),
        performance: num(raw.performance),
        qualityIndex: num(raw.qualityIndex),
        targetQty: num(raw.targetQty),
        actualQty: num(raw.actualQty),
        achieve: num(raw.achieve),
      }) as OeeLine,
  )
}

/** OEE 损失 TOP 榜 */
export async function fetchOeeLosses(days = 30, lineId?: number, limit = 20): Promise<OeeLoss[]> {
  const data = await api.get<Array<Record<string, unknown>>>('/api/production/oee/losses', {
    days,
    lineId,
    limit,
  })
  return (data ?? []).map(normalizeLoss)
}

/** 损失按类型汇总（饼图） */
export async function fetchLossTypes(
  days = 30,
  lineId?: number,
): Promise<Array<{ lossType: string; minutes: number; pct: number }>> {
  const data = await api.get<Array<Record<string, unknown>>>('/api/production/oee/loss-types', { days, lineId })
  return (data ?? []).map((raw) => ({
    lossType: String(raw.lossType ?? ''),
    minutes: num0(raw.minutes),
    pct: num0(raw.pct),
  }))
}

/** 按时段产产量（白班/夜班对比） */
export async function fetchOutputByShift(planDate?: string): Promise<Record<string, unknown>> {
  return api.get<Record<string, unknown>>('/api/production/output/by-shift', { planDate })
}