import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiBase = env.VITE_API_BASE_URL?.trim()

  return {
    plugins: [react()],
    server: {
      port: 3000,
      // Use local backend by default in dev; disable proxy if API base URL is explicitly set.
      proxy: apiBase ? undefined : {
        '/api': 'http://localhost:778',
      },
    },
  }
})
