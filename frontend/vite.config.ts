/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'

const design = fileURLToPath(new URL('../design', import.meta.url))
const src = fileURLToPath(new URL('./src', import.meta.url))

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@design': design, '@': src } },
  // design/ is outside the project root, so the dev server must be allowed to read it.
  server: { port: 5173, strictPort: true, fs: { allow: ['..'] } },
  preview: { port: 5173, strictPort: true },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
