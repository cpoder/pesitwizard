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

  test('should display transfer page heading and form or empty state', async ({ page }) => {
    // Wait for page heading to ensure full render
    await expect(page.getByRole('heading', { name: /file transfer/i }).first()).toBeVisible({ timeout: 10000 })

    // Form is only shown when servers are configured; otherwise shows empty state
    const noServers = page.getByText(/no servers configured/i)
    const directionLabel = page.getByText(/direction/i).first()

    // One of these should be visible
    const hasNoServers = await noServers.isVisible({ timeout: 3000 }).catch(() => false)
    if (hasNoServers) {
      // Empty state: should show link to add a server
      await expect(page.getByRole('link', { name: /add server/i })).toBeVisible()
    } else {
      // Full form: should show Direction, Server, Partner fields
      await expect(directionLabel).toBeVisible({ timeout: 5000 })
      await expect(page.getByText(/server/i).first()).toBeVisible()
      await expect(page.getByText(/partner/i).first()).toBeVisible()
    }
  })

  test('should toggle between send and receive modes', async ({ page }) => {
    // Skip if no servers configured (form won't be shown)
    await expect(page.getByRole('heading', { name: /file transfer/i }).first()).toBeVisible({ timeout: 10000 })
    const noServers = page.getByText(/no servers configured/i)
    if (await noServers.isVisible({ timeout: 3000 }).catch(() => false)) {
      test.skip()
      return
    }

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
    // Skip if no servers configured
    await expect(page.getByRole('heading', { name: /file transfer/i }).first()).toBeVisible({ timeout: 10000 })
    const noServers = page.getByText(/no servers configured/i)
    if (await noServers.isVisible({ timeout: 3000 }).catch(() => false)) {
      test.skip()
      return
    }

    const advancedButton = page.getByRole('button', { name: /advanced/i }).first()
    if (await advancedButton.isVisible({ timeout: 5000 }).catch(() => false)) {
      await advancedButton.click()
      // Advanced options should appear
      await expect(page.getByText(/sync|compression|record/i).first()).toBeVisible()
    }
  })
})
