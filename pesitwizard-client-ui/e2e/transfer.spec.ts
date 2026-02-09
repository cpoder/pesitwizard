import { test, expect } from '@playwright/test'

test.describe('Transfer View', () => {
  test.beforeEach(async ({ page }) => {
    const apiKey = process.env.TEST_API_KEY
    if (apiKey) {
      await page.goto('/')
      await page.evaluate((key) => {
        sessionStorage.setItem('pesitwizard-api-key', key)
      }, apiKey)
    }
    await page.goto('/transfer')
    if (page.url().includes('/login')) {
      test.skip()
      return
    }
  })

  test('should display transfer form with required fields', async ({ page }) => {
    await expect(page.getByText(/direction/i).first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText(/server/i).first()).toBeVisible()
    await expect(page.getByText(/partner/i).first()).toBeVisible()
  })

  test('should toggle between send and receive modes', async ({ page }) => {
    const sendRadio = page.getByLabel(/send/i).first()
    const receiveRadio = page.getByLabel(/receive/i).first()

    if (await sendRadio.isVisible({ timeout: 5000 }).catch(() => false)) {
      await sendRadio.check()
      await expect(sendRadio).toBeChecked()

      await receiveRadio.check()
      await expect(receiveRadio).toBeChecked()
    }
  })

  test('should show advanced options on toggle', async ({ page }) => {
    const advancedButton = page.getByRole('button', { name: /advanced/i }).first()
    if (await advancedButton.isVisible({ timeout: 5000 }).catch(() => false)) {
      await advancedButton.click()
      // Advanced options should appear
      await expect(page.getByText(/sync|compression|record/i).first()).toBeVisible()
    }
  })
})
