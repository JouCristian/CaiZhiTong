import { test, expect } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'

if (process.env.E2E_ISOLATED !== '1') throw new Error('OAuth browser writes require the isolated test harness')
test.beforeAll(async ({ request }) => {
  const bearer = await admin(request), headers = { Authorization: 'Bearer ' + bearer }
  const existing = await (await request.get('/api/materials?keyword=' + encodeURIComponent('公开体验·合成电阻'), { headers })).json()
  let item = existing.data.records[0]
  if (!item) {
    const category = await (await request.post('/api/material-categories', { headers, data: { name: '公开体验·合成材料' } })).json()
    item = (await (await request.post('/api/materials', { headers, data: { categoryId: category.data.id, name: '公开体验·合成电阻', model: 'DEMO-R10K', unit: '只', unitPrice: '0.1000', safeStock: '10', status: 'ACTIVE' } })).json()).data
  }
  const inbound = await request.post('/api/inbound-orders', { headers, data: { idempotencyKey: 'd76d7f47-0dad-4811-9ee9-0e5788690070', remark: '公开体验合成数据', lines: [{ itemId: item.id, quantity: '100', location: '演示库位' }] } })
  expect(inbound.ok()).toBeTruthy()
})

async function profile(request: APIRequestContext, id: string, extra = {}) {
  const response = await request.post('http://127.0.0.1:9098/control', { data: { id, avatar_url: null, ...extra } })
  expect(response.ok()).toBeTruthy()
}
function identity() { return '990' + Date.now() + Math.floor(Math.random() * 100).toString().padStart(2, '0') }
async function github(page: Page, target = '/dashboard') {
  await page.goto(target)
  await expect(page).toHaveURL(/\/login$/)
  await page.getByRole('button', { name: '继续使用 GitHub', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(target + '$'), { timeout: 20000 })
}
async function logout(page: Page) {
  await page.getByRole('button', { name: '账户菜单' }).click()
  await page.getByRole('menuitem', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
}
async function admin(request: APIRequestContext) {
  const response = await request.post('/api/auth/login', { data: { username: 'admin', password: process.env.DEMO_ADMIN_PASSWORD } })
  expect(response.ok()).toBeTruthy()
  return (await response.json()).data.accessToken as string
}
test('real provider redirect creates a read-only account, restores deep links, handles missing avatar and logs out', async ({ page, request }, info) => {
  await profile(request, identity(), { avatar_url: 'https://avatars.githubusercontent.com/u/990000001?v=4' })
  await page.route('https://avatars.githubusercontent.com/**', route => route.abort())
  const callbackUrls: string[] = []
  page.on('framenavigated', frame => { if (frame === page.mainFrame() && frame.url().includes('/oauth/callback')) callbackUrls.push(frame.url()) })
  await github(page, '/inventory')
  await expect(page.getByRole('heading', { name: '库存管理', exact: true })).toBeVisible()
  await expect(page.getByText('公开体验·合成电阻', { exact: true }).first()).toBeVisible()
  expect(callbackUrls.join(' ')).not.toMatch(/eyJ|accessToken|access_token/)
  await expect(page.getByRole('tab', { name: '库存流水' })).toHaveCount(0)
  const stored = await page.evaluate(() => ({ token: sessionStorage.getItem('caizhitong.session'), flow: sessionStorage.getItem('caizhitong.oauth.flow'), hash: location.hash }))
  expect(stored.token?.split('.')).toHaveLength(3); expect(stored.flow).toBeNull(); expect(stored.hash).toBe('')
  const me = await request.get('/api/auth/me', { headers: { Authorization: 'Bearer ' + stored.token } })
  expect((await me.json()).data.roles).toEqual(['GUEST'])
  const headers = { Authorization: 'Bearer ' + stored.token }
  const stock = (await (await request.get('/api/stocks', { headers })).json()).data.records[0]
  expect(Number(stock.quantity)).toBe(100)
  const batches = (await (await request.get('/api/stocks/' + stock.itemId + '/batches', { headers })).json()).data.batches.records
  expect(batches.length).toBeGreaterThan(0)
  for (const batch of batches) { expect(batch.operatorName).toBeUndefined(); expect(batch.createdBy).toBeUndefined(); expect(batch.remark).toBeUndefined(); expect(batch.orderId).toBeUndefined() }
  await page.reload(); await expect(page.getByRole('heading', { name: '库存管理', exact: true })).toBeVisible()
  if (info.project.name === 'mobile') await page.getByRole('button', { name: '打开导航' }).click()
  await expect(page.getByRole('menuitem', { name: '用户与权限' })).toHaveCount(0)
  if (info.project.name === 'mobile') await page.getByRole('dialog', { name: '工作空间导航' }).getByRole('button', { name: '关闭', exact: true }).click()
  const denied = await request.post('/api/material-categories', { headers: { Authorization: 'Bearer ' + stored.token }, data: { name: 'forbidden synthetic category' } })
  expect(denied.status()).toBe(403)
  await page.goto('/users'); await expect(page.getByText('此页面需要更多权限', { exact: true })).toBeVisible()
  await page.goto('/dashboard'); await expect(page.getByRole('heading', { name: '工作台', exact: true })).toBeVisible()
  const dir = resolve(import.meta.dirname, '../../docs/screenshots'); mkdirSync(dir, { recursive: true })
  await page.screenshot({ path: resolve(dir, 'v070-oauth-guest-' + info.project.name + '.png'), fullPage: false, animations: 'disabled' })
  await logout(page); expect(await page.evaluate(() => sessionStorage.getItem('caizhitong.session'))).toBeNull()
})
test('GitHub ID remains stable after profile rename and email change', async ({ page, request }) => {
  const id = identity(); await profile(request, id); await github(page)
  const first = await page.evaluate(() => JSON.parse(atob(sessionStorage.getItem('caizhitong.session')!.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).sub as string)
  await logout(page)
  await profile(request, id, { login: 'renamed-synthetic', email: 'changed@example.test' }); await github(page)
  const next = await page.evaluate(() => JSON.parse(atob(sessionStorage.getItem('caizhitong.session')!.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).sub as string)
  expect(next).toBe(first)
  await page.getByRole('button', { name: '账户菜单' }).click(); await expect(page.getByText('@renamed-synthetic', { exact: false })).toBeVisible()
})
test('cancelled authorization, invalid state and missing exchange context have clear safe errors', async ({ page, request }) => {
  await profile(request, identity(), { mode: 'cancel' })
  await page.goto('/login'); await page.getByRole('button', { name: '继续使用 GitHub', exact: true }).click()
  await expect(page.getByText('你已取消 GitHub 授权，可以重新尝试。')).toBeVisible()
  expect(new URL(page.url()).hash).toBe('')
  await profile(request, identity(), { mode: 'provider-error' })
  await page.getByRole('button', { name: '返回登录', exact: true }).click()
  await page.getByRole('button', { name: '继续使用 GitHub', exact: true }).click()
  await expect(page.getByText('GitHub 授权未完成，请稍后重试。')).toBeVisible()
  await page.goto('http://127.0.0.1:8081/login/oauth2/code/github?code=forged&state=forged')
  await expect(page.getByText('授权会话已过期或不匹配，请重新发起登录。')).toBeVisible()
  await page.goto('/oauth/callback#code=' + 'x'.repeat(43))
  await expect(page.getByText('登录上下文缺失或已过期，请从本页重新发起 GitHub 登录。')).toBeVisible()
  expect(await page.evaluate(() => sessionStorage.getItem('caizhitong.session'))).toBeNull()
})
test('administrator disable revokes GitHub login and existing JWT', async ({ page, request }) => {
  const id = identity(); await profile(request, id); await github(page)
  const token = await page.evaluate(() => sessionStorage.getItem('caizhitong.session'))
  const me = await (await request.get('/api/auth/me', { headers: { Authorization: 'Bearer ' + token } })).json()
  const bearer = await admin(request)
  expect((await request.patch('/api/users/' + me.data.id + '/status', { headers: { Authorization: 'Bearer ' + bearer }, data: { enabled: false } })).status()).toBe(200)
  expect((await request.get('/api/auth/me', { headers: { Authorization: 'Bearer ' + token } })).status()).toBe(401)
  await logout(page); await page.getByRole('button', { name: '继续使用 GitHub', exact: true }).click()
  await expect(page.getByText('此账户已被停用，请联系系统管理员。')).toBeVisible()
})
test('password login remains available as a secondary entry', async ({ page }, info) => {
  await page.goto('/login'); await expect(page.getByRole('button', { name: '继续使用 GitHub', exact: true })).toBeEnabled()
  await expect(page.getByLabel('用户名', { exact: true })).toHaveCount(0)
  const dir = resolve(import.meta.dirname, '../../docs/screenshots'); mkdirSync(dir, { recursive: true })
  await page.screenshot({ path: resolve(dir, 'v070-login-' + info.project.name + '.png'), fullPage: false, animations: 'disabled' })
  await page.getByRole('button', { name: '使用账号密码登录', exact: true }).click()
  await page.getByLabel('用户名', { exact: true }).fill('admin'); await page.getByLabel('密码', { exact: true }).fill(process.env.DEMO_ADMIN_PASSWORD!)
  await page.getByRole('button', { name: '登录工作空间', exact: true }).click(); await expect(page).toHaveURL(/\/dashboard$/)
})
