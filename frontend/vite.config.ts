import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * 前端请求怎么走（重要）：
 * 1) 业务接口 **不经 Vite 代理**，直连网关 `http://127.0.0.1:4399`（网关已开 CORS）。
 *    原因：node 的 http-proxy 转发 Spring Cloud Gateway 的 chunked 响应时，偶发
 *    `Parse Error: Data after Connection: close`，前端表现为 500「服务器内部错误」。
 *    地址可用环境变量覆盖：`VITE_API_BASE` / `VITE_WS_BASE`（见 src/api/http.ts、src/stores/chat.ts）。
 * 2) 下面两个代理只留给"数据库里存的相对路径资源"（种子数据的头像是 `/static/avatar/*.png`），
 *    浏览器会把这种相对地址拼到 5173 上，所以这里补两跳；上传出来的图片是绝对地址，直接打 4402。
 * 3) 想临时回到老方案（全部走代理）：把 http.ts 的 API_BASE 写成 '/api' 即可，下面第一条代理仍然在。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:4399',
        agent: false,
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, '')
      },
      '/static': {
        target: 'http://127.0.0.1:4402',
        changeOrigin: true
      },
      '/files': {
        target: 'http://127.0.0.1:4402',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://127.0.0.1:4399',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
