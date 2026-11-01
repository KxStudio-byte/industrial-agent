import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'
import { isAuthenticated } from '../api/auth'

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 登录守卫。
 *
 * 判断依据只看「本地有没有 token」，不去调 /api/auth/me 验证：
 *   - 每次跳转都打一次接口会让页面切换明显变慢；
 *   - token 是否真的有效，后端 401 会兜底（request.ts 里统一清 token 并跳登录）。
 * 这样即使 token 过期，用户最多看到一次 401 提示，而不是卡在加载中。
 */
router.beforeEach((to) => {
  const loggedIn = isAuthenticated()

  // 已登录还去登录页 → 直接送回首页，避免登录后按浏览器后退又回到登录页
  if (to.path === '/login') {
    return loggedIn ? { path: '/dashboard' } : true
  }

  if (!to.meta.public && !loggedIn) {
    // 记下原本想去的地址，登录成功后回跳
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  return true
})

// 切换页面时把内容区滚动条拉回顶部，否则长页面会停在上一页的位置
router.afterEach(() => {
  const main = document.querySelector('.main-content')
  if (main) main.scrollTop = 0
})

export default router