// 人性化处理，对应原humanizer/Humanizer.kt

use rand::Rng;

/// 人性化服务
pub struct Humanizer;

impl Humanizer {
    pub fn new() -> Self {
        Humanizer
    }

    /// 为回复添加人性化元素
    pub fn humanize(&self, text: &str) -> String {
        let mut result = text.to_string();

        // 添加随机的语气词
        if rand::thread_rng().gen_bool(0.3) {
            let particles = ["嗯~", "啊", "呢", "呀", "嘛", "哦"];
            let particle = particles[rand::thread_rng().gen_range(0..particles.len())];
            if !result.ends_with(&['~', '！', '!', '。', '？', '?'][..]) {
                result.push_str(particle);
            }
        }

        // 添加随机的表情符号
        if rand::thread_rng().gen_bool(0.2) {
            let emojis = ["✨", "💫", "🌸", "💖", "😊", "🌟"];
            let emoji = emojis[rand::thread_rng().gen_range(0..emojis.len())];
            result.push_str(emoji);
        }

        result
    }

    /// 添加打字延迟效果
    pub fn calculate_typing_delay(&self, text: &str) -> u64 {
        let char_count = text.chars().count();
        // 平均每个字符30-80ms的打字延迟
        let base_delay = char_count as u64 * 50;
        // 添加随机波动
        let jitter = rand::thread_rng().gen_range(0..500);
        (base_delay + jitter).min(3000) // 最多3秒
    }

    /// 添加思考停顿
    pub fn add_thinking_pause(&self, text: &str) -> String {
        if text.chars().count() > 50 && rand::thread_rng().gen_bool(0.3) {
            // 使用字符边界在中间位置添加省略号表示思考
            let char_count = text.chars().count();
            let mid = char_count / 2;
            let byte_pos = text.char_indices()
                .nth(mid)
                .map(|(i, _)| i)
                .unwrap_or(text.len());
            let mut result = text.to_string();
            result.insert_str(byte_pos, "……");
            return result;
        }
        text.to_string()
    }

    /// 生成口头禅
    pub fn get_catchphrase(&self, persona_name: &str) -> String {
        let catchphrases = [
            format!("{}觉得呢~", persona_name),
            format!("{}", persona_name),
            "嗯嗯~".to_string(),
            "让我想想...".to_string(),
        ];
        catchphrases[rand::thread_rng().gen_range(0..catchphrases.len())].clone()
    }

    /// 根据情绪调整语气
    pub fn adjust_tone_by_emotion(&self, text: &str, emotion: &str) -> String {
        match emotion {
            "happy" | "excited" => format!("{}~", text.trim_end_matches('~')),
            "sad" => text.replace("！", "。").replace("!", "."),
            "shy" => format!("{}///", text),
            "angry" => text.to_string(),
            _ => text.to_string(),
        }
    }
}
