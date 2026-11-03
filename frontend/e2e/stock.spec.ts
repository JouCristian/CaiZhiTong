import { test, expect } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import type { OrderDetail } from '../src/stock/types'
test.skip(process.env.E2E_ISOLATED !== '1', '库存写入仅在隔离数据库运行')
async function login(page: Page, username = 'admin', target = '/dashboard') {
  await page.goto('/login')
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
  await expect(page).toHaveURL(new RegExp(target + '$'))
}
async function fixture(request: APIRequestContext, tag: string) {
  const login = await request.post('/api/auth/login', {
      data: { username: 'admin', password: process.env.DEMO_ADMIN_PASSWORD },
    }),
    token = (await login.json()).data.accessToken
  const headers = { Authorization: 'Bearer ' + token }
  const categoryResponse = await request.post('/api/material-categories', {
    headers,
    data: { name: '库存验收-' + tag },
  })
  expect(categoryResponse.status()).toBe(200)
  const categoryId = (await categoryResponse.json()).data.id
  const rows = []
  for (const name of ['精密电阻', '陶瓷电容']) {
    const response = await request.post('/api/materials', {
      headers,
      data: {
        categoryId,
        name: name + '-' + tag,
        spec: '实验室验收规格',
        unit: '个',
        unitPrice: '0.1234',
        currency: 'CNY',
        safeStock: '10',
        status: 'ACTIVE',
      },
    })
    expect(response.status()).toBe(200)
    rows.push((await response.json()).data)
  }
  return { headers, rows }
}
async function choose(page: Page, name: string, index = 0) {
  const select = page.getByRole('combobox', { name: '选择入库材料' }).nth(index)
  await select.fill(name)
  await page
    .locator('.ant-select-dropdown:visible')
    .getByRole('option')
    .filter({ hasText: name })
    .click({ timeout: 10000 })
}
async function capture(page: Page, name: string) {
  const dir = resolve(import.meta.dirname, '../../docs/screenshots')
  mkdirSync(dir, { recursive: true })
  await page.screenshot({
    path: resolve(dir, name + '.png'),
    animations: 'disabled',
    fullPage: false,
  })
}
test('real multiline inbound updates stock and ledger, shows printable downloadable QR', async ({
  page,
  request,
}, info) => {
  test.setTimeout(90000)
  const tag = info.project.name + '-' + Date.now().toString(36),
    { rows } = await fixture(request, tag)
  await login(page, 'lab')
  await page.goto('/inbound')
  await page.getByRole('button', { name: '新建入库单' }).click()
  await page.getByRole('button', { name: '确认入库' }).click()
  await expect(
    page.getByText('请选择启用的材料', { exact: true }),
  ).toBeVisible()
  await choose(page, rows[0].name)
  await page
    .getByRole('spinbutton', { name: '入库数量 1', exact: true })
    .fill('2.125')
  await page.getByRole('textbox', { name: '库位 1', exact: true }).fill('A-01')
  await page.getByRole('button', { name: '添加材料明细' }).click()
  await choose(page, rows[1].name, 1)
  await page
    .getByRole('spinbutton', { name: '入库数量 2', exact: true })
    .fill('3.875')
  await expect(page.locator('.inbound-summary-total')).toContainText(
    'CNY 0.7404',
  )
  await capture(page, 'sprint03-inbound-form-' + info.project.name)
  const response = page.waitForResponse(
    (r) =>
      r.url().endsWith('/api/inbound-orders') &&
      r.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '确认入库' }).click()
  const detail = (await (await response).json()).data as OrderDetail
  expect(detail.lines).toHaveLength(2)
  await expect(
    page.getByRole('heading', { name: detail.order.orderNo, exact: true }),
  ).toBeVisible()
  await capture(page, 'sprint03-order-' + info.project.name)
  await page.goto('/inventory')
  await page
    .getByRole('searchbox', { name: '搜索库存名称或编码' })
    .fill(rows[0].materialCode)
  await page
    .getByRole('searchbox', { name: '搜索库存名称或编码' })
    .press('Enter')
  await expect(
    page.getByRole('button', { name: rows[0].name, exact: false }),
  ).toBeVisible()
  await expect(page.locator('.ant-table-tbody')).toContainText('2.125 个')
  await capture(page, 'sprint03-inventory-' + info.project.name)
  await page.getByRole('tab', { name: '库存流水', exact: true }).click()
  await page
    .getByRole('searchbox', { name: '搜索库存名称或编码' })
    .fill(rows[0].materialCode)
  await page
    .getByRole('searchbox', { name: '搜索库存名称或编码' })
    .press('Enter')
  await expect(page.locator('.ant-table-tbody')).toContainText('0 → 2.125')
  await expect(page.locator('.ant-table-tbody')).toContainText(
    detail.order.orderNo,
  )
  await capture(page, 'sprint03-ledger-' + info.project.name)
  await page.goto('/stock/batches/' + detail.lines[0].batchCode)
  await expect(
    page.getByRole('heading', { name: rows[0].name, exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: '二维码标签' }).click()
  const image = page
    .getByRole('dialog')
    .getByRole('img', { name: '批次二维码 ' + detail.lines[0].batchCode })
  await expect(image).toBeVisible()
  expect(
    await image.evaluate((img: HTMLImageElement) => img.naturalWidth),
  ).toBe(320)
  const download = page.waitForEvent('download')
  await page.getByRole('link', { name: '下载 PNG' }).click()
  expect((await download).suggestedFilename()).toBe(
    detail.lines[0].batchCode + '.png',
  )
  await page.evaluate(() => {
    window.print = () => {
      document.body.dataset.printCalled = '1'
    }
  })
  await page.getByRole('button', { name: '打印标签' }).click()
  expect(await page.locator('body').getAttribute('data-print-called')).toBe('1')
  await page.emulateMedia({ media: 'print' })
  await expect(page.locator('body>.print-label')).toBeVisible()
  await expect(page.locator('.app-shell')).not.toBeVisible()
  await page.emulateMedia({ media: 'screen' })
  await capture(page, 'sprint03-label-' + info.project.name)
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true)
  await page.locator('.ant-modal-close').click()
  await page.goto('/materials')
  await page
    .getByRole('searchbox', { name: '搜索名称或编码' })
    .fill(rows[0].materialCode)
  await expect(page.getByTestId('material-row-' + rows[0].id)).toContainText(
    '2.125 个',
  )
})
test('batch deep link recovers after login; student is read-only; missing batch is clear', async ({
  page,
  request,
}, info) => {
  const { headers, rows } = await fixture(
    request,
    'scan-' + info.project.name + '-' + Date.now().toString(36),
  )
  const response = await request.post('/api/inbound-orders', {
    headers,
    data: {
      idempotencyKey: crypto.randomUUID(),
      lines: [{ itemId: rows[0].id, quantity: '1' }],
    },
  })
  expect(response.status()).toBe(200)
  const detail = (await response.json()).data as OrderDetail,
    path = '/stock/batches/' + detail.lines[0].batchCode
  await page.goto(path)
  await expect(page).toHaveURL(/\/login$/)
  await login(page, 'student', path)
  await expect(
    page.getByRole('heading', { name: rows[0].name, exact: true }),
  ).toBeVisible()
  await page.goto('/inbound')
  await expect(
    page.getByText('此页面需要更多权限', { exact: true }),
  ).toBeVisible()
  const studentToken = await page.evaluate(() =>
    sessionStorage.getItem('caizhitong.session'),
  )
  expect(
    (
      await request.post('/api/inbound-orders', {
        headers: { Authorization: 'Bearer ' + studentToken },
        data: {
          idempotencyKey: crypto.randomUUID(),
          lines: [{ itemId: rows[0].id, quantity: '1' }],
        },
      })
    ).status(),
  ).toBe(403)
  await page.goto('/stock/batches/BAT-9999999999')
  await expect(
    page.getByText('库存批次不存在', { exact: true }).first(),
  ).toBeVisible()
})
test('unknown inbound response is safely replayed after refresh without duplicate stock', async ({
  page,
  request,
}, info) => {
  test.setTimeout(60000)
  const { headers, rows } = await fixture(
    request,
    'retry-' + info.project.name + '-' + Date.now().toString(36),
  )
  await page.addInitScript(() =>
    Object.defineProperty(globalThis.crypto, 'randomUUID', {
      value: undefined,
    }),
  )
  await login(page)
  await page.goto('/inbound')
  await page.getByRole('button', { name: '新建入库单' }).click()
  await choose(page, rows[0].name)
  let originalBody: unknown
  await page.route('**/api/inbound-orders', async (route) => {
    if (route.request().method() === 'POST') {
      originalBody = route.request().postDataJSON()
      await route.fetch()
      await route.abort('failed')
    } else await route.continue()
  })
  await page.getByRole('button', { name: '确认入库' }).click()
  await expect(
    page.getByRole('button', { name: '安全重试原请求' }),
  ).toBeVisible()
  await page.unroute('**/api/inbound-orders')
  await page.reload()
  await page.getByRole('button', { name: '新建入库单' }).click()
  await page.getByRole('button', { name: '安全重试原请求' }).click()
  await expect(page.getByRole('heading', { name: /^IN-/ })).toBeVisible()
  const stock = await request.get('/api/stocks?itemId=' + rows[0].id, {
    headers,
  })
  expect((await stock.json()).data.records[0].quantity).toBe('1.000')
  const replay = await request.post('/api/inbound-orders', {
    headers,
    data: originalBody,
  })
  expect((originalBody as { idempotencyKey: string }).idempotencyKey).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
  )
  expect(replay.status()).toBe(200)
  expect(
    await page.evaluate(
      () =>
        Object.keys(sessionStorage).find((key) =>
          key.startsWith('caizhitong.pending-inbound:'),
        ) || null,
    ),
  ).toBeNull()
})
