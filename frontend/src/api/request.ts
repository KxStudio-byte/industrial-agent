import axios from 'axios'
import type { ApiResult } from '../types'

/**
 * 统一 axios 实例。
 *
 * baseURL 留空 = 走相对路径，由 vite.config.ts 里的 server.proxy 转发到 8080。
 * 千万不要写死 http://localhost:8080，否则会绕开代理、触发跨域。
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 15000
})

// 请求拦截器：后续接入 JWT 后在这里统一加 Authorization 头
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：把 { code, msg, data } 拆包，业务层直接拿 data
request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult
    if (body && typeof body.code === 'number' && body.code !== 200) {
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return response
  },
  (error) => {
    const status: number | undefined = error.response?.status

    // 401 = 登录态失效。这里只做「清本地 + 跳登录」这一件通用的事，
    // 具体跳转交给页面自己决定会漏掉绝大多数入口，容易留下半死状态。
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('tokenExpireAt')
      localStorage.removeItem('user')
      const current = window.location.hash || window.location.pathname
      if (!current.includes('/login')) {
        window.location.href = '/login'
      }
    }

    // 502/503/504 不是后端返回的业务错误，而是 vite 代理连不上 8080 时
    // 由代理进程吐出来的。这时候 error.response.data 是空串，
    // 兜底逻辑会直接把 "Request failed with status code 502" 显示给用户，
    // 用户完全不知道发生了什么。统一翻译成「后端没启动」这类可行动的提示。
    if (status === 502 || status === 503 || status === 504) {
      return Promise.reject(
        new Error('后端服务未启动或无法连接，请先启动 Java 后端（8080 端口）后重试')
      )
    }
    if (!error.response) {
      return Promise.reject(new Error('网络异常，请检查后端服务是否启动'))
    }

    const msg =
      error.response?.data?.msg || error.message || '网络异常，请检查后端服务是否启动'
    return Promise.reject(new Error(msg))
  }
)

/**
 * 调接口的通用写法。
 * <p>
 * 用法：
 *   const list = await api.get<Equipment[]>('/api/equipment', { status: '预警' })
 */
export const USE_MOCK = true

export const api = {
  async get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    const { data } = await request.get<ApiResult<T>>(url, { params })
    return data.data
  },
  async post<T>(url: string, body?: unknown): Promise<T> {
    const { data } = await request.post<ApiResult<T>>(url, body)
    return data.data
  },
  async put<T>(url: string, body?: unknown): Promise<T> {
    const { data } = await request.put<ApiResult<T>>(url, body)
    return data.data
  },
  async delete<T>(url: string): Promise<T> {
    const { data } = await request.delete<ApiResult<T>>(url)
    return data.data
  }
}

export default request