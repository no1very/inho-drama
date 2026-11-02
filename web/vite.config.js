import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 构建产物输出到 dist/（由后端 Go embed 嵌入，勿改目录名）
export default defineConfig({
  plugins: [vue()],
  base: './',
  server: {
    port: 5173,
    // 开发时把 /api 代理到本地后端，便于联调
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist'
  }
})
