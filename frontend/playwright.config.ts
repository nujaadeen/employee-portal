import { defineConfig, devices } from '@playwright/test'

/** Runs against the real stack: Keycloak on 8180, backend on 8080 (profile local), Vite on 5173. */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  workers: 1,
  fullyParallel: false,
  reporter: [['list']],
  use: { ...devices['Desktop Chrome'], baseURL: 'http://localhost:5173', viewport: { width: 1440, height: 900 }, trace: 'retain-on-failure' },
  webServer: { command: 'npm run dev', url: 'http://localhost:5173', reuseExistingServer: true, timeout: 60_000 },
})
