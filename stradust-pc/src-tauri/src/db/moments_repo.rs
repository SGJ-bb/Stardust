// 朋友圈CRUD

use rusqlite::{params, Connection};

use crate::db::database::DbError;
use crate::models::moments::{Moment, MomentVisibility, LikeRecord, Comment};

/// 创建朋友圈
pub fn create_moment(conn: &Connection, moment: &Moment) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO moments (id, persona_id, content, mood, images, likes, comments,
         is_auto_generated, visibility, created_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
        params![
            moment.id, moment.persona_id, moment.content, moment.mood,
            serde_json::to_string(&moment.images).unwrap_or_default(),
            serde_json::to_string(&moment.likes).unwrap_or_default(),
            serde_json::to_string(&moment.comments).unwrap_or_default(),
            moment.is_auto_generated as i32,
            serde_json::to_string(&moment.visibility).unwrap_or_else(|_| "\"public\"".to_string()),
            moment.created_at.to_string(), moment.updated_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 列出朋友圈
pub fn list_moments(conn: &Connection, persona_id: &str) -> Result<Vec<Moment>, DbError> {
    let mut stmt = conn.prepare(
        "SELECT id, persona_id, content, mood, images, likes, comments,
                is_auto_generated, visibility, created_at, updated_at
         FROM moments WHERE persona_id = ?1 ORDER BY created_at DESC"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let moments = stmt.query_map(params![persona_id], |row| {
        let images_str: String = row.get(4)?;
        let likes_str: String = row.get(5)?;
        let comments_str: String = row.get(6)?;
        let visibility_str: String = row.get(8)?;
        Ok(Moment {
            id: row.get(0)?,
            persona_id: row.get(1)?,
            content: row.get(2)?,
            mood: row.get(3)?,
            images: serde_json::from_str(&images_str).unwrap_or_default(),
            likes: serde_json::from_str(&likes_str).unwrap_or_default(),
            comments: serde_json::from_str(&comments_str).unwrap_or_default(),
            is_auto_generated: row.get::<_, i32>(7)? != 0,
            visibility: serde_json::from_str(&visibility_str).unwrap_or(MomentVisibility::Public),
            created_at: parse_dt(row.get(9)?),
            updated_at: parse_dt(row.get(10)?),
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|m| m.ok())
      .collect();

    Ok(moments)
}

/// 删除朋友圈
pub fn delete_moment(conn: &Connection, id: &str) -> Result<(), DbError> {
    conn.execute("DELETE FROM moments WHERE id = ?1", params![id])
        .map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}

/// 添加评论
pub fn add_comment(conn: &Connection, moment_id: &str, comment: &Comment) -> Result<(), DbError> {
    // 获取现有评论
    let comments_str: String = conn.query_row(
        "SELECT comments FROM moments WHERE id = ?1",
        params![moment_id],
        |row| row.get(0),
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let mut comments: Vec<Comment> = serde_json::from_str(&comments_str).unwrap_or_default();
    comments.push(comment.clone());

    let now = chrono::Local::now().naive_local().to_string();
    conn.execute(
        "UPDATE moments SET comments = ?1, updated_at = ?2 WHERE id = ?3",
        params![serde_json::to_string(&comments).unwrap_or_default(), now, moment_id],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 切换点赞
pub fn toggle_like(conn: &Connection, moment_id: &str, like: &LikeRecord) -> Result<bool, DbError> {
    let likes_str: String = conn.query_row(
        "SELECT likes FROM moments WHERE id = ?1",
        params![moment_id],
        |row| row.get(0),
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let mut likes: Vec<LikeRecord> = serde_json::from_str(&likes_str).unwrap_or_default();

    // 检查是否已点赞
    let existing_idx = likes.iter().position(|l| l.liker_id == like.liker_id);
    let is_liking = if let Some(idx) = existing_idx {
        likes.remove(idx);
        false
    } else {
        likes.push(like.clone());
        true
    };

    let now = chrono::Local::now().naive_local().to_string();
    conn.execute(
        "UPDATE moments SET likes = ?1, updated_at = ?2 WHERE id = ?3",
        params![serde_json::to_string(&likes).unwrap_or_default(), now, moment_id],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(is_liking)
}

fn parse_dt(s: String) -> chrono::NaiveDateTime {
    chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%d %H:%M:%S")
        .unwrap_or_else(|_| chrono::Local::now().naive_local())
}
