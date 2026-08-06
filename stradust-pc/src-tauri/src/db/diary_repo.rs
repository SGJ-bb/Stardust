// 日记CRUD

use rusqlite::{params, Connection};

use crate::db::database::DbError;
use crate::models::diary::DiaryEntry;
use crate::utils::helpers;

/// 创建日记
pub fn create_diary(conn: &Connection, diary: &DiaryEntry) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO diaries (id, persona_id, title, content, mood, tags, date,
         is_auto_generated, created_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)",
        params![
            diary.id, diary.persona_id, diary.title, diary.content, diary.mood,
            serde_json::to_string(&diary.tags).unwrap_or_default(),
            diary.date.to_string(), diary.is_auto_generated as i32,
            diary.created_at.to_string(), diary.updated_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 列出日记
pub fn list_diaries(conn: &Connection, persona_id: &str) -> Result<Vec<DiaryEntry>, DbError> {
    let mut stmt = conn.prepare(
        "SELECT id, persona_id, title, content, mood, tags, date,
                is_auto_generated, created_at, updated_at
         FROM diaries WHERE persona_id = ?1 ORDER BY date DESC"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let diaries = stmt.query_map(params![persona_id], |row| {
        let tags_str: String = row.get(5)?;
        Ok(DiaryEntry {
            id: row.get(0)?,
            persona_id: row.get(1)?,
            title: row.get(2)?,
            content: row.get(3)?,
            mood: row.get(4)?,
            tags: serde_json::from_str(&tags_str).unwrap_or_default(),
            date: helpers::parse_dt(row.get(6)?),
            is_auto_generated: row.get::<_, i32>(7)? != 0,
            created_at: helpers::parse_dt(row.get(8)?),
            updated_at: helpers::parse_dt(row.get(9)?),
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|d| d.ok())
      .collect();

    Ok(diaries)
}

/// 删除日记
pub fn delete_diary(conn: &Connection, id: &str) -> Result<(), DbError> {
    conn.execute("DELETE FROM diaries WHERE id = ?1", params![id])
        .map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}
