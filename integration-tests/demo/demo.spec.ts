import { test, expect, Page } from '@playwright/test';

/**
 * PeSIT Wizard - Complete Demo Scenario
 *
 * This script demonstrates a complete workflow:
 * 1. Add a PeSIT server (localhost:30500)
 * 2. Execute a file transfer (DEMO -> DEMOFILE)
 * 3. Add the transfer to favorites (from history)
 * 4. Create a business calendar
 * 5. Schedule the favorite transfer
 *
 * Prerequisites (setup by run-demo.sh):
 * - Partner DEMO with password demo123 configured on server (8 char max)
 * - Virtual file DEMOFILE configured on server
 * - Test file /data/send/demo-report.txt created on server
 */

const CLIENT_UI_URL = process.env.CLIENT_UI_URL || 'http://localhost:3001';

// Demo timing - longer pauses for video clarity
const PAUSE = {
  short: 1500,
  medium: 2500,
  long: 4000,
  veryLong: 6000,
};

async function pause(page: Page, ms: number = PAUSE.medium) {
  await page.waitForTimeout(ms);
}

async function typeSlowly(page: Page, locator: any, text: string, delay = 80) {
  await locator.click();
  await page.waitForTimeout(200);
  for (const char of text) {
    await locator.press(char);
    await page.waitForTimeout(delay);
  }
}

test.describe('PeSIT Wizard Complete Demo', () => {

  test('Full Workflow: Server, Transfer, Favorite, Calendar, Schedule', async ({ page }) => {
    test.setTimeout(600000); // 10 minutes max

    console.log('\n' + '='.repeat(50));
    console.log('   PeSIT Wizard - Complete Demo');
    console.log('='.repeat(50) + '\n');

    // ================================================
    // INTRO: Show Dashboard
    // ================================================
    console.log('INTRO: Dashboard');

    await page.goto(CLIENT_UI_URL);
    await page.waitForLoadState('networkidle');
    await pause(page, PAUSE.veryLong);

    // ================================================
    // STEP 1: Add a PeSIT Server
    // ================================================
    console.log('\nSTEP 1: Adding PeSIT Server (localhost:30500)');

    await page.click('a[href="/servers"]');
    await page.waitForLoadState('networkidle');
    await pause(page, PAUSE.long);

    // Check if we need to add a server
    const noServers = page.locator('text=No servers configured');
    const serverExists = page.locator('text=Demo Server');

    const needsNewServer = await noServers.isVisible({ timeout: 2000 }).catch(() => false);
    const hasServer = await serverExists.isVisible({ timeout: 2000 }).catch(() => false);

    if (needsNewServer || !hasServer) {
      // Click Add Server
      await page.locator('button:has-text("Add Server")').first().click();
      await pause(page, PAUSE.medium);

      // Fill the form with slow typing for demo effect
      // Name
      const nameInput = page.locator('input[placeholder="My Server"]');
      await typeSlowly(page, nameInput, 'Demo Server');
      await pause(page, PAUSE.short);

      // Host - use internal k8s service name for cluster connectivity
      const hostInput = page.locator('input[placeholder="localhost"]');
      await hostInput.clear();
      await hostInput.fill('pesitwizard-server-vectis');
      await pause(page, PAUSE.short);

      // Port - 5000 (internal service port)
      const portInput = page.locator('input[type="number"]').first();
      await portInput.clear();
      await portInput.fill('5000');
      await pause(page, PAUSE.short);

      // Server ID - must match a server running on the PeSIT server
      const serverIdInput = page.locator('input[placeholder="PESIT-SERVER"]');
      await typeSlowly(page, serverIdInput, 'SRV01');
      await pause(page, PAUSE.medium);

      // Show completed form
      await pause(page, PAUSE.long);

      // Save
      await page.locator('button:has-text("Save")').click();
      await pause(page, PAUSE.long);

      // Close modal if one is open (press Escape or click outside)
      await page.keyboard.press('Escape');
      await pause(page, PAUSE.short);

      // Also try clicking a Cancel button if visible
      const cancelBtn = page.locator('button:has-text("Cancel")');
      if (await cancelBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
        await cancelBtn.click();
        await pause(page, PAUSE.short);
      }

      console.log('   ✓ Server created: localhost:30500');
    } else {
      console.log('   Server already exists, skipping creation');
      await pause(page, PAUSE.medium);
    }

    // Ensure any modal overlay is closed before proceeding
    const serverOverlay = page.locator('.fixed.inset-0.bg-black\\/50');
    if (await serverOverlay.isVisible({ timeout: 1000 }).catch(() => false)) {
      await page.keyboard.press('Escape');
      await pause(page, PAUSE.short);
    }

    // ================================================
    // STEP 2: Execute a File Transfer
    // ================================================
    console.log('\nSTEP 2: Executing File Transfer');

    await page.click('a[href="/transfer"]');
    await page.waitForLoadState('networkidle');
    await pause(page, PAUSE.long);

    // Check if servers are available
    const noServersTransfer = page.locator('text=No servers configured');
    if (await noServersTransfer.isVisible({ timeout: 2000 }).catch(() => false)) {
      console.log('   WARNING: No servers available for transfer');
      await pause(page, PAUSE.medium);
    } else {
      // Select direction SEND (default, but click to confirm)
      const sendRadio = page.locator('input[value="SEND"], label:has-text("Send")');
      if (await sendRadio.first().isVisible().catch(() => false)) {
        await sendRadio.first().click();
        await pause(page, PAUSE.short);
      }

      // Select server (Demo Server) - look for dropdown or select
      const serverSelect = page.locator('select').first();
      if (await serverSelect.isVisible().catch(() => false)) {
        // Get all options and find one containing "Demo"
        const options = serverSelect.locator('option');
        const count = await options.count();
        for (let i = 0; i < count; i++) {
          const text = await options.nth(i).textContent();
          if (text && text.includes('Demo')) {
            await serverSelect.selectOption({ index: i });
            break;
          }
        }
        await pause(page, PAUSE.short);
      }

      // Fill Partner ID: DEMO (max 8 chars)
      const partnerInput = page.locator('input[placeholder*="CLIENT"], input[placeholder*="Partner"]').first();
      if (await partnerInput.isVisible().catch(() => false)) {
        await partnerInput.clear();
        await typeSlowly(page, partnerInput, 'DEMO');
        await pause(page, PAUSE.short);
      }

      // Fill Password: demo123
      const passwordInput = page.locator('input[type="password"], input[placeholder*="assword"]').first();
      if (await passwordInput.isVisible().catch(() => false)) {
        await passwordInput.fill('demo123');
        await pause(page, PAUSE.short);
      }

      // Fill Local Filename: /tmp/demo-report.txt (file in client pod)
      const filenameInput = page.locator('input[placeholder*="path"], input[placeholder*="file"]').first();
      if (await filenameInput.isVisible().catch(() => false)) {
        await filenameInput.clear();
        await filenameInput.fill('/tmp/demo-report.txt');
        await pause(page, PAUSE.short);
      }

      // Fill Virtual File ID (Remote): DEMOFILE
      const remoteInput = page.locator('input[placeholder*="Virtual"], input[placeholder*="DATA"], input[placeholder*="Remote"]').first();
      if (await remoteInput.isVisible().catch(() => false)) {
        await remoteInput.clear();
        await typeSlowly(page, remoteInput, 'DEMOFILE');
        await pause(page, PAUSE.short);
      }

      // Show completed form
      await pause(page, PAUSE.long);

      // Click Send File button
      const sendBtn = page.locator('button:has-text("Send File")');
      if (await sendBtn.isVisible().catch(() => false)) {
        console.log('   Starting transfer...');
        await sendBtn.click();

        // Wait for transfer to complete (max 30 seconds)
        await pause(page, PAUSE.long);

        // Monitor transfer status
        for (let i = 0; i < 10; i++) {
          const completed = page.locator('text=COMPLETED');
          const failed = page.locator('text=FAILED');

          if (await completed.isVisible({ timeout: 1000 }).catch(() => false)) {
            console.log('   ✓ Transfer completed successfully!');
            break;
          }
          if (await failed.isVisible({ timeout: 1000 }).catch(() => false)) {
            console.log('   ✗ Transfer failed');
            break;
          }
          await pause(page, PAUSE.medium);
        }

        await pause(page, PAUSE.long);
      }
    }

    // ================================================
    // STEP 3: Add Transfer to Favorites (from History)
    // ================================================
    console.log('\nSTEP 3: Adding Transfer to Favorites');

    await page.click('a[href="/history"]');
    await page.waitForLoadState('networkidle');
    await pause(page, PAUSE.long);

    // Check if there's history
    const noHistory = page.locator('text=No transfers yet');
    if (await noHistory.isVisible({ timeout: 2000 }).catch(() => false)) {
      console.log('   No transfer history available');
    } else {
      // Find the first transfer row and click the star button
      const firstRow = page.locator('tbody tr').first();
      if (await firstRow.isVisible({ timeout: 2000 }).catch(() => false)) {
        // Click on the row to show it
        await pause(page, PAUSE.medium);

        // Click the star button to add to favorites
        const starBtn = firstRow.locator('button[title="Add to favorites"]');
        if (await starBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
          await starBtn.click();
          await pause(page, PAUSE.medium);

          // Modal should appear - the input should have a default name
          // Just click Add/Save to confirm
          const addBtn = page.locator('button:has-text("Add to Favorites"), button:has-text("Add"), button:has-text("Save")').last();
          if (await addBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
            await addBtn.click();
            console.log('   ✓ Added to favorites!');
            await pause(page, PAUSE.long);
          }
        } else {
          console.log('   Star button not found');
        }
      } else {
        console.log('   No transfers in history');
      }
    }
    await pause(page, PAUSE.medium);

    // ================================================
    // STEP 4: Create a Business Calendar
    // ================================================
    console.log('\nSTEP 4: Creating Business Calendar');

    await page.click('a[href="/calendars"]');
    await page.waitForLoadState('networkidle');
    await pause(page, PAUSE.long);

    // Click New Calendar
    const newCalendarBtn = page.locator('button:has-text("New Calendar")');
    if (await newCalendarBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await newCalendarBtn.click();
      await pause(page, PAUSE.medium);

      // Fill calendar name
      const calendarNameInput = page.locator('input[type="text"]').first();
      if (await calendarNameInput.isVisible().catch(() => false)) {
        await typeSlowly(page, calendarNameInput, 'Production Calendar');
        await pause(page, PAUSE.short);
      }

      // Description (if textarea exists)
      const descInput = page.locator('textarea, input[placeholder*="description"]').first();
      if (await descInput.isVisible().catch(() => false)) {
        await descInput.fill('Business calendar for production file transfers');
        await pause(page, PAUSE.short);
      }

      // Timezone select (default Europe/Paris should be fine)
      const tzSelect = page.locator('select').first();
      if (await tzSelect.isVisible().catch(() => false)) {
        await tzSelect.selectOption('Europe/Paris');
        await pause(page, PAUSE.short);
      }

      // Working days checkboxes are pre-selected (Mon-Fri)
      // Show the form
      await pause(page, PAUSE.long);

      // Save calendar - look for Create Calendar button specifically
      const createCalBtn = page.locator('button:has-text("Create Calendar")');
      if (await createCalBtn.isVisible().catch(() => false)) {
        await createCalBtn.click();
        console.log('   ✓ Calendar created!');
        await pause(page, PAUSE.medium);

        // Close the form if there's a Cancel or X button, or just navigate away
        const cancelBtn = page.locator('button:has-text("Cancel")');
        if (await cancelBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
          await cancelBtn.click();
          await pause(page, PAUSE.short);
        }
        await pause(page, PAUSE.medium);
      }
    } else {
      console.log('   Calendar creation button not found');
    }

    // ================================================
    // STEP 5: Schedule the Favorite Transfer
    // ================================================
    console.log('\nSTEP 5: Scheduling Favorite Transfer');

    // Close any modal overlay that might be blocking navigation
    const overlay = page.locator('.fixed.inset-0.bg-black\\/50');
    if (await overlay.isVisible({ timeout: 1000 }).catch(() => false)) {
      // Press Escape or click outside to close
      await page.keyboard.press('Escape');
      await pause(page, PAUSE.short);
    }

    await page.click('a[href="/favorites"]');
    await page.waitForLoadState('networkidle');
    await pause(page, PAUSE.long);

    // Check if there are favorites
    const noFavorites = page.locator('text=No favorites yet');
    if (await noFavorites.isVisible({ timeout: 2000 }).catch(() => false)) {
      console.log('   No favorites to schedule');
    } else {
      // Find and click the calendar/schedule button on a favorite
      const scheduleBtn = page.locator('button[title="Schedule"]').first();
      if (await scheduleBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        await scheduleBtn.click();
        await pause(page, PAUSE.medium);

        // Modal should open - configure schedule
        // Select schedule type: DAILY
        const scheduleTypeSelect = page.locator('select').first();
        if (await scheduleTypeSelect.isVisible().catch(() => false)) {
          await scheduleTypeSelect.selectOption('DAILY');
          await pause(page, PAUSE.short);

          // Set time (default 09:00 is fine, or set to 09:30)
          const timeInput = page.locator('input[type="time"]');
          if (await timeInput.isVisible().catch(() => false)) {
            await timeInput.fill('09:30');
            await pause(page, PAUSE.short);
          }

          // Select calendar if available
          const calendarSelect = page.locator('select').nth(1);
          if (await calendarSelect.isVisible().catch(() => false)) {
            const optionCount = await calendarSelect.locator('option').count();
            if (optionCount > 1) {
              // Select the calendar we just created
              await calendarSelect.selectOption({ index: 1 });
              await pause(page, PAUSE.short);
            }
          }

          // Show completed schedule config
          await pause(page, PAUSE.long);

          // Create schedule
          const createBtn = page.locator('button:has-text("Create Schedule")');
          if (await createBtn.isVisible().catch(() => false)) {
            await createBtn.click();
            console.log('   ✓ Schedule created!');
            await pause(page, PAUSE.long);
          }
        }
      } else {
        console.log('   No favorites found to schedule');
      }
    }

    // ================================================
    // STEP 6: View Scheduled Transfers
    // ================================================
    console.log('\nSTEP 6: Viewing Scheduled Transfers');

    await page.click('a[href="/schedules"]');
    await page.waitForLoadState('networkidle');
    await pause(page, PAUSE.veryLong);

    // ================================================
    // FINALE: Return to Dashboard
    // ================================================
    console.log('\nFINALE: Return to Dashboard');

    await page.click('a[href="/"]');
    await page.waitForLoadState('networkidle');
    await pause(page, PAUSE.veryLong);

    console.log('\n' + '='.repeat(50));
    console.log('   Demo Complete!');
    console.log('='.repeat(50) + '\n');
  });
});
