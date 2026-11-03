import { test, expect } from '@playwright/test'
import type { Page, APIRequestContext } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import type { Material } from '../src/materials/types'
test.skip(process.env.E2E_ISOLATED !== '1', '材料写入测试必须通过 scripts/test-browser.ps1 在独立测试库运行')

async function login(page: Page, username = 'admin') {
  const key = username === 'admin' ? 'DEMO_ADMIN_PASSWORD' : username === 'lab' ? 'DEMO_LAB_PASSWORD' : 'DEMO_STUDENT_PASSWORD'
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill(process.env[key]!)
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  await page.goto('/materials')
  await expect(page.getByRole('heading', { name: '材料管理', exact: true })).toBeVisible()
}
async function adminToken(request: APIRequestContext) {
  const response = await request.post('/api/auth/login', { data: { username: 'admin', password: process.env.DEMO_ADMIN_PASSWORD } })
  expect(response.status()).toBe(200)
  return (await response.json()).data.accessToken as string
}
async function fixture(request: APIRequestContext, tag: string) {
  const token = await adminToken(request)
  const headers = { Authorization: 'Bearer ' + token }
  const categoryResponse = await request.post('/api/material-categories', { headers, data: { name: '浏览器分类-' + tag, sortOrder: 0 } })
  expect(categoryResponse.status()).toBe(200)
  const categoryId = (await categoryResponse.json()).data.id as number
  const body = { categoryId, name: '浏览器电阻-' + tag, brand: '国巨', model: 'MFR-25', spec: '10kΩ ±1%', unit: '个', unitPrice: '0.1200', currency: 'CNY', safeStock: '100', status: 'ACTIVE', description: '独立测试库中的验收档案' }
  return { headers, categoryId, body }
}
async function chooseTree(page: Page, label: string, value: string) {
  await page.getByLabel(label, { exact: true }).click()
  await page.getByLabel(label, { exact: true }).fill(value)
  await page.locator('.ant-select-dropdown:visible').getByText(value, { exact: true }).click()
}
async function chooseSelect(page: Page, label: string, value: string) {
  // Ant Design's selected text intentionally covers its internal readonly input.
  await page.getByLabel(label, { exact: true }).first().click()
  await page.locator('.ant-select-dropdown:visible').getByText(value, { exact: true }).click()
}
async function capture(page: Page, name: string) {
  const dir = resolve(import.meta.dirname, '../../docs/screenshots')
  mkdirSync(dir, { recursive: true })
  await expect(page.locator('.ant-message-notice')).toHaveCount(0, { timeout: 6000 })
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: resolve(dir, name + '.png'), fullPage: false, animations: 'disabled' })
}
test('administrator maintains categories and a material through its full lifecycle', async ({ page }, info) => {
  test.setTimeout(90000)
  const tag = (info.project.name === 'desktop' ? 'D' : 'M') + Date.now().toString(36).slice(-6)
  const rootName = '电子元件验收-' + tag
  const childName = '精密电阻验收-' + tag
  const materialName = '金属膜电阻 10kΩ-' + tag
  await login(page)
  await page.getByRole('tab', { name: '类目管理' }).click()
  await page.getByRole('button', { name: '新增类目', exact: true }).click()
  await page.getByLabel('分类名称', { exact: true }).fill(rootName)
  await page.getByLabel('描述', { exact: true }).fill('浏览器真实维护的大类')
  await page.getByRole('button', { name: '保存类目' }).click()
  await expect(page.getByRole('heading', { name: rootName, exact: true })).toBeVisible()
  await page.getByRole('button', { name: '新增子类', exact: true }).click()
  await page.getByLabel('分类名称', { exact: true }).fill(childName)
  await page.getByRole('button', { name: '保存类目' }).click()
  await expect(page.getByRole('heading', { name: childName, exact: true })).toBeVisible()
  await page.getByRole('button', { name: '编辑类目', exact: true }).click()
  await page.getByLabel('分类名称', { exact: true }).fill(childName + '·实验室')
  await page.getByRole('button', { name: '保存类目' }).click()
  await expect(page.getByRole('heading', { name: childName + '·实验室', exact: true })).toBeVisible()
  await capture(page, 'sprint02-categories-' + info.project.name)
  await page.getByRole('tab', { name: '材料档案' }).click()
  await page.getByRole('button', { name: '新增材料', exact: true }).click()
  await page.getByLabel('材料名称', { exact: true }).fill(materialName)
  await chooseTree(page, '所属类目', childName + '·实验室')
  await page.getByLabel('品牌', { exact: true }).fill('国巨')
  await page.getByLabel('型号', { exact: true }).fill('MFR-25')
  await page.getByLabel('规格参数', { exact: true }).fill('10kΩ，±1%，0.25W')
  await page.getByLabel('单位', { exact: true }).fill('个')
  await page.getByLabel('单价', { exact: true }).fill('0.1234')
  await page.getByLabel('安全库存', { exact: true }).fill('100')
  await page.getByLabel('备注说明', { exact: true }).fill('浏览器验收档案，未入库。')
  const createdResponse = page.waitForResponse(r => r.url().endsWith('/api/materials') && r.request().method() === 'POST')
  await page.getByRole('button', { name: '保存材料', exact: true }).click()
  const created = (await (await createdResponse).json()).data as Material
  expect(created.materialCode).toMatch(/^MAT-\d{10}$/)
  expect(created.unitPrice).toBe('0.1234')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('searchbox', { name: '搜索名称或编码' }).fill(created.materialCode)
  const row = page.getByTestId('material-row-' + created.id)
  await expect(row).toContainText(materialName)
  await expect(row).toContainText('CNY 0.1234')
  await expect(page.getByText('共 1 份档案', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '查看 ' + materialName, exact: true }).click()
  await expect(page.getByRole('dialog').getByText('10kΩ，±1%，0.25W', { exact: true })).toBeVisible()
  await capture(page, 'sprint02-detail-' + info.project.name)
  await page.getByRole('button', { name: '关闭详情' }).click()
  await page.getByRole('button', { name: '编辑 ' + materialName, exact: true }).click()
  await page.getByLabel('材料名称', { exact: true }).fill(materialName + '（已更新）')
  await page.getByLabel('单价', { exact: true }).fill('0.2345')
  await page.getByRole('button', { name: '保存材料', exact: true }).click()
  await expect(row).toContainText('（已更新）')
  await expect(row).toContainText('CNY 0.2345')
  await page.reload()
  await page.getByRole('searchbox', { name: '搜索名称或编码' }).fill(created.materialCode)
  await expect(row).toContainText('（已更新）')
  await row.getByRole('button', { name: '停用', exact: true }).click()
  await expect(row.locator('.ant-tag')).toHaveText('停用')
  await chooseSelect(page, '筛选状态', '停用')
  await expect(row).toBeVisible()
  await chooseSelect(page, '筛选状态', '启用')
  await expect(page.getByText('没有符合条件的材料', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '清除筛选', exact: true }).click()
  await page.getByRole('searchbox', { name: '搜索名称或编码' }).fill(created.materialCode)
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '归档', exact: true }).click()
  await page.getByRole('button', { name: '确认归档', exact: true }).click()
  await expect(row.locator('.ant-tag')).toHaveText('已归档')
  await expect(row.getByRole('button', { name: /^编辑 / })).toHaveCount(0)
  await page.getByRole('tab', { name: '类目管理' }).click()
  await expect(page.getByRole('button', { name: '删除类目', exact: true })).toBeDisabled()
})
test('administrator can delete an empty category from the interface', async ({ page }, info) => {
  const name = '临时空类目-' + info.project.name + '-' + Date.now()
  await login(page)
  await page.getByRole('tab', { name: '类目管理' }).click()
  await page.getByRole('button', { name: '新增类目', exact: true }).click()
  await page.getByLabel('分类名称', { exact: true }).fill(name)
  await page.getByRole('button', { name: '保存类目' }).click()
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
  await page.getByRole('button', { name: '删除类目', exact: true }).click()
  await page.getByRole('button', { name: '删除类目', exact: true }).last().click()
  await expect(page.getByRole('heading', { name, exact: true })).toHaveCount(0)
  await page.reload()
  await expect(page.getByText(name, { exact: true })).toHaveCount(0)
})
test('student has read-only UI and backend rejects attempted writes', async ({ page, request }, info) => {
  const f = await fixture(request, 'student-' + info.project.name + '-' + Date.now())
  const response = await request.post('/api/materials', { headers: f.headers, data: f.body })
  expect(response.status()).toBe(200)
  const item = (await response.json()).data as Material
  await login(page, 'student')
  await expect(page.getByRole('button', { name: '新增材料', exact: true })).toHaveCount(0)
  await page.getByRole('searchbox', { name: '搜索名称或编码' }).fill(item.materialCode)
  const row = page.getByTestId('material-row-' + item.id)
  await expect(row).toBeVisible()
  await expect(row.getByRole('button', { name: /^编辑 / })).toHaveCount(0)
  const token = await page.evaluate(() => sessionStorage.getItem('caizhitong.session'))
  const forbidden = await request.put('/api/materials/' + item.id, { headers: { Authorization: 'Bearer ' + token }, data: { ...f.body, name: '越权修改' } })
  expect(forbidden.status()).toBe(403)
  await page.getByRole('button', { name: '查看 ' + f.body.name, exact: true }).click()
  await expect(page.getByRole('button', { name: '编辑档案', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '关闭详情' }).click()
  await page.getByRole('tab', { name: '类目管理' }).click()
  await expect(page.getByRole('button', { name: '新增类目', exact: true })).toHaveCount(0)
})
test('lab technician can edit a material but cannot archive or maintain categories', async ({ page, request }, info) => {
  const f = await fixture(request, 'lab-' + info.project.name + '-' + Date.now())
  const response = await request.post('/api/materials', { headers: f.headers, data: f.body })
  expect(response.status()).toBe(200)
  const item = (await response.json()).data as Material
  await login(page, 'lab')
  await expect(page.getByRole('button', { name: '新增材料', exact: true })).toBeVisible()
  await page.getByRole('searchbox', { name: '搜索名称或编码' }).fill(item.materialCode)
  const row = page.getByTestId('material-row-' + item.id)
  await expect(row).toBeVisible()
  await expect(row.getByRole('button', { name: '归档', exact: true })).toHaveCount(0)
  await row.getByRole('button', { name: '编辑 ' + item.name, exact: true }).click()
  await page.getByLabel('备注说明', { exact: true }).fill('实验员在浏览器中更新了档案')
  await page.getByRole('button', { name: '保存材料', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('tab', { name: '类目管理' }).click()
  await expect(page.getByRole('button', { name: '新增类目', exact: true })).toHaveCount(0)
})
test('search, category filtering, pagination and sorting use the server', async ({ page, request }, info) => {
  test.setTimeout(60000)
  const tag = '分页验收-' + (info.project.name === 'desktop' ? 'D' : 'M') + Date.now().toString(36).slice(-6)
  const f = await fixture(request, tag)
  for (let i = 0; i < 23; i++) {
    const response = await request.post('/api/materials', { headers: f.headers, data: { ...f.body, name: tag + '-' + String(i).padStart(2, '0') } })
    expect(response.status()).toBe(200)
  }
  await login(page)
  await page.getByRole('searchbox', { name: '搜索名称或编码' }).fill(tag)
  await expect(page.locator('.ant-table-tbody tr[data-row-key]')).toHaveCount(20)
  await expect(page.getByText('共 23 份档案', { exact: true })).toBeVisible()
  await page.locator('.ant-pagination-options .ant-select').click()
  await page.locator('.ant-select-dropdown:visible').getByText('10 条/页', { exact: true }).click()
  await expect(page.locator('.ant-table-tbody tr[data-row-key]')).toHaveCount(10)
  const responsePromise = page.waitForResponse(r => r.url().includes('/api/materials?') && r.url().includes('page=2'))
  await page.locator('.ant-pagination-next button').click()
  const secondPage = (await (await responsePromise).json()).data
  expect(secondPage.records).toHaveLength(10)
  await expect(page.locator('.ant-pagination-item-active')).toHaveText('2')
  await chooseSelect(page, '创建时间排序', '最早创建')
  await expect(page.locator('.ant-pagination-item-active')).toHaveText('1')
  await expect(page.locator('.material-name-link strong').first()).toContainText(tag + '-00')
  await page.locator('.material-taxonomy').getByText('浏览器分类-' + tag, { exact: true }).click()
  await expect(page.getByText('共 23 份档案', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await capture(page, 'sprint02-materials-' + info.project.name)
})

