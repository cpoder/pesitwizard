import { test, expect } from '@playwright/test'

test.describe('Partners View', () => {
  test.beforeEach(async ({ page }) => {
    const apiKey = process.env.TEST_API_KEY
    if (apiKey) {
      await page.goto('/')
      await page.evaluate((key) => {
        sessionStorage.setItem('pesitwizard-api-key', key)
      }, apiKey)
    }
    await page.goto('/partners')
    if (page.url().includes('/login')) {
      test.skip()
      return
    }
  })

  test('should display partners page with heading', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /partners/i }).first()).toBeVisible({
      timeout: 10000,
    })
  })

  test('should have an add partner button', async ({ page }) => {
    await expect(page.getByRole('button', { name: /add partner/i }).first()).toBeVisible({
      timeout: 10000,
    })
  })

  test('should create a partner with password', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /partners/i }).first()).toBeVisible({ timeout: 10000 })

    const suffix = Date.now()
    const partnerId = `PARTNER_${suffix}`

    // Click the Add Partner button in the header (not the empty state one)
    await page.getByRole('button', { name: 'Add partner' }).first().click()

    // Wait for dialog to open
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })

    // Fill form within the dialog
    await dialog.getByPlaceholder('MY_CLIENT_ID').fill(partnerId)
    await dialog.getByPlaceholder('Optional description').fill('Test partner for e2e')
    await dialog.getByPlaceholder('Partner password (optional)').fill('secret123')

    // Save
    await dialog.getByRole('button', { name: 'Save' }).click()

    // Modal should close and partner should appear in table
    await expect(dialog).not.toBeVisible({ timeout: 5000 })
    await expect(page.getByText(partnerId)).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('Test partner for e2e')).toBeVisible()
    await expect(page.getByText('Configured').first()).toBeVisible()
  })

  test('should create a partner without password', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /partners/i }).first()).toBeVisible({ timeout: 10000 })

    const suffix = Date.now()
    const partnerId = `NOPWD_${suffix}`

    await page.getByRole('button', { name: 'Add partner' }).first().click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })

    await dialog.getByPlaceholder('MY_CLIENT_ID').fill(partnerId)
    await dialog.getByPlaceholder('Optional description').fill('Partner without password')

    await dialog.getByRole('button', { name: 'Save' }).click()

    await expect(dialog).not.toBeVisible({ timeout: 5000 })
    await expect(page.getByText(partnerId)).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('None').first()).toBeVisible()
  })

  test('should delete a partner', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /partners/i }).first()).toBeVisible({ timeout: 10000 })

    const suffix = Date.now()
    const partnerId = `DEL_${suffix}`

    // First create a partner to delete
    await page.getByRole('button', { name: 'Add partner' }).first().click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await dialog.getByPlaceholder('MY_CLIENT_ID').fill(partnerId)
    await dialog.getByRole('button', { name: 'Save' }).click()
    await expect(dialog).not.toBeVisible({ timeout: 5000 })
    await expect(page.getByText(partnerId)).toBeVisible({ timeout: 5000 })

    // Click delete button for this partner
    const row = page.locator('tr', { hasText: partnerId })
    await row.getByRole('button', { name: 'Delete partner' }).click()

    // Confirm deletion in the confirm dialog (last dialog on page)
    const confirmDialog = page.getByRole('dialog').last()
    await expect(confirmDialog.getByText(/are you sure/i)).toBeVisible({ timeout: 5000 })
    await confirmDialog.getByRole('button', { name: /delete/i }).click()

    // Partner should be removed
    await expect(page.getByText(partnerId, { exact: true })).not.toBeVisible({ timeout: 5000 })
  })
})
