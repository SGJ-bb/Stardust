// 记忆CRUD

use rusqlite::{params, Connection};

use crate::db::database::DbError;
use crate::models::memory::{MemoryEntry, MemoryCategory, MemorableMoment, MemoryPool, SessionContext};
use crate::utils::helpers;

/// 添加记忆
pub fn add_memory(conn: &Connection, memory: &MemoryEntry) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO memories (id, persona_id, content, category, importance, source,
         created_at, last_accessed, access_count, is_active)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)",
        params![
            memory.id, memory.persona_id, memory.content,
            serde_json::to_string(&memory.category).unwrap_or_else(|_| "\"fact\"".to_string()),
            memory.importance, memory.source,
            memory.created_at.to_string(), memory.last_accessed.to_string(),
            memory.access_count, memory.is_active as i32
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 获取角色的所有记忆
pub fn list_memories(conn: &Connection, persona_id: &str) -> Result<Vec<MemoryEntry>, DbError> {
    let mut stmt = conn.prepare(
        "SELECT id, persona_id, content, category, importance, source,
                created_at, last_accessed, access_count, is_active
         FROM memories WHERE persona_id = ?1 AND is_active = 1
         ORDER BY importance DESC, created_at DESC"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let memories = stmt.query_map(params![persona_id], |row| {
        let cat_str: String = row.get(3)?;
        let category: MemoryCategory = serde_json::from_str(&cat_str).unwrap_or(MemoryCategory::Fact);
        Ok(MemoryEntry {
            id: row.get(0)?,
            persona_id: row.get(1)?,
            content: row.get(2)?,
            category,
            importance: row.get(4)?,
            source: row.get(5)?,
            event_time: None,
            event_place: None,
            event_people: None,
            event: None,
            scene: None,
            details: None,
            relationships: None,
            created_at: helpers::parse_dt(row.get(6)?),
            last_accessed: helpers::parse_dt(row.get(7)?),
            access_count: row.get(8)?,
            is_active: row.get::<_, i32>(9)? != 0,
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|m| m.ok())
      .collect();

    Ok(memories)
}

/// 搜索记忆
pub fn search_memories(conn: &Connection, persona_id: &str, query: &str) -> Result<Vec<MemoryEntry>, DbError> {
    let pattern = format!("%{}%", query);
    let mut stmt = conn.prepare(
        "SELECT id, persona_id, content, category, importance, source,
                created_at, last_accessed, access_count, is_active
         FROM memories WHERE persona_id = ?1 AND is_active = 1 AND content LIKE ?2
         ORDER BY importance DESC"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let memories = stmt.query_map(params![persona_id, pattern], |row| {
        let cat_str: String = row.get(3)?;
        let category: MemoryCategory = serde_json::from_str(&cat_str).unwrap_or(MemoryCategory::Fact);
        Ok(MemoryEntry {
            id: row.get(0)?,
            persona_id: row.get(1)?,
            content: row.get(2)?,
            category,
            importance: row.get(4)?,
            source: row.get(5)?,
            event_time: None,
            event_place: None,
            event_people: None,
            event: None,
            scene: None,
            details: None,
            relationships: None,
            created_at: helpers::parse_dt(row.get(6)?),
            last_accessed: helpers::parse_dt(row.get(7)?),
            access_count: row.get(8)?,
            is_active: row.get::<_, i32>(9)? != 0,
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|m| m.ok())
      .collect();

    Ok(memories)
}

/// 删除记忆
pub fn delete_memory(conn: &Connection, id: &str) -> Result<(), DbError> {
    conn.execute("DELETE FROM memories WHERE id = ?1", params![id])
        .map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}

/// 更新记忆访问
pub fn touch_memory(conn: &Connection, id: &str) -> Result<(), DbError> {
    let now = chrono::Local::now().naive_local().to_string();
    conn.execute(
        "UPDATE memories SET last_accessed = ?1, access_count = access_count + 1 WHERE id = ?2",
        params![now, id],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}

/// 获取记忆池
pub fn get_memory_pool(conn: &Connection, persona_id: &str, session_id: &str) -> Result<Option<MemoryPool>, DbError> {
    let result = conn.query_row(
        "SELECT id, persona_id, session_id, core_memories, recent_memories, summary_memories,
                total_tokens, max_tokens, created_at, updated_at
         FROM memory_pools WHERE persona_id = ?1 AND session_id = ?2",
        params![persona_id, session_id],
        |row| {
            let core_str: String = row.get(3)?;
            let recent_str: String = row.get(4)?;
            let summary_str: String = row.get(5)?;
            Ok(MemoryPool {
                id: row.get(0)?,
                persona_id: row.get(1)?,
                session_id: row.get(2)?,
                core_memories: serde_json::from_str(&core_str).unwrap_or_default(),
                recent_memories: serde_json::from_str(&recent_str).unwrap_or_default(),
                summary_memories: serde_json::from_str(&summary_str).unwrap_or_default(),
                total_tokens: row.get(6)?,
                max_tokens: row.get(7)?,
                created_at: helpers::parse_dt(row.get(8)?),
                updated_at: helpers::parse_dt(row.get(9)?),
            })
        },
    );

    match result {
        Ok(pool) => Ok(Some(pool)),
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(e) => Err(DbError::QueryFailed(e.to_string())),
    }
}

/// 保存记忆池
pub fn save_memory_pool(conn: &Connection, pool: &MemoryPool) -> Result<(), DbError> {
    let now = chrono::Local::now().naive_local().to_string();
    conn.execute(
        "INSERT OR REPLACE INTO memory_pools (id, persona_id, session_id, core_memories,
         recent_memories, summary_memories, total_tokens, max_tokens, created_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)",
        params![
            pool.id, pool.persona_id, pool.session_id,
            serde_json::to_string(&pool.core_memories).unwrap_or_default(),
            serde_json::to_string(&pool.recent_memories).unwrap_or_default(),
            serde_json::to_string(&pool.summary_memories).unwrap_or_default(),
            pool.total_tokens, pool.max_tokens,
            pool.created_at.to_string(), now
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 添加难忘时刻
pub fn add_memorable_moment(conn: &Connection, moment: &MemorableMoment) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO memorable_moments (id, persona_id, session_id, content, score, emotion, created_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
        params![
            moment.id, moment.persona_id, moment.session_id,
            moment.content, moment.score, moment.emotion,
            moment.created_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 获取难忘时刻
pub fn get_memorable_moments(conn: &Connection, persona_id: &str, min_score: f32) -> Result<Vec<MemorableMoment>, DbError> {
    let mut stmt = conn.prepare(
        "SELECT id, persona_id, session_id, content, score, emotion, created_at
         FROM memorable_moments WHERE persona_id = ?1 AND score >= ?2
         ORDER BY score DESC"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let moments = stmt.query_map(params![persona_id, min_score], |row| {
        Ok(MemorableMoment {
            id: row.get(0)?,
            persona_id: row.get(1)?,
            session_id: row.get(2)?,
            content: row.get(3)?,
            score: row.get(4)?,
            emotion: row.get(5)?,
            created_at: helpers::parse_dt(row.get(6)?),
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|m| m.ok())
      .collect();

    Ok(moments)
}

/// 获取会话上下文
pub fn get_session_context(conn: &Connection, session_id: &str) -> Result<Option<SessionContext>, DbError> {
    let result = conn.query_row(
        "SELECT id, session_id, persona_id, turn_count, inherited_memory, created_at
         FROM session_contexts WHERE session_id = ?1",
        params![session_id],
        |row| {
            let inherited_str: String = row.get(4)?;
            Ok(SessionContext {
                session_id: row.get(1)?,
                persona_id: row.get(2)?,
                turn_count: row.get(3)?,
                inherited_memory: serde_json::from_str(&inherited_str).unwrap_or_default(),
                created_at: helpers::parse_dt(row.get(5)?),
            })
        },
    );

    match result {
        Ok(ctx) => Ok(Some(ctx)),
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(e) => Err(DbError::QueryFailed(e.to_string())),
    }
}

/// 保存会话上下文
pub fn save_session_context(conn: &Connection, ctx: &SessionContext) -> Result<(), DbError> {
    conn.execute(
        "INSERT OR REPLACE INTO session_contexts (id, session_id, persona_id, turn_count, inherited_memory, created_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        params![
            uuid::Uuid::new_v4().to_string(), ctx.session_id, ctx.persona_id,
            ctx.turn_count, serde_json::to_string(&ctx.inherited_memory).unwrap_or_default(),
            ctx.created_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}
