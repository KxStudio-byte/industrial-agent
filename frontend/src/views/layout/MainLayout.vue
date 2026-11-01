<template>
  <div class="layout">
    <!-- ========== 左侧导航 ========== -->
    <aside class="aside">
      <div class="logo">
        <div class="logo__mark">AI</div>
        <div class="logo__text">
          <div class="logo__title">工业智能体平台</div>
          <div class="logo__sub">Industrial AI Agent</div>
        </div>
      </div>

      <nav class="nav">
        <div v-for="group in menuGroups" :key="group.title" class="nav-group">
          <div class="nav-group__title">{{ group.title }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ 'nav-item--active': isActive(item.path) }"
          >
            <span class="nav-item__text">{{ item.title }}</span>
            <svg v-if="item.spark" class="nav-item__spark" viewBox="0 0 16 16" width="14" height="14">
              <path d="M8 1l2 4.5L14.5 8 10 10l-2 4.5L6 10 1.5 8 6 5.5z" fill="currentColor" />
            </svg>
          </router-link>
        </div>
      </nav>
    </aside>

    <!-- ========== 右侧主区 ========== -->
    <div class="main-wrap">
      <!-- 顶部状态栏 -->
      <header class="header">
        <div class="header-left">
          <el-select v-model="app.factory" class="factory-select" size="default" style="width: 180px">
            <template #prefix>
              <svg viewBox="0 0 16 16" width="14" height="14" class="factory-icon">
                <path
                  d="M1 14V6l4 2V6l4 2V3h3v11z"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.2"
                  stroke-linejoin="round"
                />
                <path d="M11 5h3v9h-3z" fill="none" stroke="currentColor" stroke-width="1.2" />
              </svg>
            </template>
            <el-option v-for="f in app.factoryOptions" :key="f" :label="f" :value="f" />
          </el-select>

          <span class="header-divider" />
          <span class="header-time">{{ app.currentTime }}</span>

          <span class="header-status">
            <span class="dot dot--green" />
            系统状态: {{ app.systemStatus }}
          </span>
          <span class="header-status">
            <span class="dot dot--blue" />
            AI运行中 {{ app.agentRunning }} 个 Agent
          </span>
        </div>

        <div class="header-right">
          <span class="msg">
            消息
            <i class="msg__badge">{{ app.unreadMessages }}</i>
          </span>
          <span class="role-tag">{{ auth.roleName || '访客' }}</span>
          <span class="user">
            <span class="user__name">{{ auth.displayName }}</span>
            <span class="user__role">{{ auth.roleKey }}</span>
            <svg viewBox="0 0 12 12" width="10" height="10" class="caret">
              <path d="M2 4l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.4" />
            </svg>
          </span>
          <el-button class="logout" text :loading="loggingOut" @click="onLogout">退出</el-button>
        </div>
      </header>

      <!-- 面包屑 + 内容 -->
      <main class="main-content">
        <div class="breadcrumb">
          <span>工业智能体平台</span>
          <span class="breadcrumb__sep">/</span>
          <span>{{ route.meta.group }}</span>
          <span class="breadcrumb__sep">/</span>
          <span class="breadcrumb__cur">{{ route.meta.title }}</span>
        </div>

        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../../stores/app'
import { menuGroups } from '../../router/routes'
import { logout, useAuthState } from '../../api/auth'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const auth = useAuthState()

const loggingOut = ref(false)

/** 用前缀匹配，保证子路由也能高亮父菜单 */
function isActive(path: string): boolean {
  return route.path === path || route.path.startsWith(path + '/')
}

/**
 * 退出登录。
 * 后端失败也要跳走——logout() 内部已经保证本地 token 一定被清掉，
 * 否则用户会看到一个「登不出去」的界面。
 */
async function onLogout() {
  loggingOut.value = true
  try {
    await logout()
    ElMessage.success('已退出登录')
    await router.replace('/login')
  } finally {
    loggingOut.value = false
  }
}
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ---------- 侧边栏 ---------- */
.aside {
  width: var(--aside-w);
  flex: 0 0 var(--aside-w);
  background: #fff;
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--header-h);
  padding: 0 16px;
  border-bottom: 1px solid var(--line-light);
}
.logo__mark {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: linear-gradient(135deg, #1677ff, #4096ff);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: -0.5px;
}
.logo__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  line-height: 18px;
}
.logo__sub {
  font-size: 10px;
  color: var(--text-3);
  line-height: 12px;
  transform: scale(0.95);
  transform-origin: left;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0 24px;
}
.nav-group + .nav-group {
  margin-top: 6px;
}
.nav-group__title {
  padding: 10px 20px 4px;
  font-size: 12px;
  color: var(--text-3);
  line-height: 18px;
}
.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  padding: 0 20px;
  font-size: 13.5px;
  color: var(--text-2);
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}
.nav-item:hover {
  background: #f7f9fc;
  color: var(--brand);
}
.nav-item--active {
  background: var(--brand-light);
  color: var(--brand);
  font-weight: 500;
}
.nav-item--active .nav-item__spark {
  color: var(--brand);
}
.nav-item__spark {
  color: var(--brand);
  opacity: 0.75;
  flex-shrink: 0;
}

/* ---------- 顶栏 ---------- */
.main-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.header {
  height: var(--header-h);
  flex: 0 0 var(--header-h);
  background: #fff;
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.factory-icon {
  color: var(--text-3);
  margin-right: 2px;
}
.header-divider {
  width: 1px;
  height: 16px;
  background: var(--line);
}
.header-time {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}
.header-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-2);
  white-space: nowrap;
}
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}
.dot--green {
  background: var(--c-green);
  box-shadow: 0 0 0 3px rgba(0, 180, 42, 0.12);
}
.dot--blue {
  background: var(--brand);
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.12);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.msg {
  position: relative;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
}
.msg__badge {
  position: absolute;
  top: -6px;
  right: -10px;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--c-red);
  color: #fff;
  font-size: 10px;
  font-style: normal;
  line-height: 15px;
  text-align: center;
}
.role-tag {
  font-size: 13px;
  color: var(--text-2);
}
.user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}
.user__name {
  font-size: 13px;
  color: var(--text-1);
}
.user__role {
  font-size: 11px;
  color: var(--text-3);
}
.caret {
  color: var(--text-3);
}
.logout {
  font-size: 13px;
  color: var(--text-2);
}

/* ---------- 内容区 ---------- */
.main-content {
  flex: 1;
  overflow-y: auto;
  background: var(--page-bg);
  padding: 12px 16px 24px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-3);
  line-height: 20px;
  margin-bottom: 6px;
}
.breadcrumb__sep {
  color: var(--text-4);
}
.breadcrumb__cur {
  color: var(--text-2);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>