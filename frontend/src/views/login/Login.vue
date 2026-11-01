<template>
  <div class="login">
    <!-- ============ 左：品牌区 ============ -->
    <section class="brand">
      <!-- 光晕球用两层径向渐变叠出来：外层大范围蓝雾 + 内层白色环 -->
      <div class="brand__glow" aria-hidden="true" />
      <div class="brand__orb" aria-hidden="true" />

      <header class="brand__logo">
        <div class="brand__mark">AI</div>
        <div class="brand__names">
          <div class="brand__title">工业智能体平台</div>
          <div class="brand__sub">Industrial AI Agent Platform</div>
        </div>
      </header>

      <div class="brand__slogan">
        <h1 class="brand__headline">
          用 <span class="brand__headline-ai">AI</span>驱动工业新生产力
        </h1>
        <p class="brand__lead">连接设备 · 理解数据 · 优化流程 · 赋能决策</p>
      </div>

      <div class="brand__features">
        <div v-for="f in loginFeatures" :key="f.title" class="feature">
          <div class="feature__icon">{{ f.icon }}</div>
          <div class="feature__title">{{ f.title }}</div>
          <div class="feature__desc">{{ f.desc }}</div>
        </div>
      </div>

      <footer class="brand__footer">
        <div class="brand__footer-main">SMART INDUSTRY · INTELLIGENT FUTURE</div>
        <div class="brand__footer-sub">让工业更智能 让未来更可期</div>
      </footer>
    </section>

    <!-- ============ 右：登录卡片 ============ -->
    <section class="panel">
      <div class="card">
        <header class="card__logo">
          <div class="card__mark">AI</div>
          <div class="card__names">
            <div class="card__title">工业智能体平台</div>
            <div class="card__sub">Industrial AI Agent Platform</div>
          </div>
        </header>

        <h2 class="card__welcome">欢迎登录</h2>
        <p class="card__desc">面向制造企业的 AI 智能决策与业务执行平台</p>

        <!-- 参考原型第二张图：整体错误条 + 两个字段的下方红色提示 -->
        <el-alert
          v-if="formError"
          class="card__alert"
          :title="formError"
          type="error"
          :closable="false"
          show-icon
        />

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          hide-required-asterisk
          class="card__form"
          @submit.prevent="onSubmit"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              size="large"
              placeholder="请输入账号"
              autocomplete="username"
              :prefix-icon="User"
              @keyup.enter="onSubmit"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              size="large"
              :type="passwordVisible ? 'text' : 'password'"
              placeholder="请输入密码"
              autocomplete="current-password"
              :prefix-icon="Lock"
              @keyup.enter="onSubmit"
            >
              <template #suffix>
                <span class="pwd-toggle" @click="passwordVisible = !passwordVisible">
                  <el-icon>
                    <component :is="passwordVisible ? View : Hide" />
                  </el-icon>
                </span>
              </template>
            </el-input>
          </el-form-item>

          <div class="card__options">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <span class="card__forgot" @click="onForgot">忘记密码?</span>
          </div>

          <el-button
            type="primary"
            class="card__submit"
            :loading="loading"
            native-type="submit"
            @click="onSubmit"
          >
            {{ loading ? '登录中…' : '登录' }}
          </el-button>
        </el-form>

        <div class="card__other">
          <span class="card__other-line" />
          <span class="card__other-text">其他登录方式</span>
          <span class="card__other-line" />
        </div>

        <div class="card__methods">
          <el-tooltip
            v-for="m in loginOtherMethods"
            :key="m.key"
            :content="`${m.name}登录即将开放`"
            placement="top"
          >
            <button type="button" class="method" @click="onOtherLogin(m.name)">
              {{ m.label }}
            </button>
          </el-tooltip>
        </div>

        <div class="card__slogans">
          <template v-for="(s, i) in loginSlogans" :key="s">
            <span v-if="i > 0" class="card__slogan-sep">|</span>
            <span>{{ s }}</span>
          </template>
        </div>

        <div class="demo">
          <div class="demo__head">演示账号（密码均为 123456）</div>
          <div class="demo__list">
            <button
              v-for="a in demoAccounts"
              :key="a.username"
              type="button"
              class="demo__item"
              @click="fillDemo(a.username)"
            >
              {{ a.username }} {{ a.role }}
            </button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Hide, Lock, User, View } from '@element-plus/icons-vue'
import { login } from '../../api/auth'
import {
  demoAccounts,
  loginFeatures,
  loginOtherMethods,
  loginSlogans
} from '../../mock'

const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)
const passwordVisible = ref(false)
const rememberMe = ref(true)
/** 卡片顶部的整体错误条（原型第二张图：「账号和密码不能为空」） */
const formError = ref('')

const REMEMBER_KEY = 'loginRememberedUsername'

const form = reactive({
  username: localStorage.getItem(REMEMBER_KEY) ?? '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '账号不能为空', trigger: 'blur' }],
  password: [{ required: true, message: '密码不能为空', trigger: 'blur' }]
}

/** 校验不通过时，指出是哪个字段空了——原型的错误条是按字段拼出来的 */
async function validate(): Promise<boolean> {
  if (!formRef.value) return false
  try {
    await formRef.value.validate()
    formError.value = ''
    return true
  } catch {
    const empty: string[] = []
    if (!form.username.trim()) empty.push('账号')
    if (!form.password) empty.push('密码')
    formError.value = empty.length
      ? `${empty.join('和')}不能为空`
      : '请检查输入内容后重试'
    return false
  }
}

async function onSubmit() {
  if (loading.value) return
  if (!(await validate())) return

  loading.value = true
  try {
    await login({ username: form.username.trim(), password: form.password })
    if (rememberMe.value) {
      localStorage.setItem(REMEMBER_KEY, form.username.trim())
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }
    // 登录前想去哪就去哪；没指定就进工厂总览
    const redirect = (route.query.redirect as string) || '/dashboard'
    await router.replace(redirect)
  } catch (e) {
    formError.value = (e as Error).message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function fillDemo(username: string) {
  form.username = username
  form.password = '123456'
  formError.value = ''
  formRef.value?.clearValidate()
  ElMessage.success(`已填入演示账号 ${username}`)
}

function onForgot() {
  ElMessage.info('请联系管理员重置密码')
}

function onOtherLogin(name: string) {
  ElMessage.info(`${name}登录尚在接入中`)
}
</script>

<style scoped>
/* ============================== 整体布局 ==============================
   原型是左右各占一半，高度撑满视口；左侧蓝色品牌区、右侧浅蓝白登录区。 */
.login {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: #eef4ff;
}

/* ============================== 左侧品牌区 ============================== */
.brand {
  position: relative;
  flex: 0 0 50%;
  min-width: 0;
  padding: 44px 0 34px 44px;
  background: linear-gradient(155deg, #1d61d6 0%, #1a5cb7 45%, #19559f 100%);
  color: #fff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 外层蓝色雾状光晕：原型里是一大片柔和的蓝雾，不能有硬边 */
.brand__glow {
  position: absolute;
  top: -8%;
  right: -6%;
  width: 620px;
  height: 620px;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(170, 208, 255, 0.55) 0%,
    rgba(120, 172, 250, 0.34) 36%,
    rgba(70, 135, 232, 0.14) 58%,
    rgba(45, 110, 220, 0) 72%
  );
  pointer-events: none;
}

/*
 * 中心的「瞳孔」球体。
 * 原型量测（1600 宽下）：深色瞳孔约 40px，紧贴一圈白高光到约 67px 外径，
 * 再往外是很淡的蓝雾。整块东西很小，不需要大尺寸再缩放。
 * 画法：单层 radial 从内到外排「深色球 → 白高光 → 蓝雾 → 透明」，blur 让它柔和。
 */
.brand__orb {
  position: absolute;
  top: 3%;
  right: 1.2%;
  width: 210px;
  height: 210px;
  border-radius: 50%;
  pointer-events: none;
  background: radial-gradient(
    circle at 50% 50%,
    #0a1422 0%,
    #122036 11%,
    #243a56 16%,
    #51719a 19.5%,
    #c8d8ef 21.5%,
    #ffffff 22.5%,
    #f2f7ff 24%,
    #b9d0ec 27%,
    #8fb0d8 31%,
    rgba(126, 160, 205, 0.55) 37%,
    rgba(126, 160, 205, 0) 50%
  );
  filter: blur(2.5px);
  opacity: 0.95;
}

.brand__logo {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  z-index: 1;
}
.brand__mark {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: #fff;
  color: #1a5cc0;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: -0.5px;
}
.brand__title {
  font-size: 16px;
  font-weight: 600;
  line-height: 22px;
}
.brand__sub {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.72);
  line-height: 14px;
  letter-spacing: 0.2px;
}

.brand__slogan {
  position: relative;
  z-index: 1;
  margin-top: 66px;
}
.brand__headline {
  margin: 0;
  font-size: 38px;
  font-weight: 700;
  line-height: 50px;
  letter-spacing: 1px;
}
/* 原型里「AI」两个字是更浅的天蓝色，用来把关键词挑出来 */
.brand__headline-ai {
  color: #9dc4ff;
}
.brand__lead {
  margin: 14px 0 0;
  font-size: 14px;
  line-height: 22px;
  color: rgba(255, 255, 255, 0.9);
  letter-spacing: 0.3px;
}

.brand__features {
  position: relative;
  z-index: 2;
  display: flex;
  gap: 16px;
  margin-top: 30px;
  /* 原型里四张卡从左边距一直排到光晕下方，第四张被光晕半压 */
  max-width: 630px;
}
.feature {
  width: 124px;
  height: 124px;
  padding: 16px 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.13);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(2px);
  display: flex;
  flex-direction: column;
}
.feature__icon {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.feature__title {
  margin-top: 14px;
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
}
.feature__desc {
  margin-top: 4px;
  font-size: 11.5px;
  line-height: 16px;
  color: rgba(255, 255, 255, 0.78);
}

.brand__footer {
  position: relative;
  z-index: 1;
  margin-top: auto;
}
.brand__footer-main {
  font-size: 12px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.85);
  line-height: 18px;
}
.brand__footer-sub {
  font-size: 12px;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.7);
  line-height: 18px;
}

/* ============================== 右侧登录区 ============================== */
.panel {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(150deg, #f5f9ff 0%, #eaf2ff 50%, #e5efff 100%);
  overflow-y: auto;
}

/* 右侧背景里两团很淡的蓝色光斑，原型上可见但不抢卡片 */
.panel::before,
.panel::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.panel::before {
  top: -140px;
  right: -120px;
  width: 480px;
  height: 480px;
  background: radial-gradient(circle, rgba(140, 185, 255, 0.28) 0%, rgba(140, 185, 255, 0) 70%);
}
.panel::after {
  bottom: -180px;
  left: -60px;
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, rgba(120, 170, 255, 0.2) 0%, rgba(120, 170, 255, 0) 70%);
}

.card {
  width: 328px;
  padding: 24px 24px 20px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 8px 30px rgba(26, 92, 192, 0.12);
}

.card__logo {
  display: flex;
  align-items: center;
  gap: 10px;
}
.card__mark {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: linear-gradient(160deg, #3d8bff, #1677ff);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: -0.5px;
}
.card__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  line-height: 18px;
}
.card__sub {
  font-size: 10px;
  color: var(--text-3);
  line-height: 13px;
}

.card__welcome {
  margin: 18px 0 0;
  font-size: 26px;
  font-weight: 700;
  line-height: 34px;
  color: var(--text-1);
  letter-spacing: 1px;
}
.card__desc {
  margin: 6px 0 16px;
  font-size: 12.5px;
  line-height: 18px;
  color: var(--text-3);
}

.card__alert {
  margin-bottom: 12px;
  --el-alert-padding: 6px 10px;
  --el-alert-title-font-size: 12px;
}

.card__form :deep(.el-form-item) {
  margin-bottom: 16px;
}
.card__form :deep(.el-form-item:last-of-type) {
  margin-bottom: 0;
}
.card__form :deep(.el-input__wrapper) {
  height: 44px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px var(--line) inset;
  padding: 0 12px;
}
.card__form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--brand) inset;
}
.card__form :deep(.el-input__inner) {
  font-size: 13.5px;
}
/* 错误态：原型里字段框变红、下方红字 */
.card__form :deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--c-red) inset;
}
.card__form :deep(.el-form-item__error) {
  position: static;
  padding-top: 4px;
  font-size: 11.5px;
  line-height: 16px;
}

.pwd-toggle {
  display: inline-flex;
  align-items: center;
  color: var(--text-3);
  cursor: pointer;
  font-size: 15px;
}
.pwd-toggle:hover {
  color: var(--brand);
}

.card__options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 14px 0 0;
  height: 22px;
}
.card__options :deep(.el-checkbox__label) {
  font-size: 12.5px;
  color: var(--text-2);
}
.card__forgot {
  font-size: 12.5px;
  color: var(--brand);
  cursor: pointer;
}
.card__forgot:hover {
  text-decoration: underline;
}

.card__submit {
  width: 100%;
  height: 42px;
  margin-top: 14px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 2px;
  background: linear-gradient(90deg, #1780ff 0%, #3a9cff 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(22, 119, 255, 0.28);
}
.card__submit:hover {
  background: linear-gradient(90deg, #4096ff 0%, #57acff 100%);
}

.card__other {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 18px;
}
.card__other-line {
  flex: 1;
  height: 1px;
  background: var(--line-light);
}
.card__other-text {
  font-size: 11.5px;
  color: var(--text-3);
  white-space: nowrap;
}

.card__methods {
  display: flex;
  justify-content: center;
  gap: 14px;
  margin-top: 12px;
}
.method {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--text-2);
  font-size: 12.5px;
  cursor: pointer;
  transition: all 0.15s;
}
.method:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.card__slogans {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 14px;
  font-size: 11.5px;
  color: var(--text-3);
}
.card__slogan-sep {
  color: var(--text-4);
}

.demo {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f5f9ff;
  font-size: 11.5px;
  line-height: 18px;
  color: var(--text-3);
}
.demo__head {
  color: var(--text-2);
}
.demo__list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-top: 2px;
}
.demo__item {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  color: var(--text-3);
  cursor: pointer;
}
.demo__item:hover {
  color: var(--brand);
  text-decoration: underline;
}

/* 窄屏：隐藏品牌区，登录卡片居中 */
@media (max-width: 900px) {
  .brand {
    display: none;
  }
  .panel {
    flex: 1;
  }
}
</style>