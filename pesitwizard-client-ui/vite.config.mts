import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'

// API backend URL - configurable via VITE_API_URL environment variable
const apiTarget = process.env.VITE_API_URL || 'http://localhost:8080'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  define: {
    // Fix for sockjs-client which expects Node.js globals
    global: 'globalThis',
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('src', import.meta.url)),
    },
  },
  server: {
    port: parseInt(process.env.VITE_PORT || '3001'),
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
      },
      '/ws': {
        target: apiTarget,
        changeOrigin: true,
        ws: true,
      },
      '/ws-raw': {
        target: apiTarget,
        changeOrigin: true,
        ws: true,
      },
    },
  },
})
