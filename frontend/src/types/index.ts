/** 全局类型定义：与后端 `industrial_agent` 库的表结构对应 */

/** 统一返回体，对应后端 TestController.Result */
export interface ApiResult<T = unknown> {
  code: number
  msg: string
  data: T
}

/** 设备状态（对应 base_equipment.status） */
export type EquipmentStatus = '正常' | '预警' | '故障' | '离线'
/** 风险等级 */
export type RiskLevel = '高' | '中' | '低'
/** 工单优先级 */
export type Priority = '紧急' | '高' | '中' | '低'
/** 工单状态 */
export type WorkOrderStatus = '待处理' | '维修中' | '待验证' | '已完成'
/** Copilot 操作执行状态 */
export type ExecStatus = '待确认' | '已执行' | '已拒绝' | '执行失败'
/** 知识类型 */
export type KnowledgeType = '维修手册' | '操作规程' | '故障案例' | '检验规范' | '设备数据' | '工艺文件'

/** 操作执行流水：Copilot / Agent 产生、需要人确认的设备动作（ai_agent_task + copilot_wo_draft 合并视图） */
export interface Execution {
  id: number
  /** 操作编号，如 EXE-20260907-006 */
  execNo: string
  /** 来源智能体 */
  agentName: string
  /** 操作类型 */
  actionType: string
  /** 设备名称 */
  equipmentName: string
  /** 动作内容 */
  content: string
  /** 优先级 */
  priority: Priority
  /** 创建时间 */
  createTime: string
  /** 确认状态 */
  status: ExecStatus
  /** 执行人 */
  operator: string
  /** 执行结果 */
  result: string
  /** 关联工单号（有则说明已流转） */
  woNo: string
}

/** 知识库文档（Mock 版本，字段与 kb_document 对应 + 展示用冗余字段） */
export interface KnowledgeDoc {
  id: number
  docName: string
  format: 'PDF' | 'Word' | 'Excel' | 'Markdown' | '图片'
  size: string
  uploader: string
  uploadTime: string
  parseStatus: '已解析' | '解析中'
  knowledgeType: KnowledgeType
  relatedEquipment: string
  /** 被 Agent 调用次数 */
  callCount: number
  /** 分段数 */
  chunks: number
}

/** 设备：对应表 base_equipment + eqp_runtime_data */
export interface Equipment {
  id: number
  /** 设备编号，如 EQ-IM-003 */
  code: string
  name: string
  type: string
  model: string
  vendor: string
  lineName: string
  status: EquipmentStatus
  /** 健康度评分 0-100 */
  health: number
  riskLevel: RiskLevel
  /**
   * 以下字段全部可空：新建设备还没接实时数据、也没跑过故障预测时，
   * 后端 eqp_runtime_data / eqp_fault_predict / ai_insight 里没有对应记录，
   * 返回的就是 null。模板必须走安全访问，否则整块 UI 渲染中断。
   */
  /** 最近异常描述 */
  lastException: string | null
  /** 实时数据（eqp_runtime_data） */
  temperature: number | null
  vibration: number | null
  current: number | null
  /** 故障预测：24h / 7d / 30d 概率 */
  predict: { h24: number; d7: number; d30: number; modelVersion?: string } | null
  /** AI 诊断结论 */
  diagnosis: string | null
}

/** 维修工单：对应表 eqp_work_order */
export interface WorkOrder {
  id: number
  woNo: string
  equipmentId: number
  equipmentName: string
  faultType: string
  priority: Priority
  description: string
  assignee: string
  status: WorkOrderStatus
  source: string
  createTime: string
}

/** AI 洞察：对应表 ai_insight */
export interface Insight {
  id: number
  title: string
  objectType: string
  objectName: string
  aiJudgement: string
  probability: number
  riskLevel: RiskLevel
  impact: string
  discoverTime: string
  agent: string
}

/** 智能体：对应表 ai_agent */
export interface Agent {
  id: number
  name: string
  type: string
  desc: string
  permission: string
  status: '运行中' | '已停用'
  /** 我的 Agent 列表里才有 */
  enabled?: boolean
}

/** 智能体任务：对应表 ai_agent_task */
export interface AgentTask {
  id: number
  taskName: string
  agentName: string
  triggerTime: string
  status: '等待确认' | '执行中' | '已完成' | '执行失败'
  result: string
  duration: string
  input: string
  decision: string
}

/** 智能体运行记录：对应表 ai_agent_run_log */
export interface AgentRunLog {
  id: number
  time: string
  agentName: string
  taskName: string
  tools: string
  result: '执行成功' | '等待确认' | '执行失败'
  duration: string
  token: string
  cost: string
}

/** 工单草稿（Copilot 生成）：对应表 copilot_wo_draft */
export interface WoDraft {
  id: number
  draftNo: string
  agentName: string
  equipmentName: string
  faultType: string
  priority: Priority
  createTime: string
  status: '待确认' | '已确认' | '已驳回'
  woNo: string
  description: string
  assignee: string
}

/** 报表：对应表 ai_report */
export interface Report {
  id: number
  title: string
  type: string
  scope: string
  createTime: string
  source: string
  status: string
}

/** 产线：对应表 base_production_line */
export interface ProductionLine {
  name: string
  status: '执行中' | '已延期'
  product: string
  workTime: string
  target: number
  actual: number
  achieve: number
  oee: number
  oeeDelta: string
  quality: number
  qualityNote: string
  achieveNote: string
  planProgress: number
  batchProgress: number
  currentBatch: string
  currentBatchQty: string
  changeover: string
  availability: number
  performance: number
  qualityIndex: number
  losses: { name: string; value: number }[]
  aiHint: string
}

/** 生产计划：对应表 prod_plan */
export interface PlanItem {
  id: number
  planDate: string
  lineName: string
  product: string
  targetQty: number | null
  actualQty: number | null
  achieve: number | null
  status: '执行中' | '已延期' | '待执行'
  aiExplanation: string
}

/** 缺陷 Top */
export interface DefectItem {
  type: string
  qty: number
  rate: number
  lineName: string
}

/** 能耗优化方案 */
export interface EnergyPlan {
  id: number
  code: string
  name: string
  location: string
  medium: string
  status: '待执行' | '已完成' | '执行失败'
  saving: string
  result: string
  execTime: string
  source: string
  reason: string
  advice: string
  scope: string
}

/** 知识库文档：对应表 kb_document */
export interface KbDocument {
  id: number
  fileName: string
  format: string
  size: string
  uploader: string
  uploadTime: string
  parseStatus: '已解析' | '解析中'
  docType: string
  relatedEquipment: string
}