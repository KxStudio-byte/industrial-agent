import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles/index.css'

const app = createApp(App)

// 挂载 Pinia
app.use(createPinia())
// 挂载路由
app.use(router)
// 挂载 Element Plus
app.use(ElementPlus)

// 全局注册 Element Plus 的所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
