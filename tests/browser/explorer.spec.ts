import { test, expect, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { historyFixture } from './fixture';

async function section(page: Page, name: string) {
  await page.getByRole('button', { name, exact: true }).click();
}
async function importFixture(page: Page) {
  await page.goto('/');
  await page.locator('input[type=file][accept]').setInputFiles({ name: 'Streaming_History_Audio_test.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(historyFixture())) });
  await expect(page.locator('.stat').filter({ hasText: 'Unique titles' }).locator('.stat-value')).toHaveText('2');
}

test('file import, deduplication, filters, full CSV export, persistence, and error recovery', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push(e.message));
  await importFixture(page);
  await expect(page.locator('.dataset-meta')).toContainText('8 events');
  await expect(page.locator('svg.recharts-surface').first()).toBeVisible();
  await expect.poll(() => page.locator('.recharts-area-area, .recharts-line-curve, .recharts-rectangle, .recharts-dot').count()).toBeGreaterThan(0);
  await page.getByRole('searchbox', { name: 'Search', exact: true }).fill('Golden');
  await expect(page.locator('.stat').filter({ hasText: 'Unique titles' }).locator('.stat-value')).toHaveText('1');
  await section(page, 'History');
  await expect(page.locator('tbody tr')).toHaveCount(4);
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: /Export.*CSV/i }).click();
  const download = await downloadPromise;
  const csv = await readFile((await download.path())!, 'utf8');
  expect(csv.split('\r\n')).toHaveLength(5);
  expect(csv).toContain('Golden Evening'); expect(csv).not.toContain('Silver Morning'); expect(csv).not.toContain('192.0.2.23');
  await page.getByRole('button', { name: 'Clear all', exact: true }).click();
  await expect(page.locator('tbody tr')).toHaveCount(8);
  await section(page, 'Overview');
  if (await page.getByRole('button', { name: /More filters/ }).isVisible()) await page.getByRole('button', { name: /More filters/ }).click();
  await page.getByRole('combobox', { name: 'Device', exact: true }).selectOption('Android');
  await section(page, 'History');
  await expect(page.locator('tbody tr')).toHaveCount(4);
  await page.getByRole('button', { name: 'Clear all', exact: true }).click();
  await page.reload();
  await expect(page.locator('.dataset-meta')).toContainText('8 events');
  await section(page, 'Data & methods');
  await page.locator('input[type=file][accept]').setInputFiles({ name: 'broken.json', mimeType: 'application/json', buffer: Buffer.from('{broken') });
  await expect(page.getByRole('alert')).toContainText(/valid JSON/);
  await expect(page.locator('.dataset-meta')).toContainText('8 events');
  expect(errors).toEqual([]);
});

test('fictional demo renders every view with usable charts on desktop and mobile', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto('/');
  await page.getByRole('button', { name: /demo/i }).click();
  await expect(page.locator('.stat').first()).toBeVisible();
  await expect(page.locator('svg.recharts-surface').first()).toBeVisible();
  await expect.poll(() => page.locator('.recharts-area-area, .recharts-line-curve, .recharts-rectangle, .recharts-dot').count()).toBeGreaterThan(0);
  await page.screenshot({ path: 'test-results/demo-desktop.png', fullPage: true });
  for (const name of ['Favorites', 'Habits', 'Discoveries', 'History', 'Data & methods', 'Overview']) {
    await section(page, name);
    await expect(page.locator('main')).toBeVisible();
    expect(await page.locator('main').innerText()).not.toMatch(/undefined|NaN|Invalid Date/);
    if (name === 'Habits' || name === 'Discoveries') {
      await expect(page.locator('svg.recharts-surface').first()).toBeVisible();
      await expect.poll(() => page.locator('.recharts-area-area, .recharts-line-curve, .recharts-rectangle, .recharts-dot').count()).toBeGreaterThan(0);
    }
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator('.mobile-header')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: 'test-results/demo-mobile.png', fullPage: true });
  for (const name of ['Favorites', 'Habits', 'Discoveries', 'History', 'Data & methods']) {
    await section(page, name);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
    await page.screenshot({ path: `test-results/mobile-${name.replace(/\W/g, '-')}.png`, fullPage: true });
  }
  expect(errors).toEqual([]);
});

test('real import replaces a filtered demo and clearing removes the browser copy', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: /demo/i }).click();
  await expect(page.locator('.stat').first()).toBeVisible();
  await page.getByRole('searchbox', { name: 'Search', exact: true }).fill('Glass Atlas');
  await expect(page.getByRole('button', { name: /Remove filter Search/ })).toBeVisible();
  await section(page, 'Data & methods');
  await page.locator('input[type=file][accept]').setInputFiles({ name: 'Streaming_History_Audio_test.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(historyFixture())) });
  await expect(page.locator('.stat').filter({ hasText: 'Unique titles' }).locator('.stat-value')).toHaveText('2');
  await expect(page.getByRole('searchbox', { name: 'Search', exact: true })).toHaveValue('');
  await section(page, 'Data & methods');
  await page.getByRole('button', { name: 'Delete browser copy', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Delete browser data', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Open your Spotify Extended Streaming History', exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Open your Spotify Extended Streaming History', exact: true })).toBeVisible();
});
