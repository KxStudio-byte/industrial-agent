/**
 * 认证接口。
 *
 * 与 request.ts 的关系：
 *   request.ts 负责「统一带 token、统一拆包、统一报错」；
 *   本文件只负责「认证这件事本身」的字段形状与副作用（写/清 localStorage）。
 * 页面里不要直接读 localStorage.getItem('token')，一律走这里的 AUTH_TOKEN_KEY。
 */

import { computed, reactive } from 'vue'
import request, { api } from './request'

export const AUTH_TOKEN_KEY = 'token'
export const AUTH_TOKEN_EXPIRE_KEY = 'tokenExpireAt'
export const AUTH_USER_KEY = 'user'

export interface LoginPayload {
  username: string
  password: string
}

/** 与后端 vo/UserInfoVO 一一对应。注意 id/roleId/workshopId 是字符串（Long 序列化为 String） */
export interface CurrentUser {
  id: string
  username: string
  realName: string
  roleId: string
  roleName: string
  roleKey: string
  workshopId: string
  permissions: string[]
  /** 无 token 的接口（如 /me）不会带这个值 */
  tokenExpireAt?: number
}

/** 与后端 vo/LoginVO 一一对应。expiresIn/expiresAt 是原生 long，序列化为数字 */
export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
  expiresAt: number
  user: CurrentUser
}

export interface LogoutResult {
  userId: string
  username: string
  revokedBefore: number
  clearLocalKeys: string[]
}

/** 登录：成功后把 token 与用户信息落盘，刷新页面不会掉登录态 */
export async function login(payload: LoginPayload): Promise<LoginResult> {
  const data = await api.post<LoginResult>('/api/auth/login', payload)
  localStorage.setItem(AUTH_TOKEN_KEY, data.token)
  localStorage.setItem(AUTH_TOKEN_EXPIRE_KEY, String(data.expiresAt))
  localStorage.setItem(AUTH_USER_KEY, JSON.stringify(data.user))
  syncAuthState()
  return data
}

/** 退出登录：即使后端失败也要清本地，否则用户会卡在一个「假登录」状态 */
export async function logout(): Promise<void> {
  try {
    await api.post<LogoutResult>('/api/auth/logout')
  } finally {
    clearAuthStorage()
    syncAuthState()
  }
}

/** 当前登录用户（每次进页面/刷新后调用一次即可） */
export async function fetchCurrentUser(): Promise<CurrentUser> {
  const data = await api.get<CurrentUser>('/api/auth/me')
  localStorage.setItem(AUTH_USER_KEY, JSON.stringify(data))
  syncAuthState()
  return data
}

export function getToken(): string | null {
  return localStorage.getItem(AUTH_TOKEN_KEY)
}

export function getCachedUser(): CurrentUser | null {
  const raw = localStorage.getItem(AUTH_USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as CurrentUser
  } catch {
    // 手工改坏过就当作没有，不要让脏数据把首屏渲染打挂
    localStorage.removeItem(AUTH_USER_KEY)
    return null
  }
}

export function clearAuthStorage(): void {
  localStorage.removeItem(AUTH_TOKEN_KEY)
  localStorage.removeItem(AUTH_TOKEN_EXPIRE_KEY)
  localStorage.removeItem(AUTH_USER_KEY)
}

/* ==================== 全局登录态 ====================
   为什么用模块级 reactive 而不是 pinia：路由守卫（router/index.ts）要在
   组件树之外读写登录态，direct import 比 store 实例更省事，也不会踩
   「pinia 未安装就 useStore」的坑。等以后状态多了再迁到 pinia 不迟。 */

const authState = reactive({
  user: getCachedUser(),
  token: getToken()
})

/** 响应式的登录态快照，供模板直接绑定 */
export function useAuthState() {
  return {
    user: computed(() => authState.user),
    displayName: computed(() => authState.user?.realName || authState.user?.username || '未登录'),
    roleName: computed(() => authState.user?.roleName || ''),
    roleKey: computed(() => authState.user?.roleKey || ''),
    permissions: computed(() => authState.user?.permissions ?? []),
    isLoggedIn: computed(() => Boolean(authState.token))
  }
}

/** 登录/刷新用户信息/退出后同步内存态，保证页面不刷新也能变 */
export function syncAuthState(): void {
  authState.user = getCachedUser()
  authState.token = getToken()
}

/** 权限判定：后端没下发任何权限时一律 false，不做「默认放行」这种偷懒逻辑 */
export function hasPermission(perm: string): boolean {
  return authState.user?.permissions?.includes(perm) ?? false
}

/** 路由守卫用：有 token 就认为可进，token 是否还有效交给后端 401 兜底 */
export function isAuthenticated(): boolean {
  return Boolean(getToken())
}

export default request