import { test, expect } from "@playwright/test";
import type { APIRequestContext, Page } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { resolve } from "node:path";
test.skip(
  process.env.E2E_ISOLATED !== "1",
  "用户和配置写入只允许独立 E2E 数据库",
);
const password = "test-user-password-2026";
async function login(
  page: Page,
  username = "admin",
  secret = process.env.DEMO_ADMIN_PASSWORD!,
) {
  await page.goto("/login");
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(secret);
  await page.getByRole("button", { name: "登录工作空间" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}
async function api(request: APIRequestContext) {
  const auth = await request.post("/api/auth/login", {
      data: { username: "admin", password: process.env.DEMO_ADMIN_PASSWORD },
    }),
    headers = {
      Authorization: "Bearer " + (await auth.json()).data.accessToken,
    };
  async function send(
    method: string,
    path: string,
    body?: object,
    token = headers,
  ) {
    const r = await request.fetch(path, { method, headers: token, data: body });
    expect(r.status(), await r.text()).toBe(200);
    return (await r.json()).data;
  }
  async function create(role: string, tag: string) {
    return send("POST", "/api/users", {
      username: "s06_" + tag,
      credentials: { password },
      profile: {
        displayName: "收尾验收-" + tag,
        email: tag + "@example.test",
        roles: [role],
        enabled: true,
      },
    });
  }
  async function token(username: string, secret = password) {
    const r = await request.post("/api/auth/login", {
      data: { username, password: secret },
    });
    expect(r.status()).toBe(200);
    return { Authorization: "Bearer " + (await r.json()).data.accessToken };
  }
  return { headers, send, create, token };
}
async function choose(page: Page, label: string, value: string) {
  await page
    .getByRole("combobox", { name: label, exact: true })
    .locator(
      'xpath=ancestor::div[contains(concat(" ",normalize-space(@class)," ")," ant-select ")][1]',
    )
    .click();
  await page
    .locator(".ant-select-dropdown:visible")
    .getByText(value, { exact: true })
    .click();
}
async function capture(page: Page, name: string) {
  const dir = resolve(import.meta.dirname, "../../docs/screenshots");
  mkdirSync(dir, { recursive: true });
  await expect(page.locator(".ant-message-notice")).toHaveCount(0, {
    timeout: 6000,
  });
  await page.screenshot({
    path: resolve(dir, name + ".png"),
    animations: "disabled",
  });
}
test("administrator creates a real user, edits contact and role, then verifies old-token permission changes", async ({
  page,
  request,
}, info) => {
  test.setTimeout(90000);
  const tag = info.project.name + Date.now().toString(36),
    username = "s06_" + tag,
    a = await api(request);
  await login(page);
  await page.goto("/users");
  await expect(page.locator(".brand strong").first()).toHaveText("材智通");
  await page.getByRole("button", { name: "新增用户", exact: true }).click();
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page.getByLabel("姓名", { exact: true }).fill("采购验收-" + tag);
  await page.getByLabel("邮箱", { exact: true }).fill(tag + "@example.test");
  await page.getByLabel("初始密码", { exact: true }).fill(password);
  const created = page.waitForResponse(
    (r) => r.url().endsWith("/api/users") && r.request().method() === "POST",
  );
  await page.getByRole("button", { name: "保存用户", exact: true }).click();
  const u = (await (await created).json()).data;
  await expect(page.getByRole("dialog")).toHaveCount(0);
  const old = await a.token(username);
  await page.getByLabel("搜索用户", { exact: true }).fill(username);
  await page.getByLabel("搜索用户", { exact: true }).press("Enter");
  await expect(page.getByRole("button", { name: "编辑", exact: true })).toHaveCount(1);
  await page.getByRole("button", { name: "编辑", exact: true }).click();
  await page.getByLabel("姓名", { exact: true }).fill("已更新采购员-" + tag);
  await choose(page, "分配用户角色", "采购员");
  await page
    .getByRole("dialog")
    .locator(
      '.ant-select-selection-item[title="学生"] .ant-select-selection-item-remove',
    )
    .click();
  await page.getByRole("dialog").getByText("编辑用户", { exact: true }).click();
  await page.getByRole("button", { name: "保存用户", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  const stored = await a.send("GET", "/api/users/" + u.id);
  expect(stored.roles).toEqual(["PURCHASER"]);
  expect(stored.displayName).toBe("已更新采购员-" + tag);
  expect(
    (
      await request.get("/api/workspace/modules/requisitions", { headers: old })
    ).status(),
  ).toBe(403);
  await page.getByRole("button", { name: "详情", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("最近登录");
  await capture(page, "sprint06-users-" + info.project.name);
});
test("disable and password reset revoke JWT sessions through real administrative controls", async ({
  page,
  request,
}, info) => {
  test.setTimeout(90000);
  const a = await api(request),
    u = await a.create("LAB_TECH", info.project.name + Date.now().toString(36)),
    old = await a.token(u.username);
  await login(page);
  await page.goto("/users");
  await page.getByLabel("搜索用户").fill(u.username);
  await page.getByLabel("搜索用户").press("Enter");
  await expect(page.getByRole("button", { name: "编辑", exact: true })).toHaveCount(1);
  await page.getByRole("button", { name: "停用", exact: true }).click();
  await page.getByRole("button", { name: "确认停用", exact: true }).click();
  await expect
    .poll(async () =>
      (await request.get("/api/auth/me", { headers: old })).status(),
    )
    .toBe(401);
  await expect(page.getByRole("button", { name: "启用", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "启用", exact: true }).click();
  await page.getByRole("button", { name: "确认启用", exact: true }).click();
  await expect
    .poll(async () => (await a.send("GET", "/api/users/" + u.id)).enabled)
    .toBe(true);
  const beforeReset = await a.token(u.username);
  await page.getByRole("button", { name: "重置密码", exact: true }).click();
  await page
    .getByLabel("新密码", { exact: true })
    .fill("updated-user-password-2026");
  await page.getByRole("button", { name: "确认重置密码", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(
    (await request.get("/api/auth/me", { headers: beforeReset })).status(),
  ).toBe(401);
  await a.token(u.username, "updated-user-password-2026");
  expect(
    (
      await request.post("/api/auth/login", {
        data: { username: u.username, password },
      })
    ).status(),
  ).toBe(401);
});
test("settings persist brand, notification recipients and report semester dates without exposing secrets", async ({
  page,
  request,
}, info) => {
  test.setTimeout(90000);
  const a = await api(request),
    original = await a.send("GET", "/api/settings"),
    tag = info.project.name + Date.now().toString(36),
    name = "材智通 · 验收" + tag;
  try {
    await login(page);
    await page.goto("/settings");
    await page.getByLabel("系统名称", { exact: true }).fill(name);
    await page
      .getByLabel("系统描述", { exact: true })
      .fill("真实保存的实验室配置");
    const dates = page.locator("#semester .ant-picker input");
    await dates.nth(0).fill(original.academicYear + "-10-01");
    await dates.nth(0).press("Enter");
    await page.getByLabel("系统描述", { exact: true }).click();
    const recipients = page.getByRole("combobox", {
      name: "业务通知收件人",
      exact: true,
    });
    await recipients.fill(tag + "@example.test");
    await recipients.press("Enter");
    await page.getByLabel("系统名称", { exact: true }).click();
    const saved = page.waitForResponse(
      (r) =>
        r.url().endsWith("/api/settings") && r.request().method() === "PUT",
    );
    await page
      .getByRole("button", { name: "保存系统设置", exact: true })
      .click();
    expect((await saved).status()).toBe(200);
    await page.reload();
    await expect(page.getByLabel("系统名称", { exact: true })).toHaveValue(
      name,
    );
    const stored = await a.send("GET", "/api/settings");
    expect(stored.autumnStart).toBe(original.academicYear + "-10-01");
    expect(stored.effectiveRecipients).toContain(tag + "@example.test");
    expect(stored).not.toHaveProperty("password");
    expect(stored).not.toHaveProperty("jwtSecret");
    const brand = page.locator(".brand strong").first();
    await expect(brand).toHaveText(name);
    expect(await brand.evaluate((el) => el.getBoundingClientRect().right <= el.closest(".brand")!.getBoundingClientRect().right + 1)).toBeTruthy();
    await capture(page, "sprint06-settings-" + info.project.name);
    await page.goto("/reports");
    await expect(page.locator(".report-range")).toContainText(
      original.academicYear + "-10-01",
    );
  } finally {
    const current = await a.send("GET", "/api/settings");
    await a.send("PUT", "/api/settings", {
      ...original,
      revision: current.revision,
    });
  }
});
test("dashboard uses real net consumption and every menu has a functional page with private student scope", async ({
  page,
  request,
}, info) => {
  test.setTimeout(120000);
  const a = await api(request),
    tag = info.project.name + Date.now().toString(36),
    u = await a.create("STUDENT", tag),
    student = await a.token(u.username),
    c = await a.send("POST", "/api/material-categories", {
      name: "最终验收分类-" + tag,
    }),
    m = await a.send("POST", "/api/materials", {
      categoryId: c.id,
      name: "仪表盘电阻-" + tag,
      unit: "个",
      unitPrice: "0.1234",
      safeStock: "100",
      status: "ACTIVE",
    }),
    inbound = await a.send("POST", "/api/inbound-orders", {
      idempotencyKey: crypto.randomUUID(),
      lines: [{ itemId: m.id, quantity: "100" }],
    }),
    batch = inbound.lines[0],
    issue = await a.send(
      "POST",
      "/api/issues",
      {
        idempotencyKey: crypto.randomUUID(),
        projectNo: "FINAL-" + tag,
        usage: "真实仪表盘验收",
        lines: [{ batchId: batch.id, quantity: "20" }],
      },
      student,
    );
  await a.send("POST", "/api/returns", {
    idempotencyKey: crypto.randomUUID(),
    issueOrderId: issue.order.id,
    lines: [{ issueOrderLineId: issue.lines[0].id, quantity: "5" }],
  });
  await a.send("POST", "/api/scraps", {
    idempotencyKey: crypto.randomUUID(),
    batchId: batch.id,
    quantity: "3",
    reason: "测试仓库损耗",
  });
  const d = await a.send("GET", "/api/workspace/dashboard");
  expect(
    d.topMaterials.find((r: { materialId: number }) => r.materialId === m.id)
      .netQuantity,
  ).toBe("15.000");
  await login(page);
  await choose(page, "仪表盘统计分组", "CNY · 个");
  await expect(
    page.getByRole("heading", { name: "个人领用排行榜", exact: true }),
  ).toBeVisible();
  await expect(page.locator(".consumption-bars")).toContainText(m.name);
  await capture(page, "sprint06-dashboard-" + info.project.name);
  for (const [path, title] of Object.entries({
    materials: "材料管理",
    inventory: "库存管理",
    inbound: "入库管理",
    requisitions: "领用管理",
    returns: "归还与报废",
    alerts: "库存预警",
    reports: "数据报表",
    users: "用户与权限",
    settings: "系统设置",
  })) {
    await page.goto("/" + path);
    await expect(
      page.getByRole("heading", { name: title, exact: true }).first(),
    ).toBeVisible();
    await expect(page.getByText("开发中", { exact: true })).toHaveCount(0);
  }
  await page.getByRole("button", { name: "账户菜单" }).click();
  await page.getByRole("menuitem", { name: "退出登录" }).click();
  await login(page, u.username, password);
  await expect(page.getByText("仅本人领用", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "个人领用排行榜" }),
  ).toHaveCount(0);
  await expect(page.locator(".monthly-totals")).toContainText("净消耗 15");
  await capture(page, "sprint06-dashboard-personal-" + info.project.name);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
});
