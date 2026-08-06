// RAG检索增强，对应原rag/包：TextChunker+Embedder+VectorStore+PersonaRagManager

use crate::db::database::Database;
use crate::db::rag_repo;
use crate::utils::helpers;

/// RAG服务
pub struct RagService;

impl RagService {
    pub fn new() -> Self {
        RagService
    }

    /// 将文本分块
    /// overlap 必须 < chunk_size，否则会 panic，此处添加保护
    pub fn chunk_text(&self, text: &str, chunk_size: usize, overlap: usize) -> Vec<String> {
        if text.len() <= chunk_size {
            return vec![text.to_string()];
        }

        // 保护：overlap 必须 < chunk_size，否则步进为0导致死循环
        let overlap = overlap.min(chunk_size.saturating_sub(1));
        let step = chunk_size - overlap;

        let mut chunks = Vec::new();
        let mut start = 0;

        while start < text.len() {
            let end = (start + chunk_size).min(text.len());
            let chunk = text[start..end].to_string();
            chunks.push(chunk);

            start += step;
            if start >= text.len() {
                break;
            }
        }

        chunks
    }

    /// 添加文档到RAG索引
    pub fn index_document(&self, db: &Database, persona_id: &str, source_type: &str, source_id: &str, content: &str) -> Result<(), crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        // 先删除该文档的旧索引
        rag_repo::delete_chunks_by_source(&conn, source_type, source_id)?;

        // 分块
        let chunks = self.chunk_text(content, 500, 50);

        for (index, chunk) in chunks.iter().enumerate() {
            let rag_chunk = rag_repo::RagChunk {
                id: helpers::new_uuid(),
                persona_id: persona_id.to_string(),
                source_type: source_type.to_string(),
                source_id: Some(source_id.to_string()),
                content: chunk.clone(),
                chunk_index: index as u32,
                embedding: None, // 向量嵌入需要LLM API，暂为空
                metadata: serde_json::json!({
                    "source_type": source_type,
                    "source_id": source_id,
                    "chunk_index": index,
                }),
                created_at: helpers::now(),
            };

            rag_repo::add_chunk(&conn, &rag_chunk)?;
        }

        Ok(())
    }

    /// 搜索相关文档
    pub fn search(&self, db: &Database, persona_id: &str, query: &str, limit: u32) -> Result<Vec<rag_repo::RagChunk>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        // 基于文本相似度搜索（向量搜索需要嵌入模型）
        rag_repo::search_chunks(&conn, persona_id, query, limit)
    }

    /// 获取所有索引块
    pub fn list_chunks(&self, db: &Database, persona_id: &str) -> Result<Vec<rag_repo::RagChunk>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        rag_repo::list_chunks(&conn, persona_id)
    }
}

/// 角色RAG管理器，对应原 Android 的 PersonaRagManager
pub struct PersonaRagManager {
    /// 当前角色ID
    persona_id: String,
    /// 当前角色内容哈希（用于判断是否需要重建索引）
    current_hash: Option<String>,
    /// 是否已就绪
    ready: bool,
}

impl PersonaRagManager {
    pub fn new(persona_id: String) -> Self {
        PersonaRagManager {
            persona_id,
            current_hash: None,
            ready: false,
        }
    }

    /// 构建索引
    pub fn build_index(&mut self, db: &Database, content: &str) -> Result<(), crate::db::database::DbError> {
        let rag_service = RagService::new();
        // 计算内容哈希
        let hash = format!("{:x}", md5_hash(content));
        self.current_hash = Some(hash.clone());
        rag_service.index_document(db, &self.persona_id, "persona", &self.persona_id, content)?;
        self.ready = true;
        Ok(())
    }

    /// 检索相关内容
    pub fn retrieve(&self, db: &Database, query: &str, limit: u32) -> Result<Vec<rag_repo::RagChunk>, crate::db::database::DbError> {
        if !self.ready {
            return Ok(Vec::new());
        }
        let rag_service = RagService::new();
        rag_service.search(db, &self.persona_id, query, limit)
    }

    /// 是否就绪
    pub fn is_ready(&self) -> bool {
        self.ready
    }

    /// 获取当前哈希
    pub fn current_hash(&self) -> Option<&str> {
        self.current_hash.as_deref()
    }
}

/// 简单的 MD5 哈希（用于内容变更检测）
fn md5_hash(s: &str) -> u128 {
    use std::collections::hash_map::DefaultHasher;
    use std::hash::{Hash, Hasher};
    let mut hasher = DefaultHasher::new();
    s.hash(&mut hasher);
    hasher.finish() as u128
}

/// TF-IDF 嵌入器（简化版，基于词频）
pub struct TfidfEmbedder {
    /// 词汇表
    vocabulary: std::collections::HashMap<String, usize>,
}

impl TfidfEmbedder {
    pub fn new() -> Self {
        TfidfEmbedder {
            vocabulary: std::collections::HashMap::new(),
        }
    }

    /// 构建词汇表
    pub fn build_vocabulary(&mut self, documents: &[&str]) {
        self.vocabulary.clear();
        let mut idx = 0;
        for doc in documents {
            for word in doc.split_whitespace() {
                if !self.vocabulary.contains_key(word) {
                    self.vocabulary.insert(word.to_string(), idx);
                    idx += 1;
                }
            }
        }
    }

    /// 计算TF向量
    pub fn embed(&self, text: &str) -> Vec<f32> {
        let mut vec = vec![0.0f32; self.vocabulary.len().max(1)];
        let words: Vec<&str> = text.split_whitespace().collect();
        if words.is_empty() {
            return vec;
        }
        for word in &words {
            if let Some(&idx) = self.vocabulary.get(*word) {
                vec[idx] += 1.0;
            }
        }
        // 归一化
        let len = words.len() as f32;
        for v in &mut vec {
            *v /= len;
        }
        vec
    }
}

/// 向量存储（余弦相似度搜索）
pub struct VectorStore {
    /// 存储的向量
    vectors: Vec<(String, Vec<f32>)>,
}

impl VectorStore {
    pub fn new() -> Self {
        VectorStore { vectors: Vec::new() }
    }

    /// 添加向量
    pub fn add(&mut self, id: String, vector: Vec<f32>) {
        self.vectors.push((id, vector));
    }

    /// 余弦相似度搜索
    pub fn search(&self, query: &[f32], limit: usize) -> Vec<(String, f32)> {
        let mut results: Vec<(String, f32)> = self.vectors
            .iter()
            .map(|(id, vec)| {
                let sim = cosine_similarity(query, vec);
                (id.clone(), sim)
            })
            .collect();
        results.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
        results.truncate(limit);
        results
    }
}

/// 计算余弦相似度
fn cosine_similarity(a: &[f32], b: &[f32]) -> f32 {
    if a.len() != b.len() || a.is_empty() {
        return 0.0;
    }
    let dot: f32 = a.iter().zip(b.iter()).map(|(x, y)| x * y).sum();
    let norm_a: f32 = a.iter().map(|x| x * x).sum::<f32>().sqrt();
    let norm_b: f32 = b.iter().map(|x| x * x).sum::<f32>().sqrt();
    if norm_a < f32::EPSILON || norm_b < f32::EPSILON {
        return 0.0;
    }
    dot / (norm_a * norm_b)
}
