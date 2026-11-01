import { api } from './request'
import type { Equipment } from '../types'

/**
 * 设备中心接口。
 * <p>
 * 契约来源：docs/API接口清单.md §14-§16 + 后端 EquipmentController 实际实现。
 * 后端返回 { code, msg, data }，request.ts 的拦截器已经把 code !== 200 转成 reject，
 * 所以这里拿到的 data 一定是成功结果。
 */

/** 后端分页对象 */
export interface PageResult<T> {
  total: number
  page: number
  size: number
  pages: number
  list: T[]
}

/** 设备列表查询条件（与后端 EquipmentQuery 字段一一对应） */
export interface EquipmentQuery {
  /** 正常 / 预警 / 故障 / 离线；「全部」或不传表示不过滤 */
  status?: string
  /** A产线 / B产线 / C产线；「全部」或不传表示不过滤 */
  lineName?: string
  /** 高 / 中 / 低；「全部」或不传表示不过滤 */
  riskLevel?: string
  /** 匹配设备名称、编号、厂商 */
  keyword?: string
  /** 页码，从 1 开始。传了才返回分页对象 */
  page?: number
  /** 每页条数，最大 500 */
  size?: number
  sortField?: 'id' | 'health' | 'code' | 'updateTime'
  sortOrder?: 'asc' | 'desc'
  minHealth?: number
  maxHealth?: number
}

/**
 * 把 UI 上的「全部」归一成 undefined。
 * <p>
 * <b>为什么必须清掉而不是原样传</b>：后端虽然也把「全部」当 null 处理，
 * 但 axios 默认会把 `undefined` 的键整个丢掉，而空串 `''` 会被拼成
 * `?status=`。传空串虽然也能工作，但会把筛选条件污染进请求日志和缓存 key，
 * 排查时看不出「这个条件到底有没有生效」。所以统一清成 undefined。
 */
function cleanQuery(q: EquipmentQuery): Record<string, unknown> {
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

/**
 * 设备列表（分页）。
 * <p>
 * 后端是双模式：传 page 返回分页对象，不传返回数组。
 * 这里固定传 page/size，所以返回的永远是分页对象 —— 用统一形状可以少一处类型判断。
 */
export async function fetchEquipmentList(
  query: EquipmentQuery = {}
): Promise<PageResult<Equipment>> {
  const params = cleanQuery({ page: 1, size: 10, ...query })
  const raw = await api.get<PageResult<Equipment> | Equipment[]>('/api/equipment', params)
  // 双模式下不传 page 会返回数组，这里兜一层，调用方永远拿到分页对象
  if (Array.isArray(raw)) {
    const size = Number(params.size) || raw.length
    return {
      total: raw.length,
      page: 1,
      size,
      pages: Math.ceil(raw.length / size) || 1,
      list: raw
    }
  }
  return raw
}

/** 设备详情 */
export function fetchEquipment(id: number): Promise<Equipment> {
  return api.get<Equipment>(`/api/equipment/${id}`)
}

/** 设备总览 KPI */
export interface EquipmentSummary {
  total: number
  normal: number
  warning: number
  fault: number
  offline: number
  online: number
  onlineRate: number
  highRisk: number
  midRisk: number
  lowRisk: number
  byLine: { lineId: number; lineName: string; total: number; normal: number; abnormal: number }[]
}

export function fetchEquipmentSummary(): Promise<EquipmentSummary> {
  return api.get<EquipmentSummary>('/api/equipment/summary')
}

/** 新增 / 修改设备的请求体 */
export interface EquipmentPayload {
  code?: string
  name?: string
  lineId?: number
  type?: string
  model?: string
  vendor?: string
  health?: number
  riskLevel?: string
  status?: string
  lastException?: string
}

export function createEquipment(body: EquipmentPayload): Promise<Equipment> {
  return api.post<Equipment>('/api/equipment', body)
}

export function updateEquipment(id: number, body: EquipmentPayload): Promise<Equipment> {
  return api.put<Equipment>(`/api/equipment/${id}`, body)
}

export function deleteEquipment(id: number): Promise<void> {
  return api.delete<void>(`/api/equipment/${id}`)
}

/** 产线下拉选项（来自 base_production_line，只含运行中的产线） */
export interface LineOption {
  id: number
  lineName: string
  workshopId: number | null
}

/** 后端枚举字典（GET /api/equipment/options） */
export interface EquipmentOptions {
  status: string[]
  riskLevel: string[]
  sortField: string[]
  sortOrder: string[]
  lines: LineOption[]
}

/**
 * 枚举字典 + 产线列表。
 * <p>
 * 新增设备必须提交真实的 {@code lineId}，所以产线不能在前端硬编码 ——
 * 这里直接拿后端返回的 id，避免「A产线 → 猜成 id=1」这种脆弱的假设。
 * <p>
 * <b>为什么要在这里做一次 id 归一</b>：后端配了 Jackson 的 Long→String
 * （防止 JS 大整数精度丢失），所以即使表里是 BIGINT，JSON 里拿到的是
 * {@code "1"} 而不是 {@code 1}。功能上两者都能用（反向提交时 Jackson 会把
 * "1" 反序列化回 Long），但 {@code el-select} 的 {@code v-model} 是严格比较的，
 * 如果下拉 value 是字符串而表单初值写成数字，选中后回显会「选了但框里没字」。
 * 统一转成 number，让类型声明和运行时行为保持一致。
 */
export async function fetchEquipmentOptions(): Promise<EquipmentOptions> {
  const raw = await api.get<EquipmentOptions>('/api/equipment/options')
  return {
    ...raw,
    lines: (raw.lines ?? []).map((l) => ({
      id: Number(l.id),
      lineName: l.lineName,
      workshopId: l.workshopId == null ? null : Number(l.workshopId)
    }))
  }
}