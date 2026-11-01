import { api } from './request'
import type { PageResult } from './equipment'
import type { EquipmentStatus, Priority, RiskLevel, WorkOrderStatus } from '../types'

/**
 * 维修工单接口。
 * <p>
 * 契约来源：后端 `WorkOrderController`（/api/work-orders/**）+ `eqp_work_order` 表结构。
 * 后端返回 { code, msg, data }，request.ts 的拦截器已经把 code !== 200 转成 reject。
 * <p>
 * <b>⚠️ 所有 id 字段都要 Number() 归一</b>：后端 JacksonConfig 把 Long 序列化成字符串
 * （防止超过 2^53 的 id 在 JS 里丢精度）。功能上 "3" 和 3 都能用，
 * 但 el-select 的 v-model 是严格比较 —— 下拉 value 是字符串、表单初值是数字时，
 * 会出现「选中了但框里没字」。所以入口处统一转 number。
 */

/* ============================================================
 * 一、类型
 * ============================================================ */

/** 工单行（列表 / 详情里的 workOrder 段，与后端 WorkOrderRowVO 一一对应） */
export interface WorkOrderRow {
  id: number
  woNo: string
  equipmentId: number | null
  /** 设备名称（JOIN base_equipment；设备已删除时为 null） */
  equipmentName: string | null
  equipmentCode: string | null
  lineName: string | null
  model: string | null
  vendor: string | null
  /** 设备当前状态 */
  equipmentStatus: EquipmentStatus | null
  healthScore: number | null
  riskLevel: RiskLevel | null
  faultType: string
  priority: Priority
  description: string
  source: string
  status: WorkOrderStatus
  assigneeId: number | null
  /** 负责人姓名（后端批量补的） */
  assigneeName: string | null
  createTime: string
  updateTime: string
}

/** 工单详情里关联设备快照（与后端 WorkOrderDetailVO.Equipment 对应） */
export interface WorkOrderEquipment {
  id: number
  name: string
  code: string
  model: string | null
  vendor: string | null
  lineName: string | null
  status: EquipmentStatus
  health: number | null
  riskLevel: RiskLevel | null
  temperature: number | null
  vibration: number | null
  current: number | null
  recordTime: string | null
  h24: number | null
  d7: number | null
  d30: number | null
  diagnosis: string | null
}

/** 工单详情（GET /api/work-orders/{id}） */
export interface WorkOrderDetail {
  workOrder: WorkOrderRow
  /** 设备已被逻辑删除时为 null */
  equipment: WorkOrderEquipment | null
}

/** 工单列表查询条件（与后端 WorkOrderQuery 字段一一对应） */
export interface WorkOrderQuery {
  /** 待处理 / 维修中 / 待验证 / 已完成；「全部」或不传表示不过滤 */
  status?: string
  /** 紧急 / 高 / 中 / 低；「全部」或不传表示不过滤 */
  priority?: string
  faultType?: string
  equipmentId?: number
  assigneeId?: number
  lineName?: string
  source?: string
  /** yyyy-MM-dd HH:mm:ss */
  startTime?: string
  endTime?: string
  /** 匹配工单编号、设备名称、设备编号 */
  keyword?: string
  /** 页码，从 1 开始。传了才返回分页对象 */
  page?: number
  size?: number
  sortField?: 'id' | 'createTime' | 'priority' | 'status'
  sortOrder?: 'asc' | 'desc'
}

/** 新建工单请求体（与后端 WorkOrderCreateRequest 一一对应，全部必填） */
export interface WorkOrderCreatePayload {
  equipmentId: number
  faultType: string
  priority: Priority
  description: string
  assigneeId: number
  /** 可选；不传后端写「人工创建」 */
  source?: string
}

/** 修改工单请求体（状态走 changeWorkOrderStatus） */
export interface WorkOrderUpdatePayload {
  faultType?: string
  priority?: Priority
  description?: string
  assigneeId?: number
}

/** 工单 KPI（GET /api/work-orders/summary） */
export interface WorkOrderSummary {
  today: number
  pending: number
  repairing: number
  verifying: number
  completed: number
  total: number
}

/** 下拉选项里的设备项 */
export interface WorkOrderEquipmentOption {
  id: number
  code: string
  name: string
  status: EquipmentStatus
  /** 已拼好的「名称（编号）」 */
  label: string
}

/** 下拉选项里的用户项 */
export interface WorkOrderUserOption {
  id: number
  username: string
  realName: string | null
  label: string
}

/** 下拉选项（GET /api/work-orders/options） */
export interface WorkOrderOptions {
  status: string[]
  priority: string[]
  faultType: string[]
  equipments: WorkOrderEquipmentOption[]
  users: WorkOrderUserOption[]
}

/* ============================================================
 * 二、归一化工具
 * ============================================================ */

/** Long → number；null/undefined 保持 null */
function toNum(v: unknown): number | null {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

/**
 * 把 UI 上的「全部」归一成 undefined。
 * <p>
 * 与 equipment.ts 的 cleanQuery 同款：空串会被拼成 `?status=`，
 * 虽然后端也能处理，但会把无效条件污染进请求日志，排查时看不出条件到底有没有生效。
 */
function cleanQuery(q: WorkOrderQuery): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(q)) {
    if (v === undefined || v === null) continue
    if (typeof v === 'string') {
      const t = v.trim()
      if (t === '' || t === '全部' || t.toLowerCase() === 'all') continue
      out[k] = t
    } else {
      out[k] = v
    }
  }
  return out
}

/** 行归一：把后端返回的字符串 id 转回 number */
function normalizeRow(raw: WorkOrderRow): WorkOrderRow {
  return {
    ...raw,
    id: Number(raw.id),
    equipmentId: toNum(raw.equipmentId),
    assigneeId: toNum(raw.assigneeId),
    healthScore: toNum(raw.healthScore)
  }
}

/* ============================================================
 * 三、接口
 * ============================================================ */

/**
 * 工单列表（分页）。
 * <p>
 * 后端是双模式：传 page 返回分页对象，不传返回数组。
 * 这里固定传 page/size，返回的永远是分页对象。
 */
export async function fetchWorkOrderList(
  query: WorkOrderQuery = {}
): Promise<PageResult<WorkOrderRow>> {
  const params = cleanQuery({ page: 1, size: 10, ...query })
  const raw = await api.get<PageResult<WorkOrderRow> | WorkOrderRow[]>('/api/work-orders', params)
  if (Array.isArray(raw)) {
    const size = Number(params.size) || raw.length || 1
    return {
      total: raw.length,
      page: 1,
      size,
      pages: Math.ceil(raw.length / size) || 1,
      list: raw.map(normalizeRow)
    }
  }
  return { ...raw, list: (raw.list ?? []).map(normalizeRow) }
}

/**
 * 工单详情。
 * <p>
 * 一次返回工单 + 关联设备快照 —— 原型要求「工单信息与关联设备一并展示，无需再打开设备弹窗」。
 */
export async function fetchWorkOrderDetail(id: number): Promise<WorkOrderDetail> {
  const raw = await api.get<WorkOrderDetail>(`/api/work-orders/${id}`)
  const equip = raw.equipment
  return {
    workOrder: normalizeRow(raw.workOrder),
    equipment: equip
      ? {
          ...equip,
          id: Number(equip.id),
          health: toNum(equip.health),
          temperature: toNum(equip.temperature),
          vibration: toNum(equip.vibration),
          current: toNum(equip.current),
          h24: toNum(equip.h24),
          d7: toNum(equip.d7),
          d30: toNum(equip.d30)
        }
      : null
  }
}

/**
 * 新建工单。
 * <p>
 * 返回的是<b>写库后重新查出来的整行</b>（含后端生成的 woNo），
 * 所以前端拿到就能直接插进列表，不用再刷一次。
 */
export async function createWorkOrder(body: WorkOrderCreatePayload): Promise<WorkOrderRow> {
  const raw = await api.post<WorkOrderRow>('/api/work-orders', body)
  return normalizeRow(raw)
}

/** 修改工单（不含状态、不含设备） */
export async function updateWorkOrder(id: number, body: WorkOrderUpdatePayload): Promise<WorkOrderRow> {
  const raw = await api.put<WorkOrderRow>(`/api/work-orders/${id}`, body)
  return normalizeRow(raw)
}

/**
 * 推进工单状态。
 * <p>
 * 后端会校验流转合法性：待处理 → 维修中 → 待验证 → 已完成，
 * 且允许「维修中 → 待处理」回退（派错人时退回池子）。
 * 非法流转后端返回 code=10003，会被 request.ts 转成 reject。
 */
export async function changeWorkOrderStatus(
  id: number,
  status: WorkOrderStatus,
  remark?: string
): Promise<WorkOrderRow> {
  const raw = await api.put<WorkOrderRow>(`/api/work-orders/${id}/status`, { status, remark })
  return normalizeRow(raw)
}

/** 删除工单（逻辑删除，管理员专属） */
export function deleteWorkOrder(id: number): Promise<void> {
  return api.delete<void>(`/api/work-orders/${id}`)
}

/** 工单 KPI */
export function fetchWorkOrderSummary(): Promise<WorkOrderSummary> {
  return api.get<WorkOrderSummary>('/api/work-orders/summary')
}

/** 下拉选项（状态 / 优先级 / 故障类型 / 设备 / 负责人） */
export async function fetchWorkOrderOptions(): Promise<WorkOrderOptions> {
  const raw = await api.get<WorkOrderOptions>('/api/work-orders/options')
  return {
    status: raw.status ?? [],
    priority: raw.priority ?? [],
    faultType: raw.faultType ?? [],
    equipments: (raw.equipments ?? []).map((e) => ({ ...e, id: Number(e.id) })),
    users: (raw.users ?? []).map((u) => ({ ...u, id: Number(u.id) }))
  }
}