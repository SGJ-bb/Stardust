// 纪念相册CRUD

use rusqlite::{params, Connection};

use crate::db::database::DbError;
use crate::models::album::AlbumEntry;

/// 添加相册条目
pub fn add_album_entry(conn: &Connection, entry: &AlbumEntry) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO album_entries (id, persona_id, title, description, image_path, thumbnail,
         tags, mood, is_milestone, event_date, created_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)",
        params![
            entry.id, entry.persona_id, entry.title, entry.description,
            entry.image_path, entry.thumbnail,
            serde_json::to_string(&entry.tags).unwrap_or_default(),
            entry.mood, entry.is_milestone as i32,
            entry.event_date.map(|d| d.to_string()),
            entry.created_at.to_string(), entry.updated_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 列出相册条目
pub fn list_album_entries(conn: &Connection, persona_id: &str) -> Result<Vec<AlbumEntry>, DbError> {
    let mut stmt = conn.prepare(
        "SELECT id, persona_id, title, description, image_path, thumbnail, tags, mood,
                is_milestone, event_date, created_at, updated_at
         FROM album_entries WHERE persona_id = ?1 ORDER BY created_at DESC"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let entries = stmt.query_map(params![persona_id], |row| {
        let tags_str: String = row.get(6)?;
        let event_date_str: Option<String> = row.get(9)?;
        Ok(AlbumEntry {
            id: row.get(0)?,
            persona_id: row.get(1)?,
            title: row.get(2)?,
            description: row.get(3)?,
            image_path: row.get(4)?,
            thumbnail: row.get(5)?,
            tags: serde_json::from_str(&tags_str).unwrap_or_default(),
            mood: row.get(7)?,
            is_milestone: row.get::<_, i32>(8)? != 0,
            event_date: event_date_str.map(|s| parse_dt(s)),
            created_at: parse_dt(row.get(10)?),
            updated_at: parse_dt(row.get(11)?),
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|e| e.ok())
      .collect();

    Ok(entries)
}

/// 删除相册条目
pub fn delete_album_entry(conn: &Connection, id: &str) -> Result<(), DbError> {
    conn.execute("DELETE FROM album_entries WHERE id = ?1", params![id])
        .map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}

fn parse_dt(s: String) -> chrono::NaiveDateTime {
    chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%d %H:%M:%S")
        .unwrap_or_else(|_| chrono::Local::now().naive_local())
}
