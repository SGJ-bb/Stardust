// 表情包CRUD

use rusqlite::{params, Connection};

use crate::db::database::DbError;
use crate::models::sticker::Sticker;

/// 添加表情包
pub fn add_sticker(conn: &Connection, sticker: &Sticker) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO stickers (id, name, file_path, thumbnail, tags, category,
         is_animated, width, height, file_size, created_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
        params![
            sticker.id, sticker.name, sticker.file_path, sticker.thumbnail,
            serde_json::to_string(&sticker.tags).unwrap_or_default(),
            sticker.category, sticker.is_animated as i32,
            sticker.width, sticker.height, sticker.file_size,
            sticker.created_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 列出表情包
pub fn list_stickers(conn: &Connection, category: Option<&str>) -> Result<Vec<Sticker>, DbError> {
    let sql = match category {
        Some(_) => "SELECT id, name, file_path, thumbnail, tags, category, is_animated, width, height, file_size, created_at FROM stickers WHERE category = ?1 ORDER BY created_at DESC",
        None => "SELECT id, name, file_path, thumbnail, tags, category, is_animated, width, height, file_size, created_at FROM stickers ORDER BY created_at DESC",
    };

    let mut stmt = conn.prepare(sql).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let stickers: Vec<Sticker> = if let Some(cat) = category {
        stmt.query_map(params![cat], |row| row_to_sticker(row))
            .map_err(|e| DbError::QueryFailed(e.to_string()))?
            .filter_map(|s| s.ok()).collect()
    } else {
        stmt.query_map([], |row| row_to_sticker(row))
            .map_err(|e| DbError::QueryFailed(e.to_string()))?
            .filter_map(|s| s.ok()).collect()
    };

    Ok(stickers)
}

/// 搜索表情包
pub fn search_stickers(conn: &Connection, query: &str) -> Result<Vec<Sticker>, DbError> {
    let pattern = format!("%{}%", query);
    let mut stmt = conn.prepare(
        "SELECT id, name, file_path, thumbnail, tags, category, is_animated, width, height, file_size, created_at
         FROM stickers WHERE name LIKE ?1 OR tags LIKE ?1 OR category LIKE ?1
         ORDER BY created_at DESC"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let stickers = stmt.query_map(params![pattern], |row| row_to_sticker(row))
        .map_err(|e| DbError::QueryFailed(e.to_string()))?
        .filter_map(|s| s.ok())
        .collect();

    Ok(stickers)
}

/// 删除表情包
pub fn delete_sticker(conn: &Connection, id: &str) -> Result<(), DbError> {
    conn.execute("DELETE FROM stickers WHERE id = ?1", params![id])
        .map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}

fn row_to_sticker(row: &rusqlite::Row) -> rusqlite::Result<Sticker> {
    let tags_str: String = row.get(4)?;
    Ok(Sticker {
        id: row.get(0)?,
        name: row.get(1)?,
        file_path: row.get(2)?,
        thumbnail: row.get(3)?,
        tags: serde_json::from_str(&tags_str).unwrap_or_default(),
        category: row.get(5)?,
        is_animated: row.get::<_, i32>(6)? != 0,
        width: row.get(7)?,
        height: row.get(8)?,
        file_size: row.get(9)?,
        created_at: parse_dt(row.get(10)?),
    })
}

fn parse_dt(s: String) -> chrono::NaiveDateTime {
    chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%d %H:%M:%S")
        .unwrap_or_else(|_| chrono::Local::now().naive_local())
}
