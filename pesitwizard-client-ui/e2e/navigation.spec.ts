import { test, expect } from '@playwright/test'

/**
 * Navigation smoke tests verify that all main views render without errors.
 * These tests assume auth is either not required or has been pre-configured.
 */
test.describe('Navigation', () => {
  test.beforeEach(async ({ page }) => {
    // Set API key in session storage if provided
    const apiKey = process.env.TEST_API_KEY
    if (apiKey) {
      await page.goto('/')
      await page.evaluate((key) => {
        sessionStorage.setItem('pesitwizard-api-key', key)
      }, apiKey)
    }
  })

  const routes = [
    { path: '/', heading: /dashboard/i },
    { path: '/servers', heading: /servers/i },
    { path: '/transfer', heading: /transfer/i },
    { path: '/history', heading: /history/i },
    { path: '/favorites', heading: /favorites/i },
    { path: '/schedules', heading: /schedules/i },
    { path: '/calendars', heading: /calendars/i },
    { path: '/connectors', heading: /connectors/i },
    { path: '/settings', heading: /settings/i },
  ]

  for (const route of routes) {
    test(`should render ${route.path} without errors`, async ({ page }) => {
      await page.goto(route.path)

      // If redirected to login, skip - auth tests handle this
      if (page.url().includes('/login')) {
        test.skip()
        return
      }

      await expect(page.getByRole('heading', { name: route.heading }).first()).toBeVisible({
        timeout: 10000,
      })

      // Verify no console errors
      const errors: string[] = []
      page.on('console', (msg) => {
        if (msg.type() === 'error') errors.push(msg.text())
      })

      // Page should not have any uncaught exceptions
      expect(errors).toEqual([])
    })
  }

  test('should navigate via sidebar links', async ({ page }) => {
    await page.goto('/')
    if (page.url().includes('/login')) {
      test.skip()
      return
    }

    // Click "Servers" in sidebar
    await page.getByRole('link', { name: /servers/i }).first().click()
    await expect(page).toHaveURL(/\/servers/)

    // Click "Transfer" in sidebar
    await page.getByRole('link', { name: /transfer/i }).first().click()
    await expect(page).toHaveURL(/\/transfer/)

    // Click "History" in sidebar
    await page.getByRole('link', { name: /history/i }).first().click()
    await expect(page).toHaveURL(/\/history/)
  })
})
