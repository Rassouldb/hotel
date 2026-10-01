import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// En développement, l'API Spring Boot (port 8080) est servie via le proxy Vite :
// le navigateur ne parle qu'à une seule origine, donc pas de CORS à configurer.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
