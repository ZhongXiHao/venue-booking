import process from 'node:process'
import { defineConfig, devices } from '@playwright/test'

/**
 * 指向已部署环境时（如 docker compose 的 8088 入口），设置 E2E_BASE_URL 并跳过自动启动 dev/preview server：
 *   $env:E2E_BASE_URL='http://localhost:8088'; npx playwright test
 */
const e2eBaseUrl = process.env.E2E_BASE_URL

export default defineConfig({
  testDir: './e2e',
  /* Maximum time one test can run for. */
  timeout: 30 * 1000,
  expect: {
    timeout: 5000,
  },
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,
  /* Opt out of parallel tests on CI. */
  workers: process.env.CI ? 1 : undefined,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: 'html',
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Maximum time each action such as `click()` can take. Defaults to 0 (no limit). */
    actionTimeout: 0,
    /* Base URL to use in actions like `await page.goto('/')`. */
    baseURL: e2eBaseUrl ?? (process.env.CI ? 'http://localhost:4173' : 'http://localhost:5173'),
    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',
    /* Only on CI systems run the tests headless */
    headless: !!process.env.CI,
  },

  /* Configure projects for major browsers */
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],

  /* Run your local dev server before starting the tests */
  webServer: e2eBaseUrl
    ? undefined
    : {
        command: process.env.CI ? 'npm run preview' : 'npm run dev',
        port: process.env.CI ? 4173 : 5173,
        reuseExistingServer: !process.env.CI,
      },
})