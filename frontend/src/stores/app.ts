import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 全局布局状态。
 * 目前只有工厂选择和当前时间，后续换成真实登录态即可。
 */
export const useAppStore = defineStore('app', () => {
  /** 当前工厂（原型左上角下拉） */
  const factory = ref('华东精密制造工厂')
  const factoryOptions = [
    '华东精密制造工厂',
    '华南智能装配工厂',
    '西南精密注塑工厂',
    '华东智造集团'
  ]

  /** 顶部状态栏 */
  const systemStatus = ref('正常')
  const agentRunning = ref(13)
  const unreadMessages = ref(6)

  /** 当前登录用户 */
  const user = ref({ name: '陈建军', role: '厂长' })

  /** 顶部展示的静态时间（原型是固定时间戳，这里保留以便和原型一致） */
  const currentTime = ref('2026-09-07 17:42')

  return {
    factory,
    factoryOptions,
    systemStatus,
    agentRunning,
    unreadMessages,
    user,
    currentTime
  }
})