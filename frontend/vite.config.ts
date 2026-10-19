/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the API is proxied, so the browser sees one origin and the
// refresh-token cookie (SameSite=Strict, path /api/v1/auth) just works.
const backend = process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  define: {
    __APP_VERSION__: JSON.stringify(process.env.npm_package_version ?? 'dev'),
  },
  server: {
    port: 5173,
    proxy: {
      '/api': backend,
      '/actuator': backend,
      '/swagger-ui': backend,
      '/v3/api-docs': backend,
    },
  },
  build: {
    sourcemap: true,
    chunkSizeWarningLimit: 1000,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'lcov'],   // lcov is read by SonarCloud
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx', 'src/vite-env.d.ts'],
    },
  },
});
