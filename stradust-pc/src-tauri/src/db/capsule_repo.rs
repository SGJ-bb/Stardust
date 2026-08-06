// 时光胶囊CRUD

use rusqlite::{params, Connection};

use crate::db::database::DbError;
use crate::models::capsule::TimeCapsule;

/// 创建时光胶囊
pub fn create_capsule(conn: &Connection, capsule: &TimeCapsule) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO time_capsules (id, persona_id, title, content, mood, images,
         sealed_at, open_at, is_opened, opened_at, created_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
        params![
            capsule.id, capsule.persona_id, capsule.title, capsule.content, capsule.mood,
            serde_json::to_string(&capsule.images).unwrap_or_default(),
            capsule.sealed_at.to_string(), capsule.open_at.to_string(),
            capsule.is_opened as i32,
            capsule.opened_at.map(|t| t.to_string()),
            capsule.created_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 列出时光胶囊
pub fn list_capsules(conn: &Connection, persona_id: &str) -> Result<Vec<TimeCapsule>, DbError> {
    let mut stmt = conn.prepare(
        "SELECT id, persona_id, title, content, mood, images, sealed_at, open_at,
                is_opened, opened_at, created_at
         FROM time_capsules WHERE persona_id = ?1 ORDER BY open_at"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let capsules = stmt.query_map(params![persona_id], |row| {
        let images_str: String = row.get(5)?;
        let opened_at_str: Option<String> = row.get(9)?;
        Ok(TimeCapsule {
            id: row.get(0)?,
            persona_id: row.get(1)?,
            title: row.get(2)?,
            content: row.get(3)?,
            mood: row.get(4)?,
            images: serde_json::from_str(&images_str).unwrap_or_default(),
            sealed_at: parse_dt(row.get(6)?),
            open_at: parse_dt(row.get(7)?),
            is_opened: row.get::<_, i32>(8)? != 0,
            opened_at: opened_at_str.map(|s| parse_dt(s)),
            created_at: parse_dt(row.get(10)?),
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|c| c.ok())
      .collect();

    Ok(capsules)
}

/// 打开时光胶囊
pub fn open_capsule(conn: &Connection, id: &str) -> Result<TimeCapsule, DbError> {
    let now = chrono::Local::now().naive_local();

    // 先检查是否可以打开
    let capsule = conn.query_row(
        "SELECT id, persona_id, title, content, mood, images, sealed_at, open_at,
                is_opened, opened_at, created_at
         FROM time_capsules WHERE id = ?1",
        params![id],
        |row| {
            let images_str: String = row.get(5)?;
            let opened_at_str: Option<String> = row.get(9)?;
            Ok(TimeCapsule {
                id: row.get(0)?,
                persona_id: row.get(1)?,
                title: row.get(2)?,
                content: row.get(3)?,
                mood: row.get(4)?,
                images: serde_json::from_str(&images_str).unwrap_or_default(),
                sealed_at: parse_dt(row.get(6)?),
                open_at: parse_dt(row.get(7)?),
                is_opened: row.get::<_, i32>(8)? != 0,
                opened_at: opened_at_str.map(|s| parse_dt(s)),
                created_at: parse_dt(row.get(10)?),
            })
        },
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    if capsule.is_opened {
        return Err(DbError::AlreadyExists("时光胶囊已经打开过了".to_string()));
    }

    if now < capsule.open_at {
        return Err(DbError::QueryFailed("还没到打开时间".to_string()));
    }

    // 更新为已打开
    conn.execute(
        "UPDATE time_capsules SET is_opened = 1, opened_at = ?1 WHERE id = ?2",
        params![now.to_string(), id],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let mut opened = capsule;
    opened.is_opened = true;
    opened.opened_at = Some(now);
    Ok(opened)
}

fn parse_dt(s: String) -> chrono::NaiveDateTime {
    chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%d %H:%M:%S")
        .unwrap_or_else(|_| chrono::Local::now().naive_local())
}
