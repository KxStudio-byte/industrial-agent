# 工业智能体平台 · API 接口清单

> 依据：`frontend/src/views/`（25 个页面）+ `frontend/src/mock/index.ts`（48 个数据块）+ `industrial_agent` 库 20 张表（实测 DDL）
> 状态标：`●` 已实现（仅 `/api/hello`、`/api/health`）｜`○` 待实现

---

## 0. 全局约定

### 0.1 基础信息

| 项 | 值 |
|---|---|
| 后端服务 | Spring Boot 3.2.4，`http://localhost:8080` |
| 前端开发代理 | `vite.config.ts` 已把 `/api` → `http://localhost:8080/api`（不 rewrite） |
| 统一前缀 | `/api` |
| 字符编码 | UTF-8（`server.servlet.encoding.force: true`） |
| 鉴权 | JWT，`Authorization: Bearer <token>`（`request.ts` 已预留拦截器，`sys_user`/`sys_role` 已建表） |
| 分页 | `page`（从 1 开始）、`size`（默认 10） |
| 日期格式 | `yyyy-MM-dd HH:mm`（与前端展示一致，非 ISO） |

### 0.2 统一响应结构

后端已有 `TestController.Result`，**建议提取到 `com.industrial.agent.common.Result`** 供全局使用：

```json
{ "code": 200, "msg": "ok", "data": {} }
```

| code | 含义 | 前端处理 |
|---|---|---|
| 200 | 成功 | `request.ts` 拦截器直接放行，业务层取 `data` |
| 400 | 参数校验失败 | `ElMessage.warning(msg)` |
| 401 | 未登录 / token 失效 | 跳转登录页 |
| 403 | 无权限 | `ElMessage.error(msg)` |
| 404 | 资源不存在 | `ElMessage.error(msg)` |
| 500 | 服务端异常 | `ElMessage.error(msg)` |

### 0.3 分页响应结构

```json
{ "code": 200, "msg": "ok",
  "data": { "total": 48, "page": 1, "size": 10, "list": [] } }
```

### 0.4 前端现状的关键约束（★ 决定接口设计）

1. **所有页面一次性拿到全量数据，在前端 `computed` 里做筛选和分页**（`filtered` + `paged` + `TableFooter`）。
   例：`EquipmentList.vue` 用 `filtered.value.slice((page - 1) * 10, page * 10)`。
   → 接口**必须同时支持「返回全量」和「服务端分页」两种模式**：不传 `page` 返回 `list` 数组；传 `page` 返回分页对象。
2. **下拉筛选选项硬编码在前端**（`mock/index.ts` 的 `options` / `users` / `faultTypes`）。
   → 先由前端保留，后续抽成 `GET /api/common/dict/*` 字典接口，避免改页面结构。
3. **字段名以 `frontend/src/types/index.ts` 为准**，接口返回直接对齐这些 interface，前端零改动即可替换 mock。
4. **mock 里的"冗余展示字段"部分在表中不存在**，需 JOIN 或计算，见各接口的「数据来源」列。

---

## 1. 接口总览（按页面分组，24 个业务页面 → 88 个接口 + 公共接口 6 个 = 94 个）

| # | 页面 | 路由 | 接口数 |
|---|---|---|---|
| 1 | 工厂总览 | `/dashboard` | 3 |
| 2 | AI实时洞察 | `/ai-dashboard/insights` | 3 |
| 3 | 风险预警 | `/ai-dashboard/risk-alerts` | 3 |
| 4 | Agent广场 | `/agent/plaza` | 2 |
| 5 | 我的Agent | `/agent/mine` | 3 |
| 6 | Agent任务 | `/agent/tasks` | 4 |
| 7 | Agent运行记录 | `/agent/run-logs` | 2 |
| 8 | AI对话 | `/copilot/chat` | 3 |
| 9 | 数据分析 | `/copilot/analysis` | 4 |
| 10 | 报表生成 | `/copilot/reports` | 5 |
| 11 | 操作执行 | `/copilot/executions` | 5 |
| 12 | 设备总览 | `/equipment/overview` | 2 |
| 13 | 设备列表 | `/equipment/list` | 5 |
| 14 | 设备详情 | `/equipment/detail/:id` | 3 |
| 15 | 维修工单 | `/equipment/work-orders` | 7 |
| 16 | 生产总览 | `/production/overview` | 2 |
| 17 | 生产计划 | `/production/plans` | 5 |
| 18 | OEE分析 | `/production/oee` | 4 |
| 19 | 质量总览 | `/quality/overview` | 2 |
| 20 | 能耗总览 | `/energy/overview` | 2 |
| 21 | 能耗分析 | `/energy/analysis` | 5 |
| 22 | AI节能优化 | `/energy/saving` | 4 |
| 23 | 文档管理 | `/knowledge/documents` | 6 |
| 24 | 文档问答 | `/knowledge/qa` | 4 |
| — | **公共接口** | — | **6** |
| — | **合计** | **24 个业务页面（25 个 .vue，含 1 个抽屉组件）** | **94 个接口** |

> 其中**已实现 2 个**（`/api/hello`、`/api/health`，均在 `TestController`），**待实现 92 个**。
> 唯一重复：`GET /api/equipment/{id}`（设备列表页与详情页共用）。

---

## 2. 公共接口

| 方法 | 路径 | 说明 | 数据来源 | 状态 |
|---|---|---|---|---|
| GET | `/api/hello` | 连通性测试 | — | ● |
| GET | `/api/health` | 数据库健康检查 | `information_schema` | ● |
| POST | `/api/auth/login` | 登录，返回 JWT | `sys_user` | ○ |
| GET | `/api/auth/me` | 当前用户 + 角色 + 菜单 | `sys_user`+`sys_role`+`sys_menu` | ○ |
| GET | `/api/common/dict` | 全部字典（下拉选项） | 枚举/常量 | ○ |
| GET | `/api/common/users` | 人员下拉（`users`） | `sys_user` | ○ |

**GET /api/common/dict 响应**（对应 `mock/index.ts` 的 `options`）

```json
{ "code": 200, "msg": "ok", "data": {
  "line": ["A产线","B产线","C产线"],
  "shift": ["白班","夜班"],
  "equipmentStatus": ["正常","预警","故障","离线"],
  "riskLevel": ["高","中","低"],
  "workOrderStatus": ["待处理","维修中","待验证","已完成"],
  "planStatus": ["执行中","已延期","待执行"],
  "agentStatus": ["运行中","已停用"],
  "taskStatus": ["等待确认","执行中","已完成","执行失败"],
  "draftStatus": ["待确认","已确认","已驳回"],
  "reportType": ["生产日报","质量日报","能耗日报","设备周报"],
  "energyStatus": ["待执行","已完成","执行失败"],
  "medium": ["电","水","气","蒸汽"],
  "defectType": ["外观划伤","尺寸超差","装配漏装","色差"],
  "knowledgeType": ["维修手册","操作规程","故障案例","检验规范","设备数据","工艺文件"],
  "fileFormat": ["PDF","Word","Excel","ZIP","PNG","CSV"],
  "faultTypes": ["轴承异常","电气故障","泄漏","过热","振动超标","尺寸偏移"]
} }
```

---

## 3. 工厂总览 `/dashboard`（FactoryOverview.vue）

前端用到：`productionLines`、`factorySummary`、`riskEquipments`、`energySummary`。

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/dashboard/summary` | 全厂 KPI 卡片 | 多表聚合 |
| GET | `/api/dashboard/lines` | 产线卡片列表 | `base_production_line` + `prod_plan` + `prod_oee_loss` |
| GET | `/api/dashboard/risk-equipments` | 高风险设备 Top | `base_equipment` where `risk_level='高'` |

**GET /api/dashboard/summary** → `factorySummary`

```json
{ "todayOutput": 11960, "actualQty": 11960, "todayPlan": 12800, "planQty": 12800,
  "achieve": 93.4, "achieveNote": "较昨日 -2.1pct",
  "oee": 87.6, "oeeDelta": "较昨日 -4.8pct",
  "quality": 97.1, "qualityDelta": "较昨日 -0.6pct",
  "alerts": 3, "alertsNote": "2 项需关注",
  "equipmentTotal": 48, "equipmentNote": "三车间",
  "online": 44, "onlineRate": "91.7%",
  "offline": 2, "offlineNote": "检测仪等",
  "abnormal": 3, "abnormalNote": "含故障 1 台",
  "highRisk": 2, "highRiskNote": "需预测性维护",
  "planProgress": 93.4,
  "aiSummary": "B产线达成率 76.7%……" }
```

> **实现要点**：`todayOutput` 是 `prod_plan.actual_qty` 当日汇总，`actualQty`/`planQty` 是前端历史上两个别名，**两个字段都返回**（前端两处都在用）；`achievementNote` 类文字建议后端拼好，避免前端二次计算。

**GET /api/dashboard/lines** → `ProductionLine[]`（每项 22 个字段，见 `types/index.ts:187-210`）

```json
[{ "name": "A产线", "status": "执行中", "product": "外壳组件 A-220", "workTime": "08:00—20:00",
   "target": 4800, "actual": 4460, "achieve": 92.9,
   "oee": 89.4, "oeeDelta": "较昨日 -0.8pct",
   "quality": 98.4, "qualityNote": "良品率 98.4%", "achieveNote": "落后 340 件",
   "planProgress": 92.9, "batchProgress": 96,
   "currentBatch": "第 3 批 · 外壳A-220", "currentBatchQty": "完成 96%",
   "changeover": "换线损失 0:22",
   "availability": 95.1, "performance": 95.8, "qualityIndex": 98.1,
   "losses": [{ "name": "设备停机", "value": 42 }, { "name": "换线时间", "value": 28 }],
   "aiHint": "A产线整体平稳，3号注塑机轴承预警导致节拍略降……" }]
```

> `losses` 来自 `prod_oee_loss` 按 `loss_type` 分组求和占比；`aiHint` 来自 `prod_plan.ai_explanation`。

---

## 4. AI实时洞察 `/ai-dashboard/insights`（AiInsights.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/insights` | 洞察列表（支持 `riskLevel`、`objectType`、`keyword`、`page`、`size`） | `ai_insight` ⨝ `base_equipment` |
| POST | `/api/insights/{id}/to-work-order` | 转工单草稿 | 写 `copilot_wo_draft` |
| POST | `/api/insights/refresh` | 触发全部 Agent 重新扫描 | 写 `ai_agent_task` |

**GET /api/insights** → `Insight[]`

```json
[{ "id": 1, "title": "5号注塑机电控柜存在过热停机风险",
   "objectType": "设备", "objectName": "5号注塑机",
   "aiJudgement": "柜内温度已连续 3 小时高于 70℃，24 小时故障概率 62%。",
   "probability": 62, "riskLevel": "高",
   "impact": "预计影响产量 240 件",
   "discoverTime": "2026-09-07 15:32",
   "agent": "故障预测Agent" }]
```

| 字段 | 数据来源 | 备注 |
|---|---|---|
| `objectName` | JOIN `base_equipment.equipment_name`（当 `object_type='设备'`）；`'产线'` 时 JOIN `base_production_line.line_name` | ⚠️ 表内只有 `object_id` |
| `aiJudgement` | `ai_insight.ai_diagnosis` | 字段名不同，需映射 |
| `impact` | **表中无此列**，需新增 `ai_insight.impact` 或由 Agent 写入 JSON | ⚠️ 建议加列 |
| `agent` | `ai_insight` 无 `agent_id`，需从 `ai_agent` 关联或由 `create` 时记录 | ⚠️ 建议加列 |

**POST /api/insights/{id}/to-work-order** → 返回新草稿

```json
{ "draftNo": "AE-20260907-019", "equipmentName": "5号注塑机", "status": "待确认" }
```

---

## 5. 风险预警 `/ai-dashboard/risk-alerts`（RiskAlerts.vue）

与 AiInsights 共用数据源，筛选维度不同（`riskLevel` + `objectType`）。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/risk-alerts` | 同 `/api/insights`，语义化别名 |
| GET | `/api/risk-alerts/stats` | 顶部统计（按风险等级计数） |
| POST | `/api/risk-alerts/report` | 生成预警简报（写 `ai_report`） |

**GET /api/risk-alerts/stats**

```json
{ "high": 2, "medium": 3, "low": 4, "total": 9 }
```

> 前端 `RiskAlerts.vue` 的 `stats` 目前由 `computed` 从列表算出，**可以不做这个接口**，但做了能减少首屏数据量。

---

## 6. Agent广场 `/agent/plaza`（AgentPlaza.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/agents` | Agent 列表（支持 `type`、`status`、`keyword`） | `ai_agent` |
| POST | `/api/agents/{id}/run` | 立即运行一次 | 写 `ai_agent_task` |

**GET /api/agents** → `Agent[]`

```json
[{ "id": 1, "name": "生产调度Agent", "type": "生产类",
   "desc": "根据订单、设备与人员状态编排生产计划……",
   "permission": "人工确认后执行", "status": "运行中" }]
```

| 字段 | 数据来源 |
|---|---|
| `desc` | `ai_agent.description` |
| `permission` | `ai_agent.exec_permission` |

---

## 7. 我的Agent `/agent/mine`（MyAgents.vue）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/agents/mine` | 我的 Agent（当前用户启用/订阅的） |
| PUT | `/api/agents/{id}/status` | 启用/停用 `{ "status": "运行中" \| "已停用" }` |
| POST | `/api/agents` | 创建 Agent（创建向导提交） |

> ⚠️ **数据缺口**：mock 的 `myAgents = agents.filter(a => a.id !== 9)` 说明「我的 Agent」是 `ai_agent` 的子集，但表里**没有 `owner_id` / `user_agent_enable` 关系**。建议：
> - 方案 A（推荐）：新增 `sys_user_agent`（`user_id`,`agent_id`,`enabled`）关联表；
> - 方案 B：`ai_agent` 加 `owner_id bigint` 列，`my` 即 `owner_id = 当前用户`。

---

## 8. Agent任务 `/agent/tasks`（AgentTasks.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/agent-tasks` | 任务列表（`status`、`page`、`size`） | `ai_agent_task` ⨝ `ai_agent` |
| GET | `/api/agent-tasks/{id}` | 任务详情 | 同上 |
| GET | `/api/agent-tasks/{id}/steps` | 执行步骤树 | `ai_agent_run_log` where `task_id` |
| POST | `/api/agent-tasks/{id}/confirm` | 确认执行 `{ "decision": "approve" \| "reject" }` | 改 `status` |

**GET /api/agent-tasks** → `AgentTask[]`

```json
[{ "id": 1, "taskName": "3号注塑机轴承异常处置", "agentName": "设备运维Agent",
   "triggerTime": "2026-09-07 16:58", "status": "等待确认",
   "result": "待确认", "duration": "42.6s",
   "input": "设备 EQ-IM-003 振动 7.8mm/s（阈值 6.5），连续 6 天上升",
   "decision": "判定主轴轴承磨损，建议创建紧急维修工单并指派吴运成。" }]
```

| 字段 | 数据来源 | 备注 |
|---|---|---|
| `agentName` | JOIN `ai_agent.agent_name` | ⚠️ 表内只有 `agent_id` |
| `result` / `decision` | **`ai_agent_task` 无此列** | ⚠️ 建议加 `result`、`decision` 列，或存 `input_data` JSON |
| `duration` | SUM(`ai_agent_run_log.duration`) | 需聚合 |
| `steps` | `ai_agent_run_log` 逐条：`{ stepName, stepStatus, detail, tokenCost, duration, createTime }` | 直接映射 |

**GET /api/agent-tasks/{id}/steps** → 前端 `AgentRuns.vue` 的 `steps` computed

```json
[{ "stepName": "数据采集", "stepStatus": "完成", "detail": "读取 EQ-IM-003 近 30 天振动数据", "tokenCost": 1200, "duration": 3 },
 { "stepName": "AI分析", "stepStatus": "完成", "detail": "轴承磨损概率 87%", "tokenCost": 8600, "duration": 28 },
 { "stepName": "等待确认", "stepStatus": "等待确认", "detail": "创建工单草稿待厂长确认", "tokenCost": 2680, "duration": 11 }]
```

---

## 9. Agent运行记录 `/agent/run-logs`（AgentRuns.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/agent-run-logs` | 运行记录（`agentName`、`result`、`keyword`、`page`） | `ai_agent_run_log` ⨝ `ai_agent_task` ⨝ `ai_agent` |
| POST | `/api/agent-run-logs/{id}/rerun` | 重新发起 | 写新 `ai_agent_task` |

**GET /api/agent-run-logs** → `AgentRunLog[]`

```json
[{ "id": 1, "time": "2026-09-07 16:58", "agentName": "设备运维Agent",
   "taskName": "3号注塑机轴承异常处置",
   "tools": "查询设备、检索知识库、创建工单草稿",
   "result": "等待确认", "duration": "42.6s",
   "token": "12,480", "cost": "¥0.31" }]
```

| 字段 | 数据来源 | 备注 |
|---|---|---|
| `tools` | **表中无 `tools` 列** | ⚠️ 建议 `ai_agent_run_log` 加 `tools` varchar |
| `token` | SUM(`token_cost`) 后千分位格式化 | 后端格式化 |
| `cost` | `token_cost` × 单价 | 单价配在 `application.yml` |

---

## 10. 工业Copilot · AI对话 `/copilot/chat`（CopilotChat.vue）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/copilot/chat` | 发送消息，SSE 流式返回 |
| GET | `/api/copilot/sessions` | 历史会话列表（`copilotHistory`） |
| POST | `/api/copilot/sessions/{id}/actions/{actionId}` | 执行对话里给出的动作按钮 |

**POST /api/copilot/chat**

```json
// 请求
{ "sessionId": "s-001", "message": "3号注塑机振动异常怎么处理？" }
// 响应（SSE，text/event-stream）
data: {"type":"delta","content":"根据维修手册"}
data: {"type":"delta","content":"，主轴轴承磨损建议……"}
data: {"type":"action","actions":[{"id":"a1","label":"创建维修工单"}]}
data: {"type":"done","messageId":42}
```

> ⚠️ 前端目前 `send()` 是纯本地延时模拟，接流式需改造 `CopilotChat.vue`（用 `EventSource` 或 `fetch` + `ReadableStream`）。

---

## 11. 工业Copilot · 数据分析 `/copilot/analysis`（DataAnalysis.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/copilot/analysis/topics` | 推荐分析主题（`analysisTopics`） | 常量表/配置 |
| POST | `/api/copilot/analysis` | 提交分析 `{ "topic": "为什么今天OEE下降？" }` | 调 LLM + 查库 |
| GET | `/api/copilot/analysis/{id}` | 分析结果（文字 + 图表数据） | `ai_report` |
| GET | `/api/copilot/analysis/{id}/export` | 导出 PDF | 生成文件流 |

**POST /api/copilot/analysis 响应**

```json
{ "id": 101, "topic": "为什么今天OEE下降？",
  "conclusion": "B产线换线超时导致 OEE 下降 9.2pct……",
  "chartType": "pie",
  "chartData": [{ "name": "设备停机", "value": 42 }, { "name": "换线时间", "value": 28 }],
  "table": [{ "lineName": "B产线", "oee": 78.4 }] }
```

> 复用了 `mock` 的 `oeeLossBreakdown`，数据实际来自 `prod_oee_loss`。

---

## 12. 工业Copilot · 报表生成 `/copilot/reports`（ReportList.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/reports` | 报表列表（`keyword`、`type`、`page`） | `ai_report` ⨝ `ai_agent` |
| POST | `/api/reports` | 生成报表 `{ "title","type","scope","date" }` | 写 `ai_report` |
| GET | `/api/reports/{id}` | 报表详情（`productionDailyReport` 结构） | `ai_report.content`（JSON） |
| GET | `/api/reports/{id}/download` | 下载（PDF/Word） | 文件流 |
| POST | `/api/reports/{id}/archive` | 归档到知识库 | 写 `kb_document` |

**GET /api/reports** → `Report[]`

```json
[{ "id": 1, "title": "2026-09-07 生产日报", "type": "生产日报",
   "scope": "全厂", "createTime": "2026-09-07 18:20",
   "source": "工业报告Agent", "status": "已生成" }]
```

| 字段 | 数据来源 | 备注 |
|---|---|---|
| `title` | `ai_report.report_title` | |
| `type` | `ai_report.report_type` | |
| `scope` | **表中无此列** | ⚠️ 建议加 `scope` varchar(50) |
| `source` | JOIN `ai_agent.agent_name`（`source_agent_id`） | |
| `status` | **表中无此列** | ⚠️ 建议加 `status`（已生成/生成中/失败） |

**GET /api/reports/{id} 详情**（对应 `productionDailyReport`，`ai_report.content` 存 JSON）

```json
{ "id": 1, "factory": "华东智造集团 · 华东精密制造工厂",
  "title": "2026-09-07 生产日报", "subtitle": "供厂长及企业管理层审阅 · 机密",
  "issuer": "工业报告Agent", "scope": "全厂 A/B/C 产线", "createTime": "2026-09-07 18:20",
  "conclusion": "今日全厂实际产量 11,960 件……",
  "kpis": [{ "label": "计划产量", "value": "12,800", "unit": "件", "color": "var(--text-1)" }],
  "keyData": [{ "lineName": "A产线", "target": "4,800", "actual": "4,460",
                "achieve": "92.9%", "oee": "89.4%", "quality": "98.4%", "status": "执行中" }] }
```

> `kpis` / `keyData` 里的 `color` 是 CSS 变量，**由后端返回或前端固定**，建议前端固定（`color` 不落库）。

---

## 13. 工业Copilot · 操作执行 `/copilot/executions`（Executions.vue）

这是「Agent 产出需人确认的动作」聚合页，数据来自 `copilot_wo_draft` + `ai_agent_task`。

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/executions` | 执行流水（`status`、`agentName`、`keyword`、`page`） | `copilot_wo_draft` ⨝ `ai_agent` ⨝ `base_equipment` |
| GET | `/api/executions/kpi` | 顶部 KPI（待确认数/已执行数） | COUNT 聚合 |
| POST | `/api/executions/{id}/confirm` | 确认并流转为维修工单 | 写 `eqp_work_order` + 改草稿 `status='已确认流转'` |
| POST | `/api/executions/{id}/reject` | 驳回 `{ "reason": "..." }` | 改草稿 `status='已驳回'` |
| GET | `/api/executions/{id}/table` | 「查看执行表」明细 | 明细视图 |

**GET /api/executions** → 返回两类

```json
// 草稿视图（主列表，默认）
[{ "id": 18, "draftNo": "AE-20260907-018", "agentName": "设备运维Agent",
   "equipmentName": "3号注塑机", "faultType": "轴承异常", "priority": "紧急",
   "createTime": "2026-09-07 16:58", "status": "待确认", "woNo": "—",
   "description": "振动持续升高，AI 判断主轴轴承磨损概率 87%，建议立即检修。",
   "assignee": "吴运成" }]

// 执行流水视图（openExecutionTable 用的 Execution[]）
[{ "id": 6, "execNo": "EXE-20260907-006", "agentName": "设备运维Agent",
   "actionType": "创建维修工单", "equipmentName": "3号注塑机",
   "content": "振动 7.8mm/s 持续升高……", "priority": "紧急",
   "createTime": "2026-09-07 16:58", "status": "待确认",
   "operator": "—", "result": "等待厂长确认后流转为维修工单", "woNo": "—" }]
```

> ⚠️ **`Execution` 这个结构在库里没有直接映射的表**：`execNo`、`actionType`、`operator`、`result` 都无处存。
> 建议新增表 `copilot_execution`（`exec_no`,`agent_id`,`action_type`,`equipment_id`,`content`,`priority`,`status`,`operator_id`,`result`,`wo_no`,`create_time`），
> 或把 `Execution` 降级为「`copilot_wo_draft` 的视图 + 计算字段」——**这是当前最薄弱的环节**。

---

## 14. 设备中心 · 设备总览 `/equipment/overview`（EquipmentOverview.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/equipment/summary` | 设备 KPI（总数/在线/离线/异常/风险） | `base_equipment` COUNT 聚合 |
| GET | `/api/equipment/status-distribution` | 状态分布图 | `base_equipment` GROUP BY `status` |

**GET /api/equipment/summary**

```json
{ "total": 48, "normal": 42, "warning": 3, "fault": 1, "offline": 2,
  "online": 46, "onlineRate": 95.8,
  "highRisk": 2, "midRisk": 3, "lowRisk": 43,
  "byLine": [{ "lineName": "A产线", "total": 16, "normal": 14, "abnormal": 2 }] }
```

---

## 15. 设备中心 · 设备列表 `/equipment/list`（EquipmentList.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/equipment` | 设备列表 ✅ **主接口** | `base_equipment` ⨝ `base_production_line` ⨝ `eqp_runtime_data` |
| GET | `/api/equipment/{id}` | 设备详情 | 同上 |
| POST | `/api/equipment` | 新增设备 | `base_equipment` INSERT |
| PUT | `/api/equipment/{id}` | 编辑设备 | `base_equipment` UPDATE |
| GET | `/api/equipment/export` | 导出清单 Excel | 文件流 |

**GET /api/equipment** — 查询参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `status` | string | 正常/预警/故障/离线（`全部` 表示不过滤） |
| `lineName` | string | A产线/B产线/C产线 |
| `riskLevel` | string | 高/中/低 |
| `keyword` | string | 匹配 `equipment_name` 或 `equipment_code` |
| `page` / `size` | int | **可选**，不传返回数组，传了返回分页对象 |

→ `Equipment[]`（对齐 `types/index.ts:68-91`）

```json
[{ "id": 3, "code": "EQ-IM-003", "name": "3号注塑机", "type": "注塑",
   "model": "IM-220T", "vendor": "海天", "lineName": "A产线",
   "status": "预警", "health": 72, "riskLevel": "高",
   "lastException": "主轴轴承振动持续升高",
   "temperature": 68.4, "vibration": 7.8, "current": 42.6,
   "predict": { "h24": 12, "d7": 45, "d30": 68 },
   "diagnosis": "主轴轴承磨损概率 87%，建议 48 小时内更换。" }]
```

| 字段 | 数据来源 / 计算 | 备注 |
|---|---|---|
| `code` | `equipment_code` | |
| `name` | `equipment_name` | |
| `type` / `model` / `vendor` | `equipment_type` / `model` / `manufacturer` | |
| `lineName` | JOIN `base_production_line.line_name` on `line_id` | ⚠️ 表内只有 `line_id` |
| `status` / `health` / `riskLevel` | `status` / `health_score` / `risk_level` | |
| `lastException` | **表中无此列** | ⚠️ 建议从最近一条 `ai_insight.ai_diagnosis` 派生，或加列 `base_equipment.last_exception` |
| `temperature` / `vibration` / `current` | JOIN `eqp_runtime_data` | |
| `predict.h24/d7/d30` | **表中无此列** | ⚠️ 需新增 `eqp_fault_predict` 表（`equipment_id`,`h24`,`d7`,`d30`,`model_version`,`create_time`） |
| `diagnosis` | 最近 `ai_insight.ai_diagnosis`，或新增列 | |

---

## 16. 设备中心 · 设备详情 `/equipment/detail/:id`（EquipmentDetail.vue）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/equipment/{id}` | 设备主体（复用列表详情） |
| GET | `/api/equipment/{id}/trend` | 趋势图数据（温度/振动/电流，按小时或按天） |
| GET | `/api/equipment/{id}/insights` | 该设备的 AI 洞察列表 |

**GET /api/equipment/{id}/trend**

```json
{ "granularity": "hour",
  "points": [{ "time": "08:00", "temperature": 64.2, "vibration": 6.1, "current": 40.8 }] }
```

> ⚠️ `eqp_runtime_data` 的 `uk_equipment_id` 是**唯一键**，只能存「最新一条」实时值，**存不了历史趋势**。
> 必须新增 `eqp_runtime_history`（`equipment_id`,`temperature`,`vibration`,`current`,`record_time`，`KEY (equipment_id, record_time)`）。

---

## 17. 设备中心 · 维修工单 `/equipment/work-orders`（WorkOrderList.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/work-orders` | 工单列表（`status`、`priority`、`keyword`、`page`） | `eqp_work_order` ⨝ `base_equipment` ⨝ `sys_user` |
| GET | `/api/work-orders/{id}` | 工单详情 | 同上 |
| POST | `/api/work-orders` | 新建工单 | INSERT |
| PUT | `/api/work-orders/{id}` | 修改工单（状态流转/改派） | UPDATE |
| DELETE | `/api/work-orders/{id}` | 删除（逻辑删） | `is_deleted=1` |
| GET | `/api/work-orders/stats` | 各状态计数（`countBy`） | GROUP BY `status` |
| POST | `/api/work-orders/suggest-assignee` | AI 派工建议 | 调 LLM + 历史工单 |

**GET /api/work-orders** → `WorkOrder[]`

```json
[{ "id": 18, "woNo": "WO-20260907-018", "equipmentId": 3, "equipmentName": "3号注塑机",
   "faultType": "轴承异常", "priority": "紧急",
   "description": "振动 7.8mm/s 超阈值，主轴轴承磨损",
   "assignee": "吴运成", "status": "待处理", "source": "AI Agent",
   "createTime": "2026-09-07 16:58" }]
```

| 字段 | 数据来源 | 备注 |
|---|---|---|
| `equipmentName` | JOIN `base_equipment.equipment_name` | |
| `assignee` | JOIN `sys_user.real_name` on `assignee_id` | ⚠️ 表内只有 `assignee_id` |
| 其余 | 同名 | |

**GET /api/work-orders/stats**

```json
{ "pending": 3, "repairing": 2, "verifying": 1, "done": 12, "total": 18 }
```

**POST /api/work-orders/suggest-assignee** 请求/响应

```json
// 请求
{ "woNo": "WO-20260907-018", "faultType": "轴承异常" }
// 响应
{ "assigneeId": 3, "assignee": "吴运成", "confidence": 0.96,
  "reason": "其最近 30 天主轴承更换成功率 96%" }
```

---

## 18. 生产运营 · 生产总览 `/production/overview`（ProductionOverview.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/production/overview` | 全厂生产 KPI | `prod_plan` 当日聚合 |
| GET | `/api/production/lines` | 产线列表（同 `/api/dashboard/lines`） | 复用 |

---

## 19. 生产运营 · 生产计划 `/production/plans`（ProductionPlan.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/production/plans` | 计划列表（`lineName`、`status`、`keyword`、`page`） | `prod_plan` ⨝ `base_production_line` |
| GET | `/api/production/plans/{id}` | 计划详情 | |
| POST | `/api/production/plans` | 新建计划 | INSERT |
| PUT | `/api/production/plans/{id}` | 修改/下达计划 | UPDATE `status` |
| POST | `/api/production/plans/suggest` | AI 排产建议 | 调 LLM |

**GET /api/production/plans** → `PlanItem[]`

```json
[{ "id": 1, "planDate": "2026-09-07", "lineName": "A产线",
   "product": "外壳组件 A-220", "targetQty": 4800, "actualQty": 4460,
   "achieve": 92.9, "status": "执行中",
   "aiExplanation": "设备停机 28 分钟，预计 18:00 前可补齐" }]
```

| 字段 | 数据来源 |
|---|---|
| `product` | `prod_plan.product_name` |
| `achieve` | `actualQty / targetQty × 100`（后端计算，保留 1 位） |
| `lineName` | JOIN `base_production_line.line_name` |

> ⚠️ `PlanItem.status` 前端类型只有 `'执行中' \| '已延期' \| '待执行'`，**但表默认值含 `'已完成'`**，需前端补类型或后端不返回已完成。

---

## 20. 生产运营 · OEE分析 `/production/oee`（OeeAnalysis.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/oee/summary` | OEE 汇总（`oeeSummary`） | `prod_plan` + `prod_oee_loss` |
| GET | `/api/oee/by-line` | 分产线 OEE | 同上 GROUP BY line |
| GET | `/api/oee/losses` | 损失明细（`oeeLosses`） | `prod_oee_loss` ⨝ `prod_batch_record` |
| GET | `/api/oee/by-shift` | 分时段 OEE 趋势（`oeeByShift`） | 按 2 小时聚合 |

**GET /api/oee/summary**

```json
{ "oee": 87.6, "oeeDelta": "较昨日 -4.8pct", "target": 90,
  "availability": 94.3, "performance": 95.3, "qualityIndex": 97.4,
  "note": "OEE = 开动率 × 性能效率 × 质量指数 · 目标 90%" }
```

**GET /api/oee/losses** → `oeeLosses`

```json
[{ "lossType": "设备停机", "trigger": "故障 / 更换模具", "duration": "146 分钟",
   "impact": "-2.0pct", "lineName": "B产线", "equipment": "5号注塑机" }]
```

| 字段 | 数据来源 | 备注 |
|---|---|---|
| `duration` | `prod_oee_loss.duration_minutes` | 后端格式化 "146 分钟" |
| `trigger` / `impact` / `equipment` | **表中无** | ⚠️ `prod_oee_loss` 只有 `loss_type`,`duration_minutes`,`description`，建议 `description` 存 JSON 或加列 |
| `lineName` | 经 `batch_id` → `prod_batch_record.plan_id` → `prod_plan.line_id` 三跳 | 链路较深 |

---

## 21. 质量总览 `/quality/overview`（QualityOverview.vue）

> ⚠️ **重大缺口**：库里**没有任何质量表**。`prod_batch_record` 只有 `good_qty` / `defect_qty`，没有缺陷类型维度。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/quality/summary` | 质量 KPI（良品率/缺陷数/批次合格率/客诉） |
| GET | `/api/quality/defects` | 缺陷 Top + 分产线/分产品/分设备 |

**需要新增表**：

```sql
-- 缺陷明细
CREATE TABLE prod_defect_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL COMMENT '批次ID',
  equipment_id BIGINT DEFAULT NULL COMMENT '责任设备ID',
  defect_type VARCHAR(50) NOT NULL COMMENT '缺陷类型（外观划伤/尺寸超差/装配漏装/色差）',
  qty INT NOT NULL DEFAULT 0 COMMENT '缺陷数量',
  record_date DATE NOT NULL COMMENT '发生日期',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_date_type (record_date, defect_type)
);
```

**GET /api/quality/summary**

```json
{ "yieldRate": 97.1, "yieldDelta": "较昨日 -0.6pct",
  "defects": 80, "passRate": 98.4, "batchRate": 98.4, "complaints": 0,
  "aiSummary": "外观划伤 42 件占不良 52.5%，集中于 C产线夹具工位……" }
```

**GET /api/quality/defects** → 一次返回 4 个区块

```json
{ "top": [{ "type": "外观划伤", "qty": 42, "rate": 0.86, "lineName": "C产线" }],
  "byLine": [{ "lineName": "A产线", "rate": 98.4, "defectQty": 19, "mainDefect": "尺寸超差", "delta": "-0.2" }],
  "byProduct": [{ "product": "外壳组件 A-220", "rate": 98.4, "qty": 4460, "defectQty": 19 }],
  "byEquipment": [{ "equipment": "C产线夹具", "defectType": "外观划伤", "qty": 42, "share": 52.5, "riskLevel": "预警" }] }
```

---

## 22. 能耗中心 · 能耗总览 `/energy/overview`（EnergyOverview.vue）

> ⚠️ **重大缺口**：库里**没有任何能耗表**（`energy` 相关 0 张）。`factorySummary` 里"今日能耗 62.4 千kWh"目前完全无来源。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/energy/summary` | 能耗 KPI（综合能耗 + 4 类介质） |
| GET | `/api/energy/overview-charts` | 7 日趋势 + 介质占比 + 分车间 |

**需要新增表**：

```sql
-- 能耗读表（分时）
CREATE TABLE energy_meter (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workshop_id BIGINT DEFAULT NULL COMMENT '车间ID',
  equipment_id BIGINT DEFAULT NULL COMMENT '设备ID（可空）',
  medium VARCHAR(20) NOT NULL COMMENT '介质：电/水/气/蒸汽',
  value DECIMAL(12,2) NOT NULL COMMENT '用量',
  unit VARCHAR(20) NOT NULL COMMENT '单位（kWh/t/Nm³）',
  record_date DATE NOT NULL,
  record_hour TINYINT DEFAULT NULL COMMENT '小时 0-23，按天汇总时为空',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_date_medium (record_date, medium),
  KEY idx_equipment (equipment_id, record_date)
);

-- 节能优化方案
CREATE TABLE energy_saving_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_code VARCHAR(50) NOT NULL COMMENT 'EO-20260907-012',
  plan_name VARCHAR(200) NOT NULL,
  location VARCHAR(100) COMMENT 'A车间 / 4号空压机',
  medium VARCHAR(20) COMMENT '介质',
  status VARCHAR(20) NOT NULL DEFAULT '待执行' COMMENT '待执行/已完成/执行失败',
  saving DECIMAL(12,2) COMMENT '预计年节约',
  reason TEXT COMMENT 'AI 判断原因',
  advice TEXT COMMENT '建议措施',
  scope VARCHAR(100) COMMENT '影响范围',
  result VARCHAR(200) COMMENT '执行结果',
  exec_time DATETIME COMMENT '执行时间',
  source VARCHAR(50) COMMENT '人工/AI Agent',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_plan_code (plan_code)
) COMMENT='AI节能优化方案表';
```

**GET /api/energy/summary**

```json
{ "total": 62.4, "totalUnit": "t标煤",
  "electricity": 48.6, "electricityNote": "+12.4%",
  "water": 186, "waterNote": "-3.1%",
  "gas": 920, "gasNote": "+6.8%",
  "steam": 14.2, "steamNote": "+9.4%",
  "cost": 48700, "unitConsumption": 5.22, "savingMonthly": "¥28,600",
  "aiSummary": "A车间夜间电耗 +18.7% 为今日最大异常……" }
```

---

## 23. 能耗中心 · 能耗分析 `/energy/analysis`（EnergyAnalysis.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/energy/trend` | 趋势（按日/按小时）`?medium=电&shift=白班&granularity=hour` | `energy_meter` |
| GET | `/api/energy/by-workshop` | 分车间对比 | `energy_meter` GROUP BY workshop |
| GET | `/api/energy/equipment-rank` | 设备能耗排行 Top N | `energy_meter` GROUP BY equipment |
| GET | `/api/energy/shift` | 白班/夜班对比 | 按 `record_hour` 分段 |
| GET | `/api/energy/medium-share` | 介质占比（饼图） | GROUP BY `medium` |

**GET /api/energy/trend** → 前端 `hourlyPower`（24 点，今日 vs 昨日同时段）

```json
[{ "hour": 0, "today": 1971, "yesterday": 1862 }, { "hour": 1, "today": 1854, "yesterday": 1740 }]
```

**GET /api/energy/shift** → `shiftEnergy`

```json
[{ "workshop": "A车间", "day": 14.2, "night": 16.9 }]
```

---

## 24. 能耗中心 · AI节能优化 `/energy/saving`（EnergySaving.vue）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/energy/plans` | 方案列表（`status`、`medium`、`page`） |
| GET | `/api/energy/plans/{id}` | 方案详情 |
| POST | `/api/energy/plans/{id}/execute` | 下发执行 |
| POST | `/api/energy/plans/generate` | AI 生成新方案 |

**GET /api/energy/plans** → `EnergyPlan[]`

```json
[{ "id": 12, "code": "EO-20260907-012", "name": "关闭A车间2台低负载空压机",
   "location": "A车间 / 4号空压机", "medium": "电", "status": "待执行",
   "saving": "¥8,600/月", "result": "—", "execTime": "—",
   "source": "AI Agent", "reason": "低负载空转占全厂能耗 13.8%",
   "advice": "保留 1 台运行，压力设定 0.65MPa", "scope": "A车间" }]
```

---

## 25. 知识库 · 文档管理 `/knowledge/documents`（DocumentManage.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/knowledge/documents` | 文档列表（`docType`、`parseStatus`、`keyword`、`page`） | `kb_document` |
| GET | `/api/knowledge/documents/stats` | 顶部统计（总数/已解析/解析中） | COUNT |
| POST | `/api/knowledge/documents` | 上传文档（multipart） | INSERT + 异步解析 |
| POST | `/api/knowledge/documents/{id}/reparse` | 重新解析 | 改 `parse_status='解析中'` |
| DELETE | `/api/knowledge/documents/{id}` | 删除 | DELETE |
| GET | `/api/knowledge/documents/{id}/files` | 文档下的分段/文件清单（`kbFilesOfDoc`） | `kb_chunk` |

**GET /api/knowledge/documents** → `KbDocument[]`

```json
[{ "id": 1, "fileName": "XX型号注塑机维修手册.pdf", "format": "PDF", "size": "18.6 MB",
   "uploader": "吴运成", "uploadTime": "2026-08-12 10:24",
   "parseStatus": "已解析", "docType": "维修手册", "relatedEquipment": "3号注塑机" }]
```

| 字段 | 数据来源 | 备注 |
|---|---|---|
| `fileName` | `kb_document.doc_name` | |
| `format` | 从 `doc_name` 扩展名推导 | ⚠️ 表中无 `format` |
| `size` | `kb_document.file_size`（varchar，已存 "18.6 MB"） | ✅ 类型匹配 |
| `uploader` | JOIN `sys_user.real_name` on `upload_user_id` | |
| `docType` | `kb_document.doc_type` | |
| `relatedEquipment` | JOIN `base_equipment.equipment_name` on `equipment_id` | |

**GET /api/knowledge/documents/{id}/files**

```json
[{ "fileName": "XX型号注塑机维修手册.pdf", "format": "PDF", "size": "18.6 MB", "parseStatus": "已解析" }]
```

---

## 26. 知识库 · 文档问答 `/knowledge/qa`（DocumentQa.vue）

| 方法 | 路径 | 说明 | 数据来源 |
|---|---|---|---|
| GET | `/api/knowledge/qa/recommended` | 推荐问题（`recommendedQuestions`） | 配置 |
| GET | `/api/knowledge/qa/models` | 可选模型列表（`qaModels`） | 配置 |
| POST | `/api/knowledge/qa/ask` | 提问（SSE 流式 + 引用来源） | `kb_chunk` 向量检索 + LLM |
| POST | `/api/knowledge/qa/session/reset` | 重置会话 | — |

**POST /api/knowledge/qa/ask**

```json
// 请求
{ "question": "3号注塑机轴承更换周期是多久？", "model": "deepseek-v4.1", "topK": 5 }
// 响应（SSE）
data: {"type":"delta","content":"根据《XX型号注塑机维修手册》第 3.2 节……"}
data: {"type":"sources","sources":[
  {"docId":1,"docName":"XX型号注塑机维修手册.pdf","chunkId":12,"score":0.91,"snippet":"主轴轴承建议每 2000 小时更换一次"}]}
data: {"type":"done"}
```

> **RAG 链路**：`kb_document` → `kb_chunk.chunk_content` + `kb_chunk.vector_id` → 向量库（`ai-service` 负责）→ 召回 → 拼 prompt → LLM → 带引用返回。

---

## 27. 数据库缺口汇总（需要补的表/列）

> 这是本次分析最重要的产出：**前端需要的数据，库里有一半没有落点**。

### 27.1 需要新增的表（6 张）

| 表 | 用途 | 服务页面 |
|---|---|---|
| `eqp_runtime_history` | 设备温度/振动/电流**历史**时序（现有 `eqp_runtime_data` 唯一键只存最新值） | 设备详情趋势图 |
| `eqp_fault_predict` | 故障预测概率 h24/d7/d30 | 设备详情、工单列表 |
| `prod_defect_record` | 缺陷明细（类型/数量/责任设备） | 质量总览 |
| `energy_meter` | 能耗读表（介质/分时/分车间/分设备） | 能耗总览、能耗分析 |
| `energy_saving_plan` | AI 节能方案 | AI节能优化 |
| `copilot_execution` | Copilot/Agent 操作流水（`execNo`/`actionType`/`operator`/`result`） | 操作执行 |

### 27.2 需要新增的列（8 处）

| 表 | 建议加列 | 原因 |
|---|---|---|
| `base_equipment` | `last_exception varchar(200)` | 前端 `Equipment.lastException` 无来源；也可从 `ai_insight` 派生 |
| `ai_insight` | `impact varchar(100)` | `Insight.impact` 前端必填，表内无 |
| `ai_insight` | `agent_id bigint` | `Insight.agent`（哪个 Agent 发现的）无来源 |
| `ai_insight` | `discover_time datetime` | 前端有 `discoverTime`，`create_time` 语义不同 |
| `ai_agent_task` | `result varchar(200)`、`decision text` | 前端 `AgentTask.result` / `.decision` 无来源 |
| `ai_agent_run_log` | `tools varchar(200)` | 前端 `AgentRunLog.tools` 无来源 |
| `ai_report` | `scope varchar(50)`、`status varchar(20)` | 前端 `Report.scope` / `.status` 无来源 |
| `prod_oee_loss` | `trigger varchar(100)`、`impact_pct decimal(5,2)`、`equipment_id bigint` | 前端 `oeeLosses` 三列无来源 |
| `sys_user_agent`（关联表） | `user_id`,`agent_id`,`enabled` | "我的 Agent" 无归属关系 |

### 27.3 字段名映射表（前端 camelCase ↔ 库 snake_case）

| 前端字段 | 库字段 | 表 |
|---|---|---|
| `Equipment.code` | `equipment_code` | `base_equipment` |
| `Equipment.name` | `equipment_name` | `base_equipment` |
| `Equipment.type` | `equipment_type` | `base_equipment` |
| `Equipment.vendor` | `manufacturer` | `base_equipment` |
| `Equipment.health` | `health_score` | `base_equipment` |
| `Equipment.lineName` | `line_id` → `line_name`（JOIN） | `base_production_line` |
| `WorkOrder.woNo` | `wo_no` | `eqp_work_order` |
| `WorkOrder.assignee` | `assignee_id` → `real_name`（JOIN） | `sys_user` |
| `WorkOrder.equipmentName` | `equipment_id` → `equipment_name`（JOIN） | `base_equipment` |
| `Insight.aiJudgement` | `ai_diagnosis` | `ai_insight` |
| `Agent.desc` | `description` | `ai_agent` |
| `Agent.permission` | `exec_permission` | `ai_agent` |
| `AgentTask.agentName` | `agent_id` → `agent_name`（JOIN） | `ai_agent` |
| `PlanItem.product` | `product_name` | `prod_plan` |
| `PlanItem.lineName` | `line_id` → `line_name`（JOIN） | `base_production_line` |
| `KbDocument.fileName` | `doc_name` | `kb_document` |
| `KbDocument.relatedEquipment` | `equipment_id` → `equipment_name`（JOIN） | `base_equipment` |
| `EnergyPlan.code` | `plan_code` | `energy_saving_plan`（待建） |

> `application.yml` 已配 `map-underscore-to-camel-case: true`，**同名字段自动映射无需处理**，只有「改名」和「联表」的才需要写 `@TableField` 别名或 `resultMap`。

---

## 28. 建议的接口实现优先级

| 阶段 | 范围 | 理由 |
|---|---|---|
| **P0** | 公共 `Result`/`GlobalExceptionHandler` + 设备中心 15 个接口 + 工单 7 个 | 表结构完整（`base_equipment`/`eqp_runtime_data`/`eqp_work_order` 齐全），只缺预测字段，可先返回 mock 预测值 |
| **P1** | Agent 中心 11 个 + Copilot 操作执行 5 个 | `ai_agent`/`ai_agent_task`/`ai_agent_run_log`/`copilot_wo_draft` 表完整 |
| **P2** | 生产运营 11 个 + OEE 4 个 | `prod_plan`/`prod_batch_record`/`prod_oee_loss` 完整 |
| **P3** | 质量 + 能耗 19 个 | **必须先建表**（`prod_defect_record`/`energy_meter`/`energy_saving_plan`） |
| **P4** | 知识库 10 个 + AI 对话/问答 | 依赖 `ai-service`（向量库 + LLM），是独立子系统 |
| **P5** | 鉴权 + 菜单权限 6 个 | `sys_user`/`sys_role`/`sys_menu` 表已建，但前端尚未做登录页 |

---

## 29. 待确认问题

1. **`use_mock` 开关策略**：建议 `request.ts` 的 `USE_MOCK` 改为读 `import.meta.env.VITE_USE_MOCK`，后端每完成一组接口就把对应页面的 mock 关掉，实现灰度替换。
2. **分页模式**：前端目前全量 + 客户端分页。是否统一改服务端分页？（数据量 48 台设备无压力，但运行记录/执行流水会增长）
3. **AI 相关接口的 LLM 选型**：`ai-service`（Python）承接还是 Java 直连？（建议 Python 独立服务，Java 通过 HTTP 调）
4. **故障预测 `predict` 的算法**：规则式（振动/温度阈值）还是模型？决定是否建 `eqp_fault_predict` 表。
5. **租户/权限**：`sys_workshop` 已建，是否需要按车间数据隔离（`sys_user.workshop_id` 已有列）？