import { test, expect } from '@playwright/test'
import { resolve } from 'node:path'

test('login effects preserve the real authentication entry on desktop and mobile', async ({ page }, info) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.goto('/login')
  const button = page.getByRole('button', { name: '继续使用 GitHub', exact: true })
  await expect(button).toBeEnabled()
  await expect(page.getByRole('heading', { name: '欢迎回到材智通' })).toBeVisible()
  if (info.project.name === 'desktop') {
    await expect(page.locator('.particle-text[data-ready]')).toBeVisible()
    await expect(page.locator('.login-orbit-ring .circular-text')).toBeVisible()
    await expect(page.locator('.material-veil-fallback')).toBeVisible()
    // Sample the settled title, not its deliberate particle gathering phase.
    await page.waitForTimeout(2300)
  } else {
    await expect(page.locator('.login-story')).not.toBeVisible()
    await expect(page.locator('canvas')).toHaveCount(0)
    await expect(page.locator('.login-orbit-ring svg')).toBeVisible()
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: resolve(import.meta.dirname, '../../docs/screenshots/ui-polish-login-' + info.project.name + '.png'), fullPage: true })
  await page.getByRole('button', { name: '使用账号密码登录', exact: true }).click()
  await expect(page.getByLabel('用户名', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '登录工作空间', exact: true })).toBeVisible()
  if (info.project.name === 'desktop') {
    const canvas = page.locator('.dither-veil canvas')
    if (await canvas.count()) {
      await canvas.evaluate(element => {
        const gl = (element as HTMLCanvasElement).getContext('webgl2')
        gl?.getExtension('WEBGL_lose_context')?.loseContext()
      })
      await expect(canvas).toHaveCSS('opacity', '0')
      await canvas.hover()
      await expect(canvas).toHaveCSS('opacity', '0')
      await expect(page.locator('.material-veil-fallback')).toBeVisible()
    }
  }
  expect(errors).toEqual([])
})

test('unavailable WebGL keeps the local brand mark and readable title', async ({ page }, info) => {
  await page.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext
    HTMLCanvasElement.prototype.getContext = function (type: string, ...args: unknown[]) {
      if (type.startsWith('webgl')) return null
      return Reflect.apply(original, this, [type, ...args])
    } as typeof original
  })
  await page.goto('/login')
  await expect(page.getByRole('button', { name: '继续使用 GitHub', exact: true })).toBeEnabled()
  if (info.project.name === 'desktop') {
    await expect(page.locator('.material-veil-fallback')).toBeVisible()
    await expect(page.locator('.particle-text[data-ready]')).toBeVisible()
    await expect(page.locator('.dither-veil canvas')).toHaveCount(0)
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
