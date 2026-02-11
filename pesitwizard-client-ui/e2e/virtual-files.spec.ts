import { test, expect } from '@playwright/test'

test.describe('Virtual Files View', () => {
  test.beforeEach(async ({ page }) => {
    const apiKey = process.env.TEST_API_KEY
    if (apiKey) {
      await page.goto('/')
      await page.evaluate((key) => {
        sessionStorage.setItem('pesitwizard-api-key', key)
      }, apiKey)
    }
    await page.goto('/virtual-files')
    if (page.url().includes('/login')) {
      test.skip()
      return
    }
  })

  test('should display virtual files page with heading', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /virtual files/i }).first()).toBeVisible({
      timeout: 10000,
    })
  })

  test('should have an add virtual file button', async ({ page }) => {
    await expect(page.getByRole('button', { name: /add virtual file/i }).first()).toBeVisible({
      timeout: 10000,
    })
  })

  test('should create a virtual file with direction BOTH', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /virtual files/i }).first()).toBeVisible({ timeout: 10000 })

    const suffix = Date.now()
    const vfName = `VF_BOTH_${suffix}`

    // Click Add Virtual File (header button)
    await page.getByRole('button', { name: 'Add virtual file' }).first().click()

    // Wait for dialog
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })

    // Fill form within dialog
    await dialog.getByPlaceholder('DATA_FILE').fill(vfName)
    await dialog.getByPlaceholder('Optional description').fill('Production data file')
    // Direction defaults to BOTH, leave as-is

    // Save
    await dialog.getByRole('button', { name: 'Save' }).click()

    // Modal should close and virtual file should appear in table
    await expect(dialog).not.toBeVisible({ timeout: 5000 })
    await expect(page.getByText(vfName)).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('Production data file')).toBeVisible()
    await expect(page.getByText('BOTH').first()).toBeVisible()
  })

  test('should create a virtual file with direction SEND', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /virtual files/i }).first()).toBeVisible({ timeout: 10000 })

    const suffix = Date.now()
    const vfName = `VF_SEND_${suffix}`

    await page.getByRole('button', { name: 'Add virtual file' }).first().click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })

    await dialog.getByPlaceholder('DATA_FILE').fill(vfName)
    await dialog.getByPlaceholder('Optional description').fill('Send-only file')

    // Change direction to SEND (first select in the dialog)
    await dialog.locator('select').first().selectOption('SEND')

    // Save
    await dialog.getByRole('button', { name: 'Save' }).click()

    await expect(dialog).not.toBeVisible({ timeout: 5000 })
    await expect(page.getByText(vfName)).toBeVisible({ timeout: 5000 })
  })

  test('should create a virtual file with record length', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /virtual files/i }).first()).toBeVisible({ timeout: 10000 })

    const suffix = Date.now()
    const vfName = `VF_REC_${suffix}`

    await page.getByRole('button', { name: 'Add virtual file' }).first().click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })

    await dialog.getByPlaceholder('DATA_FILE').fill(vfName)
    await dialog.getByPlaceholder('Optional description').fill('Fixed record length file')

    // Select record length 1024 (second select in the dialog)
    await dialog.locator('select').nth(1).selectOption('1024')

    await dialog.getByRole('button', { name: 'Save' }).click()

    await expect(dialog).not.toBeVisible({ timeout: 5000 })
    await expect(page.getByText(vfName)).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('1024 B')).toBeVisible()
  })

  test('should delete a virtual file', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /virtual files/i }).first()).toBeVisible({ timeout: 10000 })

    const suffix = Date.now()
    const vfName = `VF_DEL_${suffix}`

    // First create a virtual file to delete
    await page.getByRole('button', { name: 'Add virtual file' }).first().click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await dialog.getByPlaceholder('DATA_FILE').fill(vfName)
    await dialog.getByRole('button', { name: 'Save' }).click()
    await expect(dialog).not.toBeVisible({ timeout: 5000 })
    await expect(page.getByText(vfName)).toBeVisible({ timeout: 5000 })

    // Click delete button for this virtual file
    const row = page.locator('tr', { hasText: vfName })
    await row.getByRole('button', { name: 'Delete virtual file' }).click()

    // Confirm deletion in the confirm dialog (last dialog on page)
    const confirmDialog = page.getByRole('dialog').last()
    await expect(confirmDialog.getByText(/are you sure/i)).toBeVisible({ timeout: 5000 })
    await confirmDialog.getByRole('button', { name: /delete/i }).click()

    // Virtual file should be removed
    await expect(page.getByText(vfName, { exact: true })).not.toBeVisible({ timeout: 5000 })
  })
})
