import { test, expect } from '@playwright/test'

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    const apiKey = process.env.TEST_API_KEY
    if (apiKey) {
      await page.goto('/')
      await page.evaluate((key) => {
        sessionStorage.setItem('pesitwizard-api-key', key)
      }, apiKey)
    }
    await page.goto('/')
    if (page.url().includes('/login')) {
      test.skip()
      return
    }
  })

  test('should display stats cards', async ({ page }) => {
    await expect(page.getByText(/total transfers/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText(/completed/i).first()).toBeVisible()
  })

  test('should have a refresh button', async ({ page }) => {
    const refreshButton = page.getByRole('button', { name: /refresh/i }).first()
    await expect(refreshButton).toBeVisible()
  })

  test('should display configured servers section', async ({ page }) => {
    await expect(page.getByText(/configured servers/i).first()).toBeVisible({ timeout: 10000 })
  })
})
