async page => {
  const errors = [];
  page.on("console", m => { if (m.type() === "error") errors.push(m.text().slice(0, 200)); });
  page.on("pageerror", e => errors.push("PAGEERROR: " + String(e.message).slice(0, 200)));
  const r = {};

  await page.goto("http://localhost:5173/login", { waitUntil: "networkidle" });
  await page.fill("input[placeholder=\"用户名\"]", "testuser01");
  await page.fill("input[placeholder=\"密码\"]", "test123456");
  await page.click("button.submit");
  await page.waitForTimeout(2500);
  r.afterLoginUrl = page.url();

  await page.goto("http://localhost:5173/orders", { waitUntil: "networkidle" });
  await page.waitForTimeout(1200);
  r.commentBtnCount = await page.locator("button:has-text(\"去评价\")").count();

  if (r.commentBtnCount > 0) {
    await page.locator("button:has-text(\"去评价\")").first().click();
    await page.waitForTimeout(2500);
    r.urlAfterClick = page.url();
    r.visibleDialogs = await page.locator(".el-dialog").evaluateAll(els => els.filter(e => e.offsetParent !== null).length);
    const dlg = page.locator(".el-dialog").filter({ hasText: "评价" }).first();
    r.commentDialogVisible = await dlg.isVisible().catch(() => false);
    r.rateCount = await page.locator(".el-dialog .el-rate").count();
  }

  r.consoleErrors = errors;
  return JSON.stringify(r);
}
