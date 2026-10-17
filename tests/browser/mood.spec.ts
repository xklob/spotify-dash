import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

test('offline metadata import, weighting, export, persistence and invalid-import recovery', async ({ page }) => {
  const errors: string[] = [], outbound: string[] = [];
  page.on('pageerror', e => errors.push(e.message)); page.on('request', r => { if (r.url().includes('reccobeats')) outbound.push(r.url()); });
  await page.goto('/');
  const rows = JSON.parse(await readFile('tests/fixtures/mood-history.json', 'utf8')).slice(0, 8);
  await page.locator('input[type=file][accept]').setInputFiles({ name: 'Streaming_History_Audio_mood.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(rows)) });
  await page.getByRole('button', { name: 'Mood & energy', exact: true }).click();
  await expect(page.getByText('No mood scores for this selection', { exact: true })).toBeVisible();
  await page.getByLabel('Import metadata JSON').setInputFiles('tests/fixtures/mood-metadata.json');
  const energy = page.locator('.stat').filter({ hasText: 'Musical energy' }).locator('.stat-value');
  await expect(energy).toHaveText('0.20');
  await expect(page.locator('.stat').filter({ hasText: 'Time with mood scores' }).locator('.stat-value')).toHaveText('29.4%');
  await page.getByRole('group', { name: 'Weight mood scores by' }).getByRole('button', { name: 'Events', exact: true }).click();
  await expect(energy).toHaveText('0.30');
  await page.getByRole('group', { name: 'Weight mood scores by' }).getByRole('button', { name: 'Distinct tracks', exact: true }).click();
  await expect(energy).toHaveText('0.40');
  await page.getByRole('button', { name: 'Manage metadata' }).click();
  const downloadPromise = page.waitForEvent('download'); await page.getByRole('button', { name: 'Export for Android' }).click();
  const download = await downloadPromise; const metadata = JSON.parse(await readFile((await download.path())!, 'utf8'));
  expect(metadata.format).toBe('spotify-dash-metadata'); expect(metadata.tracks).toHaveLength(5);
  expect(JSON.stringify(metadata)).not.toContain('ms_played');
  await page.reload(); await expect(energy).toHaveText('0.20');
  await page.getByLabel('Import metadata JSON').setInputFiles({ name: 'bad.json', mimeType: 'application/json', buffer: Buffer.from('{"format":"bad"}') });
  await expect(page.getByRole('alert')).toContainText('metadata JSON'); await expect(energy).toHaveText('0.20');
  await page.getByRole('searchbox', { name: 'Search', exact: true }).fill('Fictional B'); await expect(energy).toHaveText('1.00');
  await page.getByRole('button', { name: 'Clear all', exact: true }).click(); await expect(energy).toHaveText('0.20');
  expect(outbound).toEqual([]); expect(errors).toEqual([]);
});

test('mood demo renders every chart on desktop and phone, with tables and drill-downs', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message));
  await page.goto('/'); await page.getByRole('button', { name: /demo/i }).click();
  await page.getByRole('button', { name: 'Mood & energy', exact: true }).click();
  await expect(page.locator('.mood-density button')).toHaveCount(100);
  await expect(page.locator('.recharts-line-curve').first()).toBeVisible();
  await page.screenshot({ path: 'test-results/mood-desktop.png', fullPage: true });
  const titles = ['Mood over time', 'Mood distribution', 'Earlier and later', 'Mood by weekday and hour', 'Sound and texture', 'Energy and playback behavior', 'New and familiar tracks', 'Catalog coverage by year', 'Tracks behind the scores'];
  for (const title of titles) { await page.getByRole('heading', { name: title, exact: true }).scrollIntoViewIfNeeded(); await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible(); }
  await page.getByRole('button', { name: 'Show Mood over time as table' }).click();
  await expect(page.getByRole('table', { name: 'Mood trend values' })).toBeVisible();
  await page.getByRole('button', { name: 'Show Mood over time as chart' }).click();
  await page.getByLabel('Smooth 3 periods').check();
  await page.getByRole('group', { name: 'Mood period', exact: true }).getByRole('button', { name: 'Weekly', exact: true }).click();
  await expect(page.locator('.recharts-line-curve').first()).toBeVisible();
  await page.locator('.mood-hour-row button').first().click();
  await expect(page.locator('.mood-hour-scroll').locator('..').getByRole('status')).toContainText('Mon');
  await page.setViewportSize({ width: 390, height: 844 });
  for (const title of titles) {
    await page.getByRole('heading', { name: title, exact: true }).scrollIntoViewIfNeeded();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  }
  await page.screenshot({ path: 'test-results/mood-mobile.png', fullPage: true });
  const select = page.getByLabel('Explore a period’s tracks'); const first = await select.locator('option').nth(1).getAttribute('value');
  await select.selectOption(first!); await expect(page.getByRole('button', { name: 'Clear all', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Clear all', exact: true }).click();
  expect(errors).toEqual([]);
});
