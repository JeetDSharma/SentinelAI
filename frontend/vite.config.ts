import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The dev server proxies backend paths to Spring Boot, so the browser sees one origin (no CORS config needed).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
})
