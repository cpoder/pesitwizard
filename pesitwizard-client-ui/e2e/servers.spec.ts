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
    await expect(page.getByRole('heading', { name: /servers/i }).first()).toBeVisible({ timeout: 10000 })

    // Click the Add Server button (header button with aria-label)
    await page.getByRole('button', { name: 'Add server' }).first().click()

    // Dialog should open with form fields
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await expect(dialog.getByPlaceholder('My Server')).toBeVisible()
    await expect(dialog.getByPlaceholder('localhost')).toBeVisible()
  })

  test('should create a server', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /servers/i }).first()).toBeVisible({ timeout: 10000 })

    const suffix = Date.now()
    const serverName = `E2E Server ${suffix}`

    // Click Add Server (header button)
    await page.getByRole('button', { name: 'Add server' }).first().click()

    // Wait for dialog
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })

    // Fill form within dialog
    await dialog.getByPlaceholder('My Server').fill(serverName)
    await dialog.getByPlaceholder('localhost').fill('192.168.1.100')
    await dialog.locator('input[type="number"]').fill('6502')
    await dialog.getByPlaceholder('PESIT-SERVER').fill(`SRV${suffix}`)
    await dialog.getByPlaceholder('Optional description').fill('E2E test server')

    // Save
    await dialog.getByRole('button', { name: 'Save' }).click()

    // Modal should close and server should appear
    await expect(dialog).not.toBeVisible({ timeout: 5000 })
    await expect(page.getByRole('heading', { name: serverName })).toBeVisible({ timeout: 5000 })
  })

  test('should delete a server', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /servers/i }).first()).toBeVisible({ timeout: 10000 })

    const suffix = Date.now()
    const serverName = `DelSrv ${suffix}`

    // First create a server to delete
    await page.getByRole('button', { name: 'Add server' }).first().click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await dialog.getByPlaceholder('My Server').fill(serverName)
    await dialog.getByPlaceholder('localhost').fill('10.0.0.1')
    await dialog.locator('input[type="number"]').fill('6502')
    await dialog.getByPlaceholder('PESIT-SERVER').fill(`DEL${suffix}`)
    await dialog.getByRole('button', { name: 'Save' }).click()
    await expect(dialog).not.toBeVisible({ timeout: 5000 })
    await expect(page.getByRole('heading', { name: serverName })).toBeVisible({ timeout: 5000 })

    // Set up handler for native confirm() dialog before clicking delete
    page.on('dialog', async (dialog) => {
      await dialog.accept()
    })

    // Find the specific server card (direct child of grid) and click its delete button
    const serverCard = page.locator('.card').filter({ hasText: serverName })
    await serverCard.getByRole('button', { name: 'Delete server' }).click()

    // Server should be removed
    await expect(page.getByRole('heading', { name: serverName })).not.toBeVisible({ timeout: 5000 })
  })
})
