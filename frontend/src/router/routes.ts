import type { RouteRecordRaw } from 'vue-router'

/**
 * 侧边栏菜单结构。
 * group 为分组标题（原型里的小号灰字），children 为可点击的菜单项。
 * breadcrumb 用于顶部面包屑：工业智能体平台 / <group> / <title>
 */
export interface MenuItem {
title: string
path: string
/** 侧边栏右侧的闪电角标（AI 智能入口） */
spark?: boolean
}

export interface MenuGroup {
title: string
items: MenuItem[]
}

export const menuGroups: MenuGroup[] = [
{
  title: 'AI驾驶舱',
  items: [
    { title: '工厂总览', path: '/dashboard' },
    { title: 'AI实时洞察', path: '/ai-dashboard/insights', spark: true },
    { title: '风险预警', path: '/ai-dashboard/risk-alerts', spark: true }
  ]
},
{
  title: '智能体中心',
  items: [
    { title: 'Agent广场', path: '/agent/plaza' },
    { title: '我的Agent', path: '/agent/mine' },
    { title: 'Agent任务', path: '/agent/tasks', spark: true },
    { title: 'Agent运行记录', path: '/agent/run-logs', spark: true }
  ]
},
{
  title: '工业Copilot',
  items: [
    { title: 'AI对话', path: '/copilot/chat', spark: true },
    { title: '数据分析', path: '/copilot/analysis', spark: true },
    { title: '报表生成', path: '/copilot/reports', spark: true },
    { title: '操作执行', path: '/copilot/executions', spark: true }
  ]
},
{
  title: '设备中心',
  items: [
    { title: '设备总览', path: '/equipment/overview', spark: true },
    { title: '设备列表', path: '/equipment/list', spark: true },
    { title: '维修工单', path: '/equipment/work-orders', spark: true }
  ]
},
{
  title: '生产运营',
  items: [
    { title: '生产总览', path: '/production/overview', spark: true },
    { title: '生产计划', path: '/production/plans', spark: true },
    { title: 'OEE分析', path: '/production/oee', spark: true }
  ]
},
{
  title: '质量中心',
  items: [{ title: '质量总览', path: '/quality/overview', spark: true }]
},
{
  title: '能源中心',
  items: [
    { title: '能耗总览', path: '/energy/overview', spark: true },
    { title: '能耗分析', path: '/energy/analysis', spark: true },
    { title: 'AI节能优化', path: '/energy/saving', spark: true }
  ]
},
{
  title: '工业知识库',
  items: [
    { title: '文档管理', path: '/knowledge/documents', spark: true },
    { title: '文档问答', path: '/knowledge/qa', spark: true }
  ]
}
]

/** 路由表：每个页面都带 meta.group / meta.title 供面包屑使用 */
export const routes: RouteRecordRaw[] = [
  {
    // 登录页独占视口，不套外壳，所以 meta.public 让路由守卫放行
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    // 业务页统一套这层外壳（侧边栏 + 顶栏）。
    // 用嵌套路由而不是在 App.vue 里写 v-if，是为了让路由表本身就表达
    // 「哪些页面带壳、哪些不带」，守卫只看 meta，不用记特例。
    path: '/',
    component: () => import('../views/layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/FactoryOverview.vue'),
        meta: { title: '工厂总览', group: 'AI驾驶舱' }
      },
      {
        path: 'ai-dashboard/insights',
        name: 'AiInsights',
        component: () => import('../views/dashboard/AiInsights.vue'),
        meta: { title: 'AI实时洞察', group: 'AI驾驶舱' }
      },
      {
        path: 'ai-dashboard/risk-alerts',
        name: 'RiskAlerts',
        component: () => import('../views/dashboard/RiskAlerts.vue'),
        meta: { title: '风险预警', group: 'AI驾驶舱' }
      },
      {
        path: 'agent/plaza',
        name: 'AgentPlaza',
        component: () => import('../views/agent/AgentPlaza.vue'),
        meta: { title: 'Agent广场', group: '智能体中心' }
      },
      {
        path: 'agent/mine',
        name: 'MyAgents',
        component: () => import('../views/agent/MyAgents.vue'),
        meta: { title: '我的Agent', group: '智能体中心' }
      },
      {
        path: 'agent/tasks',
        name: 'AgentTasks',
        component: () => import('../views/agent/AgentTasks.vue'),
        meta: { title: 'Agent任务', group: '智能体中心' }
      },
      {
        path: 'agent/run-logs',
        name: 'AgentRunLogs',
        component: () => import('../views/agent/AgentRuns.vue'),
        meta: { title: 'Agent运行记录', group: '智能体中心' }
      },
      {
        path: 'copilot/chat',
        name: 'CopilotChat',
        component: () => import('../views/copilot/CopilotChat.vue'),
        meta: { title: 'AI对话', group: '工业Copilot' }
      },
      {
        path: 'copilot/analysis',
        name: 'CopilotAnalysis',
        component: () => import('../views/copilot/DataAnalysis.vue'),
        meta: { title: '数据分析', group: '工业Copilot' }
      },
      {
        path: 'copilot/reports',
        name: 'CopilotReports',
        component: () => import('../views/copilot/ReportList.vue'),
        meta: { title: '报表生成', group: '工业Copilot' }
      },
      {
        path: 'copilot/executions',
        name: 'CopilotExecutions',
        component: () => import('../views/copilot/Executions.vue'),
        meta: { title: '操作执行', group: '工业Copilot' }
      },
      {
        path: 'equipment/overview',
        name: 'EquipmentOverview',
        component: () => import('../views/equipment/EquipmentOverview.vue'),
        meta: { title: '设备总览', group: '设备中心' }
      },
      {
        path: 'equipment/list',
        name: 'EquipmentList',
        component: () => import('../views/equipment/EquipmentList.vue'),
        meta: { title: '设备列表', group: '设备中心' }
      },
      {
        path: 'equipment/detail/:id',
        name: 'EquipmentDetail',
        component: () => import('../views/equipment/EquipmentDetail.vue'),
        meta: { title: '设备详情', group: '设备中心', breadcrumb: '设备列表' }
      },
      {
        path: 'equipment/work-orders',
        name: 'WorkOrders',
        component: () => import('../views/equipment/WorkOrderList.vue'),
        meta: { title: '维修工单', group: '设备中心' }
      },
      {
        path: 'production/overview',
        name: 'ProductionOverview',
        component: () => import('../views/production/ProductionOverview.vue'),
        meta: { title: '生产总览', group: '生产运营' }
      },
      {
        path: 'production/plans',
        name: 'ProductionPlans',
        component: () => import('../views/production/ProductionPlan.vue'),
        meta: { title: '生产计划', group: '生产运营' }
      },
      {
        path: 'production/oee',
        name: 'OeeAnalysis',
        component: () => import('../views/production/OeeAnalysis.vue'),
        meta: { title: 'OEE分析', group: '生产运营' }
      },
      {
        path: 'quality/overview',
        name: 'QualityOverview',
        component: () => import('../views/quality/QualityOverview.vue'),
        meta: { title: '质量总览', group: '质量中心' }
      },
      {
        path: 'energy/overview',
        name: 'EnergyOverview',
        component: () => import('../views/energy/EnergyOverview.vue'),
        meta: { title: '能耗总览', group: '能源中心' }
      },
      {
        path: 'energy/analysis',
        name: 'EnergyAnalysis',
        component: () => import('../views/energy/EnergyAnalysis.vue'),
        meta: { title: '能耗分析', group: '能源中心' }
      },
      {
        path: 'energy/saving',
        name: 'EnergySaving',
        component: () => import('../views/energy/EnergySaving.vue'),
        meta: { title: 'AI节能优化', group: '能源中心' }
      },
      {
        path: 'knowledge/documents',
        name: 'KbDocuments',
        component: () => import('../views/knowledge/DocumentManage.vue'),
        meta: { title: '文档管理', group: '工业知识库' }
      },
      {
        path: 'knowledge/qa',
        name: 'KbQa',
        component: () => import('../views/knowledge/DocumentQa.vue'),
        meta: { title: '文档问答', group: '工业知识库' }
      },
    ]
  },
  {
    // 兜底：路径打错时回工厂总览，而不是白屏
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

