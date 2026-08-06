// 群聊CRUD

use rusqlite::{params, Connection};

use crate::db::database::DbError;
use crate::models::group_chat::{GroupChat, GroupMessage};

/// 创建群聊
pub fn create_group_chat(conn: &Connection, group: &GroupChat) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO group_chats (id, name, persona_ids, description, is_active, created_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
        params![
            group.id, group.name,
            serde_json::to_string(&group.persona_ids).unwrap_or_default(),
            group.description, group.is_active as i32,
            group.created_at.to_string(), group.updated_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 列出群聊
pub fn list_group_chats(conn: &Connection) -> Result<Vec<GroupChat>, DbError> {
    let mut stmt = conn.prepare(
        "SELECT id, name, persona_ids, description, is_active, created_at, updated_at
         FROM group_chats WHERE is_active = 1 ORDER BY updated_at DESC"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let groups = stmt.query_map([], |row| {
        let ids_str: String = row.get(2)?;
        Ok(GroupChat {
            id: row.get(0)?,
            name: row.get(1)?,
            persona_ids: serde_json::from_str(&ids_str).unwrap_or_default(),
            description: row.get(3)?,
            is_active: row.get::<_, i32>(4)? != 0,
            created_at: parse_dt(row.get(5)?),
            updated_at: parse_dt(row.get(6)?),
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|g| g.ok())
      .collect();

    Ok(groups)
}

/// 删除群聊
pub fn delete_group_chat(conn: &Connection, id: &str) -> Result<(), DbError> {
    conn.execute("DELETE FROM group_chats WHERE id = ?1", params![id])
        .map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}

/// 保存群聊消息
pub fn save_group_message(conn: &Connection, msg: &GroupMessage) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO group_messages (id, group_id, persona_id, content, emotion, action, timestamp)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
        params![msg.id, msg.group_id, msg.persona_id, msg.content,
                msg.emotion, msg.action, msg.timestamp.to_string()],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 获取群聊消息
pub fn get_group_messages(conn: &Connection, group_id: &str, limit: Option<u32>) -> Result<Vec<GroupMessage>, DbError> {
    let sql = match limit {
        Some(l) => format!(
            "SELECT id, group_id, persona_id, content, emotion, action, timestamp
             FROM group_messages WHERE group_id = ?1 ORDER BY timestamp ASC LIMIT {}", l
        ),
        None => "SELECT id, group_id, persona_id, content, emotion, action, timestamp
                 FROM group_messages WHERE group_id = ?1 ORDER BY timestamp ASC".to_string(),
    };

    let mut stmt = conn.prepare(&sql).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let messages = stmt.query_map(params![group_id], |row| {
        Ok(GroupMessage {
            id: row.get(0)?,
            group_id: row.get(1)?,
            persona_id: row.get(2)?,
            content: row.get(3)?,
            emotion: row.get(4)?,
            action: row.get(5)?,
            timestamp: parse_dt(row.get(6)?),
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|m| m.ok())
      .collect();

    Ok(messages)
}

fn parse_dt(s: String) -> chrono::NaiveDateTime {
    chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%d %H:%M:%S")
        .unwrap_or_else(|_| chrono::Local::now().naive_local())
}
