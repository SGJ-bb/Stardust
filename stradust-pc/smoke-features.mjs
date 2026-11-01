/**
 * 新功能专项冒烟：工具权限确认弹窗 + 消息重新生成/变体切换 + 密度切换
 */
import { chromium } from "playwright";
import { createServer } from "vite";

const server = await createServer({ configFile: "vite.config.ts", server: { port: 5201, strictPort: true } });
await server.listen();
const BASE = "http://localhost:5201";

const results = [];
let passed = 0, failed = 0;
const check = (name, ok, extra = "") => { results.push(`${ok ? "PASS" : "FAIL"}  ${name}${extra ? " — " + extra : ""}`); ok ? passed++ : failed++; };

const sse = (frames) => frames.map((f) => `data: ${JSON.stringify(f)}\n\n`).join("") + "data: [DONE]\n\n";

const browser = await chromium.launch({ executablePath: "F:/stradust/_pw_browsers/chromium-1234/chrome-win64/chrome.exe" });
const page = await browser.newPage({ viewport: { width: 1280, height: 860 } });
page.on("pageerror", (e) => console.log("PAGEERROR:", String(e).slice(0, 200)));

let turn = 0;
await page.route("https://mock-llm.test/**", async (route) => {
  const req = route.request();
  const body = req.postDataJSON() ?? {};
  const lastUser = [...(body.messages ?? [])].reverse().find((m) => m.role === "user");
  const userText = typeof lastUser?.content === "string" ? lastUser.content : "";
  turn++;

  // 第二轮起：若请求里有 tool 结果（role=tool 消息），说明确认已回，直接给最终回复
  const hasToolResult = (body.messages ?? []).some((m) => m.role === "tool");
  if (userText.includes("现在几点") && !hasToolResult && body.tools) {
    // 先流一点文字再发起 tool_call（模拟真实分片）
    await route.fulfill({
      status: 200, contentType: "text/event-stream",
      body: sse([
        { choices: [{ delta: { content: "" }, index: 0, finish_reason: null }] },
        { choices: [{ delta: { tool_calls: [{ index: 0, id: "call-1", function: { name: "get_current_time", arguments: "{}" } }] }, index: 0, finish_reason: null }] },
        { choices: [{ delta: {}, index: 0, finish_reason: "tool_calls" }] },
      ]),
    });
    return;
  }
  await route.fulfill({
    status: 200, contentType: "text/event-stream",
    body: sse([{ choices: [{ delta: { content: hasToolResult ? "现在是下午啦，记得喝水～[[emotion:comfort]]" : `这是第${turn}次生成的回复，每次都不太一样哦[[emotion:happy]]` }, index: 0, finish_reason: null }] }]),
  });
});

await page.addInitScript(() => {
  localStorage.setItem("stradust-activation", JSON.stringify({ state: { isFirstActivated: true, isPremiumUnlocked: false }, version: 0 }));
});
await page.goto(BASE + "/", { waitUntil: "networkidle" });
await page.evaluate(() => {
  const key = "stradust-settings";
  const raw = localStorage.getItem(key);
  if (!raw) return;
  const state = JSON.parse(raw);
  state.state.settings.providers = [{ id: "mock-1", name: "MockLLM", provider: "openai", apiKey: "sk-test", baseUrl: "https://mock-llm.test/v1", modelId: "mock-model", params: { temperature: 0.7, topP: 0.9, maxTokens: 500, presencePenalty: 0, frequencyPenalty: 0, stopSequences: [] }, isDefault: true }];
  state.state.settings.activeProviderId = "mock-1";
  // 把 get_current_time 设为"每次询问"
  state.state.settings.toolPermissions = { get_current_time: "ask" };
  localStorage.setItem(key, JSON.stringify(state));
});
await page.reload({ waitUntil: "networkidle" });

// 建角色
await page.goto(BASE + "/persona/new", { waitUntil: "networkidle" });
await page.locator('input[placeholder="角色名称"]').fill("小测");
await page.locator('button:has-text("保存")').first().click();
await page.waitForTimeout(1000);

// ── 测试 1：工具确认弹窗 ──
await page.goto(BASE + "/chat", { waitUntil: "networkidle" });
await page.waitForTimeout(800);
const ta = page.locator("textarea").first();
await ta.click();
await ta.fill("现在几点了呀");
await ta.press("Enter");
const confirmVisible = await page.waitForSelector("text=AI 想要执行", { timeout: 15000 }).then(() => true).catch(() => false);
check("ask 权限触发确认弹窗", confirmVisible);
if (confirmVisible) {
  check("弹窗显示工具中文名", (await page.locator("text=查看时间").count()) > 0);
  await page.locator('button:text-is("允许")').click();
  const finalReply = await page.waitForSelector("text=记得喝水", { timeout: 15000 }).then(() => true).catch(() => false);
  check("允许后工具执行并续流回复", finalReply);
}

// ── 测试 2：重新生成与变体切换 ──
await ta.click();
await ta.fill("随便聊聊");
await ta.press("Enter");
await page.waitForSelector("text=次生成的回复", { timeout: 15000 }).catch(() => undefined);
await page.waitForTimeout(800);
// hover AI 消息行 → 点 ⋯ → 点重新生成
const aiRow = page.locator(".chat-msg-row", { has: page.locator("text=次生成的回复") }).last();
await aiRow.hover();
const moreBtn = aiRow.locator("button:has(svg.lucide-more-horizontal), button:has(svg)").last();
await moreBtn.click({ timeout: 5000 }).catch(async () => {
  await aiRow.locator('[role="button"], button').first().click().catch(() => undefined);
});
await page.locator('[role="menuitem"]:has-text("重新生成")').click({ timeout: 5000 }).catch((e) => console.log("menu fail:", e.message.slice(0, 80)));
const variantShown = await page.waitForSelector("text=2/2", { timeout: 20000 }).then(() => true).catch(() => false);
check("重新生成产生变体并显示 2/2", variantShown);
if (variantShown) {
  await page.locator('button[aria-label="上一个回复"]').click();
  const back11 = await page.locator("text=1/2").count() > 0;
  check("变体可切回 1/2", back11);
}

// ── 测试 3：密度切换 ──
await page.goto(BASE + "/settings", { waitUntil: "networkidle" });
await page.waitForTimeout(800);
const densityBtn = page.locator('button:has-text("舒适")').first();
if (await densityBtn.count()) {
  await densityBtn.click();
  const attr = await page.evaluate(() => document.documentElement.getAttribute("data-density"));
  check("密度切换写入 data-density", attr === "cozy", `attr=${attr}`);
} else {
  check("密度切换写入 data-density", false, "未找到控件");
}
const bgSlider = await page.locator("text=背景明暗").count();
check("背景明暗控件存在", bgSlider > 0);

// ── 回归：deny 权限 ──
await page.evaluate(() => {
  const state = JSON.parse(localStorage.getItem("stradust-settings") || "{}");
  state.state.settings.toolPermissions = { get_current_time: "deny" };
  localStorage.setItem("stradust-settings", JSON.stringify(state));
});
await page.goto(BASE + "/chat", { waitUntil: "networkidle" });
await page.reload({ waitUntil: "networkidle" });
await page.waitForTimeout(600);
const ta2 = page.locator("textarea").first();
await ta2.click();
await ta2.fill("现在几点了呀");
await ta2.press("Enter");
const noConfirm = await page.waitForTimeout(6000).then(() => page.locator("text=AI 想要执行").count()).catch(() => 1);
check("deny 权限不弹窗直接拒绝", noConfirm === 0);

await page.screenshot({ path: "ui-review/features-smoke.png" });
await browser.close();
await server.close();
console.log("\n════ 功能冒烟 ════");
results.forEach((r) => console.log(r));
console.log(`\n通过 ${passed}/${passed + failed}`);
process.exit(failed ? 1 : 0);
