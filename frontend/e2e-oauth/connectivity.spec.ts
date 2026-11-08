import { expect, test } from '@playwright/test'
import type { Page, Route } from '@playwright/test'

if (process.env.E2E_ISOLATED !== '1') throw new Error('Connectivity tests require the isolated test database')
test.use({ reducedMotion: 'reduce' })
const configuration = '**/api/auth/oauth/config'
const github = (page: Page) => page.getByRole('button', { name: '继续使用 GitHub', exact: true })
const reconnect = (page: Page) => page.getByRole('button', { name: '重新连接', exact: true })
const respond = (route: Route, enabled: boolean) => route.fulfill({ json: { code: 0, data: { githubEnabled: enabled } } })

test('healthy provider remains secure, repeated clicks start one OAuth flow and create only a guest', async ({ page, request }) => {
  await request.post('http://127.0.0.1:9098/control', { data: { id: '996' + Date.now(), login: 'connectivity-synthetic', name: '连接测试访客', email: null, avatar_url: null } })
  let starts = 0
  await page.route('**/oauth2/authorization/github?**', async route => { starts++; await route.continue() })
  await page.goto('/login')
  await expect(github(page)).toBeEnabled()
  await github(page).evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click() })
  await expect(page).toHaveURL(/\/demo\/dashboard$/)
  expect(starts).toBe(1)
  const token = await page.evaluate(() => sessionStorage.getItem('caizhitong.session'))
  const headers = { Authorization: 'Bearer ' + token }
  expect((await (await request.get('/api/auth/me', { headers })).json()).data.roles).toEqual(['GUEST'])
  expect((await request.get('/api/materials', { headers })).status()).toBe(403)
})

test('hung detection times out after eight seconds without an endless login spinner', async ({ page }) => {
  await page.clock.install()
  let attempts = 0
  await page.route(configuration, () => { attempts++ })
  await page.goto('/login')
  await expect.poll(() => attempts).toBe(1)
  await expect(github(page)).not.toHaveClass(/ant-btn-loading/)
  await page.clock.fastForward(8001)
  await expect(page.getByText('服务暂未就绪，可能正在唤醒', { exact: true })).toBeVisible()
  await expect(reconnect(page)).toBeEnabled()
  await expect(page.getByLabel('用户名', { exact: true })).toBeVisible()
  await expect(page.getByText('将自动重新检测', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: test.info().outputPath('waking.png'), fullPage: true })
})

test('cold-start recovery enables GitHub automatically and preserves the password form already in use', async ({ page }) => {
  await page.clock.install()
  let attempts = 0
  await page.route(configuration, route => ++attempts === 1 ? route.fulfill({ status: 503, body: 'Service starting' }) : respond(route, true))
  await page.goto('/login')
  await expect(page.getByText('服务暂未就绪，可能正在唤醒', { exact: true })).toBeVisible()
  await page.getByLabel('用户名', { exact: true }).fill('synthetic-draft')
  await page.clock.fastForward(5001)
  await expect(github(page)).toBeEnabled()
  await expect(page.getByLabel('用户名', { exact: true })).toBeVisible()
  await expect(page.getByLabel('用户名', { exact: true })).toHaveValue('synthetic-draft')
  await expect(page.getByRole('button', { name: '登录工作空间', exact: true })).toBeEnabled()
  expect(attempts).toBe(2)
})

test('network failures have a finite retry budget and explicit manual recovery', async ({ page }) => {
  await page.clock.install()
  let attempts = 0
  await page.route(configuration, route => ++attempts <= 6 ? route.abort('failed') : respond(route, true))
  await page.goto('/login')
  await expect(page.getByText('网络连接失败', { exact: true })).toBeVisible()
  for (const [index, delay] of [5000, 15000, 30000, 45000, 45000].entries()) {
    await page.clock.fastForward(delay + 1)
    await expect.poll(() => attempts).toBe(index + 2)
    await expect(reconnect(page)).toBeEnabled()
  }
  await expect(page.getByText('已停止自动重试，可手动重新连接', { exact: true })).toBeVisible()
  await page.clock.fastForward(600000)
  expect(attempts).toBe(6)
  await page.screenshot({ path: test.info().outputPath('network-unavailable.png'), fullPage: true })
  await reconnect(page).click()
  await expect(github(page)).toBeEnabled()
  expect(attempts).toBe(7)
})

test('manual retry coalesces double clicks and cancels the old retry timer', async ({ page }) => {
  await page.clock.install()
  let attempts = 0
  let pending: Route | undefined
  await page.route(configuration, route => {
    attempts++
    if (attempts === 1) return route.abort('failed')
    pending = route
  })
  await page.goto('/login')
  await expect(reconnect(page)).toBeEnabled()
  await reconnect(page).evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click() })
  await expect.poll(() => attempts).toBe(2)
  await expect(reconnect(page)).toBeDisabled()
  await respond(pending!, true)
  await expect(github(page)).toBeEnabled()
  await page.clock.fastForward(600000)
  expect(attempts).toBe(2)
})

test('password login works while OAuth is pending; stale responses cannot change the next login page', async ({ page }) => {
  let old: Route | undefined
  let attempts = 0
  let passwordSubmissions = 0
  page.on('request', request => { if (request.method() === 'POST' && request.url().endsWith('/api/auth/login')) passwordSubmissions++ })
  await page.route(configuration, route => {
    if (++attempts === 1) { old = route; return }
    return respond(route, false)
  })
  await page.goto('/login')
  await expect.poll(() => attempts).toBe(1)
  await page.getByLabel('用户名', { exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill(process.env.DEMO_ADMIN_PASSWORD!)
  await page.getByRole('button', { name: '登录工作空间', exact: true }).evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click() })
  await expect(page).toHaveURL(/\/dashboard$/)
  expect(passwordSubmissions).toBe(1)
  await page.getByRole('button', { name: '账户菜单', exact: true }).click()
  await page.getByRole('menuitem', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByText('GitHub 登录尚未启用，仍可使用账号密码登录。', { exact: true })).toBeVisible()
  await respond(old!, true).catch(() => {}) // The prior request has legitimately been aborted.
  await expect(github(page)).toBeDisabled()
  await expect(page.getByLabel('用户名', { exact: true })).toBeVisible()
})

test('an explicit disabled configuration stops retrying and keeps password login available', async ({ page }) => {
  await page.clock.install()
  let attempts = 0
  await page.route(configuration, route => { attempts++; return respond(route, false) })
  await page.goto('/login')
  await expect(page.getByText('GitHub 登录尚未启用，仍可使用账号密码登录。', { exact: true })).toBeVisible()
  await expect(page.getByLabel('用户名', { exact: true })).toBeVisible()
  await page.clock.fastForward(600000)
  expect(attempts).toBe(1)
  await expect(reconnect(page)).toHaveCount(0)
})

test('invalid configuration and permanent HTTP failures are not mistaken for disabled OAuth', async ({ page }) => {
  await page.clock.install()
  let attempts = 0
  await page.route(configuration, route => ++attempts === 1 ? route.fulfill({ json: { code: 0, data: {} } }) : route.fulfill({ status: 404, body: 'not found' }))
  await page.goto('/login')
  await expect(page.getByText('登录服务响应异常', { exact: true })).toBeVisible()
  await expect(page.getByText('GitHub 登录尚未启用，仍可使用账号密码登录。', { exact: true })).toHaveCount(0)
  await expect(reconnect(page)).toBeEnabled()
  await reconnect(page).click()
  await expect(page.getByText('登录服务响应异常', { exact: true })).toBeVisible()
  await expect(page.getByText('已停止自动重试，可手动重新连接', { exact: true })).toBeVisible()
  await page.clock.fastForward(600000)
  expect(attempts).toBe(2)
})
