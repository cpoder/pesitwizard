import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'

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
    port: 3001,
    proxy: {
      '/api': {
        target: 'http://localhost:9081',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:9081',
        changeOrigin: true,
        ws: true,
      },
    },
  },
})
