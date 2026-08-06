// RAG向量CRUD

use rusqlite::{params, Connection};

use crate::db::database::DbError;

/// RAG文本块
#[derive(Debug, Clone)]
pub struct RagChunk {
    pub id: String,
    pub persona_id: String,
    pub source_type: String,
    pub source_id: Option<String>,
    pub content: String,
    pub chunk_index: u32,
    pub embedding: Option<Vec<u8>>,
    pub metadata: serde_json::Value,
    pub created_at: chrono::NaiveDateTime,
}

/// 添加RAG块
pub fn add_chunk(conn: &Connection, chunk: &RagChunk) -> Result<(), DbError> {
    conn.execute(
        "INSERT INTO rag_chunks (id, persona_id, source_type, source_id, content,
         chunk_index, embedding, metadata, created_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)",
        params![
            chunk.id, chunk.persona_id, chunk.source_type, chunk.source_id,
            chunk.content, chunk.chunk_index, chunk.embedding,
            serde_json::to_string(&chunk.metadata).unwrap_or_default(),
            chunk.created_at.to_string()
        ],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    Ok(())
}

/// 搜索RAG块（基于文本相似度，向量搜索需要额外实现）
pub fn search_chunks(conn: &Connection, persona_id: &str, query: &str, limit: u32) -> Result<Vec<RagChunk>, DbError> {
    let pattern = format!("%{}%", query);
    let mut stmt = conn.prepare(
        "SELECT id, persona_id, source_type, source_id, content, chunk_index,
                embedding, metadata, created_at
         FROM rag_chunks WHERE persona_id = ?1 AND content LIKE ?2
         ORDER BY created_at DESC LIMIT ?3"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let chunks = stmt.query_map(params![persona_id, pattern, limit], |row| {
        let metadata_str: String = row.get(7)?;
        Ok(RagChunk {
            id: row.get(0)?,
            persona_id: row.get(1)?,
            source_type: row.get(2)?,
            source_id: row.get(3)?,
            content: row.get(4)?,
            chunk_index: row.get(5)?,
            embedding: row.get(6)?,
            metadata: serde_json::from_str(&metadata_str).unwrap_or(serde_json::Value::Object(Default::default())),
            created_at: parse_dt(row.get(8)?),
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|c| c.ok())
      .collect();

    Ok(chunks)
}

/// 获取角色的所有RAG块
pub fn list_chunks(conn: &Connection, persona_id: &str) -> Result<Vec<RagChunk>, DbError> {
    let mut stmt = conn.prepare(
        "SELECT id, persona_id, source_type, source_id, content, chunk_index,
                embedding, metadata, created_at
         FROM rag_chunks WHERE persona_id = ?1 ORDER BY chunk_index"
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;

    let chunks = stmt.query_map(params![persona_id], |row| {
        let metadata_str: String = row.get(7)?;
        Ok(RagChunk {
            id: row.get(0)?,
            persona_id: row.get(1)?,
            source_type: row.get(2)?,
            source_id: row.get(3)?,
            content: row.get(4)?,
            chunk_index: row.get(5)?,
            embedding: row.get(6)?,
            metadata: serde_json::from_str(&metadata_str).unwrap_or(serde_json::Value::Object(Default::default())),
            created_at: parse_dt(row.get(8)?),
        })
    }).map_err(|e| DbError::QueryFailed(e.to_string()))?
      .filter_map(|c| c.ok())
      .collect();

    Ok(chunks)
}

/// 删除RAG块
pub fn delete_chunks_by_source(conn: &Connection, source_type: &str, source_id: &str) -> Result<(), DbError> {
    conn.execute(
        "DELETE FROM rag_chunks WHERE source_type = ?1 AND source_id = ?2",
        params![source_type, source_id],
    ).map_err(|e| DbError::QueryFailed(e.to_string()))?;
    Ok(())
}

fn parse_dt(s: String) -> chrono::NaiveDateTime {
    chrono::NaiveDateTime::parse_from_str(&s, "%Y-%m-%d %H:%M:%S")
        .unwrap_or_else(|_| chrono::Local::now().naive_local())
}
