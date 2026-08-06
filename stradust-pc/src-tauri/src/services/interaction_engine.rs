// 主动互动引擎，对应原interaction/ProactiveInteractionEngine.kt

use std::sync::atomic::{AtomicI64, Ordering};
use std::time::Duration;
use chrono::Timelike;
use rand::Rng;

/// 互动引擎
pub struct InteractionEngine {
    /// 上次互动时间戳
    last_interaction: AtomicI64,
    /// 上次唠叨时间戳
    last_nag: AtomicI64,
    /// 是否启用主动互动
    proactive_enabled: std::sync::atomic::AtomicBool,
    /// 最小互动间隔（秒）
    min_interval_secs: u64,
    /// 最大互动间隔（秒）
    max_interval_secs: u64,
    /// 唠叨间隔（秒）
    nag_interval_secs: u64,
    /// 空闲超时（秒）
    idle_timeout_secs: u64,
}

impl InteractionEngine {
    pub fn new() -> Self {
        InteractionEngine {
            last_interaction: AtomicI64::new(chrono::Local::now().timestamp()),
            last_nag: AtomicI64::new(0),
            proactive_enabled: std::sync::atomic::AtomicBool::new(true),
            min_interval_secs: 1800,    // 30分钟
            max_interval_secs: 7200,    // 2小时
            nag_interval_secs: 3600,    // 1小时
            idle_timeout_secs: 1800,    // 30分钟
        }
    }

    /// 记录用户互动
    pub fn record_interaction(&self) {
        self.last_interaction.store(chrono::Local::now().timestamp(), Ordering::SeqCst);
    }

    /// 检查是否应该主动互动
    pub fn should_proactive_interact(&self) -> bool {
        if !self.proactive_enabled.load(Ordering::SeqCst) {
            return false;
        }

        let now = chrono::Local::now().timestamp();
        let last = self.last_interaction.load(Ordering::SeqCst);
        let elapsed = (now - last) as u64;

        elapsed >= self.min_interval_secs
    }

    /// 检查是否应该唠叨
    pub fn should_nag(&self) -> bool {
        let now = chrono::Local::now().timestamp();
        let last_nag = self.last_nag.load(Ordering::SeqCst);
        let last_interaction = self.last_interaction.load(Ordering::SeqCst);

        // 用户空闲超过唠叨间隔
        let idle_time = (now - last_interaction) as u64;
        let nag_elapsed = if last_nag == 0 { self.nag_interval_secs } else { (now - last_nag) as u64 };

        idle_time >= self.idle_timeout_secs && nag_elapsed >= self.nag_interval_secs
    }

    /// 记录唠叨
    pub fn record_nag(&self) {
        self.last_nag.store(chrono::Local::now().timestamp(), Ordering::SeqCst);
    }

    /// 生成主动互动内容
    pub fn generate_proactive_content(&self) -> String {
        let hour = chrono::Local::now().hour();

        let messages = match hour {
            6..=8 => vec![
                "早安~新的一天开始了呢！",
                "早上好呀~今天也要元气满满哦！",
                "醒了吗？早安早安~",
            ],
            9..=11 => vec![
                "上午好~工作还顺利吗？",
                "要不要休息一下呀？",
                "记得喝水哦~",
            ],
            12..=13 => vec![
                "午饭时间到了~记得吃饭哦！",
                "中午了，休息一下吧~",
                "吃饱了吗？",
            ],
            14..=17 => vec![
                "下午好~还在忙吗？",
                "下午茶时间~要不要休息一下？",
                "加油哦，快到下班时间了~",
            ],
            18..=19 => vec![
                "傍晚了~辛苦一天了！",
                "晚饭吃了吗？",
                "今天过得怎么样呀？",
            ],
            20..=22 => vec![
                "晚上好~放松一下吧~",
                "今天有什么有趣的事吗？",
                "晚上记得早点休息哦~",
            ],
            23..=24 | 0..=1 => vec![
                "夜深了，该睡觉了哦~",
                "还不睡吗？熬夜不好的...",
                "晚安~做个好梦~",
            ],
            _ => vec!["嘿~在吗？"],
        };

        let idx = rand::thread_rng().gen_range(0..messages.len());
        messages[idx].to_string()
    }

    /// 生成唠叨内容
    pub fn generate_nag_content(&self) -> String {
        let nags = vec![
            "好无聊啊...来陪我聊天嘛~",
            "你都在忙什么呀？都不理我了...",
            "喂喂喂~我还在呢！",
            "一个人好寂寞...你在吗？",
            "哼，都不来找人家聊天...",
        ];

        let idx = rand::thread_rng().gen_range(0..nags.len());
        nags[idx].to_string()
    }

    /// 设置主动互动启用状态
    pub fn set_proactive_enabled(&self, enabled: bool) {
        self.proactive_enabled.store(enabled, Ordering::SeqCst);
    }

    /// 设置互动间隔
    pub fn set_intervals(&mut self, min_secs: u64, max_secs: u64, nag_secs: u64) {
        self.min_interval_secs = min_secs;
        self.max_interval_secs = max_secs;
        self.nag_interval_secs = nag_secs;
    }
}
