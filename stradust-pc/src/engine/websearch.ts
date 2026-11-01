/**
 * 网页搜索引擎 —— 移植自 Android search/WebSearchEngine.kt（DuckDuckGo html / Bing / Baidu 解析思路照搬）。
 *
 * Web 等价替代说明：
 * 浏览器直接 fetch html.duckduckgo.com / baidu.com 会被 CORS 拦截，故实现三级策略：
 *   (a) 若在 kv 集合配置了搜索 API（key="search-api"：{url,apiKey}），直连 JSON API
 *       （Bing API 协议照搬 Kotlin searchBingApi）；
 *   (b) 走公共 CORS 代理 https://api.allorigins.win/raw?url= 抓取搜索结果 HTML 后
 *       用与 Kotlin 相同的正则解析（此为 Web 端必要的替代手段，非官方接口）；
 *   (c) 均失败时返回明确的错误文案。
 */

import { simpleCompletion } from "./llmClient";
import { kvGet } from "@/lib/db";
import { useSettingsStore } from "@/stores/useSettingsStore";

export interface SearchResult {
  title: string;
  snippet: string;
  link: string;
}

interface SearchApiConfig {
  /** API 完整地址（含或不含查询参数），如 https://api.bing.microsoft.com/v7.0/search */
  url: string;
  apiKey: string;
}

const CORS_PROXY = "https://api.allorigins.win/raw?url=";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

// ═══════════════════════ HTML 解析（照搬 Kotlin 正则） ═══════════════════════

function stripHtml(html: string): string {
  return html
    .replace(/<[^>]*>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&quot;/g, '"')
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .trim();
}

/** 照搬 parseDuckDuckGoResults */
function parseDuckDuckGoResults(html: string): SearchResult[] {
  const results: SearchResult[] = [];
  try {
    const blocks = html.split('class="result"').slice(1).slice(0, 5);
    for (const block of blocks) {
      const titleMatch = block.match(/<a[^>]*class="result__a"[^>]*href="([^"]*)"[^>]*>([\s\S]*?)<\/a>/i);
      const snippetMatch = block.match(/<a[^>]*class="result__snippet"[^>]*>([\s\S]*?)<\/a>/i);
      if (titleMatch) {
        results.push({
          title: stripHtml(titleMatch[2]),
          snippet: snippetMatch ? stripHtml(snippetMatch[1]) : "",
          link: decodeDuckDuckGoUrl(titleMatch[1]),
        });
      }
    }
  } catch { /* ignore */ }
  return results.length ? results : parseBingResults(html);
}

/** DuckDuckGo 跳转链接 //duckduckgo.com/l/?uddg=<encoded> → 原始 URL */
function decodeDuckDuckGoUrl(href: string): string {
  const m = href.match(/[?&]uddg=([^&]+)/);
  if (m) {
    try { return decodeURIComponent(m[1]); } catch { return href; }
  }
  return href.startsWith("//") ? `https:${href}` : href;
}

/** 照搬 parseBingResults */
function parseBingResults(html: string): SearchResult[] {
  const results: SearchResult[] = [];
  try {
    const titlePattern = /<h2[^>]*>[\s\S]*?<a[^>]*href="([^"]*)"[^>]*>([\s\S]*?)<\/a>[\s\S]*?<\/h2>/gi;
    const snippetPattern = /<p[^>]*>([\s\S]*?)<\/p>/gi;
    const titles: RegExpMatchArray[] = [];
    let m: RegExpExecArray | null;
    while ((m = titlePattern.exec(html)) !== null && titles.length < 5) titles.push(m as unknown as RegExpMatchArray);
    const snippets: RegExpExecArray[] = [];
    while ((m = snippetPattern.exec(html)) !== null && snippets.length < 5) snippets.push(m);
    for (let i = 0; i < titles.length; i++) {
      results.push({
        link: titles[i][1],
        title: stripHtml(titles[i][2]),
        snippet: i < snippets.length ? stripHtml(snippets[i][1]) : "",
      });
    }
  } catch { /* ignore */ }
  return results;
}

/** 照搬 parseBaiduResults */
function parseBaiduResults(html: string): SearchResult[] {
  const results: SearchResult[] = [];
  try {
    const blocks = html.split('class="result').slice(1).slice(0, 5);
    for (const block of blocks) {
      const titleMatch =
        block.match(/<h3[^>]*>[\s\S]*?<a[^>]*>([\s\S]*?)<\/a>/i) ??
        block.match(/<a[^>]*>([\s\S]*?)<\/a>/i);
      const linkMatch = block.match(/href="([^"]*)"/i);
      const snippetMatch =
        block.match(/<span[^>]*class="content-right_[^"]*"[^>]*>([\s\S]*?)<\/span>/i) ??
        block.match(/<div[^>]*class="c-abstract"[^>]*>([\s\S]*?)<\/div>/i);
      if (titleMatch) {
        results.push({
          title: stripHtml(titleMatch[1]),
          snippet: snippetMatch ? stripHtml(snippetMatch[1]) : "",
          link: linkMatch?.[1] ?? "",
        });
      }
    }
  } catch { /* ignore */ }
  return results.length ? results : parseBingResults(html);
}

// ═══════════════════════ 抓取（代理 / API） ═══════════════════════

async function fetchViaProxy(targetUrl: string, timeoutMs = 15000): Promise<string | null> {
  try {
    const res = await fetch(CORS_PROXY + encodeURIComponent(targetUrl), { signal: AbortSignal.timeout(timeoutMs) });
    if (!res.ok) return null;
    return await res.text();
  } catch {
    return null;
  }
}

async function searchDuckDuckGo(query: string): Promise<SearchResult[]> {
  const html = await fetchViaProxy(`https://html.duckduckgo.com/html/?q=${encodeURIComponent(query)}`);
  return html ? parseDuckDuckGoResults(html) : [];
}

async function searchBingFallback(query: string): Promise<SearchResult[]> {
  const html = await fetchViaProxy(`https://cn.bing.com/search?q=${encodeURIComponent(query)}`);
  return html ? parseBingResults(html) : [];
}

async function searchBaidu(query: string): Promise<SearchResult[]> {
  const html = await fetchViaProxy(`https://www.baidu.com/s?wd=${encodeURIComponent(query)}`);
  return html ? parseBaiduResults(html) : [];
}

/** 照搬 searchBingApi：Ocp-Apim-Subscription-Key 头 + webPages.value 解析 */
async function searchBingApi(cfg: SearchApiConfig, query: string): Promise<SearchResult[]> {
  try {
    const sep = cfg.url.includes("?") ? "&" : "?";
    const url = `${cfg.url}${sep}q=${encodeURIComponent(query)}&mkt=zh-CN&count=5`;
    const res = await fetch(url, {
      headers: { "Ocp-Apim-Subscription-Key": cfg.apiKey, "User-Agent": UA },
      signal: AbortSignal.timeout(15000),
    });
    if (!res.ok) return [];
    const json = (await res.json()) as Record<string, any>;
    const values = json?.webPages?.value;
    if (!Array.isArray(values)) return [];
    return values.slice(0, 5).map((item: Record<string, unknown>) => ({
      title: String(item?.name ?? ""),
      snippet: String(item?.snippet ?? ""),
      link: String(item?.url ?? ""),
    }));
  } catch {
    return [];
  }
}

// ═══════════════════════ 对外 API ═══════════════════════

function getSearchApiConfig(): Promise<SearchApiConfig | null> {
  return kvGet<SearchApiConfig>("search-api");
}

function currentEngine(): string {
  return useSettingsStore.getState().settings.aiFeatures?.searchProvider ?? "duckduckgo";
}

/**
 * 搜索网页。engine："duckduckgo"(默认)|"bing"|"baidu"；缺省读 settings.aiFeatures.searchProvider。
 * 三级策略：(a) 配置的搜索 API 直连 → (b) CORS 代理抓 HTML → (c) 抛错（调用方捕获出文案）。
 */
export async function searchWeb(query: string, engine?: string): Promise<SearchResult[]> {
  const q = query.trim();
  if (!q) return [];
  const eng = engine ?? currentEngine();

  // (a) 搜索 API 直连
  const apiCfg = await getSearchApiConfig();
  if (apiCfg?.url && apiCfg?.apiKey) {
    const apiResults = await searchBingApi(apiCfg, q);
    if (apiResults.length) return apiResults;
  }

  // (b) CORS 代理抓取解析（引擎顺序对齐 Kotlin：主引擎失败回退 bing）
  let results: SearchResult[] = [];
  if (eng === "baidu") {
    results = await searchBaidu(q);
    if (!results.length) results = await searchDuckDuckGo(q);
  } else if (eng === "bing") {
    results = await searchBingFallback(q);
    if (!results.length) results = await searchDuckDuckGo(q);
  } else {
    results = await searchDuckDuckGo(q);
    if (!results.length) results = await searchBingFallback(q);
  }
  if (results.length) return results;

  // (c) 全部失败
  throw new Error("搜索失败：网络或代理服务不可用，请稍后重试或在设置中配置搜索 API");
}

/** 照搬 searchAndSummarize 的结果格式化提示词结构，交给 LLM 总结 */
const SUMMARIZE_SYSTEM =
  "你是联网搜索助手。根据搜索结果回答用户问题：优先使用标题和摘要中的信息，" +
  "标注信息来源编号；摘要不足时可参考链接与标题判断；如结果无关请如实说明。回答用简洁中文。";

/**
 * 搜索并总结：先 searchWeb 拿结果，再以编号列表（标题/链接/摘要，摘要截 300 字，
 * 与 Kotlin searchAndSummarize 输出格式一致）作为上下文交 LLM 总结。
 */
export async function searchAndSummarize(query: string): Promise<string> {
  let results: SearchResult[];
  try {
    results = await searchWeb(query);
  } catch (e) {
    return (e as Error).message || "没搜到相关内容，换个关键词试试";
  }
  if (!results.length) return "没搜到相关内容，换个关键词试试";

  const sb: string[] = ["搜索结果（最新、最相关的内容）："];
  results.slice(0, 5).forEach((item, i) => {
    sb.push(`${i + 1}. ${item.title}`);
    sb.push(`   链接：${item.link}`);
    if (item.snippet.trim()) {
      sb.push(`   摘要：${item.snippet.slice(0, 300)}`);
    } else if (item.link.trim()) {
      sb.push("   （请根据链接中的网页URL和标题自行判断内容，如无法确定请告知用户）");
    }
  });

  try {
    const text = await simpleCompletion(`用户问题：${query}\n\n${sb.join("\n")}`, {
      system: SUMMARIZE_SYSTEM,
      maxTokens: 500,
    });
    return text.trim() || sb.join("\n");
  } catch {
    // LLM 不可用时直接返回格式化搜索结果（Kotlin 行为是返回原文，此处对齐）
    return sb.join("\n");
  }
}
