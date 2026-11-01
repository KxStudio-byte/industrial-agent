// 遍历全部路由截图 + 收集控制台错误
const puppeteer = require('puppeteer-core')
const fs = require('fs')
const path = require('path')

const ROUTES = [
  ['dashboard', '/dashboard'],
  ['ai-insights', '/ai-dashboard/insights'],
  ['risk-alerts', '/ai-dashboard/risk-alerts'],
  ['agent-plaza', '/agent/plaza'],
  ['agent-mine', '/agent/mine'],
  ['agent-tasks', '/agent/tasks'],
  ['agent-runs', '/agent/run-logs'],
  ['copilot-chat', '/copilot/chat'],
  ['copilot-analysis', '/copilot/analysis'],
  ['copilot-reports', '/copilot/reports'],
  ['copilot-executions', '/copilot/executions'],
  ['equip-overview', '/equipment/overview'],
  ['equip-list', '/equipment/list'],
  ['equip-detail', '/equipment/detail/3'],
  ['work-orders', '/equipment/work-orders'],
  ['prod-overview', '/production/overview'],
  ['prod-plans', '/production/plans'],
  ['prod-oee', '/production/oee'],
  ['quality', '/quality/overview'],
  ['energy-overview', '/energy/overview'],
  ['energy-analysis', '/energy/analysis'],
  ['energy-saving', '/energy/saving'],
  ['kb-docs', '/knowledge/documents'],
  ['kb-qa', '/knowledge/qa']
]

;(async () => {
  const out = path.resolve(__dirname, 'out')
  fs.mkdirSync(out, { recursive: true })
  const browser = await puppeteer.launch({
    executablePath: 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    headless: 'new',
    args: ['--no-sandbox', '--disable-gpu', '--lang=zh-CN']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1600, height: 1000, deviceScaleFactor: 1 })

  const errors = []
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(`[console] ${page.url()} :: ${m.text()}`)
  })
  page.on('pageerror', (e) => errors.push(`[pageerror] ${page.url()} :: ${e.message}`))

  for (const [name, route] of ROUTES) {
    await page.goto('http://127.0.0.1:5173' + route, { waitUntil: 'networkidle2', timeout: 60000 })
    await new Promise((r) => setTimeout(r, 1200))
    const title = await page.title()
    const bodyText = (await page.evaluate(() => document.body.innerText)).slice(0, 160).replace(/\s+/g, ' ')
    fs.writeFileSync(path.join(out, name + '.txt'), bodyText)
    await page.screenshot({ path: path.join(out, name + '.png') })
    console.log(`OK ${name.padEnd(18)} ${route.padEnd(24)} title=${title} :: ${bodyText.slice(0, 70)}`)
  }

  await page.goto('http://127.0.0.1:5173/dashboard', { waitUntil: 'networkidle2' })
  await browser.close()
  console.log('\n=== ERRORS (' + errors.length + ') ===')
  ;[...new Set(errors)].forEach((e) => console.log(e))
})().catch((e) => {
  console.error('FATAL', e)
  process.exit(1)
})