import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // dev: gọi /api qua proxy tới Spring Boot, khỏi phải cấu hình CORS cho từng cổng
    proxy: {
      '/api': { target: process.env.VITE_BACKEND_URL ?? 'http://localhost:8080', changeOrigin: true },
      // ảnh sản phẩm admin tải lên, backend phục vụ tại /uploads/**
      '/uploads': { target: process.env.VITE_BACKEND_URL ?? 'http://localhost:8080', changeOrigin: true },
    },
  },
})
