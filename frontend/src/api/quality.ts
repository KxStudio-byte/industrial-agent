import { api } from './request'

/**
 * 质量中心接口。
 *
 * 契约来源：docs/API接口清单.md §21 + 后端 QualityController 实际实现。
 * 后端返回 { code, msg, data }，request.ts 的拦截器已经把 code !== 200 转成 reject，
 * 所以这里拿到的 data 一定是成功结果。
 *
 * ⚠️ 后端 Jackson 会把 BigDecimal 序列化成数字、Long 序列化成字符串（如 "131"），
 * 所以凡是后端为 Long 的字段（lineId / equipmentId），这里都用 num() 归一化成 number。
 */

/** 查询参数（与后端 QualityQuery 字段一一对应） */
export interface QualityQuery {
  /** 统计基准日期，默认今天。格式 yyyy-MM-dd */
  date?: string
  /** 缺陷统计窗口天数，默认 30，范围 1~365 */
  days?: number
  /**
   * 批次合格判定阈值(%)：批次不良率 ≤ 该值即合格。
   * 后端默认 4.0。不传则用后端默认值。
   */
  batchPassThreshold?: number
}

/** 质量总览 KPI */
export interface QualitySummary {
  /** 良品率(%)，数量口径 = Σ良品 / Σ(良品+不良) */
  yieldRate: number
  /** 较昨日变化文案，如 "较昨日 +0.04pct"；昨日无数据时为 "昨日无数据" */
  yieldDelta: string
  /** 缺陷数量（件），单日。注意是件数不是记录条数 */
  defects: number
  /** 缺陷数说明 */
  defectsNote: string
  /** 合格率(%)，数量口径，与 yieldRate 同算式但独立字段 */
  passRate: number
  /** 批次合格率(%)，批次口径 */
  batchRate: number
  /** 批次合格率说明 */
  batchRateNote: string
  /** 本次采用的批次合格阈值(%) */
  batchPassThreshold: number
  /** 客诉数量（条），本周口径 */
  complaints: number
  /** 客诉说明 */
  complaintsNote: string
  /** AI 质量分析摘要。本期后端固定返回 null（ai-service 未部署） */
  aiSummary: string | null
  /** aiSummary 不可用的原因，可直接展示 */
  aiSummaryUnavailableReason: string | null
  /** 统计口径说明 */
  scope: string
}

/** 缺陷 Top 一行 */
export interface DefectTop {
  type: string
  qty: number
  /** 不良构成占比(%) = 该类型缺陷件数 / 窗口内总缺陷件数 × 100 */
  rate: number
  /** 该类型缺陷件数最多的产线 */
  lineName: string
}

/** 产线维度一行 */
export interface DefectByLine {
  lineName: string
  lineId: number
  /** 良品率(%)，单日 */
  rate: number
  /** 批次合格率(%)，单日 */
  batchRate: number
  defectQty: number
  mainDefect: string
  mainDefectQty: number
  /** 较昨日良品率变化，如 "-0.21"；昨日无数据时 null */
  delta: string | null
}

/** 产品维度一行 */
export interface DefectByProduct {
  product: string
  /** 良品率(%) */
  rate: number
  /** 产量（件） */
  qty: number
  defectQty: number
}

/** 设备维度一行 */
export interface DefectByEquipment {
  equipment: string
  equipmentId: number
  lineName: string
  defectType: string
  qty: number
  /** 不良贡献(%) */
  share: number
  /** 健康度 0~100 */
  health: number
  riskLevel: string
  status: string
}

/** 缺陷统计（4 个区块） */
export interface QualityDefects {
  top: DefectTop[]
  byLine: DefectByLine[]
  byProduct: DefectByProduct[]
  byEquipment: DefectByEquipment[]
  /** 统计口径说明 */
  scope: string
}

/** 后端数字字段兜底：可能是 number 也可能被序列化成 string */
function num(v: unknown, fallback = 0): number {
  if (typeof v === 'number' && Number.isFinite(v)) return v
  if (typeof v === 'string' && v.trim() !== '') {
    const n = Number(v)
    if (Number.isFinite(n)) return n
  }
  return fallback
}

/** 质量总览 KPI */
export async function fetchQualitySummary(query: QualityQuery = {}): Promise<QualitySummary> {
  const raw = await api.get<Record<string, unknown>>('/api/quality/summary', {
    date: query.date,
    days: query.days,
    batchPassThreshold: query.batchPassThreshold
  })
  return {
    yieldRate: num(raw.yieldRate),
    yieldDelta: String(raw.yieldDelta ?? ''),
    defects: num(raw.defects),
    defectsNote: String(raw.defectsNote ?? ''),
    passRate: num(raw.passRate),
    batchRate: num(raw.batchRate),
    batchRateNote: String(raw.batchRateNote ?? ''),
    batchPassThreshold: num(raw.batchPassThreshold, 4),
    complaints: num(raw.complaints),
    complaintsNote: String(raw.complaintsNote ?? ''),
    aiSummary: raw.aiSummary == null ? null : String(raw.aiSummary),
    aiSummaryUnavailableReason:
      raw.aiSummaryUnavailableReason == null ? null : String(raw.aiSummaryUnavailableReason),
    scope: String(raw.scope ?? '')
  }
}

/** 缺陷统计：Top + 产线/产品/设备三维度 */
export async function fetchQualityDefects(query: QualityQuery = {}): Promise<QualityDefects> {
  const raw = await api.get<Record<string, unknown>>('/api/quality/defects', {
    date: query.date,
    days: query.days,
    batchPassThreshold: query.batchPassThreshold
  })

  const arr = (v: unknown): Record<string, unknown>[] =>
    Array.isArray(v) ? (v as Record<string, unknown>[]) : []

  return {
    top: arr(raw.top).map((r) => ({
      type: String(r.type ?? ''),
      qty: num(r.qty),
      rate: num(r.rate),
      lineName: r.lineName == null ? '—' : String(r.lineName)
    })),
    byLine: arr(raw.byLine).map((r) => ({
      lineName: String(r.lineName ?? ''),
      lineId: num(r.lineId),
      rate: num(r.rate),
      batchRate: num(r.batchRate),
      defectQty: num(r.defectQty),
      mainDefect: r.mainDefect == null ? '—' : String(r.mainDefect),
      mainDefectQty: num(r.mainDefectQty),
      delta: r.delta == null ? null : String(r.delta)
    })),
    byProduct: arr(raw.byProduct).map((r) => ({
      product: String(r.product ?? ''),
      rate: num(r.rate),
      qty: num(r.qty),
      defectQty: num(r.defectQty)
    })),
    byEquipment: arr(raw.byEquipment).map((r) => ({
      equipment: String(r.equipment ?? ''),
      equipmentId: num(r.equipmentId),
      lineName: r.lineName == null ? '—' : String(r.lineName),
      defectType: String(r.defectType ?? ''),
      qty: num(r.qty),
      share: num(r.share),
      health: num(r.health),
      riskLevel: String(r.riskLevel ?? ''),
      status: String(r.status ?? '')
    })),
    scope: String(raw.scope ?? '')
  }
}