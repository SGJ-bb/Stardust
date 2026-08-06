// 智能摘要技能
// 纯 Rust 实现的提取式摘要算法，不依赖外部 LLM API 或 CLI 工具

use async_trait::async_trait;
use crate::models::chat::ToolDefinition;
use crate::plugins::plugin_trait::{PluginContext, PluginResult, ToolPlugin};

pub struct SummarizePlugin { enabled: bool }

impl SummarizePlugin {
    pub fn new() -> Self { SummarizePlugin { enabled: true } }

    /// 中文停用词表
    fn stop_words() -> &'static [&'static str] {
        &[
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
            "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去",
            "你", "会", "着", "没有", "看", "好", "自己", "这", "他", "她",
            "它", "们", "那", "个", "被", "从", "把", "对", "与", "而",
            "但", "以", "及", "等", "可以", "这个", "那个", "什么", "怎么",
            "如何", "因为", "所以", "如果", "虽然", "但是", "然后", "或者",
            "还是", "已经", "正在", "将", "能", "可能", "应该", "需要",
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "must", "shall",
            "can", "need", "dare", "ought", "used", "to", "of", "in",
            "for", "on", "with", "at", "by", "from", "as", "into",
            "through", "during", "before", "after", "above", "below",
            "between", "out", "off", "over", "under", "again", "further",
            "then", "once", "here", "there", "when", "where", "why",
            "how", "all", "each", "few", "more", "most", "other",
            "some", "such", "no", "nor", "not", "only", "own", "same",
            "so", "than", "too", "very", "just", "and", "but", "if",
            "or", "because", "until", "while", "this", "that", "these",
            "those", "it", "its", "his", "her", "their", "our", "my",
            "your", "i", "you", "he", "she", "we", "they", "what",
            "which", "who", "whom", "about", "up", "down",
        ]
    }

    /// 将文本按句子分割（支持中英文标点）
    fn split_sentences(text: &str) -> Vec<String> {
        let mut sentences = Vec::new();
        let mut current = String::new();
        let chars: Vec<char> = text.chars().collect();

        let mut i = 0;
        while i < chars.len() {
            let ch = chars[i];
            current.push(ch);

            // 检测句子结束符
            match ch {
                '。' | '！' | '？' | '…' | '.' | '!' | '?' => {
                    // 检查下一个字符是否也是结束符（如"!!!"或"。。。"）
                    let next_is_end = if i + 1 < chars.len() {
                        matches!(chars[i + 1], '。' | '！' | '？' | '.' | '!' | '?')
                    } else {
                        false
                    };
                    if !next_is_end {
                        let trimmed = current.trim().to_string();
                        if !trimmed.is_empty() && trimmed.len() > 2 {
                            // 过滤太短的片段
                            sentences.push(trimmed);
                        }
                        current.clear();
                    }
                }
                '\n' | '\r' => {
                    // 换行也作为句子分隔符（处理空行分段）
                    let next_is_nl = if i + 1 < chars.len() {
                        matches!(chars[i + 1], '\n' | '\r')
                    } else {
                        false
                    };
                    if !next_is_nl {
                        let trimmed = current.trim().to_string();
                        if !trimmed.is_empty() && trimmed.len() > 4 {
                            sentences.push(trimmed);
                        }
                        current.clear();
                    }
                }
                _ => {}
            }
            i += 1;
        }

        // 处理末尾剩余内容
        let remaining = current.trim().to_string();
        if !remaining.is_empty() && remaining.len() > 6 {
            sentences.push(remaining);
        }

        sentences
    }

    /// 对文本进行分词（简单按空格和常见边界切分）
    fn tokenize(text: &str) -> Vec<String> {
        let mut tokens = Vec::new();
        let mut current = String::new();

        for ch in text.chars() {
            if ch.is_whitespace() || ch.is_ascii_punctuation() || "，。！？、；：\u{201c}\u{201d}\u{2018}\u{2019}（）【】《》—…·".contains(ch) {
                if !current.is_empty() {
                    tokens.push(current.to_lowercase());
                    current.clear();
                }
                // 对于 CJK 字符，每个字作为一个 token
                if ('\u{4e00}'..='\u{9fff}').contains(&ch)
                    || ('\u{3400}'..='\u{4dbf}').contains(&ch)
                    || ('\u{f900}'..='\u{faff}').contains(&ch)
                {
                    tokens.push(ch.to_string());
                }
            } else {
                current.push(ch);
            }
        }

        if !current.is_empty() {
            tokens.push(current.to_lowercase());
        }

        tokens
    }

    /// 提取关键词（基于词频统计）
    fn extract_keywords(text: &str, top_n: usize) -> Vec<(String, usize)> {
        let stop_words: std::collections::HashSet<&str> =
            Self::stop_words().iter().copied().collect();

        let tokens = Self::tokenize(text);
        let mut freq: std::collections::HashMap<String, usize> = std::collections::HashMap::new();

        for token in &tokens {
            if token.len() < 2 { continue; } // 跳过单字符
            if stop_words.contains(token.as_str()) { continue; }
            *freq.entry(token.clone()).or_insert(0) += 1;
        }

        let mut keyword_vec: Vec<(String, usize)> = freq.into_iter().collect();
        keyword_vec.sort_by(|a, b| b.1.cmp(&a.1));
        keyword_vec.into_iter().take(top_n).collect()
    }

    /// 计算单个句子的得分
    fn score_sentence(
        sentence: &str,
        position: usize,
        total_sentences: usize,
        keyword_freq: &std::collections::HashMap<String, usize>,
    ) -> f64 {
        let mut score = 0.0;

        // 1. 句子长度得分（适中长度最佳：20-100个字符）
        let len = sentence.chars().count();
        if len >= 10 && len <= 150 {
            score += 5.0;
        } else if len > 150 {
            score += 2.0; // 长句也有价值但略低
        } else {
            score += 1.0;
        }

        // 2. 位置权重（开头和结尾的句子更重要）
        let pos_ratio = if total_sentences > 1 {
            position as f64 / (total_sentences - 1) as f64
        } else {
            0.5
        };
        if pos_ratio < 0.15 {
            score += 8.0; // 开头句子高权重
        } else if pos_ratio > 0.85 {
            score += 7.0; // 结尾句子次高权重
        } else if pos_ratio < 0.3 {
            score += 4.0;
        } else {
            score += 1.0;
        };

        // 3. 关键词频率得分
        let tokens = Self::tokenize(sentence);
        let stop_words: std::collections::HashSet<&str> =
            Self::stop_words().iter().copied().collect();
        let mut keyword_score = 0usize;
        for token in &tokens {
            if token.len() >= 2 && !stop_words.contains(token.as_str()) {
                if let Some(&freq) = keyword_freq.get(token) {
                    keyword_score += freq;
                }
            }
        }
        score += (keyword_score as f64).sqrt() * 2.0; // 平方根压缩避免长句优势过大

        // 4. 包含数字的句子通常有信息量
        if sentence.chars().any(|c| c.is_ascii_digit()) {
            score += 2.0;
        }

        // 5. 包含引号或专有名词标记的句子
        if contains_any(sentence, &["\u{300c}", "\u{300d}", "\u{201c}", "\u{201d}", "\u{2018}", "\u{2019}", "\u{300a}", "\u{300b}"]) {
            score += 1.5;
        }

        score
    }

    /// 根据模式生成摘要
    fn generate_summary(
        text: &str,
        mode: &str,
        max_length: usize,
    ) -> (String, Vec<(String, usize)>, usize, usize) {
        let original_len = text.chars().count();
        let sentences = Self::split_sentences(text);

        if sentences.is_empty() {
            return (
                "文本过短或无法进行有效摘要。".to_string(),
                vec![],
                original_len,
                0,
            );
        }

        // 提取全文关键词频率
        let all_keywords = Self::extract_keywords(text, 50);
        let mut keyword_freq: std::collections::HashMap<String, usize> =
            std::collections::HashMap::new();
        for (word, freq) in &all_keywords {
            keyword_freq.insert(word.clone(), *freq);
        }

        // 计算每句话的得分
        let mut scored_sentences: Vec<(usize, f64, String)> = Vec::new(); // (原始位置, 得分, 内容)
        for (idx, sentence) in sentences.iter().enumerate() {
            let score = Self::score_sentence(sentence, idx, sentences.len(), &keyword_freq);
            scored_sentences.push((idx, score, sentence.clone()));
        }

        // 按得分降序排序
        scored_sentences.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));

        // 根据 mode 决定选取数量
        let select_count = match mode {
            "bullet" | "bullet_points" | "key_insights" => {
                ((sentences.len() as f64) * 0.25).ceil() as usize
            }
            "detailed" | "tl;dr" => {
                ((sentences.len() as f64) * 0.35).ceil() as usize
            }
            _ => {
                // brief 模式
                ((sentences.len() as f64) * 0.18).ceil().max(1.0).min(5.0) as usize
            }
        }.max(1);

        // 取 top-N 并按原文顺序排列
        let mut selected: Vec<(usize, &String)> = scored_sentences
            .iter()
            .take(select_count)
            .map(|(pos, _, content)| (*pos, content))
            .collect();
        selected.sort_by_key(|(pos, _)| *pos);

        // 组装摘要文本
        let mut summary_parts: Vec<String> = Vec::new();
        let mut summary_char_count = 0usize;

        for (_, sentence) in &selected {
            let s_chars = sentence.chars().count();
            if summary_char_count + s_chars > max_length && !summary_parts.is_empty() {
                break; // 达到长度限制
            }
            summary_parts.push((*sentence).clone());
            summary_char_count += s_chars;
        }

        let summary_text = match mode {
            "bullet" | "bullet_points" | "key_insights" => {
                let bullet_text: Vec<String> = summary_parts
                    .iter()
                    .map(|s| format!("• {}", s.trim()))
                    .collect();
                bullet_text.join("\n")
            }
            _ => {
                summary_parts.join("")
            }
        };

        // 取前 8 个关键词作为最终输出
        let keywords = Self::extract_keywords(text, 8);

        (summary_text, keywords, original_len, summary_char_count)
    }
}

/// 辅助函数：检查字符串是否包含任一目标子串
fn contains_any(s: &str, targets: &[&str]) -> bool {
    targets.iter().any(|t| s.contains(t))
}

#[async_trait]
impl ToolPlugin for SummarizePlugin {
    fn name(&self) -> &str { "summarize" }
    fn description(&self) -> &str { "长文本智能摘要与关键点提取，支持多语言和多种摘要模式" }
    fn get_definition(&self) -> ToolDefinition {
        ToolDefinition {
            r#type: "function".to_string(),
            function: crate::models::chat::ToolFunction {
                name: "summarize".to_string(),
                description: "对长文本进行智能摘要，提取关键信息和要点。支持文章、论文、报告、会议记录等多种文本类型".to_string(),
                parameters: serde_json::json!({
                    "type": "object",
                    "properties": {
                        "text": { "type": "string", "description": "需要摘要的文本内容" },
                        "mode": {
                            "type": "string",
                            "enum": ["brief", "detailed", "bullet"],
                            "description": "摘要模式：brief=简洁摘要, detailed=详细摘要, bullet=要点列表"
                        },
                        "max_length": { "type": "integer", "description": "最大字数限制，默认200" }
                    },
                    "required": ["text"]
                }),
            },
        }
    }

    async fn execute(&self, arguments: &serde_json::Value, _context: &PluginContext) -> PluginResult {
        let text = arguments["text"].as_str().unwrap_or("");
        let mode = arguments["mode"].as_str().unwrap_or("brief");
        let max_length = arguments["max_length"]
            .as_u64()
            .unwrap_or(200)
            .max(50) as usize;

        if text.is_empty() {
            return PluginResult::err("待摘要文本不能为空");
        }

        tracing::info!("[summarize] mode={}, len={}", mode, text.len());

        let original_char_count = text.chars().count();

        // 短文本直接返回提示
        if original_char_count < 30 {
            return PluginResult::ok_with_data(
                format!(
                    "📝 文本较短（{}字），无需摘要。\n\n原文:\n{}",
                    original_char_count, text
                ),
                serde_json::json!({
                    "mode": mode,
                    "original_length": original_char_count,
                    "summary_length": original_char_count,
                    "is_short_text": true
                })
            );
        }

        let start_time = std::time::SystemTime::now();

        let (summary, keywords, orig_len, sum_len) =
            Self::generate_summary(text, mode, max_length);

        let duration_ms = start_time
            .elapsed()
            .map_or(0, |d| d.as_millis() as u64);

        // 组装输出
        let mode_label = match mode {
            "bullet" | "bullet_points" | "key_insights" => "要点列表",
            "detailed" | "tl;dr" => "详细摘要",
            _ => "简洁摘要",
        };

        let mut output = String::new();
        output.push_str(&format!("📝 {}（{}模式）：\n", mode_label, mode));
        output.push_str(&"─".repeat(40));
        output.push('\n');
        output.push_str("\n");
        output.push_str(&summary);
        output.push('\n');

        // 关键词
        if !keywords.is_empty() {
            output.push_str("\n🏷 关键词: ");
            let kw_strs: Vec<String> = keywords
                .iter()
                .map(|(w, f)| format!("{}({})", w, f))
                .collect();
            output.push_str(&kw_strs.join(", "));
            output.push('\n');
        }

        // 统计信息
        let ratio_pct = if orig_len > 0 {
            (sum_len as f64 / orig_len as f64 * 100.0)
        } else {
            0.0
        };
        let stats_line = format!(
            "\n统计: 原文 {}字 -> 摘要 {}字 (压缩比 {:.1}%) 耗时 {}ms",
            orig_len, sum_len, ratio_pct, duration_ms
        );
        output.push_str(&stats_line);

        PluginResult::ok_with_data(
            output,
            serde_json::json!({
                "mode": mode,
                "original_length": orig_len,
                "summary_length": sum_len,
                "compression_ratio": if orig_len > 0 { Some(sum_len as f64 / orig_len as f64 * 100.0) } else { None },
                "keywords": keywords.iter().map(|(w, _)| w.clone()).collect::<Vec<_>>(),
                "duration_ms": duration_ms
            })
        )
    }

    fn is_enabled(&self) -> bool { self.enabled }
    fn set_enabled(&mut self, enabled: bool) { self.enabled = enabled; }
}
