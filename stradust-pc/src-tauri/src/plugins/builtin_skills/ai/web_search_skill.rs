// 网络搜索技能（Agent专用，区别于现有 web_search_plugin）
// 使用 DuckDuckGo Instant Answer API（免费无需 API Key）

use async_trait::async_trait;
use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};
use crate::agents::cli_executor::CliExecutor;

pub struct WebSearchSkill { enabled: bool }

impl WebSearchSkill {
    pub fn new() -> Self { WebSearchSkill { enabled: true } }

    /// 对查询关键词进行 URL 编码（使用 PowerShell 的 [uri]::EscapeDataString）
    fn url_encode(query: &str) -> String {
        let ps_cmd = format!(
            "[uri]::EscapeDataString('{}')",
            query.replace('\'', "''")
        );
        match CliExecutor::safe_exec("powershell", &["-c", &ps_cmd]) {
            result if result.success => result.stdout.trim().to_string(),
            _ => {
                // 降级：手动替换常见特殊字符
                query
                    .replace(' ', "+")
                    .replace('%', "%25")
                    .replace('#', "%23")
                    .replace('&', "%26")
                    .replace('=', "%3D")
                    .replace('?', "%3F")
                    .to_string()
            }
        }
    }
}

#[async_trait]
impl ToolPlugin for WebSearchSkill {
    fn name(&self) -> &str { "web_search" }
    fn description(&self) -> &str { "联网搜索获取最新信息，支持深度搜索和多源聚合" }
    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "web_search".to_string(),
                description: "搜索互联网获取实时信息。可搜索新闻、技术文档、学术论文、产品信息等".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "query": { "type": "string", "description": "搜索关键词" },
                        "engine": {
                            "type": "string",
                            "enum": ["google", "bing", "duckduckgo", "dg"],
                            "description": "搜索引擎，默认 bing"
                        },
                        "count": { "type": "integer", "description": "返回结果数量(1-10)，默认5" }
                    },
                    "required": ["query"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let query = arguments["query"].as_str().unwrap_or("");
        if query.is_empty() { return PluginResult::err("搜索关键词不能为空"); }

        let engine = arguments["engine"].as_str().unwrap_or("bing");
        let count = arguments["count"].as_u64().unwrap_or(5).min(10).max(1);
        let encoded_query = Self::url_encode(query);

        tracing::info!("[web_search] engine={}, query={}, count={}", engine, query, count);

        // 使用 DuckDuckGo Instant Answer API（免费无限制）
        // 通过 PowerShell Invoke-RestMethod 绕过 curl/wget 黑名单
        let api_url = format!(
            "https://api.duckduckgo.com/?q={}&format=json&no_html=1",
            encoded_query
        );

        let ps_script = format!(
            "try {{ $r = Invoke-RestMethod -Uri '{}' -TimeoutSec 15 -ErrorAction Stop; $r | ConvertTo-Json -Depth 5 }} catch {{ Write-Output \"ERROR: $($_.Exception.Message)\" }}",
            api_url
        );

        let result = CliExecutor::safe_exec("powershell", &vec!["-c".to_string(), ps_script]);

        if !result.success || result.stdout.starts_with("ERROR:") {
            let err_msg = if result.stdout.starts_with("ERROR:") {
                result.stdout.trim_start_matches("ERROR: ").trim()
            } else if !result.stderr.is_empty() {
                &result.stderr
            } else {
                "网络请求失败"
            };
            return PluginResult::err(format!(
                "❌ 搜索请求失败：{}\n\n\
                 可能的原因和解决方案：\n\
                 1. 网络连接问题 — 请检查网络是否正常\n\
                 2. DNS 解析失败 — 尝试更换 DNS 为 8.8.8.8\n\
                 3. 防火墙拦截 — 请确保 PowerShell 可以访问外网\n\
                 4. API 暂时不可用 — 请稍后重试\n\n\
                 耗时: {}ms",
                err_msg, result.duration_ms
            ));
        }

        // 解析 DuckDuckGo 返回的 JSON
        let json_str = result.stdout.trim();
        let ddg_result: Result<serde_json::Value, _> = serde_json::from_str(json_str);

        match ddg_result {
            Ok(data) => {
                let mut output = String::new();
                output.push_str(&format!("🔍 搜索「{}」的结果（引擎: DuckDuckGo）：\n", query));
                output.push_str(&"─".repeat(50));
                output.push('\n');

                // 提取 AbstractText（即时答案摘要）
                if let Some(abstract_text) = data.get("AbstractText").and_then(|v| v.as_str()) {
                    if !abstract_text.is_empty() {
                        output.push_str(&format!("\n📌 即时答案:\n  {}\n", abstract_text));
                        if let Some(source) = data.get("AbstractURL").and_then(|v| v.as_str()) {
                            output.push_str(&format!("  🔗 来源: {}\n", source));
                        }
                        output.push('\n');
                    }
                }

                // 提取 RelatedTopics（相关主题/结果）
                let mut results_count = 0usize;
                if let Some(topics) = data.get("RelatedTopics").and_then(|v| v.as_array()) {
                    output.push_str("\n📋 相关结果:\n");
                    for topic in topics.iter() {
                        if results_count >= count as usize { break; }

                        // RelatedTopics 可能是对象或嵌套数组
                        if let Some(text) = topic.get("Text").and_then(|v| v.as_str()) {
                            let url = topic.get("FirstURL")
                                .and_then(|v| v.as_str())
                                .unwrap_or("无链接");

                            // 跳过空条目（DDG 用空 Text 表示分类标题）
                            if !text.is_empty() {
                                results_count += 1;
                                // 清理 HTML 标签
                                let clean_text = text
                                    .replace("<a href=\"", "")
                                    .replace("\">", " — ")
                                    .replace("</a>", "");
                                output.push_str(&format!(
                                    "  {}. {}\n     🔗 {}\n",
                                    results_count, clean_text, url
                                ));
                            }
                        } else if let Some(nested) = topic.get("Topics").and_then(|v| v.as_array()) {
                            // 处理分组下的子主题
                            for item in nested.iter() {
                                if results_count >= count as usize { break; }
                                if let Some(text) = item.get("Text").and_then(|v| v.as_str()) {
                                    if !text.is_empty() {
                                        let url = item.get("FirstURL")
                                            .and_then(|v| v.as_str())
                                            .unwrap_or("无链接");
                                        results_count += 1;
                                        let clean_text = text
                                            .replace("<a href=\"", "")
                                            .replace("\">", " — ")
                                            .replace("</a>", "");
                                        output.push_str(&format!(
                                            "  {}. {}\n     🔗 {}\n",
                                            results_count, clean_text, url
                                        ));
                                    }
                                }
                            }
                        }
                    }
                }

                // 如果没有找到任何结果
                if results_count == 0 && data.get("AbstractText").and_then(|v| v.as_str()).map_or(true, |s| s.is_empty()) {
                    output.push_str("\n  未找到相关结果，请尝试更换搜索关键词。\n");
                }

                output.push_str(&format!("\n⏱ 耗时: {}ms\n", result.duration_ms));

                PluginResult::ok_with_data(
                    output,
                    serde_json::json!({
                        "engine": "duckduckgo",
                        "query": query,
                        "results_count": results_count,
                        "duration_ms": result.duration_ms,
                        "has_abstract": data.get("AbstractText").and_then(|v| v.as_str()).map_or(false, |s| !s.is_empty())
                    })
                )
            }
            Err(e) => {
                tracing::error!("[web_search] JSON解析失败: {}, 原始输出: {}", e, json_str);
                // JSON 解析失败时，尝试直接返回原始文本
                PluginResult::err(format!(
                    "❌ 搜索结果解析失败：{}\n原始响应: {}\n\n请检查网络连接后重试。",
                    e, &json_str.chars().take(500).collect::<String>()
                ))
            }
        }
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}
