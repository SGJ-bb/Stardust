/**
 * 浏览器模式端到端冒烟测试（Playwright）
 * 用法: node smoke-test.mjs
 * 前提: vite dev server 运行在 5173
 */
import { chromium } from "playwright";

const BASE = "http://localhost:5173";
const MOCK_ORIGIN = "https://mock-llm.test";

const results = [];
let passed = 0, failed = 0;
function check(name, ok, extra = "") {
  results.push(`${ok ? "PASS" : "FAIL"}  ${name}${extra ? " — " + extra : ""}`);
  if (ok) passed++; else failed++;
}

// OpenAI 兼容 SSE mock（带 [[emotion]] 协议 + 非流式兜底）
function sseBody(text) {
  const pieces = [text.slice(0, 6), text.slice(6)];
  let out = "";
  for (const p of pieces) {
    out += `data: ${JSON.stringify({ choices: [{ delta: { content: p }, index: 0, finish_reason: null }] })}\n\n`;
  }
  out += `data: ${JSON.stringify({ choices: [{ delta: {}, index: 0, finish_reason: "stop" }] })}\n\n`;
  out += "data: [DONE]\n\n";
  return out;
}

const browser = await chromium.launch({ executablePath: "F:/stradust/_pw_browsers/chromium-1234/chrome-win64/chrome.exe" });
const page = await browser.newPage();

const pageErrors = [];
page.on("pageerror", (e) => pageErrors.push(String(e)));
page.on("console", (m) => { if (process.env.SMOKE_VERBOSE) console.log("PAGE-CONSOLE:", m.type(), m.text().slice(0, 200)); });
page.on("request", (r) => { if (process.env.SMOKE_VERBOSE && r.url().includes("test")) console.log("REQ:", r.method(), r.url()); });

// mock LLM 路由拦截
await page.route(`${MOCK_ORIGIN}/**`, async (route) => {
  const url = route.request().url();
  const body = route.request().postDataJSON?.() ?? {};
  const hasStream = true; // 统一走流式（非流式帧格式也兼容）
void hasStream;
  if (hasStream) {
    await route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body: sseBody("很高兴见到你呀～今天也要开心哦[[emotion:happy]]"),
    });
  } else if (String(url).includes("/api/tags")) {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ models: [{ name: "qwen2.5:7b", size: 1000 }] }) });
  } else {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ choices: [{ message: { content: "你好呀～[[emotion:happy]]" }, finish_reason: "stop" }] }) });
  }
});

// 预置：跳过首次激活对话框与入场动画
await page.addInitScript(() => {
  localStorage.setItem("stradust-activation", JSON.stringify({ state: { isFirstActivated: true, isPremiumUnlocked: false }, version: 0 }));
});
await page.goto(BASE, { waitUntil: "networkidle" });

// ── 1. 基础渲染 ──
check("首页渲染", await page.locator("text=星尘").first().isVisible().catch(() => false) || (await page.content()).includes("聊天"));

// ── 2. 注入 LLM 配置（app 已落盘完整 settings 后合并，避免破坏默认结构） ──
const injected = await page.evaluate(async ([origin]) => {
  const key = "stradust-settings";
  const raw = localStorage.getItem(key);
  if (!raw) return false;
  const state = JSON.parse(raw);
  if (!state?.state?.settings?.appearance) return false;
  state.state.settings.providers = [{
    id: "mock-1", name: "MockLLM", provider: "openai", apiKey: "sk-test",
    baseUrl: `${origin}/v1`, modelId: "mock-model",
    params: { temperature: 0.7, topP: 0.9, maxTokens: 500, presencePenalty: 0, frequencyPenalty: 0, stopSequences: [] },
    isDefault: true,
  }];
  state.state.settings.activeProviderId = "mock-1";
  localStorage.setItem(key, JSON.stringify(state));
  return true;
}, [MOCK_ORIGIN]);
check("LLM 配置注入", injected);
if (injected) await page.reload({ waitUntil: "networkidle" });

// ── 3. 创建角色（如没有） ──
await page.goto(`${BASE}/persona/new`, { waitUntil: "networkidle" });
const nameInput = page.locator('input[placeholder="角色名称"]');
if (await nameInput.isVisible().catch(() => false)) {
  await nameInput.fill("测试星尘");
  await page.locator('textarea[placeholder="性格特征、说话风格、行为习惯等"]').fill("温柔可爱，喜欢撒娇").catch(() => undefined);
  const saveBtn = page.locator('button:has-text("保存")').first();
  await saveBtn.waitFor({ state: "visible", timeout: 5000 }).catch(() => undefined);
  if (await saveBtn.isEnabled().catch(() => false)) {
    await saveBtn.click();
    await page.waitForTimeout(1500);
  }
}
await page.goto(`${BASE}/`, { waitUntil: "networkidle" });
const personaVisible = (await page.content()).includes("测试星尘");
check("角色创建并持久化", personaVisible);

// ── 4. 聊天主链路（SSE 流式 + emotion 协议剥离 + 落库） ──
await page.goto(`${BASE}/chat`, { waitUntil: "networkidle" });
await page.waitForTimeout(1200);
const chatInput = page.locator("textarea").first();
await chatInput.waitFor({ timeout: 8000 }).catch(() => undefined);
await chatInput.click().catch(() => undefined);
await chatInput.fill("你好呀");
await chatInput.press("Enter");
await page.waitForTimeout(3000);
const midText = await page.locator("body").innerText();
console.log("3s AFTER ENTER:", midText.slice(-400).split(String.fromCharCode(10)).join(" | "));
const aiBubble = await page.waitForSelector("text=今天也要开心", { timeout: 20000 }).then(() => true).catch(() => false);
if (!aiBubble) {
  const dbgText = await page.locator("body").innerText();
  console.log("CHAT DEBUG BODY:", dbgText.slice(0, 500).split(String.fromCharCode(10)).join(" | "));
}
check("LLM 流式回复到达", aiBubble);
const bodyText = await page.locator("body").innerText();
check("[[emotion]] 标记未泄漏到界面", !bodyText.includes("[[emotion"));

// 刷新验证 IndexedDB 历史
await page.reload({ waitUntil: "networkidle" });
await page.waitForTimeout(800);
const historyKept = (await page.locator("body").innerText()).includes("很高兴见到你");
check("聊天历史从 IndexedDB 恢复", historyKept);

// ── 5. 各功能页渲染（无崩溃） ──
const routes = [
  ["/diary", "日记"], ["/moments", "朋友圈"], ["/achievement", "成就"],
  ["/calendar", "日历"], ["/sticker", "表情"], ["/skin-shop", "皮肤"],
  ["/model-manager", "模型"], ["/alarm", "闹钟"], ["/bedtime-radio", "电台"],
  ["/album", "相册"], ["/capsule", "胶囊"], ["/chat-history", "记录"],
  ["/activation", "激活"], ["/local-model", "本地模型"], ["/memory", "记忆"],
  ["/settings", "设置"], ["/favorites", "收藏"], ["/nicknames", "昵称"],
  ["/ilink", "微信互联"], ["/group-chat", "群聊"], ["/world", "虚拟世界"],
];
for (const [route, label] of routes) {
  await page.goto(`${BASE}${route}`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1000);
  const content = await page.content();
  const crashed = content.includes("页面出错") || content.includes("Something went wrong") || content.includes("发生了错误");
  check(`页面可渲染: ${label}(${route})`, !crashed && content.length > 500, crashed ? "渲染崩溃" : "");
}

// ── 6. 收藏/签到等交互（可用 DOM 直接调 store 的走真实 UI 太脆，抽查关键写库路径） ──
await page.goto(`${BASE}/chat`, { waitUntil: "networkidle" });
const wroteDb = await page.evaluate(async () => {
  // 直接测引擎写读（等价性验证）
  const { getAll, put } = await import("/src/lib/db.ts");
  await put("checkins", { id: "self", personaId: "x", streak: 1, totalDays: 1, lastCheckIn: new Date().toISOString(), history: [] });
  const all = await getAll("checkins");
  return all.some((r) => r.id === "self");
});
check("IndexedDB 写入/读取", wroteDb);

// ── 7. 设置页引擎节/新功能存在 ──
await page.goto(`${BASE}/settings?tab=voice`, { waitUntil: "networkidle" });
await page.waitForTimeout(600);
const voiceText = await page.locator("body").innerText();
check("语音设置含 TTS/ASR 引擎选项", /引擎|TTS|语音合成/.test(voiceText));

// ── 8. 控制台错误汇总 ──
check("无未捕获页面异常", pageErrors.length === 0, pageErrors.slice(0, 3).join(" | "));

await page.screenshot({ path: "test-screenshots/smoke-final.png", fullPage: true });
await browser.close();

console.log("\n════════ 冒烟测试结果 ════════");
for (const r of results) console.log(r);
console.log(`\n通过 ${passed} / ${passed + failed}`);
process.exit(failed > 0 ? 1 : 0);
