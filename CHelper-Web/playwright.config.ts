import { defineConfig } from '@playwright/test'

const browserExecutable = process.env.CHELPER_BROWSER_EXECUTABLE
const testPort = 43617
const baseURL = `http://127.0.0.1:${testPort}`

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  use: {
    baseURL,
    browserName: 'chromium',
    headless: true,
    launchOptions: browserExecutable ? { executablePath: browserExecutable } : undefined,
  },
  webServer: {
    command: `pnpm exec vite --host 127.0.0.1 --port ${testPort} --strictPort`,
    url: baseURL,
    timeout: 120_000,
  },
})
