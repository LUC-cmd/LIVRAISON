import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// En developpement, /api et /ws sont relayes vers le backend Spring Boot.
const backend = process.env.VITE_BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backend, changeOrigin: false },
      '/ws': { target: backend, ws: true, changeOrigin: false },
    },
  },
});
