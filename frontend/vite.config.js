import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vitejs.dev/config/
// LingNow.cc - AI Powered Code Generator
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 2002,
    proxy: {
      '/api': {
        target: 'http://localhost:2001',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
