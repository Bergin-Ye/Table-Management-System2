import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        // 注入 Element Plus 主题变量覆盖（Apple 系统色）。
        // 使用命名空间 ep，避免与业务样式 tokens 的全局变量名冲突。
        additionalData: `@use "@/styles/element.scss" as ep;`,
      },
    },
  },
  server: {
    port: 5173,
    open: false,
    proxy: {
      // 前端 /api 统一代理到后端 8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
