// 通用工具函数

use regex::Regex;
use std::path::PathBuf;

/// 生成UUID
pub fn new_uuid() -> String {
    uuid::Uuid::new_v4().to_string()
}

/// 获取应用数据目录
pub fn get_app_data_dir() -> PathBuf {
    let base = dirs::data_dir().unwrap_or_else(|| PathBuf::from("."));
    base.join("stradust")
}

/// 获取数据库路径
pub fn get_db_path() -> PathBuf {
    get_app_data_dir().join("stradust.db")
}

/// 估算文本的token数量（简单估算：中文1字≈1.5token，英文1词≈1token）
pub fn estimate_tokens(text: &str) -> u32 {
    let chinese_count = text.chars().filter(|c| '\u{4e00}' <= *c && *c <= '\u{9fff}').count();
    let other_len = text.len() - chinese_count * 3; // 粗略减去中文字符字节
    let english_words = other_len.max(0) as f32 / 5.0; // 粗略估算英文单词数
    (chinese_count as f32 * 1.5 + english_words).ceil() as u32
}

/// 截断文本到指定token数
pub fn truncate_to_tokens(text: &str, max_tokens: u32) -> String {
    let tokens = estimate_tokens(text);
    if tokens <= max_tokens {
        return text.to_string();
    }

    // 粗略截断
    let ratio = max_tokens as f32 / tokens as f32;
    let target_chars = (text.len() as f32 * ratio) as usize;
    let mut result = text.chars().take(target_chars).collect::<String>();
    result.push_str("...");
    result
}

/// 清理文本中的敏感信息
pub fn sanitize_text(text: &str) -> String {
    // 移除可能的API密钥模式
    let re = Regex::new(r"(sk-|api_key|apikey|secret|token|password)\s*[:=]\s*\S+")
        .unwrap_or_else(|_| Regex::new("").unwrap());
    re.replace_all(text, "$1: ***").to_string()
}

/// 格式化日期时间
pub fn format_datetime(dt: chrono::NaiveDateTime) -> String {
    dt.format("%Y-%m-%d %H:%M:%S").to_string()
}

/// 格式化日期
pub fn format_date(dt: chrono::NaiveDateTime) -> String {
    dt.format("%Y-%m-%d").to_string()
}

/// 解析日期时间字符串（多格式兼容）
/// 统一版本，供所有 repo 复用，避免重复定义
pub fn parse_dt(s: String) -> chrono::NaiveDateTime {
    chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%d %H:%M:%S")
        .ok()
        .or_else(|| chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%dT%H:%M:%S").ok())
        .or_else(|| {
            chrono::NaiveDate::parse_from_str(&s, "%Y-%m-%d").ok()
                .and_then(|d| d.and_hms_opt(0, 0, 0))
        })
        .unwrap_or_else(|| chrono::Local::now().naive_local())
}

/// 解析日期时间字符串（返回Option版本，供命令层使用）
pub fn parse_datetime(s: &str) -> Option<chrono::NaiveDateTime> {
    chrono::NaiveDateTime::parse_from_str(s, "%Y-%m-%d %H:%M:%S").ok()
        .or_else(|| chrono::NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M:%S").ok())
        .or_else(|| {
            chrono::NaiveDate::parse_from_str(s, "%Y-%m-%d").ok()
                .and_then(|d| d.and_hms_opt(0, 0, 0))
        })
}

/// 获取当前时间
pub fn now() -> chrono::NaiveDateTime {
    chrono::Local::now().naive_local()
}

/// 安全地将JSON值转为字符串
pub fn json_to_string(value: &serde_json::Value) -> String {
    match value {
        serde_json::Value::String(s) => s.clone(),
        serde_json::Value::Null => String::new(),
        other => other.to_string(),
    }
}

/// 模糊匹配
pub fn fuzzy_match(query: &str, target: &str) -> bool {
    let query_lower = query.to_lowercase();
    let target_lower = target.to_lowercase();
    target_lower.contains(&query_lower)
}

/// 安全的除法（避免除零）
pub fn safe_divide(a: f32, b: f32) -> f32 {
    if b.abs() < f32::EPSILON {
        0.0
    } else {
        a / b
    }
}

/// 限制值在范围内
pub fn clamp(value: f32, min: f32, max: f32) -> f32 {
    value.max(min).min(max)
}

/// 根据文件扩展名猜测 MIME 类型
pub fn mime_guess_from_path(path: &std::path::Path) -> &'static str {
    match path.extension().and_then(|e| e.to_str()) {
        Some("json") => "application/json",
        Some("moc3") => "application/octet-stream",
        Some("png") => "image/png",
        Some("jpg") | Some("jpeg") => "image/jpeg",
        Some("webp") => "image/webp",
        Some("txt") | Some("text") => "text/plain",
        _ => "application/octet-stream",
    }
}
