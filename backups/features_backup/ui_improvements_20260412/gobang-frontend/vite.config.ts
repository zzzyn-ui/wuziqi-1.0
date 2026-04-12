import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],

  // 路径别名配置
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },

  // 全局变量定义
  define: {
    __APP_VERSION__: JSON.stringify('1.0.0'),
    __API_BASE_URL__: JSON.stringify(process.env.API_BASE_URL || '/api'),
    // SockJS 需要 global 对象
    global: 'globalThis',
  },

  // 代理配置
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
})
