import { test, expect } from '@playwright/test'

test.describe('Servers View', () => {
  test.beforeEach(async ({ page }) => {
    const apiKey = process.env.TEST_API_KEY
    if (apiKey) {
      await page.goto('/')
      await page.evaluate((key) => {
        sessionStorage.setItem('pesitwizard-api-key', key)
      }, apiKey)
    }
    await page.goto('/servers')
    if (page.url().includes('/login')) {
      test.skip()
      return
    }
  })

  test('should display servers page with heading', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /servers/i }).first()).toBeVisible({
      timeout: 10000,
    })
  })

  test('should have an add server button', async ({ page }) => {
    const addButton = page.getByRole('button', { name: /add server/i }).first()
    await expect(addButton).toBeVisible({ timeout: 10000 })
  })

  test('should open add server form', async ({ page }) => {
    const addButton = page.getByRole('button', { name: /add server/i }).first()
    if (await addButton.isVisible({ timeout: 5000 }).catch(() => false)) {
      await addButton.click()
      // Form fields should appear
      await expect(page.getByLabel(/name/i).first()).toBeVisible({ timeout: 5000 })
      await expect(page.getByLabel(/host/i).first()).toBeVisible()
      await expect(page.getByLabel(/port/i).first()).toBeVisible()
    }
  })
})
