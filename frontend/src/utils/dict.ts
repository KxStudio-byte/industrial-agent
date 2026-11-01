/** 各业务状态到展示颜色/样式的映射，集中管理，避免每个页面各写一套 if */

/** 设备状态 -> tag-dot 样式类 */
export const EQUIPMENT_STATUS_CLASS: Record<string, string> = {
  正常: 'tag-dot--green',
  预警: 'tag-dot--orange',
  故障: 'tag-dot--red',
  离线: 'tag-dot--gray'
}

/** 风险等级 -> 样式类 */
export const RISK_CLASS: Record<string, string> = {
  高: 'tag-dot--red',
  中: 'tag-dot--orange',
  低: 'tag-dot--green'
}

/** 优先级 -> 样式类 */
export const PRIORITY_CLASS: Record<string, string> = {
  紧急: 'tag-dot--red',
  高: 'tag-dot--orange',
  中: 'tag-dot--orange',
  低: 'tag-dot--gray'
}

/** 工单状态 -> 样式类 */
export const WORK_ORDER_STATUS_CLASS: Record<string, string> = {
  待处理: 'tag-dot--orange',
  维修中: 'tag-dot--blue',
  待验证: 'tag-dot--blue',
  已完成: 'tag-dot--green'
}

/** 兼容旧引用 */
export const WO_STATUS_CLASS = WORK_ORDER_STATUS_CLASS

/** 智能体/任务通用状态 -> 样式类 */
export const RUN_STATUS_CLASS: Record<string, string> = {
  运行中: 'tag-dot--green',
  已停用: 'tag-dot--gray',
  等待确认: 'tag-dot--orange',
  执行中: 'tag-dot--blue',
  已完成: 'tag-dot--green',
  执行成功: 'tag-dot--green',
  执行失败: 'tag-dot--red',
  待确认: 'tag-dot--orange',
  已确认: 'tag-dot--blue',
  已驳回: 'tag-dot--gray',
  已生成: 'tag-dot--blue',
  已解析: 'tag-dot--blue',
  解析中: 'tag-dot--orange',
  待执行: 'tag-dot--orange'
}

/** Agent 运行记录的执行结果 -> 样式类 */
export const RUN_RESULT_CLASS: Record<string, string> = {
  成功: 'tag-dot--green',
  已成功: 'tag-dot--green',
  执行成功: 'tag-dot--green',
  已完成: 'tag-dot--green',
  失败: 'tag-dot--red',
  执行失败: 'tag-dot--red',
  超时: 'tag-dot--red',
  部分成功: 'tag-dot--orange',
  失败已回滚: 'tag-dot--red',
  已回滚: 'tag-dot--orange'
}

/** 知识类型 -> 样式类 */
export const KNOWLEDGE_TYPE_CLASS: Record<string, string> = {
  维修手册: 'tag-dot--blue',
  操作规程: 'tag-dot--green',
  故障案例: 'tag-dot--orange',
  检验规范: 'tag-dot--green',
  设备数据: 'tag-dot--gray',
  工艺文件: 'tag-dot--blue'
}

/** 计划状态 -> 样式类 */
export const PLAN_STATUS_CLASS: Record<string, string> = {
  执行中: 'tag-dot--blue',
  已延期: 'tag-dot--orange',
  待执行: 'tag-dot--gray'
}

/** Copilot 操作执行状态 -> 样式类 */
export const EXEC_STATUS_CLASS: Record<string, string> = {
  待确认: 'tag-dot--orange',
  已执行: 'tag-dot--blue',
  已拒绝: 'tag-dot--gray',
  执行失败: 'tag-dot--red'
}

/** 能耗优化任务状态 -> 样式类 */
export const ENERGY_PLAN_STATUS_CLASS: Record<string, string> = {
  待执行: 'tag-dot--orange',
  执行中: 'tag-dot--blue',
  已完成: 'tag-dot--green',
  执行失败: 'tag-dot--red'
}

/** 兼容旧引用 */
export const PLAN_EXEC_CLASS = ENERGY_PLAN_STATUS_CLASS

/** 健康度 -> 颜色 */
export function healthColor(health: number): string {
  if (health >= 85) return 'var(--c-green)'
  if (health >= 70) return 'var(--c-orange)'
  return 'var(--c-red)'
}

/** 取样式类，取不到时降级为灰色 */
export function cls(map: Record<string, string>, key: string): string {
  return map[key] ?? 'tag-dot--gray'
}