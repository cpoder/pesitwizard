import { test, expect } from '@playwright/test'

test.describe('Login Flow', () => {
  test('should show login page when auth is required', async ({ page }) => {
    await page.goto('/')
    // If auth is required, should redirect to login
    const url = page.url()
    if (url.includes('/login')) {
      await expect(page.getByRole('heading', { name: /pesit wizard/i })).toBeVisible()
      await expect(page.getByLabel(/api key/i)).toBeVisible()
      await expect(page.getByRole('button', { name: /connect|login/i })).toBeVisible()
    } else {
      // Auth not required, should show dashboard
      await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()
    }
  })

  test('should reject invalid API key', async ({ page }) => {
    await page.goto('/login')
    // Only test if login page is shown
    const loginHeading = page.getByRole('heading', { name: /pesit wizard/i })
    if (await loginHeading.isVisible({ timeout: 2000 }).catch(() => false)) {
      await page.getByLabel(/api key/i).fill('invalid-key-12345')
      await page.getByRole('button', { name: /connect|login/i }).click()
      await expect(page.getByRole('alert')).toBeVisible({ timeout: 5000 })
    }
  })

  test('should navigate to dashboard with valid API key', async ({ page }) => {
    const apiKey = process.env.TEST_API_KEY
    if (!apiKey) {
      test.skip()
      return
    }

    await page.goto('/login')
    await page.getByLabel(/api key/i).fill(apiKey)
    await page.getByRole('button', { name: /connect|login/i }).click()
    await expect(page).toHaveURL('/', { timeout: 5000 })
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()
  })
})
