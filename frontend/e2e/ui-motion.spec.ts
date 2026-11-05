import { test, expect } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'

test.use({ reducedMotion: 'no-preference' })
async function login(page: import('@playwright/test').Page) {
  await page.goto('/login')
  const githubEntry = page.getByRole('button', { name: '继续使用 GitHub', exact: true })
  await expect(githubEntry).toBeVisible()
  await expect(githubEntry).not.toHaveClass(/ant-btn-loading/)
  const passwordEntry = page.getByRole('button', { name: '使用账号密码登录', exact: true })
  if (await passwordEntry.isVisible()) await passwordEntry.click()
  await page.getByLabel('用户名', { exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill(process.env.DEMO_ADMIN_PASSWORD!)
  await page.getByRole('button', { name: '登录工作空间', exact: true }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
}
const directory = resolve(import.meta.dirname, '../../docs/screenshots')
function prepareScreenshots() { mkdirSync(directory, { recursive: true }) }

test('navigation stays within its rail while collapsing, expanding and using a short viewport', async ({ page }, info) => {
  await login(page)
  prepareScreenshots()
  if (info.project.name === 'desktop') {
    await page.setViewportSize({ width: 1120, height: 720 })
    const rail = page.locator('.sidebar')
    for (let i = 0; i < 3; i++) {
      await page.getByRole('button', { name: '收起侧边栏', exact: true }).click()
      await expect(page.locator('.app-shell')).toHaveClass(/is-collapsed/)
      await expect.poll(() => rail.evaluate(e => Math.round(e.getBoundingClientRect().width))).toBe(80)
      expect(await rail.locator('nav').evaluate(e => e.scrollWidth <= e.clientWidth)).toBe(true)
      const bounds = await rail.boundingBox()
      for (const item of await rail.getByRole('menuitem').all()) {
        const box = await item.boundingBox()
        expect(box && bounds && box.x >= bounds.x && box.x + box.width <= bounds.x + bounds.width + 1).toBeTruthy()
      }
      if (i === 0) await page.screenshot({ path: resolve(directory, 'ui-polish-sidebar-collapsed.png'), animations: 'disabled' })
      await page.getByRole('button', { name: '展开侧边栏', exact: true }).click()
      await expect.poll(() => rail.evaluate(e => Math.round(e.getBoundingClientRect().width))).toBe(256)
      expect(await rail.locator('nav').evaluate(e => e.scrollWidth <= e.clientWidth)).toBe(true)
    }
    await page.screenshot({ path: resolve(directory, 'ui-polish-sidebar-expanded.png'), animations: 'disabled' })
  } else {
    await page.getByRole('button', { name: '打开导航', exact: true }).click()
    const drawer = page.getByRole('dialog', { name: '工作空间导航' })
    await expect(drawer.getByRole('menuitem', { name: '系统设置' })).toBeVisible()
    expect(await drawer.locator('nav').evaluate(e => e.scrollWidth <= e.clientWidth)).toBe(true)
    await page.screenshot({ path: resolve(directory, 'ui-polish-sidebar-mobile.png'), animations: 'disabled' })
    await page.keyboard.press('Escape')
    await expect(drawer).not.toBeVisible()
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('drawer enters from off-screen, slides out before unmounting and restores focus on repeated opens', async ({ page }) => {
  await login(page)
  await page.goto('/materials')
  const trigger = page.getByRole('button', { name: '新增材料', exact: true })
  for (let i = 0; i < 2; i++) {
    await expect(trigger).toBeEnabled()
    await trigger.focus()
    // Record the first painted position, not only the finished open state.
    // This catches a panel flashing at its destination before sliding in.
    const frames = await trigger.evaluate(button => new Promise<{ x: number; width: number; viewport: number }[]>(resolve => {
      const frames: { x: number; width: number; viewport: number }[] = []
      const start = performance.now()
      function sample() {
        const panel = document.querySelector('.czt-drawer.ant-drawer-right .ant-drawer-content-wrapper')
        if (panel) {
          const bounds = panel.getBoundingClientRect()
          frames.push({ x: bounds.x, width: bounds.width, viewport: innerWidth })
        }
        if (performance.now() - start < 650) requestAnimationFrame(sample)
        else resolve(frames)
      }
      ;(button as HTMLButtonElement).click()
      requestAnimationFrame(sample)
    }))
    expect(frames.length).toBeGreaterThan(5)
    expect(frames[0].x).toBeGreaterThanOrEqual(frames[0].viewport - 1)
    const middle = frames.filter(frame => frame.x < frame.viewport - 2 && frame.x > frame.viewport - frame.width + 2)
    expect(new Set(middle.map(frame => Math.round(frame.x))).size).toBeGreaterThanOrEqual(5)
    for (let frame = 1; frame < frames.length; frame++) expect(frames[frame].x).toBeLessThanOrEqual(frames[frame - 1].x + 1)
    const drawer = page.getByRole('dialog', { name: '新增材料', exact: true })
    await expect(drawer.getByLabel('材料名称', { exact: true })).toBeVisible()
    const panel = page.locator('.czt-drawer .ant-drawer-content-wrapper').last()
    await expect(panel).toHaveAttribute('data-motion-ready', 'true')
    await expect(panel).toHaveCSS('transform', 'none')
    const start = await panel.boundingBox()
    await drawer.getByRole('button', { name: /取\s*消/, exact: true }).click()
    // rc-motion prepares the leave classes over several frames. Require an
    // intermediate on-screen position, which rejects instant removal/jumps.
    await expect.poll(async () => {
      const closing = await panel.boundingBox()
      return Boolean(start && closing && closing.x > start.x + 2 && closing.x < start.x + start.width - 2)
    }, { timeout: 300, intervals: [16] }).toBe(true)
    await expect(drawer).not.toBeVisible()
    await expect(trigger).toBeFocused()
  }
})

test('reduced-motion login remains complete and drawers close without decorative animation', async ({ page }, info) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '欢迎回到材智通' })).toBeVisible()
  await expect(page.locator('.login-story canvas')).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  prepareScreenshots()
  await page.screenshot({ path: resolve(directory, 'ui-polish-login-reduced-' + info.project.name + '.png'), fullPage: true, animations: 'disabled' })
  await login(page)
  await page.goto('/materials')
  await page.getByRole('button', { name: '新增材料', exact: true }).click()
  await page.getByRole('dialog', { name: '新增材料', exact: true }).getByRole('button', { name: /取\s*消/, exact: true }).click()
  await expect(page.getByRole('dialog', { name: '新增材料', exact: true })).not.toBeVisible()
})
