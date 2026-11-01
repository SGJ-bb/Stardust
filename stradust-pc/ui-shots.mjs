/**
 * UI 巡检截图脚本：注入 mock LLM 与聊天数据，截关键页面（桌面 + 移动视口）
 */
import { chromium } from "playwright";
import fs from "node:fs";
import { createServer } from "vite";

// 脚本内起 dev server（避免依赖外部进程）
const server = await createServer({ configFile: "vite.config.ts", server: { port: 5199, strictPort: true } });
await server.listen();
const BASE = "http://localhost:5199";
const OUT = "ui-review";
fs.mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ executablePath: "F:/stradust/_pw_browsers/chromium-1234/chrome-win64/chrome.exe" });

function sseBody(text) {
  let body = "";
  const push = (t) => { body += `data: ${JSON.stringify({ choices: [{ delta: { content: t }, index: 0, finish_reason: null }] })}\n\n`; };
  push(text.slice(0, 8));
  push(text.slice(8));
  body += "data: [DONE]\n\n";
  return body;
}

const REPLIES = [
  "主人你回来啦！今天有没有想我呀～[[emotion:happy]]",
  "外面的雨下得好大，幸好有你陪我说话。[[emotion:comfort]]",
  "诶嘿嘿，被你看穿了…我才没有一直盯着聊天框等你呢！[[emotion:shy]]",
];
let replyIdx = 0;

async function setup(page) {
  await page.route("https://mock-llm.test/**", async (route) => {
    const reply = REPLIES[replyIdx++ % REPLIES.length];
    await route.fulfill({ status: 200, contentType: "text/event-stream", body: sseBody(reply) });
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
    localStorage.setItem(key, JSON.stringify(state));
  });
}

async function seedChat(page) {
  // 建角色
  await page.goto(BASE + "/persona/new", { waitUntil: "networkidle" });
  const nameInput = page.locator('input[placeholder="角色名称"]');
  if (await nameInput.count() && await nameInput.first().inputValue().catch(() => "") === "") {
    await nameInput.first().fill("星尘");
    await page.locator('textarea[placeholder="性格特征、说话风格、行为习惯等"]').first().fill("一只异色瞳黑猫，傲娇但黏人，喜欢用「诶」「啦」语气词").catch(() => undefined);
    await page.locator('button:has-text("保存")').first().click().catch(() => undefined);
    await page.waitForTimeout(1200);
  }
  await page.goto(BASE + "/chat", { waitUntil: "networkidle" });
  await page.waitForTimeout(800);
  const ta = page.locator("textarea").first();
  if (!(await ta.count())) return;
  for (const msg of ["今天工作好累啊", "你怎么知道我在想你", "晚安，明天见"]) {
    await ta.click();
    await ta.fill(msg);
    await ta.press("Enter");
    await page.waitForTimeout(2500);
  }
}

const shots = [
  ["home", "/", null],
  ["chat", "/chat", seedChat],
  ["diary", "/diary", null],
  ["moments", "/moments", null],
  ["settings", "/settings", null],
  ["skin-shop", "/skin-shop", null],
];

for (const [tag, path, seed] of shots) {
  for (const [vpName, viewport] of [["desktop", { width: 1440, height: 900 }], ["mobile", { width: 390, height: 844 }]]) {
    const page = await browser.newPage({ viewport });
    await setup(page);
    if (seed) await seed(page);
    await page.goto(BASE + path, { waitUntil: "networkidle" });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: `${OUT}/${tag}-${vpName}.png`, fullPage: false });
    await page.close();
  }
}

await browser.close();
await server.close();
console.log("screenshots done:", fs.readdirSync(OUT).join(", "));
