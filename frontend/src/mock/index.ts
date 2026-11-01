/**
 * 全局模拟数据。
 *
 * 字段命名与 `industrial_agent` 库的表结构一一对应：
 *   base_equipment / eqp_runtime_data / eqp_work_order / ai_insight / ai_agent
 *   ai_agent_task / ai_agent_run_log / copilot_wo_draft / kb_document / prod_plan
 *
 * 等后端接口就绪后，把 views 里的 `import { ... } from '@/mock'` 换成 `api.get(...)` 即可，
 * 页面模板不需要改。
 */

import type {
  Agent,
  AgentRunLog,
  AgentTask,
  DefectItem,
  EnergyPlan,
  Equipment,
  Execution,
  Insight,
  KbDocument,
  KnowledgeDoc,
  PlanItem,
  ProductionLine,
  Report,
  WoDraft,
  WorkOrder
} from '../types'

/* ======================= 产线 ======================= */

export const productionLines: ProductionLine[] = [
  {
    name: 'A产线',
    status: '执行中',
    product: '外壳组件 A-220',
    workTime: '08:00—20:00',
    target: 4800,
    actual: 4460,
    achieve: 92.9,
    oee: 89.4,
    oeeDelta: '较昨日 -0.8pct',
    quality: 98.4,
    qualityNote: '良品率 98.4%',
    achieveNote: '落后 340 件',
    planProgress: 92.9,
    batchProgress: 96,
    currentBatch: '第 3 批 · 外壳A-220',
    currentBatchQty: '完成 96%',
    changeover: '换线损失 0:22',
    availability: 95.1,
    performance: 95.8,
    qualityIndex: 98.1,
    losses: [
      { name: '设备停机', value: 42 },
      { name: '换线时间', value: 28 },
      { name: '质量损失', value: 19 },
      { name: '其他', value: 11 }
    ],
    aiHint: 'A产线整体平稳，3号注塑机轴承预警导致节拍略降，建议 48 小时内更换。'
  },
  {
    name: 'B产线',
    status: '已延期',
    product: '结构件 B-18',
    workTime: '08:00—20:00',
    target: 3600,
    actual: 2760,
    achieve: 76.7,
    oee: 78.4,
    oeeDelta: '较昨日 -9.2pct',
    quality: 97.2,
    qualityNote: '色差 8 件',
    achieveNote: '落后 840 件',
    planProgress: 76.7,
    batchProgress: 47,
    currentBatch: '第 3 批 · 结构件B-18',
    currentBatchQty: '完成 47%',
    changeover: '换线损失 14:00—15:30 增加 23%',
    availability: 88.6,
    performance: 91.2,
    qualityIndex: 97.0,
    losses: [
      { name: '设备停机', value: 42 },
      { name: '换线时间', value: 28 },
      { name: '质量损失', value: 19 },
      { name: '其他', value: 11 }
    ],
    aiHint: 'B产线 14:00—15:30 换线超时 23%，是今日 OEE 下降主因，建议优化换线 SOP。'
  },
  {
    name: 'C产线',
    status: '执行中',
    product: '精密件 C-05',
    workTime: '08:00—20:00',
    target: 4400,
    actual: 4740,
    achieve: 107.7,
    oee: 91.2,
    oeeDelta: '较昨日 +0.4pct',
    quality: 96.5,
    qualityNote: '外观划伤 42 件',
    achieveNote: '超产 340 件',
    planProgress: 107.7,
    batchProgress: 88,
    currentBatch: '第 4 批 · 精密件C-05',
    currentBatchQty: '完成 88%',
    changeover: '换线损失 0:18',
    availability: 96.2,
    performance: 96.5,
    qualityIndex: 98.2,
    losses: [
      { name: '设备停机', value: 42 },
      { name: '换线时间', value: 28 },
      { name: '质量损失', value: 19 },
      { name: '其他', value: 11 }
    ],
    aiHint: 'C产线超产，但外观划伤 42 件集中在夹具工位，建议校正夹具并复检批次。'
  }
]

/* ======================= 工厂总览 ======================= */

export const factorySummary = {
  todayOutput: 11960,
  actualQty: 11960,
  todayPlan: 12800,
  planQty: 12800,
  achieve: 93.4,
  achieveNote: '较昨日 -2.1pct',
  oee: 87.6,
  oeeDelta: '较昨日 -4.8pct',
  quality: 97.1,
  qualityDelta: '较昨日 -0.6pct',
  alerts: 3,
  alertsNote: '2 项需关注',
  equipmentTotal: 48,
  equipmentNote: '三车间',
  online: 44,
  onlineRate: '91.7%',
  offline: 2,
  offlineNote: '检测仪等',
  abnormal: 3,
  abnormalNote: '含故障 1 台',
  highRisk: 2,
  highRiskNote: '需预测性维护',
  planProgress: 93.4,
  aiSummary:
    'B产线达成率 76.7%、OEE 78.4%，14:00—15:30 换线超时 23%，当前第 3 批仅完成 47%。C产线虽超额完成，良品率 96.5% 受外观划伤拖累。建议先看 OEE 分析，再决定是否调整换线 SOP。'
}

/* ======================= 设备 ======================= */

export const equipments: Equipment[] = [
  {
    id: 3,
    code: 'EQ-IM-003',
    name: '3号注塑机',
    type: '注塑机',
    model: 'IM-220T',
    vendor: '海天塑机',
    lineName: 'A产线',
    status: '预警',
    health: 82,
    riskLevel: '中',
    lastException: '振动持续升高',
    temperature: 78,
    vibration: 7.8,
    current: 38.6,
    predict: { h24: 12, d7: 38, d30: 67 },
    diagnosis: '主轴轴承磨损概率较高，建议 48 小时内更换 6208 轴承。'
  },
  {
    id: 4,
    code: 'EQ-AC-004',
    name: '4号空压机',
    type: '空压机',
    model: 'AC-160',
    vendor: '阿特拉斯',
    lineName: 'A产线',
    status: '预警',
    health: 71,
    riskLevel: '高',
    lastException: '低负载空转',
    temperature: 92,
    vibration: 5.4,
    current: 24.1,
    predict: { h24: 21, d7: 44, d30: 58 },
    diagnosis: '长期低负载空转，单位气耗偏高，建议纳入联控并做泄漏排查。'
  },
  {
    id: 5,
    code: 'EQ-IM-005',
    name: '5号注塑机',
    type: '注塑机',
    model: 'IM-220T',
    vendor: '海天塑机',
    lineName: 'C产线',
    status: '故障',
    health: 41,
    riskLevel: '高',
    lastException: '电控柜过热保护',
    temperature: 78,
    vibration: 9.2,
    current: 41.7,
    predict: { h24: 62, d7: 88, d30: 96 },
    diagnosis: '电控柜温度 78℃，已触发过热保护停机 38 分钟，需立即检修。'
  }
]

export const riskEquipments = equipments.filter((e) => e.status !== '正常')

/* ======================= 维修工单 ======================= */

export const workOrders: WorkOrder[] = [
  {
    id: 18,
    woNo: 'WO-20260907-018',
    equipmentId: 3,
    equipmentName: '3号注塑机',
    faultType: '轴承异常',
    priority: '紧急',
    description: '振动值 7.8mm/s 持续升高，主轴轴承磨损概率 87%。',
    assignee: '吴运成',
    status: '待处理',
    source: '设备运维Agent 自动创建',
    createTime: '2026-09-07 16:58'
  },
  {
    id: 17,
    woNo: 'WO-20260907-017',
    equipmentId: 5,
    equipmentName: '5号注塑机',
    faultType: '电气故障',
    priority: '紧急',
    description: '电控柜过热保护动作，设备停机 38 分钟。',
    assignee: '赵德海',
    status: '维修中',
    source: '故障预测Agent 预警',
    createTime: '2026-09-07 14:05'
  },
  {
    id: 16,
    woNo: 'WO-20260907-016',
    equipmentId: 4,
    equipmentName: '4号空压机',
    faultType: '泄漏',
    priority: '高',
    description: '管网压力波动，疑似接头泄漏。',
    assignee: '赵德海',
    status: '待验证',
    source: '设备运维Agent 自动创建',
    createTime: '2026-09-07 15:11'
  },
  {
    id: 15,
    woNo: 'WO-20260907-015',
    equipmentId: 3,
    equipmentName: '3号注塑机',
    faultType: '过热',
    priority: '中',
    description: '料筒加热圈温度超上限 5℃。',
    assignee: '吴运成',
    status: '已完成',
    source: '人工创建',
    createTime: '2026-09-07 09:32'
  }
]

/* ======================= AI 洞察 ======================= */

export const insights: Insight[] = [
  {
    id: 1,
    title: '5号注塑机电控柜存在过热停机风险',
    objectType: '设备',
    objectName: '5号注塑机',
    aiJudgement: '柜内温度已连续 3 小时高于 70℃，24 小时故障概率 62%。',
    probability: 62,
    riskLevel: '高',
    impact: '预计影响产量 240 件',
    discoverTime: '2026-09-07 15:32',
    agent: '故障预测Agent'
  },
  {
    id: 2,
    title: 'B产线换线时间显著高于基线',
    objectType: '产线',
    objectName: 'B产线',
    aiJudgement: '今日换线 90 分钟，基线 73 分钟，超时 23%，集中在下午换型。',
    probability: 78,
    riskLevel: '高',
    impact: 'OEE 影响约 3.3pct',
    discoverTime: '2026-09-07 15:10',
    agent: '生产分析Agent'
  },
  {
    id: 3,
    title: 'C产线外观划伤集中于夹具工位',
    objectType: '产线',
    objectName: 'C产线',
    aiJudgement: '外观划伤 42 件中 30 件来自同一夹具，定位偏差 0.12mm 超公差。',
    probability: 84,
    riskLevel: '中',
    impact: '良品率下降 0.6pct',
    discoverTime: '2026-09-07 13:48',
    agent: '质量分析Agent'
  },
  {
    id: 4,
    title: 'A车间夜间电耗高于白班',
    objectType: '车间',
    objectName: 'A车间',
    aiJudgement: '夜间电耗较白班 +18.7%，与 4号空压机低负载空转强相关。',
    probability: 71,
    riskLevel: '中',
    impact: '月成本约 ¥28,600',
    discoverTime: '2026-09-07 08:20',
    agent: '能耗分析Agent'
  }
]

/* ======================= 智能体 ======================= */

export const agents: Agent[] = [
  {
    id: 1,
    name: '生产调度Agent',
    type: '生产类',
    desc: '根据订单、设备与人员状态编排生产计划，异常时给出排程调整建议。',
    permission: '人工确认后执行',
    status: '运行中'
  },
  {
    id: 2,
    name: '生产分析Agent',
    type: '生产类',
    desc: '归因分析产量、达成率与 OEE 波动，输出可执行的改进建议。',
    permission: '建议',
    status: '运行中'
  },
  {
    id: 3,
    name: '设备运维Agent',
    type: '设备类',
    desc: '融合 IoT 实时数据与维修知识，自动生成工单草稿并推荐负责人。',
    permission: '人工确认后执行',
    status: '运行中'
  },
  {
    id: 4,
    name: '故障预测Agent',
    type: '设备类',
    desc: '基于振动、温度、电流趋势预测设备故障概率与剩余寿命。',
    permission: '建议',
    status: '运行中'
  },
  {
    id: 5,
    name: '设备诊断Agent',
    type: '设备类',
    desc: '回答设备故障排查问题，检索手册与历史案例给出处置步骤。',
    permission: '只读',
    status: '运行中'
  },
  {
    id: 6,
    name: '质量分析Agent',
    type: '质量类',
    desc: '分析不良分布与批次追溯，定位根因并建议整改措施。',
    permission: '建议',
    status: '运行中'
  },
  {
    id: 7,
    name: '能耗分析Agent',
    type: '能源类',
    desc: '拆解车间与设备能耗，识别异常用能并生成节能优化方案。',
    permission: '人工确认后执行',
    status: '运行中'
  },
  {
    id: 8,
    name: '工业报告Agent',
    type: '报告类',
    desc: '自动汇总多源数据，生成生产日报、周报与管理层决策报告。',
    permission: '建议',
    status: '运行中'
  },
  {
    id: 9,
    name: '知识问答Agent',
    type: '知识类',
    desc: '基于工业知识库回答问题，引用原文分段并给出可信度。',
    permission: '只读',
    status: '运行中'
  }
]

export const myAgents = agents.filter((a) => a.id !== 9)

/* ======================= 智能体任务 ======================= */

export const agentTasks: AgentTask[] = [
  {
    id: 1,
    taskName: '3号注塑机轴承异常处置',
    agentName: '设备运维Agent',
    triggerTime: '2026-09-07 16:58',
    status: '等待确认',
    result: '待确认',
    duration: '42.6s',
    input: '设备 EQ-IM-003 振动 7.8mm/s（阈值 6.5），连续 6 天上升',
    decision: '判定主轴轴承磨损，建议创建紧急维修工单并指派吴运成。'
  },
  {
    id: 2,
    taskName: 'B产线排程调整建议',
    agentName: '生产调度Agent',
    triggerTime: '2026-09-07 15:46',
    status: '等待确认',
    result: '待确认',
    duration: '18.2s',
    input: 'B产线达成率 76.7%，落后 840 件',
    decision: '建议拆分第 3 批并顺延 90 分钟，避免影响明日交付。'
  },
  {
    id: 3,
    taskName: '今日 OEE 下降归因分析',
    agentName: '生产分析Agent',
    triggerTime: '2026-09-07 15:10',
    status: '已完成',
    result: '已完成：设备停机贡献 42%',
    duration: '31.4s',
    input: '今日 OEE 87.6%，较昨日 -4.8pct',
    decision: '输出归因结论与 4 条改进建议。'
  },
  {
    id: 4,
    taskName: 'C产线外观划伤根因定位',
    agentName: '质量分析Agent',
    triggerTime: '2026-09-07 13:48',
    status: '已完成',
    result: '已完成：夹具定位偏差',
    duration: '26.9s',
    input: '外观划伤 42 件',
    decision: '定位夹具工位，建议校正夹具并复检批次。'
  },
  {
    id: 5,
    taskName: 'A车间夜间电耗异常检测',
    agentName: '能耗分析Agent',
    triggerTime: '2026-09-07 08:20',
    status: '已完成',
    result: '已完成：4号空压机空转',
    duration: '22.1s',
    input: 'A车间夜间电耗较白班 +18.7%',
    decision: '建议关闭 2 台低负载空压机，预计节约 ¥28,600/月。'
  },
  {
    id: 6,
    taskName: '2026-09-07 生产日报生成',
    agentName: '工业报告Agent',
    triggerTime: '2026-09-07 18:20',
    status: '执行失败',
    result: '执行失败：数据源超时',
    duration: '60.0s',
    input: '全厂生产数据汇总',
    decision: '已重试 2 次仍失败，等待人工介入。'
  }
]

/* ======================= 运行记录 ======================= */

export const agentRunLogs: AgentRunLog[] = [
  {
    id: 1,
    time: '2026-09-07 16:58',
    agentName: '设备运维Agent',
    taskName: '3号注塑机轴承异常处置',
    tools: '查询设备、检索知识库、创建工单草稿',
    result: '等待确认',
    duration: '42.6s',
    token: '12,480',
    cost: '¥0.31'
  },
  {
    id: 2,
    time: '2026-09-07 15:46',
    agentName: '生产调度Agent',
    taskName: 'B产线排程调整建议',
    tools: '查询生产、查询设备',
    result: '等待确认',
    duration: '18.2s',
    token: '6,120',
    cost: '¥0.15'
  },
  {
    id: 3,
    time: '2026-09-07 15:10',
    agentName: '生产分析Agent',
    taskName: '今日 OEE 下降归因分析',
    tools: '查询生产、查询设备、计算结果',
    result: '执行成功',
    duration: '31.4s',
    token: '9,860',
    cost: '¥0.24'
  }
]

/* ======================= 工单草稿 / 操作执行 ======================= */

export const woDrafts: WoDraft[] = [
  {
    id: 18,
    draftNo: 'AE-20260907-018',
    agentName: '设备运维Agent',
    equipmentName: '3号注塑机',
    faultType: '轴承异常',
    priority: '紧急',
    createTime: '2026-09-07 16:58',
    status: '待确认',
    woNo: '—',
    description: '振动持续升高，AI 判断主轴轴承磨损概率 87%，建议立即检修。',
    assignee: '吴运成'
  },
  {
    id: 16,
    draftNo: 'AE-20260907-016',
    agentName: '故障预测Agent',
    equipmentName: '4号空压机',
    faultType: '泄漏',
    priority: '中',
    createTime: '2026-09-07 15:11',
    status: '待确认',
    woNo: '—',
    description: '管网压力波动，疑似接头泄漏，7 日故障概率 38%。',
    assignee: '赵德海'
  },
  {
    id: 14,
    draftNo: 'AE-20260907-014',
    agentName: '生产分析Agent',
    equipmentName: '5号注塑机',
    faultType: '电气故障',
    priority: '高',
    createTime: '2026-09-07 14:22',
    status: '已确认',
    woNo: 'WO-20260907-014',
    description: '电控柜过载保护动作，设备停机。',
    assignee: '吴运成'
  },
  {
    id: 88,
    draftNo: 'AE-20260906-088',
    agentName: '设备运维Agent',
    equipmentName: '4号空压机',
    faultType: '泄漏',
    priority: '中',
    createTime: '2026-09-06 19:40',
    status: '已确认',
    woNo: 'WO-20260906-088',
    description: '管网压力波动，疑似管道接头泄漏。',
    assignee: '赵德海'
  },
  {
    id: 72,
    draftNo: 'AE-20260906-072',
    agentName: '故障预测Agent',
    equipmentName: '1号冲压机',
    faultType: '过热',
    priority: '中',
    createTime: '2026-09-06 09:18',
    status: '已驳回',
    woNo: '—',
    description: '液压油温度偏高，建议排查冷却回路。',
    assignee: '吴运成'
  }
]

/* ======================= 操作执行（Copilot / Agent 待确认动作） ======================= */

export const executions: Execution[] = [
  {
    id: 6,
    execNo: 'EXE-20260907-006',
    agentName: '设备运维Agent',
    actionType: '创建维修工单',
    equipmentName: '3号注塑机',
    content: '振动 7.8mm/s 持续升高，主轴轴承磨损概率 87%，建议创建紧急维修工单并指派吴运成。',
    priority: '紧急',
    createTime: '2026-09-07 16:58',
    status: '待确认',
    operator: '—',
    result: '等待厂长确认后流转为维修工单',
    woNo: '—'
  },
  {
    id: 5,
    execNo: 'EXE-20260907-005',
    agentName: '能耗分析Agent',
    actionType: '下发节能指令',
    equipmentName: '4号空压机',
    content: 'A车间 2 台空压机长期低负载空转，建议关闭并切换至联控模式，预计节约 ¥28,600/月。',
    priority: '高',
    createTime: '2026-09-07 16:20',
    status: '待确认',
    operator: '—',
    result: '等待厂长确认后下发 PLC 指令',
    woNo: '—'
  },
  {
    id: 4,
    execNo: 'EXE-20260907-004',
    agentName: '生产调度Agent',
    actionType: '调整生产计划',
    equipmentName: 'B产线',
    content: 'B产线延期 840 件，建议将第 3 批拆分为两段并顺延 90 分钟，避免影响明日交付。',
    priority: '中',
    createTime: '2026-09-07 15:46',
    status: '已执行',
    operator: '陈建军',
    result: '计划已调整，B产线第 3 批预计 20:40 完成',
    woNo: '—'
  },
  {
    id: 3,
    execNo: 'EXE-20260907-003',
    agentName: '设备诊断Agent',
    actionType: '参数调整',
    equipmentName: '5号注塑机',
    content: '电控柜温度 78℃，建议将冷却风机运行频率由 35Hz 提升至 45Hz。',
    priority: '高',
    createTime: '2026-09-07 14:05',
    status: '已执行',
    operator: '陈建军',
    result: '散热参数已下发，柜内温度回落至 62℃',
    woNo: '—'
  },
  {
    id: 2,
    execNo: 'EXE-20260906-002',
    agentName: '质量分析Agent',
    actionType: '创建质量工单',
    equipmentName: 'C产线夹具',
    content: '外观划伤 42 件，判定为夹具定位偏差，建议校正夹具并复检当班批次。',
    priority: '中',
    createTime: '2026-09-06 20:12',
    status: '已拒绝',
    operator: '陈建军',
    result: '已由质量部线下处理',
    woNo: '—'
  },
  {
    id: 1,
    execNo: 'EXE-20260906-001',
    agentName: '能耗分析Agent',
    actionType: '下发节能指令',
    equipmentName: 'C车间蒸汽阀',
    content: '蒸汽支路压力偏高，建议设定温度下调 2℃，预计节约 ¥9,400/月。',
    priority: '中',
    createTime: '2026-09-06 18:30',
    status: '已执行',
    operator: '陈建军',
    result: '已下发并生效，当日已兑现 ¥312',
    woNo: '—'
  }
]

/* ======================= 报表 ======================= */

export const reports: Report[] = [
  {
    id: 1,
    title: '2026-09-07 生产日报',
    type: '生产日报',
    scope: '全厂',
    createTime: '2026-09-07 18:20',
    source: '工业报告Agent',
    status: '已生成'
  },
  {
    id: 2,
    title: '2026-09-07 质量日报',
    type: '质量日报',
    scope: 'C产线',
    createTime: '2026-09-07 17:05',
    source: '质量分析Agent',
    status: '已生成'
  },
  {
    id: 3,
    title: '2026-09-07 OEE波动说明',
    type: '生产日报',
    scope: 'B产线',
    createTime: '2026-09-07 16:40',
    source: '数据分析',
    status: '已生成'
  },
  {
    id: 4,
    title: '2026-09-06 能耗日报',
    type: '能耗日报',
    scope: '全厂',
    createTime: '2026-09-06 18:12',
    source: '能耗分析Agent',
    status: '已生成'
  },
  {
    id: 5,
    title: '2026-09-06 生产日报',
    type: '生产日报',
    scope: '全厂',
    createTime: '2026-09-06 18:08',
    source: '工业报告Agent',
    status: '已生成'
  }
]

/* ======================= 生产计划 ======================= */

export const planItems: PlanItem[] = [
  {
    id: 1,
    planDate: '2026-09-07',
    lineName: 'A产线',
    product: '外壳组件 A-220',
    targetQty: 4800,
    actualQty: 4460,
    achieve: 92.9,
    status: '执行中',
    aiExplanation: '3号注塑机轴承预警降速运行，预计影响 340 件，可在夜班补回。'
  },
  {
    id: 2,
    planDate: '2026-09-07',
    lineName: 'B产线',
    product: '结构件 B-18',
    targetQty: 3600,
    actualQty: 2760,
    achieve: 76.7,
    status: '已延期',
    aiExplanation: '换线超时 23%，第 3 批仅完成 47%，建议拆分批次并顺延 90 分钟。'
  },
  {
    id: 3,
    planDate: '2026-09-07',
    lineName: 'C产线',
    product: '精密件 C-05',
    targetQty: 4400,
    actualQty: 4740,
    achieve: 107.7,
    status: '执行中',
    aiExplanation: '超产 340 件，但外观划伤 42 件需复检，建议校正夹具。'
  },
  {
    id: 4,
    planDate: '2026-09-08',
    lineName: 'A产线',
    product: '外壳组件 A-220',
    targetQty: 4800,
    actualQty: null,
    achieve: null,
    status: '待执行',
    aiExplanation: '明日计划按现有节拍执行，3号注塑机检修需安排在 12:00—14:00 空窗。'
  }
]

/* ======================= OEE ======================= */

export const oeeSummary = {
  oee: 87.6,
  oeeDelta: '较昨日 -4.8pct',
  value: 87.6,
  delta: '较昨日 -4.8pct',
  target: 90,
  availability: 94.3,
  availabilityNote: '目标 95%',
  performance: 95.3,
  performanceNote: '目标 96%',
  qualityIndex: 97.4,
  qualityNote: '目标 98%',
  quality: 97.4,
  note: 'OEE = 开动率 × 性能效率 × 质量指数 · 目标 90%，B产线低于目标 11.6pct'
}

export const oeeByLine = [
  { lineName: 'A产线', oee: 89.4, delta: '-0.8', availability: 95.1, performance: 95.8, qualityIndex: 98.1, downtime: '28 分钟', lossBottleneck: '设备停机' },
  { lineName: 'B产线', oee: 78.4, delta: '-9.2', availability: 88.6, performance: 91.2, qualityIndex: 97.0, downtime: '96 分钟', lossBottleneck: '换线时间' },
  { lineName: 'C产线', oee: 91.2, delta: '+0.4', availability: 96.2, performance: 96.5, qualityIndex: 98.2, downtime: '22 分钟', lossBottleneck: '质量损失' }
]

export const oeeLosses = [
  { lossType: '设备停机', trigger: '故障 / 更换模具', duration: '146 分钟', impact: '-2.0pct', lineName: 'B产线', equipment: '5号注塑机' },
  { lossType: '换线时间', trigger: '换型 / 调机', duration: '90 分钟', impact: '-1.3pct', lineName: 'B产线', equipment: '—' },
  { lossType: '质量损失', trigger: '不良 / 返工', duration: '61 分钟', impact: '-0.9pct', lineName: 'C产线', equipment: 'C产线夹具' },
  { lossType: '速度降低', trigger: '降速运行', duration: '44 分钟', impact: '-0.4pct', lineName: 'A产线', equipment: '3号注塑机' },
  { lossType: '小停机', trigger: '卡料 / 卡模', duration: '18 分钟', impact: '-0.2pct', lineName: 'C产线', equipment: '4号空压机' },
  { lossType: '其他', trigger: '待料 / 交接', duration: '12 分钟', impact: '-0.1pct', lineName: 'A产线', equipment: '—' }
]

export const oeeByShift = [
  { time: '08:00—10:00', factory: 91.2, a: 92.4, b: 86.1, c: 94.8 },
  { time: '10:00—12:00', factory: 89.6, a: 90.8, b: 83.2, c: 93.6 },
  { time: '12:00—14:00', factory: 86.4, a: 88.2, b: 78.6, c: 91.2 },
  { time: '14:00—16:00', factory: 84.1, a: 86.5, b: 72.1, c: 90.4 },
  { time: '16:00—18:00', factory: 87.2, a: 89.1, b: 74.8, c: 92.1 }
]

export const oeeEquipmentContribution = [
  { equipment: '5号注塑机', loss: '-2.1pct', downtime: '38 分钟' },
  { equipment: 'B产线换线', loss: '-1.3pct', downtime: '90 分钟' },
  { equipment: '3号注塑机', loss: '-0.6pct', downtime: '降速 22 分钟' },
  { equipment: 'C产线夹具', loss: '-0.5pct', downtime: '61 分钟' }
]

/* ======================= 质量 ======================= */

export const qualitySummary = {
  yieldRate: 97.1,
  yieldDelta: '较昨日 -0.6pct',
  defects: 80,
  defectsNote: '今日',
  passRate: 98.4,
  batchRate: 98.4,
  batchNote: '今日',
  complaints: 0,
  complaintsNote: '本周',
  aiSummary: '外观划伤 42 件占不良 52.5%，集中于 C产线夹具工位，建议先校正夹具再复检批次。'
}

export const defectTop: DefectItem[] = [
  { type: '外观划伤', qty: 42, rate: 0.86, lineName: 'C产线' },
  { type: '尺寸超差', qty: 19, rate: 0.39, lineName: 'A产线' },
  { type: '装配漏装', qty: 11, rate: 0.23, lineName: 'C产线' },
  { type: '色差', qty: 8, rate: 0.16, lineName: 'B产线' }
]

export const qualityByLine = [
  { lineName: 'A产线', rate: 98.4, defectQty: 19, mainDefect: '尺寸超差', delta: '-0.2' },
  { lineName: 'B产线', rate: 97.2, defectQty: 8, mainDefect: '色差', delta: '-0.4' },
  { lineName: 'C产线', rate: 96.5, defectQty: 53, mainDefect: '外观划伤', delta: '-1.1' }
]

export const qualityByProduct = [
  { product: '外壳组件 A-220', rate: 98.4, qty: 4460, defectQty: 19 },
  { product: '结构件 B-18', rate: 97.2, qty: 2760, defectQty: 8 },
  { product: '精密件 C-05', rate: 96.5, qty: 4740, defectQty: 53 }
]

export const qualityByEquipment = [
  { equipment: 'C产线夹具', defectType: '外观划伤', qty: 42, share: 52.5, riskLevel: '预警' },
  { equipment: '1号冲压机', defectType: '尺寸超差', qty: 19, share: 23.8, riskLevel: '正常' },
  { equipment: '5号注塑机', defectType: '装配漏装', qty: 11, share: 13.8, riskLevel: '故障' },
  { equipment: '3号注塑机', defectType: '色差', qty: 8, share: 10.0, riskLevel: '预警' }
]

/* ======================= 能耗 ======================= */

export const energySummary = {
  total: 62.4,
  totalUnit: 't标煤',
  totalNote: '综合能耗',
  electricity: 48.6,
  electricityNote: '+12.4%',
  water: 186,
  waterNote: '-3.1%',
  gas: 920,
  gasNote: '+6.8%',
  steam: 14.2,
  steamNote: '+9.4%',
  aiSummary: 'A车间夜间电耗 +18.7% 为今日最大异常，4号空压机低负载空转占全厂能耗 13.8%。'
}

export const energyTrend7d = [
  { date: '09-01', value: 61.2, current: 61.2, last: 60.4 },
  { date: '09-02', value: 60.8, current: 60.8, last: 61.1 },
  { date: '09-03', value: 63.4, current: 63.4, last: 62.0 },
  { date: '09-04', value: 62.1, current: 62.1, last: 61.5 },
  { date: '09-05', value: 61.6, current: 61.6, last: 61.0 },
  { date: '09-06', value: 60.2, current: 60.2, last: 60.6 },
  { date: '09-07', value: 62.4, current: 62.4, last: 60.9 }
]

export const energyByMedium = [
  { name: '电', medium: '电', value: 48.6, today: 48.6, yesterday: 43.2, unit: '千kWh' },
  { name: '蒸汽', medium: '蒸汽', value: 14.2, today: 14.2, yesterday: 13.0, unit: 't' },
  { name: '气', medium: '气', value: 920, today: 920, yesterday: 861, unit: 'Nm³' },
  { name: '水', medium: '水', value: 186, today: 186, yesterday: 192, unit: 't' }
]

/** 今日分时电耗（0—23 时），yesterday 为昨日同时段 */
export const hourlyPower = [
  1971, 1854, 1802, 1798, 1846, 2103, 2864, 3201, 3468, 3521, 3477, 3352, 3128, 3402, 3567,
  3610, 3588, 3496, 3204, 2988, 2751, 2540, 2306, 2102
].map((today, i) => ({
  hour: i,
  today,
  yesterday: [
    1862, 1740, 1705, 1698, 1731, 1962, 2688, 3012, 3286, 3340, 3301, 3188, 2974, 3210, 3380,
    3421, 3398, 3304, 3031, 2820, 2588, 2390, 2168, 1984
  ][i]
}))

export const energyCost = {
  cost: 48700,
  costUnit: '元',
  costNote: '今日能耗成本',
  unitConsumption: 5.22,
  unit: '5.22 kWh/件',
  unitNote: '单位产品能耗',
  unitHint: '单位产品能耗',
  saving: '¥28,600',
  savingNote: '可节约 / 月'
}

export const energyByWorkshop = [
  { workshop: 'A车间', share: 38.2, medium: '电 68% / 气 22%', delta: '+18.7%', abnormal: '夜间电耗异常', energy: 23.8 },
  { workshop: 'B车间', share: 33.6, medium: '电 74% / 蒸汽 18%', delta: '+4.2%', abnormal: '—', energy: 21.0 },
  { workshop: 'C车间', share: 28.2, medium: '电 52% / 蒸汽 31%', delta: '-2.4%', abnormal: '—', energy: 17.6 }
]

export const equipmentEnergyRank = [
  { equipment: '4号空压机', medium: '电', value: '8.6 千kWh', share: 13.8, delta: '+38%', note: '低负载空转' },
  { equipment: '3号注塑机', medium: '电', value: '6.2 千kWh', share: 9.9, delta: '+4%', note: '轴承预警降速' },
  { equipment: '蒸汽发生器', medium: '蒸汽', value: '4.1 t', share: 6.6, delta: '+11%', note: '设定温度偏高' },
  { equipment: '5号注塑机', medium: '电', value: '5.8 千kWh', share: 9.3, delta: '+3%', note: '—' },
  { equipment: '1号冲压机', medium: '电', value: '3.4 千kWh', share: 5.4, delta: '-1%', note: '—' }
]

export const workshopHourly = [
  { hour: '00', a: 986, b: 842, c: 620 },
  { hour: '04', a: 941, b: 820, c: 610 },
  { hour: '08', a: 1420, b: 1180, c: 964 },
  { hour: '12', a: 1680, b: 1420, c: 1180 },
  { hour: '16', a: 1740, b: 1502, c: 1246 },
  { hour: '20', a: 1264, b: 1102, c: 880 }
]

/** 白班 vs 夜班电耗（按车间） */
export const shiftEnergy = [
  { workshop: 'A车间', day: 14.2, night: 16.9 },
  { workshop: 'B车间', day: 12.6, night: 11.4 },
  { workshop: 'C车间', day: 10.1, night: 9.6 }
]

export const mediumShare = [
  { name: '电', value: 78, text: '48.6 千kWh' },
  { name: '蒸汽', value: 9, text: '14.2 t' },
  { name: '气', value: 8, text: '920 Nm³' },
  { name: '水', value: 5, text: '186 t' }
]

export const energyPlans: EnergyPlan[] = [
  {
    id: 12,
    code: 'EO-20260907-012',
    name: '关闭A车间2台低负载空压机',
    location: 'A车间 / 4号空压机',
    medium: '电',
    status: '待执行',
    saving: '¥28,600/月',
    result: '—',
    execTime: '—',
    source: '能耗分析Agent',
    reason: '4号空压机长期低负载空转，加载率仅 21%，单位气耗高于基线 38%。',
    advice: '关闭 2 台低负载空压机并切换联控模式，由 3号空压机统一供气。',
    scope: 'A车间气动设备，需确认夜班用气峰值'
  },
  {
    id: 8,
    code: 'EO-20260907-008',
    name: 'C车间蒸汽设定下调2℃',
    location: 'C车间 / 蒸汽发生器',
    medium: '蒸汽',
    status: '已完成',
    saving: '¥9,400/月',
    result: '负荷 -6.2%，当日已兑现 ¥312',
    execTime: '2026-09-07 09:20',
    source: '能耗分析Agent',
    reason: '蒸汽支路压力偏高，设定温度高于工艺要求。',
    advice: '设定温度下调 2℃。',
    scope: 'C车间烘干工序'
  },
  {
    id: 21,
    code: 'EO-20260906-021',
    name: '空压机联控避峰',
    location: 'A车间 / 空压站',
    medium: '电',
    status: '已完成',
    saving: '¥16,800/月',
    result: '夜班峰值下降 11%',
    execTime: '2026-09-06 21:05',
    source: '能耗分析Agent',
    reason: '夜班峰值时段电价上浮 60%。',
    advice: '联控策略避开 20:00—22:00 高峰。',
    scope: 'A车间空压站'
  },
  {
    id: 14,
    code: 'EO-20260906-014',
    name: '夜间照明分区关闭',
    location: '全厂',
    medium: '电',
    status: '已完成',
    saving: '¥2,100/月',
    result: '照度达标，耗电 -8%',
    execTime: '2026-09-06 20:00',
    source: '能耗分析Agent',
    reason: '夜间非作业区照明常亮。',
    advice: '按分区定时关闭。',
    scope: '非作业区照明'
  },
  {
    id: 33,
    code: 'EO-20260905-033',
    name: '冷却塔风机变频',
    location: 'B车间 / 冷却塔',
    medium: '电',
    status: '执行失败',
    saving: '¥5,600/月',
    result: 'PLC 通讯超时，指令未下发',
    execTime: '2026-09-05 14:30',
    source: '能耗分析Agent',
    reason: '冷却塔风机长时间工频运行。',
    advice: '按水温变频控制风机转速。',
    scope: 'B车间冷却系统'
  }
]

export const energyOptimizeDetail = energyPlans[0]

/* ======================= 知识库文档 ======================= */

export const knowledgeDocs: KnowledgeDoc[] = [
  {
    id: 1,
    docName: 'XX型号注塑机维修手册.pdf',
    format: 'PDF',
    size: '18.6 MB',
    uploader: '林晓岚',
    uploadTime: '2026-08-12 09:18',
    parseStatus: '已解析',
    knowledgeType: '维修手册',
    relatedEquipment: '3号注塑机',
    callCount: 486,
    chunks: 1284
  },
  {
    id: 2,
    docName: 'A产线换线SOP.docx',
    format: 'Word',
    size: '2.4 MB',
    uploader: '李明轩',
    uploadTime: '2026-07-30 14:02',
    parseStatus: '已解析',
    knowledgeType: '操作规程',
    relatedEquipment: 'A产线',
    callCount: 312,
    chunks: 96
  },
  {
    id: 3,
    docName: 'E302故障案例.zip',
    format: 'Word',
    size: '6.1 MB',
    uploader: '赵德海',
    uploadTime: '2026-05-18 11:20',
    parseStatus: '已解析',
    knowledgeType: '故障案例',
    relatedEquipment: '3号注塑机',
    callCount: 208,
    chunks: 154
  },
  {
    id: 4,
    docName: '外观检验规范.pdf',
    format: 'PDF',
    size: '4.8 MB',
    uploader: '孙清雅',
    uploadTime: '2026-06-02 16:40',
    parseStatus: '已解析',
    knowledgeType: '检验规范',
    relatedEquipment: 'C产线',
    callCount: 168,
    chunks: 412
  },
  {
    id: 5,
    docName: '3号机振动波形.csv',
    format: 'Excel',
    size: '1.2 MB',
    uploader: '吴运成',
    uploadTime: '2026-09-07 08:11',
    parseStatus: '解析中',
    knowledgeType: '设备数据',
    relatedEquipment: '3号注塑机',
    callCount: 12,
    chunks: 0
  }
]

export const kbDocuments: KbDocument[] = knowledgeDocs.map((d) => ({
  id: d.id,
  fileName: d.docName,
  format: d.format,
  size: d.size,
  uploader: d.uploader,
  uploadTime: d.uploadTime,
  parseStatus: d.parseStatus,
  docType: d.knowledgeType,
  relatedEquipment: d.relatedEquipment
}))

/** 知识条目下的文件清单（抽屉） */
export const kbFilesOfDoc = [
  { fileName: 'XX型号注塑机维修手册.pdf', format: 'PDF', size: '18.6 MB', parseStatus: '已解析' },
  { fileName: '液压系统附图.png', format: 'PNG', size: '2.1 MB', parseStatus: '已解析' },
  { fileName: 'E302复位检查表.xlsx', format: 'Excel', size: '0.4 MB', parseStatus: '已解析' }
]

export const recommendedQuestions = [
  '3号注塑机出现E302错误怎么办？',
  'A产线换线 SOP 关键步骤有哪些？',
  '外观划伤应依据哪份质量规范判定？'
]

export const qaModels = [
  '工业知识问答 · Qwen-Industry',
  '工业知识问答 · DeepSeek-Industry',
  '工业知识问答 · GLM4-Industry'
]

/* ======================= 下拉选项 ======================= */

export const options = {
  line: ['全部', 'A产线', 'B产线', 'C产线'],
  shift: ['全部', '白班', '夜班'],
  equipmentStatus: ['全部', '正常', '预警', '故障', '离线'],
  riskLevel: ['全部', '高', '中', '低'],
  workOrderStatus: ['全部', '待处理', '维修中', '待验证', '已完成'],
  planStatus: ['全部', '执行中', '已延期', '待执行'],
  agentStatus: ['全部', '运行中', '已停用'],
  taskStatus: ['全部', '等待确认', '执行中', '已完成', '执行失败'],
  draftStatus: ['全部', '待确认', '已确认', '已驳回'],
  reportType: ['全部', '生产日报', '质量日报', '能耗日报', '设备周报'],
  energyStatus: ['全部', '待执行', '已完成', '执行失败'],
  medium: ['全部', '电', '水', '气', '蒸汽'],
  defectType: ['全部', '外观划伤', '尺寸超差', '装配漏装', '色差'],
  knowledgeType: ['全部', '维修手册', '操作规程', '故障案例', '检验规范', '设备数据', '工艺文件'],
  docType: ['全部', '维修手册', '操作规程', '故障案例', '检验规范', '设备数据', '工艺文件'],
  fileFormat: ['PDF', 'Word', 'Excel', 'ZIP', 'PNG', 'CSV']
}

/** 人员（负责人 / 上传人） */
export const users = ['吴运成', '赵德海', '林晓岚', '李明轩', '孙清雅']

/** 故障类型 */
export const faultTypes = ['轴承异常', '电气故障', '泄漏', '过热', '振动超标', '尺寸偏移']

/* ======================= 数据分析主题 ======================= */

export const analysisTopics = [
  '为什么今天OEE下降？',
  '为什么B产线达成率低于目标？',
  'C产线外观划伤集中在哪个工位？',
  'A车间夜间电耗为何上升？',
  '本周设备故障趋势如何？'
]

/** OEE 下降原因拆解 */
export const oeeLossBreakdown = [
  { name: '设备停机', value: 42 },
  { name: '换线时间', value: 28 },
  { name: '质量损失', value: 19 },
  { name: '其他', value: 11 }
]

/* ======================= 生产日报内容（报表详情） ======================= */

export const productionDailyReport = {
  factory: '华东智造集团 · 华东精密制造工厂',
  title: '2026-09-07 生产日报',
  subtitle: '供厂长及企业管理层审阅 · 机密',
  issuer: '工业报告Agent',
  scope: '全厂 A/B/C 产线',
  createTime: '2026-09-07 18:20',
  conclusion:
    '今日全厂实际产量 11,960 件，计划 12,800 件，达成率 93.4%，较昨日下降 2.1 个百分点。全厂 OEE 87.6%，较昨日下降 4.8pct，为近 7 日最低。良品率 97.1%（-0.6pct），本周客诉 0。建议优先处理 B产线换线超时 与 5号注塑机电气停机。',
  kpis: [
    { label: '计划产量', value: '12,800', unit: '件', color: 'var(--text-1)' },
    { label: '实际产量', value: '11,960', unit: '件', color: 'var(--c-orange)' },
    { label: '全厂 OEE', value: '87.6%', unit: '较昨日 -4.8pct', color: 'var(--c-red)' },
    { label: '良品率', value: '97.1%', unit: '外观划伤为主', color: 'var(--c-orange)' }
  ],
  keyData: [
    { lineName: 'A产线', target: '4,800', actual: '4,460', achieve: '92.9%', oee: '89.4%', quality: '98.4%', status: '执行中' },
    { lineName: 'B产线', target: '3,600', actual: '2,760', achieve: '76.7%', oee: '78.4%', quality: '97.2%', status: '已延期' },
    { lineName: 'C产线', target: '4,400', actual: '4,740', achieve: '107.7%', oee: '91.2%', quality: '96.5%', status: '执行中' },
    { lineName: '全厂', target: '12,800', actual: '11,960', achieve: '93.4%', oee: '87.6%', quality: '97.1%', status: '—' }
  ],
  reasons: [
    { label: '设备停机', text: '5号注塑机电控柜过热保护，停机 38 分钟，拖累全厂 OEE 约 2.1pct；3号注塑机轴承预警降速运行。', value: '42%' },
    { label: '换线时间', text: 'B产线 14:00—15:30 换线超时 23%，当前第 3 批仅完成 47%，产线已标记延期。', value: '28%' },
    { label: '质量损失', text: 'C产线外观划伤 42 件，夹具定位偏差导致，不良贡献过半。', value: '19%' }
  ],
  reasonsNote: 'OEE 下降 4.8pct 的损失构成如下，请管理层重点关注前两项：',
  suggestions: [
    '立即安排 5 号注塑机电控柜检修，建议今晚班前完成。',
    '优化 B 产线换线 SOP，缩短 14:00 后换型时间，必要时调整 15:00 后批次。',
    '校正 C 产线夹具并复核外观检验标准，预计可将不良率压回 0.4% 以内。',
    '3 号注塑机纳入预测性维护观察；主轴轴承 6208 库存仅 2 件，低于安全库存。'
  ],
  footer: '数据来源：MES / IoT / 质量 SPC · 由工业报告Agent 自动汇总'
}

/* ======================= AI 对话 ======================= */

export const chatSuggestions = [
  '今天哪条产线 OEE 最差，为什么？',
  '3号注塑机现在什么情况？',
  '帮我生成本周设备风险清单',
  'A车间夜间电耗为什么上升？'
]

/** AI对话页「历史记录」抽屉使用 */
export const copilotHistory = [
  { id: 1, q: '为什么今天 OEE 下降了？', time: '今天 16:12' },
  { id: 2, q: '帮我生成本周设备风险清单', time: '今天 14:05' },
  { id: 3, q: 'C产线外观划伤怎么处理？', time: '昨天 17:20' }
]

export const chatHistory = [
  { role: 'user', text: '为什么今天 OEE 下降了？' },
  {
    role: 'ai',
    text: '今日全厂 OEE 87.6%，较昨日下降 4.8pct。主要原因是 B 产线换线超时（14:00—15:30 增加 23%），造成 74 分钟停机损失，占 OEE 降幅的 3.3pct；其次是 5 号注塑机电气故障停机 38 分钟（-2.1pct）。A产线小幅下降 0.8pct，C 产线基本持平。建议优先优化 B 产线换线 SOP。',
    tools: ['查询生产', '查询设备'],
    time: '2026-09-07 16:12'
  }
]

/* ======================= 登录页 ======================= */

/** 左侧四张能力卡（原型：图标字 + 标题 + 副标题） */
export const loginFeatures = [
  { icon: '机', title: '智能生产', desc: 'AI 驱动柔性制造' },
  { icon: '数', title: '数据洞察', desc: '实时分析与预测' },
  { icon: '运', title: '智能运维', desc: '设备健康管理' },
  { icon: '决', title: '协同决策', desc: '构建智慧工厂' }
]

/**
 * 其他登录方式。
 * 原型上是「企 / LD / 手」，目前后端尚未接入对应协议，点击只做提示，
 * 保留真实图标位，等 SSO、LDAP、企业微信开放平台接好再换实现。
 */
export const loginOtherMethods = [
  { key: 'enterprise', label: '企', name: '企业微信' },
  { key: 'ldap', label: 'LD', name: 'LDAP 域账号' },
  { key: 'mobile', label: '手', name: '手机验证码' }
]

/**
 * 演示账号。
 * 注意：这里的 username 必须与数据库 sys_user 里真实存在的账号一致，
 * 否则「点一下自动填充」会把用户带到 20001 登录失败，比不给提示更糟。
 * 目前库里只种了 admin / operator 两个账号，其余五个角色还没建。
 */
export const demoAccounts = [
  { username: 'admin', name: '吴运成', role: '平台管理员' },
  { username: 'operator', name: '赵德海', role: '设备工程师' }
]

/** 登录页底部的价值点（原型：安全 | 稳定 | 智能 | 高效） */
export const loginSlogans = ['安全', '稳定', '智能', '高效']