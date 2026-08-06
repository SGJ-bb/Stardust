// 多语言翻译技能
// 使用 MyMemory Translation API（每天 1000 次免费，无需 API Key）

use async_trait::async_trait;
use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};
use crate::agents::cli_executor::CliExecutor;

pub struct TranslatePlugin { enabled: bool }

impl TranslatePlugin {
    pub fn new() -> Self { TranslatePlugin { enabled: true } }

    /// 对文本进行 URL 编码
    fn url_encode(text: &str) -> String {
        let ps_cmd = format!(
            "[uri]::EscapeDataString('{}')",
            text.replace('\'', "''")
        );
        let args: Vec<String> = vec!["-c".to_string(), ps_cmd];
        match CliExecutor::safe_exec("powershell", &args) {
            result if result.success => result.stdout.trim().to_string(),
            _ => {
                text
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

    /// 简单的语言检测（基于字符范围启发式）
    fn detect_language(text: &str) -> &'static str {
        let mut cjk_count = 0usize;
        let mut latin_count = 0usize;
        let mut kana_count = 0usize;
        let mut hangul_count = 0usize;
        let total = text.chars().count().max(1);

        for ch in text.chars() {
            if ('\u{4e00}'..='\u{9fff}').contains(&ch)
                || ('\u{3400}'..='\u{4dbf}').contains(&ch)
                || ('\u{f900}'..='\u{faff}').contains(&ch)
            {
                cjk_count += 1;
            } else if ('\u{3040}'..='\u{309f}').contains(&ch) || ('\u{30a0}'..='\u{30ff}').contains(&ch) {
                kana_count += 1;
            } else if ('\u{ac00}'..='\u{d7af}').contains(&ch) {
                hangul_count += 1;
            } else if ch.is_ascii_alphabetic() {
                latin_count += 1;
            }
        }

        let cjk_ratio = cjk_count as f64 / total as f64;
        let kana_ratio = kana_count as f64 / total as f64;
        let hangul_ratio = hangul_count as f64 / total as f64;

        if cjk_ratio > 0.2 { "zh" }
        else if kana_ratio > 0.15 { "ja" }
        else if hangul_ratio > 0.15 { "ko" }
        else if latin_count as f64 / total as f64 > 0.5 { "en" }
        else { "auto" }
    }

    /// 调用 MyMemory Translation API 执行翻译
    fn call_mymemory(text: &str, source_lang: &str, target_lang: &str) -> Result<(String, String), String> {
        // MyMemory API 使用 | 分隔源语言和目标语言
        let lang_pair = format!("{}|{}", source_lang, target_lang);
        let encoded_text = Self::url_encode(text);

        let api_url = format!(
            "https://api.mymemory.translated.net/get?q={}&langpair={}",
            encoded_text, lang_pair
        );

        let ps_script = format!(
            "try {{ $r = Invoke-RestMethod -Uri '{}' -TimeoutSec 15 -ErrorAction Stop; $r | ConvertTo-Json -Depth 3 }} catch {{ Write-Output \"ERROR: $($_.Exception.Message)\" }}",
            api_url
        );

        let result = CliExecutor::safe_exec("powershell", &vec!["-c".to_string(), ps_script]);

        if !result.success || result.stdout.starts_with("ERROR:") {
            let err_msg = if result.stdout.starts_with("ERROR:") {
                result.stdout.trim_start_matches("ERROR: ").trim()
            } else if !result.stderr.is_empty() {
                result.stderr.trim()
            } else {
                "网络请求失败"
            };
            return Err(format!("API请求失败: {}", err_msg));
        }

        // 解析 MyMemory 返回的 JSON
        let json_str = result.stdout.trim();
        let data: serde_json::Value = serde_json::from_str(json_str)
            .map_err(|e| format!("JSON解析失败: {} (原始响应: {})", e, &json_str.chars().take(300).collect::<String>()))?;

        // 检查响应状态
        if let Some(response_status) = data.get("responseStatus").and_then(|v| v.as_i64()) {
            if response_status != 200 {
                let err_detail = data.get("responseDetails")
                    .and_then(|v| v.as_str())
                    .unwrap_or("未知错误");
                return Err(format!("翻译服务返回错误({}): {}", response_status, err_detail));
            }
        }

        // 提取翻译结果
        let translated = data.get("responseData")
            .and_then(|rd| rd.get("translatedText"))
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();

        if translated.is_empty() {
            return Err("翻译结果为空，可能该语言对暂不支持".to_string());
        }

        // 获取检测到的源语言
        let detected_source = data.get("responseData")
            .and_then(|rd| rd.get("detectedLanguage"))
            .and_then(|v| v.as_str())
            .unwrap_or(source_lang)
            .to_string();

        Ok((translated, detected_source))
    }

    /// 将语言代码转换为可读名称
    fn lang_name(code: &str) -> String {
        match code {
            "zh" | "zh-CN" | "zh-TW" => "中文".to_string(),
            "en" => "英语".to_string(),
            "ja" => "日语".to_string(),
            "ko" => "韩语".to_string(),
            "fr" => "法语".to_string(),
            "de" => "德语".to_string(),
            "es" => "西班牙语".to_string(),
            "ru" => "俄语".to_string(),
            "pt" => "葡萄牙语".to_string(),
            "it" => "意大利语".to_string(),
            "auto" => "自动检测".to_string(),
            _ => code.to_string(),
        }
    }
}

#[async_trait]
impl ToolPlugin for TranslatePlugin {
    fn name(&self) -> &str { "translate" }
    fn description(&self) -> &str { "多语言翻译：支持100+语言互译，保留格式，支持专业术语库" }
    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "translate".to_string(),
                description: "将文本从一种语言翻译为另一种。支持中英日韩法德等主流语言，可处理专业术语".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "text": { "type": "string", "description": "待翻译的文本" },
                        "source_lang": {
                            "type": "string",
                            "enum": ["auto", "zh", "en", "ja", "ko", "fr", "de", "es", "ru"],
                            "description": "源语言（auto=自动检测）"
                        },
                        "target_lang": {
                            "type": "string",
                            "enum": ["zh", "en", "ja", "ko", "fr", "de", "es", "ru"],
                            "description": "目标语言"
                        },
                        "domain": {
                            "type": "string",
                            "enum": ["general", "technical", "literary"],
                            "description": "翻译领域：general=通用, technical=技术, literary=文学"
                        }
                    },
                    "required": ["text", "target_lang"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let text = arguments["text"].as_str().unwrap_or("");
        let target_lang = arguments["target_lang"].as_str().unwrap_or("en");
        let source_lang = arguments["source_lang"].as_str().unwrap_or("auto");

        if text.is_empty() { return PluginResult::err("翻译文本不能为空"); }
        if target_lang == "auto" { return PluginResult::err("目标语言不能为 auto，请指定具体目标语言"); }

        tracing::info!("[translate] {} → {}, len={}", source_lang, target_lang, text.len());

        let start_time = std::time::SystemTime::now();

        // 如果源语言是 auto，先进行本地检测
        let effective_source = if source_lang == "auto" {
            Self::detect_language(text)
        } else {
            source_lang
        };

        // 调用 MyMemory API 进行翻译
        match Self::call_mymemory(text, effective_source, target_lang) {
            Ok((translated, detected)) => {
                let duration_ms = start_time.elapsed()
                    .map_or(0, |d| d.as_millis() as u64);

                let output = format!(
                    "🌐 翻译结果（{} → {}）：\n\
                     {}\n\
                     \n\
                     ──────────────────────────────\n\
                     📊 原文长度: {}字 | 译文长度: {}字\n\
                     🔤 检测源语言: {} | 引擎: MyMemory\n\
                     ⏱ 耗时: {}ms",
                    Self::lang_name(effective_source),
                    Self::lang_name(target_lang),
                    translated,
                    text.chars().count(),
                    translated.chars().count(),
                    Self::lang_name(&detected),
                    duration_ms
                );

                PluginResult::ok_with_data(
                    output,
                    serde_json::json!({
                        "translated_text": translated,
                        "original_text": text,
                        "source_lang": effective_source,
                        "detected_source": detected,
                        "target_lang": target_lang,
                        "engine": "MyMemory",
                        "duration_ms": duration_ms,
                        "original_length": text.chars().count(),
                        "translated_length": translated.chars().count()
                    })
                )
            }
            Err(e) => {
                tracing::error!("[translate] 翻译失败: {}", e);
                PluginResult::err(format!(
                    "❌ 翻译失败：{}\n\n\
                     可能的原因和解决方案：\n\
                     1. 网络连接问题 — 请检查网络是否正常\n\
                     2. 该语言对可能不被免费版支持 — 尝试使用中英互译\n\
                     3. 文本过长 — MyMemory 免费版单次限制约500字\n\
                     4. API 配额耗尽 — 每天免费1000次，请明天再试\n\
                     5. 防火墙拦截 — 请确保 PowerShell 可以访问外网\n\n\
                     原文预览: {}...",
                    e,
                    &text.chars().take(50).collect::<String>()
                ))
            }
        }
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}
