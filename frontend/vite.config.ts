import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 前端请求 /api/xxx -> 转发到 http://localhost:8080/api/xxx
      '/api': {
        target: 'http://localhost:8080',
        // changeOrigin: true 会把请求头里的 Host 改成 target 的 host，
        // 避免后端因 Host 校验/虚拟主机匹配问题返回 404 或 403
        changeOrigin: true
        // 注意：没有配置 rewrite，因为后端接口本身就带 /api 前缀。
        // 如果后端接口最终是 /hello（不含 /api），才需要加：
        // rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})