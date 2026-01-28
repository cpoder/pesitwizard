import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  timeout: 120000,
  expect: {
    timeout: 10000
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.CLIENT_UI_URL || 'http://localhost:5173',
    trace: 'off',
    video: {
      mode: 'on',
      size: { width: 1280, height: 720 }
    },
    viewport: { width: 1280, height: 720 },
    actionTimeout: 15000,
    navigationTimeout: 30000,
  },
  outputDir: './recordings',
  projects: [
    {
      name: 'demo',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
