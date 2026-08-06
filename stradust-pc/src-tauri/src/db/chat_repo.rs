// 聊天记录CRUD

use rusqlite::{params, Connection};

use crate::db::database::DbError;
use crate::models::chat::{ChatMessage, ChatRole, ChatSession, ToolCall};

/// 创建聊天会话
pub fn create_session(conn: &Connection, persona_id: &str, title: Option<&str>) -> Result<ChatSession, DbError> {
    let id = uuid::Uuid::new_v4().to_string();
    let now = chrono::Local::now().naive_local();

    let session = ChatSession {
        id: id.clone(),
        persona_id: persona_id.to_string(),
        title: title.map(|t| t.to_string()),
        created_at: now,
        updated_at: now,
        is_active: true,
    };

    conn.execute(
        "INSERT INTO chat_sessions (id, persona_id, title, created_at, updated_at, is_active)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        params![session.id, session.persona_id, session.title,
                session.created_at.to_string(), session.updated_at.to_string(),
                session.is_active as i32],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(session)
}

/// 获取活跃会话
pub fn get_active_session(conn: &Connection, persona_id: &str) -> Result<Option<ChatSession>, DbError> {
    let result = conn.query_row(
        "SELECT id, persona_id, title, created_at, updated_at, is_active
         FROM chat_sessions WHERE persona_id = ?1 AND is_active = 1
         ORDER BY updated_at DESC LIMIT 1",
        params![persona_id],
        |row| {
            Ok(ChatSession {
                id: row.get(0)?,
                persona_id: row.get(1)?,
                title: row.get(2)?,
                created_at: parse_dt(row.get(3)?),
                updated_at: parse_dt(row.get(4)?),
                is_active: row.get::<_, i32>(5)? != 0,
            })
        },
    );

    match result {
        Ok(session) => Ok(Some(session)),
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(e) => Err(DbError::QueryFailed(e.to_string())),
    }
}

/// 列出会话
pub fn list_sessions(conn: &Connection, persona_id: &str) -> Result<Vec<ChatSession>, DbError> {
    let mut stmt = conn.prepare(
        "SELECT id, persona_id, title, created_at, updated_at, is_active
         FROM chat_sessions WHERE persona_id = ?1 ORDER BY updated_at DESC"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let sessions = stmt.query_map(params![persona_id], |row| {
        Ok(ChatSession {
            id: row.get(0)?,
            persona_id: row.get(1)?,
            title: row.get(2)?,
            created_at: parse_dt(row.get(3)?),
            updated_at: parse_dt(row.get(4)?),
            is_active: row.get::<_, i32>(5)? != 0,
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|s| s.ok())
      .collect();

    Ok(sessions)
}

/// 保存聊天消息
pub fn save_message(conn: &Connection, msg: &ChatMessage) -> Result<(), DbError> {
    let tool_calls_json = msg.tool_calls.as_ref()
        .map(|tc| serde_json::to_string(tc).unwrap_or_default());

    conn.execute(
        "INSERT INTO chat_messages (id, persona_id, session_id, role, content, emotion, action, tool_calls, timestamp, is_favorite)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)",
        params![
            msg.id, msg.persona_id, msg.session_id,
            serde_json::to_string(&msg.role).unwrap_or_else(|_| "\"user\"".to_string()),
            msg.content, msg.emotion, msg.action, tool_calls_json,
            msg.timestamp.to_string(), msg.is_favorite as i32
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 获取会话消息
pub fn get_session_messages(conn: &Connection, session_id: &str, limit: Option<u32>) -> Result<Vec<ChatMessage>, DbError> {
    let sql = match limit {
        Some(l) => format!(
            "SELECT id, persona_id, session_id, role, content, emotion, action, tool_calls, timestamp, is_favorite
             FROM chat_messages WHERE session_id = ?1 ORDER BY timestamp ASC LIMIT {}", l
        ),
        None => "SELECT id, persona_id, session_id, role, content, emotion, action, tool_calls, timestamp, is_favorite
                 FROM chat_messages WHERE session_id = ?1 ORDER BY timestamp ASC".to_string(),
    };

    let mut stmt = conn.prepare(&sql).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let messages = stmt.query_map(params![session_id], |row| {
        let role_str: String = row.get(3)?;
        let role: ChatRole = serde_json::from_str(&role_str).unwrap_or(ChatRole::User);
        let tool_calls_str: Option<String> = row.get(7)?;
        let tool_calls: Option<Vec<ToolCall>> = tool_calls_str
            .and_then(|s| serde_json::from_str(&s).ok());

        Ok(ChatMessage {
            id: row.get(0)?,
            persona_id: row.get(1)?,
            session_id: row.get(2)?,
            role,
            content: row.get(4)?,
            emotion: row.get(5)?,
            action: row.get(6)?,
            tool_calls,
            timestamp: parse_dt(row.get(8)?),
            is_favorite: row.get::<_, i32>(9)? != 0,
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|m| m.ok())
      .collect();

    Ok(messages)
}

/// 切换收藏状态
pub fn toggle_favorite(conn: &Connection, message_id: &str) -> Result<bool, DbError> {
    let current: bool = conn.query_row(
        "SELECT is_favorite FROM chat_messages WHERE id = ?1",
        params![message_id],
        |row| row.get::<_, i32>(0).map(|v| v != 0),
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let new_val = !current;
    conn.execute(
        "UPDATE chat_messages SET is_favorite = ?1 WHERE id = ?2",
        params![new_val as i32, message_id],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(new_val)
}

/// 清除会话消息
pub fn clear_session_messages(conn: &Connection, session_id: &str) -> Result<(), DbError> {
    conn.execute("DELETE FROM chat_messages WHERE session_id = ?1", params![session_id])
        .map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}

/// 删除会话
pub fn delete_session(conn: &Connection, session_id: &str) -> Result<(), DbError> {
    conn.execute("DELETE FROM chat_sessions WHERE id = ?1", params![session_id])
        .map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}

/// 获取收藏消息
pub fn get_favorite_messages(conn: &Connection, persona_id: &str) -> Result<Vec<ChatMessage>, DbError> {
    let mut stmt = conn.prepare(
        "SELECT id, persona_id, session_id, role, content, emotion, action, tool_calls, timestamp, is_favorite
         FROM chat_messages WHERE persona_id = ?1 AND is_favorite = 1 ORDER BY timestamp DESC"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let messages = stmt.query_map(params![persona_id], |row| {
        let role_str: String = row.get(3)?;
        let role: ChatRole = serde_json::from_str(&role_str).unwrap_or(ChatRole::User);
        let tool_calls_str: Option<String> = row.get(7)?;
        let tool_calls: Option<Vec<ToolCall>> = tool_calls_str
            .and_then(|s| serde_json::from_str(&s).ok());

        Ok(ChatMessage {
            id: row.get(0)?,
            persona_id: row.get(1)?,
            session_id: row.get(2)?,
            role,
            content: row.get(4)?,
            emotion: row.get(5)?,
            action: row.get(6)?,
            tool_calls,
            timestamp: parse_dt(row.get(8)?),
            is_favorite: true,
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
