// 情绪分析+守护，对应原emotion/包

use crate::models::emotion::*;

/// 情绪服务
pub struct EmotionService;

impl EmotionService {
    pub fn new() -> Self {
        EmotionService
    }

    /// 分析文本情绪
    pub fn analyze_emotion(&self, text: &str) -> EmotionAnalysisResult {
        // 基于关键词的情绪分析
        let emotion_scores = self.calculate_emotion_scores(text);

        // 找出最高分的情绪
        let (primary_emotion, primary_score) = emotion_scores
            .iter()
            .max_by(|a, b| a.1.partial_cmp(&b.1).unwrap_or(std::cmp::Ordering::Equal))
            .map(|(e, s)| (*e, *s))
            .unwrap_or((Emotion::Neutral, 0.5));

        // 找出次要情绪
        let secondary_emotion = emotion_scores
            .iter()
            .filter(|(e, _)| *e != primary_emotion)
            .max_by(|a, b| a.1.partial_cmp(&b.1).unwrap_or(std::cmp::Ordering::Equal))
            .map(|(e, _)| *e);

        EmotionAnalysisResult {
            emotion: primary_emotion,
            confidence: primary_score,
            secondary_emotion,
            intensity: primary_emotion.intensity() * primary_score,
        }
    }

    /// 计算各情绪得分
    fn calculate_emotion_scores(&self, text: &str) -> Vec<(Emotion, f32)> {
        let keyword_map: Vec<(Emotion, &[&str])> = vec![
            (Emotion::Happy, &["开心", "高兴", "快乐", "幸福", "棒", "好", "哈哈", "嘻嘻", "😊", "😄", "happy", "glad"]),
            (Emotion::Sad, &["难过", "伤心", "悲伤", "哭", "泪", "遗憾", "😢", "😭", "sad", "sorry"]),
            (Emotion::Angry, &["生气", "愤怒", "烦", "讨厌", "恨", "气死", "😡", "angry", "mad"]),
            (Emotion::Surprised, &["惊讶", "吃惊", "天哪", "不会吧", "哇", "😱", "😮", "surprised", "wow"]),
            (Emotion::Tsundere, &["哼", "才不是", "才没有", "别误会", "傲娇", "tsundere"]),
            (Emotion::Shy, &["害羞", "不好意思", "脸红", "腼腆", "😳", "shy"]),
            (Emotion::Excited, &["兴奋", "激动", "期待", "太好了", "🎉", "excited"]),
            (Emotion::Calm, &["平静", "冷静", "淡定", "还好", "嗯", "calm"]),
            (Emotion::Worried, &["担心", "忧虑", "不安", "纠结", "worried"]),
            (Emotion::Neutral, &["哦", "嗯", "好的", "知道了", "neutral"]),
        ];

        keyword_map
            .iter()
            .map(|(emotion, keywords)| {
                let score = keywords.iter()
                    .filter(|kw| text.contains(*kw))
                    .count() as f32 / keywords.len() as f32;
                let adjusted_score = (score * 3.0).min(1.0); // 放大得分
                (*emotion, adjusted_score)
            })
            .collect()
    }

    /// 情绪守护，防止极端情绪
    pub fn guard_emotion(&self, emotion: &Emotion, context: &EmotionContext) -> EmotionGuardResult {
        let mut adjusted = emotion.clone();
        let mut was_adjusted = false;
        let mut reason = None;

        // 如果用户情绪很低落，AI不应过于开心
        if context.user_emotion == Some(Emotion::Sad) && *emotion == Emotion::Happy {
            adjusted = Emotion::Calm;
            was_adjusted = true;
            reason = Some("用户情绪低落时，调整为平静温和的情绪".to_string());
        }

        // 如果用户很生气，AI不应表现出无所谓
        if context.user_emotion == Some(Emotion::Angry) && *emotion == Emotion::Neutral {
            adjusted = Emotion::Worried;
            was_adjusted = true;
            reason = Some("用户生气时，调整为关心的情绪".to_string());
        }

        // 如果连续多次负面情绪，注入积极情绪
        if context.negative_streak >= 3 && emotion.intensity() > 0.7 {
            adjusted = Emotion::Calm;
            was_adjusted = true;
            reason = Some("连续负面情绪过多，注入平静情绪".to_string());
        }

        EmotionGuardResult {
            original_emotion: emotion.clone(),
            adjusted_emotion: adjusted,
            was_adjusted,
            reason,
        }
    }

    /// 情绪到动作的映射
    pub fn map_emotion_to_action(&self, emotion: &Emotion) -> Action {
        match emotion {
            Emotion::Happy => Action::Smile,
            Emotion::Sad => Action::Idle,
            Emotion::Angry => Action::Angry,
            Emotion::Tsundere => Action::Tsundere,
            Emotion::Shy => Action::Shy,
            Emotion::Excited => Action::Clap,
            Emotion::Calm => Action::Idle,
            Emotion::Worried => Action::Think,
            Emotion::Surprised => Action::Surprise,
            Emotion::Neutral => Action::Idle,
        }
    }

    /// 动作到Live2D参数的映射
    pub fn map_action_to_live2d(&self, action: &Action) -> Live2DAction {
        match action {
            Action::Idle => Live2DAction { motion_group: "Idle".to_string(), motion_index: 0, expression_name: "neutral".to_string(), duration_ms: 3000 },
            Action::Talk => Live2DAction { motion_group: "TapBody".to_string(), motion_index: 0, expression_name: "neutral".to_string(), duration_ms: 2000 },
            Action::Nod => Live2DAction { motion_group: "Nod".to_string(), motion_index: 0, expression_name: "neutral".to_string(), duration_ms: 1500 },
            Action::ShakeHead => Live2DAction { motion_group: "Shake".to_string(), motion_index: 0, expression_name: "neutral".to_string(), duration_ms: 1500 },
            Action::Wave => Live2DAction { motion_group: "Wave".to_string(), motion_index: 0, expression_name: "happy".to_string(), duration_ms: 2000 },
            Action::Think => Live2DAction { motion_group: "Idle".to_string(), motion_index: 1, expression_name: "thinking".to_string(), duration_ms: 3000 },
            Action::Smile => Live2DAction { motion_group: "Idle".to_string(), motion_index: 0, expression_name: "happy".to_string(), duration_ms: 2000 },
            Action::Cry => Live2DAction { motion_group: "Idle".to_string(), motion_index: 2, expression_name: "sad".to_string(), duration_ms: 3000 },
            Action::Angry => Live2DAction { motion_group: "TapBody".to_string(), motion_index: 1, expression_name: "angry".to_string(), duration_ms: 2000 },
            Action::Surprise => Live2DAction { motion_group: "TapBody".to_string(), motion_index: 2, expression_name: "surprised".to_string(), duration_ms: 1500 },
            Action::Shy => Live2DAction { motion_group: "Idle".to_string(), motion_index: 3, expression_name: "shy".to_string(), duration_ms: 2000 },
            Action::Tsundere => Live2DAction { motion_group: "Shake".to_string(), motion_index: 1, expression_name: "angry".to_string(), duration_ms: 2000 },
            Action::Hug => Live2DAction { motion_group: "Wave".to_string(), motion_index: 1, expression_name: "happy".to_string(), duration_ms: 2500 },
            Action::Clap => Live2DAction { motion_group: "Wave".to_string(), motion_index: 2, expression_name: "happy".to_string(), duration_ms: 2000 },
            Action::Yawn => Live2DAction { motion_group: "Idle".to_string(), motion_index: 4, expression_name: "neutral".to_string(), duration_ms: 3000 },
            Action::Stretch => Live2DAction { motion_group: "Idle".to_string(), motion_index: 5, expression_name: "neutral".to_string(), duration_ms: 2000 },
            Action::Dance => Live2DAction { motion_group: "Dance".to_string(), motion_index: 0, expression_name: "happy".to_string(), duration_ms: 5000 },
            Action::Eat => Live2DAction { motion_group: "TapBody".to_string(), motion_index: 3, expression_name: "happy".to_string(), duration_ms: 3000 },
            Action::Drink => Live2DAction { motion_group: "TapBody".to_string(), motion_index: 4, expression_name: "neutral".to_string(), duration_ms: 2000 },
            Action::Read => Live2DAction { motion_group: "Idle".to_string(), motion_index: 6, expression_name: "thinking".to_string(), duration_ms: 4000 },
            Action::Sleep => Live2DAction { motion_group: "Idle".to_string(), motion_index: 7, expression_name: "neutral".to_string(), duration_ms: 5000 },
        }
    }
}

/// 情绪上下文
#[derive(Debug, Clone, Default)]
pub struct EmotionContext {
    pub user_emotion: Option<Emotion>,
    pub previous_emotion: Option<Emotion>,
    pub negative_streak: u32,
    pub conversation_mood: Option<Emotion>,
}
