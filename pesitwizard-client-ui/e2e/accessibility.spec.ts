import { test, expect } from '@playwright/test'

/**
 * Basic accessibility checks using Playwright's built-in assertions.
 * For comprehensive a11y audits, consider integrating @axe-core/playwright.
 */
test.describe('Accessibility', () => {
  test.beforeEach(async ({ page }) => {
    const apiKey = process.env.TEST_API_KEY
    if (apiKey) {
      await page.goto('/')
      await page.evaluate((key) => {
        sessionStorage.setItem('pesitwizard-api-key', key)
      }, apiKey)
    }
  })

  test('login page should have proper lang attribute', async ({ page }) => {
    await page.goto('/login')
    const lang = await page.getAttribute('html', 'lang')
    expect(lang).toBe('en')
  })

  test('login page should have page title', async ({ page }) => {
    await page.goto('/login')
    await expect(page).toHaveTitle(/pesit wizard/i)
  })

  test('all icon-only buttons should have accessible names', async ({ page }) => {
    await page.goto('/')
    if (page.url().includes('/login')) {
      test.skip()
      return
    }

    // Find all buttons and verify they have accessible names
    const buttons = page.getByRole('button')
    const count = await buttons.count()
    for (let i = 0; i < count; i++) {
      const button = buttons.nth(i)
      if (await button.isVisible()) {
        const name = await button.getAttribute('aria-label')
        const text = await button.textContent()
        const hasAccessibleName = (name && name.trim().length > 0) || (text && text.trim().length > 0)
        expect(hasAccessibleName, `Button ${i} should have accessible name`).toBeTruthy()
      }
    }
  })

  test('dashboard should have skip navigation link', async ({ page }) => {
    await page.goto('/')
    if (page.url().includes('/login')) {
      test.skip()
      return
    }

    // Skip link should exist (visually hidden until focused)
    const skipLink = page.getByText(/skip to main content/i)
    await expect(skipLink).toBeAttached()
  })

  test('sidebar navigation should be keyboard navigable', async ({ page }) => {
    await page.goto('/')
    if (page.url().includes('/login')) {
      test.skip()
      return
    }

    // Tab should eventually reach sidebar links
    await page.keyboard.press('Tab')
    await page.keyboard.press('Tab')
    await page.keyboard.press('Tab')

    const activeElement = await page.evaluate(() => {
      return document.activeElement?.tagName
    })
    // Should be focused on an interactive element
    expect(['A', 'BUTTON', 'INPUT']).toContain(activeElement)
  })
})
