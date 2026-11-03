import { test, expect } from "@playwright/test";
import type { APIRequestContext, Page } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { resolve } from "node:path";
test.skip(process.env.E2E_ISOLATED !== "1", "归还报废测试仅写隔离数据库");
async function login(page: Page, username = "admin") {
  await page.goto("/login");
  await page.getByLabel("用户名", { exact: true }).fill(username);
  await page
    .getByLabel("密码", { exact: true })
    .fill(
      process.env[
        username === "student" ? "DEMO_STUDENT_PASSWORD" : "DEMO_ADMIN_PASSWORD"
      ]!,
    );
  await page.getByRole("button", { name: "登录工作空间" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}
async function fixture(request: APIRequestContext, tag: string) {
  async function auth(username: string, password: string) {
    const r = await request.post("/api/auth/login", {
      data: { username, password },
    });
    expect(r.ok()).toBeTruthy();
    return { Authorization: "Bearer " + (await r.json()).data.accessToken };
  }
  const headers = await auth("admin", process.env.DEMO_ADMIN_PASSWORD!),
    student = await auth("student", process.env.DEMO_STUDENT_PASSWORD!);
  async function post(path: string, data: object, token = headers) {
    const r = await request.post(path, { headers: token, data });
    expect(r.status(), await r.text()).toBe(200);
    return (await r.json()).data;
  }
  const category = await post("/api/material-categories", {
      name: "闭环验收-" + tag,
    }),
    material = await post("/api/materials", {
      categoryId: category.id,
      name: "闭环电阻-" + tag,
      spec: "10kΩ ±1%",
      unit: "个",
      unitPrice: "0.1234",
      safeStock: "100",
      status: "ACTIVE",
    }),
    inbound = await post("/api/inbound-orders", {
      idempotencyKey: crypto.randomUUID(),
      lines: [{ itemId: material.id, quantity: "100" }],
    }),
    batch = inbound.lines[0],
    issue = await post(
      "/api/issues",
      {
        idempotencyKey: crypto.randomUUID(),
        projectNo: "E2E-" + tag,
        usage: "课程电路实验",
        course: "电子技术课程",
        lines: [{ batchId: batch.id, quantity: "20" }],
      },
      student,
    );
  return { headers, student, material, batch, issue, post };
}
async function choose(page: Page, label: string, text: string) {
  const input = page.getByLabel(label, { exact: true });
  await input
    .locator(
      'xpath=ancestor::div[contains(concat(" ",normalize-space(@class)," ")," ant-select ")][1]',
    )
    .click();
  await page
    .locator(".ant-select-dropdown:visible")
    .getByText(text, { exact: true })
    .click();
}
async function capture(page: Page, file: string) {
  const dir = resolve(import.meta.dirname, "../../docs/screenshots");
  mkdirSync(dir, { recursive: true });
  await expect(page.locator('.ant-message-notice')).toHaveCount(0, { timeout: 6000 });
  await page.screenshot({
    path: resolve(dir, file + ".png"),
    animations: "disabled",
  });
}
test("staff returns partially, reloads persistent history and sees original batch increase", async ({
  page,
  request,
}, info) => {
  test.setTimeout(90000);
  const tag = info.project.name + Date.now().toString(36),
    f = await fixture(request, tag);
  await login(page);
  await page.goto("/returns");
  await page.getByRole("button", { name: "登记归还", exact: true }).click();
  await page
    .getByLabel("查找原领用单", { exact: true })
    .fill(f.issue.order.issueNo);
  await page.getByLabel("查找原领用单", { exact: true }).press("Enter");
  await choose(
    page,
    "选择原领用单",
    `${f.issue.order.issueNo} · ${f.issue.order.recipientName} · ${f.material.name}`,
  );
  await expect(page.getByText("可归还", { exact: false })).toBeVisible();
  await page.getByLabel("本次归还数量", { exact: true }).fill("5");
  await page.getByLabel("归还备注", { exact: true }).fill("完好退回原批次");
  await capture(page, "sprint05-return-form-" + info.project.name);
  const saved = page.waitForResponse(
    (r) => r.url().endsWith("/api/returns") && r.request().method() === "POST",
  );
  await page.getByRole("button", { name: "确认归还并入库" }).click();
  const d = (await (await saved).json()).data;
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.reload();
  await page.getByLabel("搜索流转记录").fill(d.order.returnNo);
  await page.getByLabel("搜索流转记录").press("Enter");
  await page
    .getByRole("button", { name: d.order.returnNo, exact: true })
    .click();
  await expect(page.getByRole("dialog")).toContainText("本次归还 5 个");
  await capture(page, "sprint05-return-history-" + info.project.name);
  const b = await request.get("/api/stock-batches/" + f.batch.batchCode, {
    headers: f.headers,
  });
  expect((await b.json()).data.quantity).toBe("85.000");
});
test("warehouse scrap uploads a real photo and records immutable stock deduction", async ({
  page,
  request,
}, info) => {
  test.setTimeout(90000);
  const tag = info.project.name + Date.now().toString(36),
    f = await fixture(request, tag);
  await login(page);
  await page.goto("/returns?tab=scrap");
  await page.getByRole("button", { name: "登记报废", exact: true }).click();
  const material = page.getByRole("combobox", { name: "搜索领用材料", exact: true });
  await material.fill(f.material.name);
  await page
    .locator(".ant-select-dropdown:visible")
    .getByText(f.material.name + " · " + f.material.materialCode, {
      exact: true,
    })
    .click();
  await choose(page, "选择报废批次", f.batch.batchCode + " · 80 个");
  await page.getByLabel("报废数量", { exact: true }).fill("3");
  await page
    .getByLabel("报废原因", { exact: true })
    .fill("仓库受潮，核对后报废");
  await page
    .getByLabel("上传损耗照片")
    .setInputFiles({
      name: "loss.png",
      mimeType: "image/png",
      buffer: Buffer.from(
        "iVBORw0KGgoAAAANSUhEUgAAACgAAAAeCAIAAADRv8uKAAAARklEQVR4nO3QMREAIBTD0E/9K2FhQSArNZAujYF3l7XPnUSKqFMYTCT2VxhLHOUVxhJHeYWxxFFeYSxxlFcYSxzlFcaKrX7JhgJJwazvrAAAAABJRU5ErkJggg==",
        "base64",
      ),
    });
  await expect(
    page.getByText("已保存：loss.png", { exact: true }),
  ).toBeVisible();
  const saved = page.waitForResponse(
    (r) => r.url().endsWith("/api/scraps") && r.request().method() === "POST",
  );
  await page.getByRole("button", { name: "确认报废并扣库" }).click();
  const d = (await (await saved).json()).data;
  await page.getByRole("button", { name: d.scrapNo, exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("仓库受潮，核对后报废");
  await expect(page.getByAltText("仓库损耗照片")).toBeVisible();
  await capture(page, "sprint05-scrap-" + info.project.name);
  const b = await request.get("/api/stock-batches/" + f.batch.batchCode, {
    headers: f.headers,
  });
  expect((await b.json()).data.quantity).toBe("77.000");
});
test("alerts are real, can be acknowledged and honestly show unconfigured email", async ({
  page,
  request,
}, info) => {
  test.setTimeout(60000);
  const f = await fixture(request, info.project.name + Date.now().toString(36));
  await login(page);
  await page.goto("/alerts");
  await page.getByLabel("搜索预警材料").fill(f.material.materialCode);
  await page.getByLabel("搜索预警材料").press("Enter");
  const filtered = page.waitForResponse((r) => {
    const url = new URL(r.url());
    return url.pathname === "/api/alerts" &&
      url.searchParams.get("keyword") === f.material.materialCode &&
      url.searchParams.get("status") === "ACTIVE" && r.status() === 200;
  });
  await choose(page, "预警处理状态", "待处理");
  await filtered;
  await expect(page.getByText(f.material.name, { exact: true })).toHaveCount(1);
  await expect(page.getByText(f.material.name, { exact: true })).toBeVisible();
  await page
    .getByRole("button", { name: "查看 / 处理", exact: true })
    .first()
    .click();
  await expect(page.getByRole("dialog")).toContainText(
    "站内预警正常可用，邮件尚未发送",
  );
  await page
    .getByLabel("处理备注", { exact: true })
    .fill("已核对，等待采购补充");
  await page.getByRole("button", { name: "确认预警并保存备注" }).click();
  await expect(
    page.getByRole("dialog").getByText("已确认", { exact: true }),
  ).toBeVisible();
  await capture(page, "sprint05-alert-" + info.project.name);
});
test("report preview and all six authenticated downloads use actual stock history", async ({
  page,
  request,
}, info) => {
  test.setTimeout(180000);
  const tag = info.project.name + Date.now().toString(36),
    f = await fixture(request, tag);
  await f.post("/api/returns", {
    idempotencyKey: crypto.randomUUID(),
    issueOrderId: f.issue.order.id,
    lines: [{ issueOrderLineId: f.issue.lines[0].id, quantity: "5" }],
  });
  await login(page);
  await page.goto("/reports");
  await page
    .getByLabel("材料名称或编码", { exact: true })
    .fill(f.material.materialCode);
  await page.getByRole("button", { name: "查询报表", exact: true }).click();
  await expect(page.locator(".report-totals")).toContainText("净消耗 15");
  await expect(page.locator(".report-totals")).toContainText("1.851");
  for (const tab of ["学期耗材", "经费对应", "个人领用"]) {
    await page.getByRole("tab", { name: tab, exact: true }).click();
    if (tab === "个人领用") {
      const r = await request.get("/api/reports/users?keyword=student", {
          headers: f.headers,
        }),
        u = (await r.json()).data.find(
          (p: { username: string }) => p.username === "student",
        );
      await choose(page, "报表领用人", u.displayName + " · student");
      await page.getByRole("button", { name: "查询报表", exact: true }).click();
    }
    await expect(page.locator(".report-totals")).toContainText("净消耗 15");
    for (const format of ["Excel", "PDF"]) {
      const download = page.waitForEvent("download");
      await page
        .getByRole("button", { name: "导出 " + format, exact: true })
        .click();
      const d = await download;
      expect(d.suggestedFilename()).toMatch(
        format === "Excel" ? /\.xlsx$/ : /\.pdf$/,
      );
      expect(await d.failure()).toBeNull();
    }
  }
  await capture(page, "sprint05-reports-" + info.project.name);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
});
test("student has personal returns and report access but cannot mutate or view funding", async ({
  page,
  request,
}, info) => {
  const f = await fixture(request, info.project.name + Date.now().toString(36));
  await f.post("/api/returns", {
    idempotencyKey: crypto.randomUUID(),
    issueOrderId: f.issue.order.id,
    lines: [{ issueOrderLineId: f.issue.lines[0].id, quantity: "1" }],
  });
  await login(page, "student");
  await page.goto("/returns");
  await expect(
    page.getByRole("button", { name: "登记归还", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("tab", { name: "我的归还", exact: true }),
  ).toBeVisible();
  await page.goto("/reports");
  await expect(
    page.getByRole("tab", { name: "个人领用", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("tab", { name: "经费对应", exact: true }),
  ).toHaveCount(0);
  const result = await request.get("/api/reports/funding", {
    headers: f.student,
  });
  expect(result.status()).toBe(403);
});
test("a lost return response restores the same UUID and does not add stock twice", async ({
  page,
  request,
}, info) => {
  test.setTimeout(60000);
  const f = await fixture(request, info.project.name + Date.now().toString(36));
  await login(page);
  const me = await page.evaluate(async () => {
    const token = sessionStorage.getItem("caizhitong.session");
    return (
      await (
        await fetch("/api/auth/me", {
          headers: { Authorization: "Bearer " + token },
        })
      ).json()
    ).data;
  });
  const pending = {
    path: "/api/returns",
    body: {
      idempotencyKey: crypto.randomUUID(),
      issueOrderId: f.issue.order.id,
      lines: [{ issueOrderLineId: f.issue.lines[0].id, quantity: "2" }],
    },
  };
  await page.evaluate(
    ({ id, pending }) =>
      sessionStorage.setItem(
        "caizhitong.pending-lifecycle:" + id,
        JSON.stringify(pending),
      ),
    { id: me.id, pending },
  );
  await page.goto("/returns");
  await page.route("**/api/returns", async (route) => {
    if (route.request().method() === "POST") {
      await route.fetch();
      await route.abort();
    } else await route.continue();
  });
  await page
    .getByRole("button", { name: "安全重试原请求", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "安全重试原请求", exact: true }),
  ).toBeEnabled();
  await expect(
    page.getByText("无法连接服务，请检查后端是否启动", { exact: true }),
  ).toBeVisible();
  await page.unroute("**/api/returns");
  await page.reload();
  await page
    .getByRole("button", { name: "安全重试原请求", exact: true })
    .click();
  await expect(
    page.getByText("有一笔结果待确认的库存操作", { exact: true }),
  ).toHaveCount(0);
  const b = await request.get("/api/stock-batches/" + f.batch.batchCode, {
    headers: f.headers,
  });
  expect((await b.json()).data.quantity).toBe("82.000");
});
