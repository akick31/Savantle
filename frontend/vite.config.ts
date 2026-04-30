import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiBase = env.VITE_API_BASE_URL?.trim()

  return {
    plugins: [react()],
    define: {
      'process.env.VITE_API_BASE_URL': JSON.stringify(apiBase),
    },
    server: {
      port: 3000,
      proxy: apiBase ? undefined : {
        '/api': 'http://localhost:163',
      },
    },
  }
})