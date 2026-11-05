import { test, expect } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import type { IssueDetail } from '../src/issues/types'
test.skip(process.env.E2E_ISOLATED !== '1', '领用写入必须在独立数据库运行')
async function login(page: Page, username = 'student', deep?: string) {
  if (!deep) await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page
    .getByLabel('密码', { exact: true })
    .fill(
      process.env[
        username === 'student'
          ? 'DEMO_STUDENT_PASSWORD'
          : username === 'lab'
            ? 'DEMO_LAB_PASSWORD'
            : 'DEMO_ADMIN_PASSWORD'
      ]!,
    )
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page).toHaveURL(deep ? new RegExp(deep + '$') : /\/dashboard$/)
}
async function fixture(request: APIRequestContext, tag: string, limit = '50') {
  const auth = await request.post('/api/auth/login', {
      data: { username: 'admin', password: process.env.DEMO_ADMIN_PASSWORD },
    }),
    headers = {
      Authorization: 'Bearer ' + (await auth.json()).data.accessToken,
    }
  const category = await request.post('/api/material-categories', {
    headers,
    data: { name: '领用验收-' + tag },
  })
  expect(category.status()).toBe(200)
  const response = await request.post('/api/materials', {
    headers,
    data: {
      categoryId: (await category.json()).data.id,
      name: '金属膜电阻-' + tag,
      spec: '10kΩ，±1%，0.25W',
      unit: '个',
      unitPrice: '0.1234',
      safeStock: '10',
      status: 'ACTIVE',
    },
  })
  expect(response.status()).toBe(200)
  const material = (await response.json()).data
  expect(
    (
      await request.patch('/api/materials/' + material.id + '/issue-limit', {
        headers,
        data: { monthlyIssueLimit: limit },
      })
    ).status(),
  ).toBe(200)
  const inbound = await request.post('/api/inbound-orders', {
    headers,
    data: {
      idempotencyKey: crypto.randomUUID(),
      lines: [
        { itemId: material.id, quantity: '800' },
        { itemId: material.id, quantity: '100' },
      ],
    },
  })
  expect(inbound.status()).toBe(200)
  const batches = (await inbound.json()).data.lines
  return { headers, material, batches }
}
async function capture(page: Page, name: string) {
  const dir = resolve(import.meta.dirname, '../../docs/screenshots')
  mkdirSync(dir, { recursive: true })
  await expect(page.locator('.ant-message-notice')).toHaveCount(0, {
    timeout: 6000,
  })
  await page.screenshot({
    path: resolve(dir, name + '.png'),
    fullPage: false,
    animations: 'disabled',
  })
}
async function fields(page: Page, qty = '20', project = 'LAB-2026-ISSUE') {
  await page.getByLabel('本次领用数量', { exact: true }).fill(qty)
  await page.getByLabel('项目编号', { exact: true }).fill(project)
  await page.getByLabel('用途', { exact: true }).fill('模拟电路课程实验搭建')
  await page.getByRole('button', { name: '核对领用信息' }).click()
  await expect(
    page.getByText('请核对领用信息，确认后立即扣减库存与额度。', {
      exact: true,
    }),
  ).toBeVisible()
}
test('QR batch route recovers login, issues 20 of 800, persists history and downloads real PDF', async ({
  page,
  request,
}, info) => {
  test.setTimeout(90000)
  const { material, batches, headers } = await fixture(
      request,
      info.project.name + '-' + Date.now().toString(36),
    ),
    batch = batches[0],
    path = '/stock/batches/' + batch.batchCode
  await page.goto(path)
  await expect(page).toHaveURL(/\/login$/)
  await login(page, 'student', path)
  await page.getByRole('button', { name: '领用此批次', exact: true }).click()
  await expect(
    page.getByRole('dialog', { name: '领用此批次', exact: true }),
  ).toBeVisible()
  await expect(page.getByRole('status')).toContainText('本月已领 0 个')
  await expect(page.getByRole('status')).toContainText('剩余 50')
  await page.getByRole('button', { name: '核对领用信息' }).click()
  await expect(page.getByText('请填写项目编号', { exact: true })).toBeVisible()
  await fields(page)
  await expect(page.locator('.issue-amount')).toContainText('CNY 2.468')
  await capture(page, 'sprint04-issue-form-' + info.project.name)
  const response = page.waitForResponse(
    (r) => r.url().endsWith('/api/issues') && r.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '确认领用', exact: true }).click()
  const d = (await (await response).json()).data as IssueDetail
  await expect(
    page.getByRole('heading', { name: d.order.issueNo, exact: true }),
  ).toBeVisible()
  await expect(page.getByRole('dialog')).toContainText('800 → 780 个')
  await expect(page.locator('.czt-drawer:not(.ant-drawer-open) .ant-drawer-content-wrapper')).toHaveCount(0)
  const detailOverlay = page.locator('.ant-drawer-open').filter({ has: page.getByRole('heading', { name: d.order.issueNo, exact: true }) })
  await expect.poll(() => detailOverlay.evaluate(element => element === document.activeElement || element.contains(document.activeElement))).toBe(true)
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载 PDF 凭证' }).click()
  expect((await download).suggestedFilename()).toBe(d.order.issueNo + '.pdf')
  await capture(page, 'sprint04-issue-detail-' + info.project.name)
  const refreshed = await request.get('/api/stock-batches/' + batch.batchCode, {
    headers,
  })
  expect((await refreshed.json()).data.quantity).toBe('780.000')
  await page.getByRole('button', { name: '关闭详情', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page
    .getByRole('searchbox', { name: '搜索领用单或材料' })
    .fill(d.order.issueNo)
  await page.getByRole('searchbox', { name: '搜索领用单或材料' }).press('Enter')
  await expect(page.locator('.issue-history')).toContainText(d.order.issueNo)
  await capture(page, 'sprint04-history-' + info.project.name)
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true)
  await page.reload()
  await expect(page.locator('.issue-history')).toContainText(d.order.issueNo)
  await page.goto('/stock/batches/' + batches[1].batchCode)
  await page.getByRole('button', { name: '领用此批次', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('剩余 30')
  await fields(page, '30')
  await page.getByRole('button', { name: '确认领用', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('100 → 70 个')
  await page.goto('/stock/batches/' + batch.batchCode)
  await page.getByRole('button', { name: '领用此批次', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('剩余 0')
  await page.getByRole('button', { name: '核对领用信息' }).click()
  await expect(
    page.getByText('个人本月剩余额度不足', { exact: true }),
  ).toBeVisible()
  await page.goto('/materials')
  await page
    .getByRole('searchbox', { name: '搜索名称或编码' })
    .fill(material.materialCode)
  await expect(page.getByTestId('material-row-' + material.id)).toContainText(
    '850 个',
  )
})
test('desktop manual selection, administrator quota settings and complete management view', async ({
  page,
  request,
}, info) => {
  test.setTimeout(90000)
  const { material, batches } = await fixture(
    request,
    'manual-' + info.project.name + '-' + Date.now().toString(36),
  )
  await login(page, 'admin')
  await page.goto('/materials')
  await page
    .getByRole('searchbox', { name: '搜索名称或编码' })
    .fill(material.materialCode)
  await page
    .getByRole('button', { name: '查看 ' + material.name, exact: true })
    .click()
  await page.getByRole('button', { name: '设置领用限额' }).click()
  await page.getByLabel('每人每月限额', { exact: true }).fill('60')
  await page.getByRole('button', { name: '保存限额' }).click()
  await expect(
    page.getByText('每人每月限额：60 个', { exact: true }),
  ).toBeVisible()
  await capture(page, 'sprint04-quota-' + info.project.name)
  await page.goto('/requisitions')
  await page.getByRole('button', { name: '新建领用单' }).click()
  await page
    .getByRole('combobox', { name: '搜索领用材料' })
    .fill(material.materialCode)
  await page
    .locator('.ant-select-dropdown:visible')
    .getByRole('option')
    .filter({ hasText: material.name })
    .click()
  await page.getByRole('combobox', { name: '选择领用批次' }).click()
  await page
    .locator('.ant-select-dropdown:visible')
    .getByRole('option')
    .filter({ hasText: batches[0].batchCode })
    .click()
  await fields(page, '1', 'ADMIN-' + material.materialCode)
  await page.getByRole('button', { name: '确认领用', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('799 个')
  await page.getByRole('button', { name: '关闭详情', exact: true }).click()
  await page.getByRole('tab', { name: '全部领用', exact: true }).click()
  await page
    .getByRole('textbox', { name: '筛选项目编号' })
    .fill('ADMIN-' + material.materialCode)
  await expect(page.locator('.issue-history')).toContainText(material.name)
  await capture(page, 'sprint04-management-' + info.project.name)
})
test('lost issue response can be replayed after refresh and private details reject another user', async ({
  page,
  request,
}, info) => {
  test.setTimeout(60000)
  const { headers, batches, material } = await fixture(
    request,
    'retry-' + info.project.name + '-' + Date.now().toString(36),
  )
  await page.addInitScript(() =>
    Object.defineProperty(globalThis.crypto, 'randomUUID', {
      value: undefined,
    }),
  )
  await login(page)
  await page.goto('/stock/batches/' + batches[0].batchCode)
  await page.getByRole('button', { name: '领用此批次', exact: true }).click()
  await fields(page, '5', 'RETRY-' + material.id)
  let original: unknown
  await page.route('**/api/issues', async (route) => {
    if (route.request().method() === 'POST') {
      original = route.request().postDataJSON()
      await route.fetch()
      await route.abort('failed')
    } else await route.continue()
  })
  await page.getByRole('button', { name: '确认领用', exact: true }).click()
  await expect(
    page.getByRole('button', { name: '安全重试原请求' }),
  ).toBeVisible()
  await page.unroute('**/api/issues')
  await page.reload()
  await expect(
    page.getByRole('button', { name: '安全重试原请求' }),
  ).toBeVisible()
  await page.getByRole('button', { name: '安全重试原请求' }).click()
  await expect(page.getByRole('dialog')).toContainText('800 → 795 个')
  const stock = await request.get(
    '/api/stock-batches/' + batches[0].batchCode,
    { headers },
  )
  expect((await stock.json()).data.quantity).toBe('795.000')
  const studentToken = await page.evaluate(() =>
    sessionStorage.getItem('caizhitong.session'),
  )
  const repeat = await request.post('/api/issues', {
    headers: { Authorization: 'Bearer ' + studentToken },
    data: original,
  })
  expect((original as { idempotencyKey: string }).idempotencyKey).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
  )
  expect(repeat.status()).toBe(200)
  const privateIssue = await request.post('/api/issues', {
    headers,
    data: {
      idempotencyKey: crypto.randomUUID(),
      projectNo: 'PRIVATE',
      usage: '管理员本人领用',
      lines: [{ batchId: batches[1].id, quantity: '1' }],
    },
  })
  expect(privateIssue.status()).toBe(200)
  const privateId = (await privateIssue.json()).data.order.id
  await page.goto('/requisitions?order=' + privateId)
  await expect(page.getByRole('alert')).toContainText(
    '只能查看本人的领用单和凭证',
  )
  const forbidden = await request.get('/api/issues/' + privateId + '/pdf', {
    headers: { Authorization: 'Bearer ' + studentToken },
  })
  expect(forbidden.status()).toBe(403)
  await page.goto('/stock/batches/BAT-9999999999')
  await expect(
    page.getByText('库存批次不存在', { exact: true }).first(),
  ).toBeVisible()
})
