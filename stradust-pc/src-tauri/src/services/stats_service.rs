// 统计/成就/签到/成长，对应原gamify/和stats/包

use crate::db::database::Database;
use crate::db::achievement_repo;
use crate::models::achievement::{Achievement, AchievementType, CheckIn, AffectionData, Growth};
use crate::utils::helpers;

/// 统计服务
pub struct StatsService;

impl StatsService {
    pub fn new() -> Self {
        StatsService
    }

    /// 列出成就
    pub fn list_achievements(&self, db: &Database, persona_id: &str) -> Result<Vec<Achievement>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        achievement_repo::list_achievements(&conn, persona_id)
    }

    /// 签到
    pub fn check_in(&self, db: &Database, persona_id: &str) -> Result<CheckIn, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        achievement_repo::check_in(&conn, persona_id)
    }

    /// 获取好感度
    pub fn get_affection(&self, db: &Database, persona_id: &str) -> Result<AffectionData, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        achievement_repo::get_affection_data(&conn, persona_id)
    }

    /// 更新好感度
    pub fn update_affection(&self, db: &Database, persona_id: &str, exp_delta: i32, trust_delta: f32, intimacy_delta: f32) -> Result<AffectionData, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        achievement_repo::update_affection(&conn, persona_id, exp_delta, trust_delta, intimacy_delta)
    }

    /// 获取成长数据
    pub fn get_growth(&self, db: &Database, persona_id: &str) -> Growth {
        let affection = self.get_affection(db, persona_id).ok();

        match affection {
            Some(data) => {
                let level = match data.level {
                    crate::models::achievement::AffectionLevel::Stranger => 1,
                    crate::models::achievement::AffectionLevel::Acquaintance => 2,
                    crate::models::achievement::AffectionLevel::Friend => 3,
                    crate::models::achievement::AffectionLevel::CloseFriend => 4,
                    crate::models::achievement::AffectionLevel::Soulmate => 5,
                };

                let title = match data.level {
                    crate::models::achievement::AffectionLevel::Stranger => "初识",
                    crate::models::achievement::AffectionLevel::Acquaintance => "相识",
                    crate::models::achievement::AffectionLevel::Friend => "挚友",
                    crate::models::achievement::AffectionLevel::CloseFriend => "密友",
                    crate::models::achievement::AffectionLevel::Soulmate => "灵魂伴侣",
                };

                Growth {
                    persona_id: persona_id.to_string(),
                    level,
                    exp: data.exp,
                    exp_to_next_level: data.exp_to_next_level,
                    total_exp: data.total_exp,
                    title: title.to_string(),
                }
            }
            None => Growth {
                persona_id: persona_id.to_string(),
                level: 1,
                exp: 0,
                exp_to_next_level: 100,
                total_exp: 0,
                title: "初识".to_string(),
            },
        }
    }

    /// 检查并更新成就进度
    pub fn check_achievements(&self, db: &Database, persona_id: &str) -> Result<Vec<Achievement>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        let achievements = achievement_repo::list_achievements(&conn, persona_id)?;

        // 检查每个成就的进度
        for achievement in &achievements {
            let progress = self.calculate_achievement_progress(db, persona_id, &achievement.achievement_type);
            if let Ok(p) = progress {
                let _ = achievement_repo::update_achievement_progress(&conn, &achievement.id, p);
            }
        }

        achievement_repo::list_achievements(&conn, persona_id)
    }

    /// 计算成就进度
    fn calculate_achievement_progress(&self, db: &Database, persona_id: &str, achievement_type: &AchievementType) -> Result<f32, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        match achievement_type {
            AchievementType::ChatCount => {
                let role_value = serde_json::to_string(&crate::models::chat::ChatRole::User)
                    .unwrap_or_else(|_| "\"user\"".to_string());
                let count: i32 = conn.query_row(
                    "SELECT COUNT(*) FROM chat_messages WHERE persona_id = ?1 AND role = ?2",
                    rusqlite::params![persona_id, role_value],
                    |row| row.get(0),
                ).unwrap_or(0);
                Ok((count as f32 / 100.0).min(1.0))
            }
            AchievementType::CheckInStreak => {
                let streak: i32 = conn.query_row(
                    "SELECT MAX(streak_count) FROM check_ins WHERE persona_id = ?1",
                    rusqlite::params![persona_id],
                    |row| row.get(0),
                ).unwrap_or(0);
                Ok((streak as f32 / 7.0).min(1.0))
            }
            AchievementType::MemoryCount => {
                let count: i32 = conn.query_row(
                    "SELECT COUNT(*) FROM memories WHERE persona_id = ?1 AND is_active = 1",
                    rusqlite::params![persona_id],
                    |row| row.get(0),
                ).unwrap_or(0);
                Ok((count as f32 / 50.0).min(1.0))
            }
            AchievementType::DiaryCount => {
                let count: i32 = conn.query_row(
                    "SELECT COUNT(*) FROM diaries WHERE persona_id = ?1",
                    rusqlite::params![persona_id],
                    |row| row.get(0),
                ).unwrap_or(0);
                Ok((count as f32 / 10.0).min(1.0))
            }
            _ => Ok(0.0),
        }
    }
}
