// 网页搜索，对应原search/WebSearchEngine.kt
// 实现基本的网页搜索（调用搜索 API）

use thiserror::Error;

/// 搜索错误
#[derive(Debug, Error)]
pub enum SearchError {
    #[error("搜索请求失败: {0}")]
    RequestFailed(String),
    #[error("搜索未配置")]
    NotConfigured,
}

/// 搜索结果
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct SearchResult {
    pub title: String,
    pub url: String,
    pub snippet: String,
}

/// 搜索服务
pub struct SearchService {
    client: reqwest::Client,
}

impl SearchService {
    pub fn new() -> Self {
        SearchService {
            client: reqwest::Client::new(),
        }
    }

    /// 执行网页搜索
    /// 支持 SearXNG 自建搜索实例，也可降级为模拟结果
    pub async fn search(&self, query: &str, num_results: u32) -> Result<Vec<SearchResult>, SearchError> {
        tracing::info!("执行网页搜索: {}", query);

        // 尝试调用 SearXNG 搜索 API（自建实例）
        let searxng_url = std::env::var("SEARXNG_URL").unwrap_or_else(|_| "http://localhost:8080".to_string());

        let url = format!("{}/search?q={}&format=json&limit={}",
            searxng_url,
            urlencoding::encode(query),
            num_results
        );

        let result = self.client
            .get(&url)
            .send()
            .await;

        match result {
            Ok(resp) if resp.status().is_success() => {
                #[derive(serde::Deserialize)]
                struct SearxResult {
                    results: Vec<SearxItem>,
                }
                #[derive(serde::Deserialize)]
                struct SearxItem {
                    title: Option<String>,
                    url: Option<String>,
                    content: Option<String>,
                }

                if let Ok(data) = resp.json::<SearxResult>().await {
                    return Ok(data.results.into_iter().take(num_results as usize).map(|item| {
                        SearchResult {
                            title: item.title.unwrap_or_default(),
                            url: item.url.unwrap_or_default(),
                            snippet: item.content.unwrap_or_default(),
                        }
                    }).collect());
                }
                // 解析失败，降级
                tracing::warn!("SearXNG 响应解析失败，降级为模拟结果");
            }
            Ok(resp) => {
                tracing::warn!("SearXNG 返回错误状态: {}", resp.status());
            }
            Err(e) => {
                tracing::warn!("SearXNG 请求失败: {}，降级为模拟结果", e);
            }
        }

        // 降级：返回模拟结果
        Ok(vec![
            SearchResult {
                title: format!("搜索结果: {}", query),
                url: "https://example.com".to_string(),
                snippet: format!("关于「{}」的搜索结果（搜索服务未配置，返回模拟数据）", query),
            }
        ])
    }
}

/// URL 编码（简化版）
mod urlencoding {
    pub fn encode(s: &str) -> String {
        s.chars().map(|c| {
            if c.is_ascii_alphanumeric() || c == '-' || c == '_' || c == '.' || c == '~' {
                c.to_string()
            } else {
                format!("%{:02X}", c as u8)
            }
        }).collect()
    }
}
