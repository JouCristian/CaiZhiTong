import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'

async function login(page: Page, username = 'admin') {
  const key = username === 'admin' ? 'DEMO_ADMIN_PASSWORD' : username === 'lab' ? 'DEMO_LAB_PASSWORD' : 'DEMO_STUDENT_PASSWORD'
  const password = process.env[key]
  if (!password) throw new Error('Run scripts/setup-local.ps1 before E2E tests')
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
}
async function openMenu(page: Page) {
  const button = page.getByRole('button', { name: '打开导航' })
  if (await button.isVisible()) await button.click()
}
test('unauthenticated deep link redirects and form validation is visible', async ({ page }) => {
  await page.goto('/materials')
  await expect(page).toHaveURL(/\/login$/)
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page.getByText('请输入用户名', { exact: true })).toBeVisible()
  await expect(page.getByText('请输入密码', { exact: true })).toBeVisible()
})
test('wrong password returns actual server error', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill('incorrect-password')
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page.getByRole('alert')).toContainText('用户名或密码错误')
  await expect(page).toHaveURL(/\/login$/)
})
test('admin login restores after refresh, visits user management, handles 404 and logs out', async ({ page }, info) => {
  const errors: string[] = []
  page.on('pageerror', e => errors.push(e.message))
  await login(page)
  await expect(page.locator('.metric-value')).toHaveCount(4)
  const snapshot = await page.evaluate(async () => {
    const response = await fetch('/api/workspace/overview', { headers: { Authorization: 'Bearer ' + sessionStorage.getItem('caizhitong.session') } })
    return (await response.json()).data as { metrics: { materials: number; stock: number; pending: number; alerts: number }; activities: unknown[] }
  })
  for (const [index,key] of (['materials','stock','pending','alerts'] as const).entries()) await expect(page.locator('.metric-value').nth(index)).toHaveText(snapshot.metrics[key]+(index<2?'种':'项'))
  if(snapshot.activities.length) await expect(page.locator('.activity-records li')).toHaveCount(snapshot.activities.length)
  else await expect(page.getByText('暂无材料流转记录。', { exact: false })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
  const screenshotDir = resolve(import.meta.dirname, '../../docs/screenshots')
  mkdirSync(screenshotDir, { recursive: true })
  await page.screenshot({ path: resolve(screenshotDir, 'dashboard-' + info.project.name + '.png'), fullPage: true, animations: 'disabled' })
  await openMenu(page)
  await page.getByRole('menuitem', { name: '用户与权限' }).click()
  await expect(page).toHaveURL(/\/users$/)
  await expect(page.getByRole('button', { name: '新增用户', exact: true })).toBeVisible()
  await page.goto('/missing-page')
  await expect(page.getByText('没有找到这个页面', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '返回工作台' }).click()
  await page.getByRole('button', { name: '账户菜单' }).click()
  await page.getByRole('menuitem', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect(await page.evaluate(() => sessionStorage.getItem('caizhitong.session'))).toBeNull()
  await page.goto('/dashboard')
  await expect(page).toHaveURL(/\/login$/)
  expect(errors).toEqual([])
})
test('student sees restricted menu and forbidden direct URL with backend 403', async ({ page, request }) => {
  await login(page, 'student')
  await openMenu(page)
  await expect(page.getByRole('menuitem', { name: '用户与权限' })).toHaveCount(0)
  await expect(page.getByRole('menuitem', { name: '入库管理' })).toHaveCount(0)
  const token = await page.evaluate(() => sessionStorage.getItem('caizhitong.session'))
  const response = await request.get('/api/workspace/modules/users', { headers: { Authorization: 'Bearer ' + token } })
  expect(response.status()).toBe(403)
  await page.goto('/users')
  await expect(page.getByText('此页面需要更多权限', { exact: true })).toBeVisible()
})
test('lab technician gets inventory and inbound but no system menu', async ({ page }) => {
  await login(page, 'lab')
  await openMenu(page)
  await expect(page.getByRole('menuitem', { name: '入库管理' })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: '系统设置' })).toHaveCount(0)
  await page.getByRole('menuitem', { name: '库存管理' }).click()
  await expect(page).toHaveURL(/\/inventory$/)
  await expect(page.getByRole('tab', { name: '材料库存', exact: true })).toBeVisible()
})
test('invalid persisted token is rejected by backend and session is cleared', async ({ page }) => {
  await page.goto('/login')
  await page.evaluate(() => sessionStorage.setItem('caizhitong.session', 'invalid.jwt.signature'))
  await page.goto('/dashboard')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('alert')).toContainText('登录已过期')
  expect(await page.evaluate(() => sessionStorage.getItem('caizhitong.session'))).toBeNull()
})
test('dashboard API 401 clears authenticated session', async ({ page }) => {
  await login(page)
  const token = await page.evaluate(() => sessionStorage.getItem('caizhitong.session'))
  // Corrupt only the next overview request. The real backend returns 401;
  // background profile checks must not expire the session before navigation.
  await page.goto('/materials')
  await expect(page.getByRole('heading', { name: '材料管理', exact: true })).toBeVisible()
  await page.route('**/api/workspace/overview', route => route.continue({
    headers: { ...route.request().headers(), authorization: 'Bearer invalid.jwt.signature' },
  }))
  await openMenu(page)
  await page.getByRole('link', { name: '材智通工作台' }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect(token).toBeTruthy()
  expect(await page.evaluate(() => sessionStorage.getItem('caizhitong.session'))).toBeNull()
})
test('login layout has no horizontal overflow and captures actual UI', async ({ page }, info) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '欢迎回到材智通' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  const screenshotDir = resolve(import.meta.dirname, '../../docs/screenshots')
  mkdirSync(screenshotDir, { recursive: true })
  await page.screenshot({ path: resolve(screenshotDir, 'login-' + info.project.name + '.png'), fullPage: true, animations: 'disabled' })
})
test('dashboard layout supports keyboard and has no horizontal overflow', async ({ page }) => {
  await login(page)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  const mobileButton = page.getByRole('button', { name: '打开导航' })
  if (await mobileButton.isVisible()) {
    await mobileButton.click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).not.toBeVisible()
  } else {
    await page.getByRole('button', { name: '收起侧边栏', exact: true }).click()
    await expect(page.locator('.app-shell')).toHaveClass(/is-collapsed/)
    await page.getByRole('button', { name: '展开侧边栏', exact: true }).click()
    await expect(page.locator('.app-shell')).not.toHaveClass(/is-collapsed/)
  }
})
test('client session expiry automatically returns to login', async ({ page }) => {
  await page.clock.install()
  await login(page)
  const remaining = await page.evaluate(() => {
    const token = sessionStorage.getItem('caizhitong.session')!
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as { exp: number }
    return Math.max(0, payload.exp * 1000 - Date.now())
  })
  await page.clock.fastForward(remaining + 1000)
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('alert')).toContainText('登录已过期')
  expect(await page.evaluate(() => sessionStorage.getItem('caizhitong.session'))).toBeNull()
})
test('temporary restoration network error offers retry without discarding token', async ({ page }) => {
  await login(page)
  await page.route('**/api/auth/me', route => route.abort('failed'))
  await page.reload()
  await expect(page.getByText('暂时无法恢复登录', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => sessionStorage.getItem('caizhitong.session'))).toBeTruthy()
  await page.unroute('**/api/auth/me')
  await page.getByRole('button', { name: '重新连接' }).click()
  await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
})

