// 成就CRUD

use rusqlite::{params, Connection};

use crate::db::database::DbError;
use crate::models::achievement::{Achievement, AchievementType, CheckIn, AffectionData, AffectionLevel};
use crate::utils::helpers;

/// 创建成就
pub fn create_achievement(conn: &Connection, achievement: &Achievement) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO achievements (id, persona_id, achievement_type, title, description, icon,
         is_unlocked, progress, target, unlocked_at, created_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
        params![
            achievement.id, achievement.persona_id,
            serde_json::to_string(&achievement.achievement_type).unwrap_or_else(|_| "\"chat_count\"".to_string()),
            achievement.title, achievement.description, achievement.icon,
            achievement.is_unlocked as i32, achievement.progress, achievement.target,
            achievement.unlocked_at.map(|t| t.to_string()),
            achievement.created_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 列出成就
pub fn list_achievements(conn: &Connection, persona_id: &str) -> Result<Vec<Achievement>, DbError> {
    let mut stmt = conn.prepare(
        "SELECT id, persona_id, achievement_type, title, description, icon,
                is_unlocked, progress, target, unlocked_at, created_at
         FROM achievements WHERE persona_id = ?1 ORDER BY created_at"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let achievements = stmt.query_map(params![persona_id], |row| {
        let type_str: String = row.get(2)?;
        let unlocked_at_str: Option<String> = row.get(9)?;
        Ok(Achievement {
            id: row.get(0)?,
            persona_id: row.get(1)?,
            achievement_type: serde_json::from_str(&type_str).unwrap_or(AchievementType::ChatCount),
            title: row.get(3)?,
            description: row.get(4)?,
            icon: row.get(5)?,
            is_unlocked: row.get::<_, i32>(6)? != 0,
            progress: row.get(7)?,
            target: row.get(8)?,
            unlocked_at: unlocked_at_str.map(|s| helpers::parse_dt(s)),
            created_at: helpers::parse_dt(row.get(10)?),
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|a| a.ok())
      .collect();

    Ok(achievements)
}

/// 更新成就进度
pub fn update_achievement_progress(conn: &Connection, id: &str, progress: f32) -> Result<(), DbError> {
    let now = chrono::Local::now().naive_local().to_string();
    let unlocked = progress >= 1.0;
    conn.execute(
        "UPDATE achievements SET progress = ?1, is_unlocked = ?2, unlocked_at = CASE WHEN ?2 = 1 THEN ?3 ELSE unlocked_at END WHERE id = ?4",
        params![progress, unlocked as i32, now, id],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}

/// 签到
pub fn check_in(conn: &Connection, persona_id: &str) -> Result<CheckIn, DbError> {
    let today = chrono::Local::now().naive_local().date();
    let now = chrono::Local::now().naive_local();
    let today_str = today.to_string();

    // 检查今天是否已签到
    let already_checked: bool = conn.query_row(
        "SELECT COUNT(*) FROM check_ins WHERE persona_id = ?1 AND check_in_date = ?2",
        params![persona_id, today_str],
        |row| row.get::<_, i32>(0).map(|c| c > 0),
    ).unwrap_or(false);

    if already_checked {
        return Err(DbError::AlreadyExists("今天已经签到过了".to_string()));
    }

    // 获取最近一次签到记录
    let last_check_in: Option<(String, u32)> = conn.query_row(
        "SELECT check_in_date, streak_count FROM check_ins WHERE persona_id = ?1 ORDER BY check_in_date DESC LIMIT 1",
        params![persona_id],
        |row| Ok((row.get::<_, String>(0)?, row.get::<_, u32>(1)?)),
    ).ok();

    // 判断是否连续：检查最近一次签到日期是否是昨天
    let new_streak = if let Some((last_date_str, last_streak)) = last_check_in {
        if let Ok(last_date) = chrono::NaiveDate::parse_from_str(&last_date_str, "%Y-%m-%d") {
            let yesterday = today - chrono::Duration::days(1);
            if last_date == yesterday {
                // 连续签到
                last_streak + 1
            } else {
                // 中断了，重新开始
                1
            }
        } else {
            1
        }
    } else {
        // 第一次签到
        1
    };

    let reward_exp = 10 + new_streak * 5;

    let check_in_record = CheckIn {
        id: uuid::Uuid::new_v4().to_string(),
        persona_id: persona_id.to_string(),
        check_in_date: now,
        streak_count: new_streak,
        reward_exp,
        created_at: now,
    };

    conn.execute(
        "INSERT INTO check_ins (id, persona_id, check_in_date, streak_count, reward_exp, created_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        params![
            check_in_record.id, check_in_record.persona_id,
            check_in_record.check_in_date.to_string(),
            check_in_record.streak_count, check_in_record.reward_exp,
            check_in_record.created_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(check_in_record)
}

/// 获取好感度数据
pub fn get_affection_data(conn: &Connection, persona_id: &str) -> Result<AffectionData, DbError> {
    conn.query_row(
        "SELECT persona_id, level, exp, total_exp, trust_score, intimacy_score, updated_at
         FROM affection_data WHERE persona_id = ?1",
        params![persona_id],
        |row| {
            let level_str: String = row.get(1)?;
            // 兼容两种格式：带引号的JSON格式和不带引号的纯字符串
            let level: AffectionLevel = if level_str.starts_with('"') {
                serde_json::from_str(&level_str).unwrap_or(AffectionLevel::Stranger)
            } else {
                match level_str.as_str() {
                    "soulmate" => AffectionLevel::Soulmate,
                    "close_friend" => AffectionLevel::CloseFriend,
                    "friend" => AffectionLevel::Friend,
                    "acquaintance" => AffectionLevel::Acquaintance,
                    _ => AffectionLevel::Stranger,
                }
            };
            let exp = row.get(2)?;
            Ok(AffectionData {
                persona_id: row.get(0)?,
                level,
                exp,
                exp_to_next_level: AffectionLevel::from_exp(exp).exp_to_next() - exp,
                total_exp: row.get(3)?,
                trust_score: row.get(4)?,
                intimacy_score: row.get(5)?,
                updated_at: helpers::parse_dt(row.get(6)?),
            })
        },
    ).map_err(|e| match e {
        rusqlite::Error::QueryReturnedNoRows => DbError::NotFound(format!("角色 {} 无好感度数据", persona_id)),
        e => DbError::QueryFailed(e.to_string()),
    })
}

/// 更新好感度
pub fn update_affection(conn: &Connection, persona_id: &str, exp_delta: i32, trust_delta: f32, intimacy_delta: f32) -> Result<AffectionData, DbError> {
    let now = chrono::Local::now().naive_local().to_string();

    // 先尝试获取现有数据
    let exists: bool = conn.query_row(
        "SELECT COUNT(*) FROM affection_data WHERE persona_id = ?1",
        params![persona_id],
        |row| row.get::<_, i32>(0).map(|c| c > 0),
    ).unwrap_or(false);

    if !exists {
        // 初始化好感度数据，level 使用不带引号的字符串，与 UPDATE 中的 CASE 一致
        conn.execute(
            "INSERT INTO affection_data (persona_id, level, exp, total_exp, trust_score, intimacy_score, updated_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            params![persona_id, "stranger", 0i32, 0i32, 0.0f64, 0.0f64, now],
        ).map_err(|e| DbError::QueryFailed(e.to_string()))?;
    }

    // 更新数据
    // total_exp 只累加正的 exp 变化，避免因惩罚导致总经验下降
    let positive_exp_delta = exp_delta.max(0);
    conn.execute(
        "UPDATE affection_data SET exp = MAX(0, exp + ?1), total_exp = total_exp + ?2,
         trust_score = MAX(0, MIN(100, trust_score + ?3)),
         intimacy_score = MAX(0, MIN(100, intimacy_score + ?4)),
         level = CASE
            WHEN MAX(0, exp + ?1) >= 4000 THEN 'soulmate'
            WHEN MAX(0, exp + ?1) >= 1500 THEN 'close_friend'
            WHEN MAX(0, exp + ?1) >= 500 THEN 'friend'
            WHEN MAX(0, exp + ?1) >= 100 THEN 'acquaintance'
            ELSE 'stranger'
         END,
         updated_at = ?5
         WHERE persona_id = ?6",
        params![exp_delta, positive_exp_delta as i64, trust_delta, intimacy_delta, now, persona_id],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    get_affection_data(conn, persona_id)
}
