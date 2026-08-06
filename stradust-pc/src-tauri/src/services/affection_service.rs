// 好感度系统，对应原affection/AffectionManager.kt

use crate::db::database::Database;
use crate::db::achievement_repo;
use crate::models::achievement::{AffectionData, AffectionEvaluation, AffectionLevel};

/// 好感度服务
pub struct AffectionService;

impl AffectionService {
    pub fn new() -> Self {
        AffectionService
    }

    /// 评估用户行为对好感度的影响
    pub fn evaluate_user_behavior(&self, user_message: &str, is_positive: bool) -> AffectionEvaluation {
        let mut exp_change = 0i32;
        let mut trust_change = 0.0f32;
        let mut intimacy_change = 0.0f32;

        if is_positive {
            // 积极行为
            let positive_keywords = [
                ("谢谢", 5, 0.5, 0.2),
                ("喜欢", 8, 0.3, 0.8),
                ("爱", 15, 1.0, 1.5),
                ("想你", 10, 0.5, 1.0),
                ("陪伴", 8, 0.8, 0.5),
                ("关心", 6, 0.6, 0.4),
                ("分享", 5, 0.4, 0.3),
                ("早安", 3, 0.2, 0.1),
                ("晚安", 3, 0.2, 0.1),
            ];

            for (keyword, exp, trust, intimacy) in &positive_keywords {
                if user_message.contains(keyword) {
                    exp_change += exp;
                    trust_change += trust;
                    intimacy_change += intimacy;
                }
            }

            // 基础积极互动
            if exp_change == 0 {
                exp_change = 2;
                trust_change = 0.1;
                intimacy_change = 0.1;
            }
        } else {
            // 消极行为
            let negative_keywords = [
                ("讨厌", -10, -0.5, -0.8),
                ("滚", -15, -1.0, -1.0),
                ("烦", -8, -0.3, -0.5),
                ("无聊", -3, -0.1, -0.2),
                ("不要", -5, -0.2, -0.3),
            ];

            for (keyword, exp, trust, intimacy) in &negative_keywords {
                if user_message.contains(keyword) {
                    exp_change += exp;
                    trust_change += trust;
                    intimacy_change += intimacy;
                }
            }
        }

        let reason = if exp_change > 0 {
            format!("积极互动，好感度 +{}", exp_change)
        } else if exp_change < 0 {
            format!("消极互动，好感度 {}", exp_change)
        } else {
            "普通互动".to_string()
        };

        AffectionEvaluation {
            exp_change,
            trust_change,
            intimacy_change,
            reason,
        }
    }

    /// 获取好感度等级
    pub fn get_affection_level(&self, db: &Database, persona_id: &str) -> Result<AffectionLevel, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        match achievement_repo::get_affection_data(&conn, persona_id) {
            Ok(data) => Ok(data.level),
            Err(_) => Ok(AffectionLevel::Stranger),
        }
    }

    /// 添加经验值
    pub fn add_exp(&self, db: &Database, persona_id: &str, exp: i32, trust: f32, intimacy: f32) -> Result<AffectionData, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        achievement_repo::update_affection(&conn, persona_id, exp, trust, intimacy)
    }

    /// 获取好感度数据
    pub fn get_affection_data(&self, db: &Database, persona_id: &str) -> Result<AffectionData, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        achievement_repo::get_affection_data(&conn, persona_id)
    }

    /// 判断行为是否积极
    pub fn is_positive_behavior(&self, text: &str) -> bool {
        let positive = ["谢谢", "喜欢", "爱", "想你", "关心", "分享", "早安", "晚安", "好的", "可以", "没问题"];
        let negative = ["讨厌", "滚", "烦", "无聊", "不要", "闭嘴", "走开"];

        let pos_count = positive.iter().filter(|k| text.contains(*k)).count();
        let neg_count = negative.iter().filter(|k| text.contains(*k)).count();

        pos_count > neg_count
    }
}
