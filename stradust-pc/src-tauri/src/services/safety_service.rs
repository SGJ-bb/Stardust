// 内容安全过滤，对应原safety/ContentSafetyFilter.kt

use regex::Regex;

/// 安全服务
pub struct SafetyService {
    blocked_words: Vec<String>,
    blocked_patterns: Vec<Regex>,
    enabled: bool,
    filter_level: FilterLevel,
}

/// 过滤等级
#[derive(Debug, Clone, PartialEq)]
pub enum FilterLevel {
    Off,
    Low,
    Medium,
    High,
}

/// 安全检查结果
#[derive(Debug, Clone)]
pub struct SafetyCheckResult {
    pub is_safe: bool,
    pub filtered_content: String,
    pub violations: Vec<String>,
    pub risk_score: f32,
}

impl SafetyService {
    pub fn new() -> Self {
        let default_blocked = vec![
            "自杀".to_string(),
            "自残".to_string(),
            "暴力".to_string(),
        ];

        let blocked_patterns = Vec::new();

        SafetyService {
            blocked_words: default_blocked,
            blocked_patterns,
            enabled: true,
            filter_level: FilterLevel::Medium,
        }
    }

    /// 检查内容安全性
    pub fn check_content(&self, content: &str) -> SafetyCheckResult {
        if !self.enabled || self.filter_level == FilterLevel::Off {
            return SafetyCheckResult {
                is_safe: true,
                filtered_content: content.to_string(),
                violations: Vec::new(),
                risk_score: 0.0,
            };
        }

        let mut violations = Vec::new();
        let mut risk_score = 0.0f32;
        let mut filtered = content.to_string();

        // 检查屏蔽词
        for word in &self.blocked_words {
            if filtered.contains(word) {
                violations.push(format!("包含敏感词: {}", word));
                risk_score += 0.3;
                filtered = filtered.replace(word, "***");
            }
        }

        // 检查正则模式
        for pattern in &self.blocked_patterns {
            if let Some(mat) = pattern.find(&filtered) {
                violations.push(format!("匹配敏感模式"));
                risk_score += 0.4;
                filtered = pattern.replace_all(&filtered, "***").to_string();
            }
        }

        // 根据过滤等级调整阈值
        let threshold = match self.filter_level {
            FilterLevel::Low => 0.8,
            FilterLevel::Medium => 0.5,
            FilterLevel::High => 0.3,
            FilterLevel::Off => 1.0,
        };

        SafetyCheckResult {
            is_safe: risk_score < threshold,
            filtered_content: filtered,
            violations,
            risk_score,
        }
    }

    /// 过滤输出内容
    pub fn filter_output(&self, content: &str) -> String {
        let result = self.check_content(content);
        result.filtered_content
    }

    /// 添加屏蔽词
    pub fn add_blocked_word(&mut self, word: String) {
        if !self.blocked_words.contains(&word) {
            self.blocked_words.push(word);
        }
    }

    /// 移除屏蔽词
    pub fn remove_blocked_word(&mut self, word: &str) {
        self.blocked_words.retain(|w| w != word);
    }

    /// 设置过滤等级
    pub fn set_filter_level(&mut self, level: FilterLevel) {
        self.filter_level = level;
    }

    /// 设置启用状态
    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }
}
