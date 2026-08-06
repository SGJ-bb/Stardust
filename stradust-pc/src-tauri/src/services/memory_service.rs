// 记忆引擎：MemoryPool评估/压缩/会话管理，对应原memory/包所有功能

use crate::db::database::Database;
use crate::db::memory_repo;
use crate::models::memory::*;
use crate::utils::helpers;

/// 记忆服务
pub struct MemoryService;

impl MemoryService {
    pub fn new() -> Self {
        MemoryService
    }

    /// 添加或更新记忆
    /// 使用精确匹配 content = ? 做去重，而非模糊搜索
    pub fn add_or_update(&self, db: &Database, memory: &MemoryEntry) -> Result<(), crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        // 使用精确匹配 content 做去重
        let existing: Option<MemoryEntry> = conn.query_row(
            "SELECT id, persona_id, content, category, importance, source,
                    created_at, last_accessed, access_count, is_active
             FROM memories WHERE persona_id = ?1 AND content = ?2 AND is_active = 1
             LIMIT 1",
            rusqlite::params![memory.persona_id, memory.content],
            |row| {
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
                    created_at: crate::utils::helpers::parse_datetime(&row.get::<_, String>(6)?)
                        .unwrap_or_else(crate::utils::helpers::now),
                    last_accessed: crate::utils::helpers::parse_datetime(&row.get::<_, String>(7)?)
                        .unwrap_or_else(crate::utils::helpers::now),
                    access_count: row.get(8)?,
                    is_active: row.get::<_, i32>(9)? != 0,
                })
            },
        ).ok();

        if let Some(similar) = existing {
            // 更新已有记忆的重要性
            let new_importance = (similar.importance + memory.importance) / 2.0;
            let now = helpers::now().to_string();
            conn.execute(
                "UPDATE memories SET importance = ?1, last_accessed = ?2, access_count = access_count + 1 WHERE id = ?3",
                rusqlite::params![new_importance, now, similar.id],
            ).map_err(|e| crate::db::database::DbError::QueryFailed(e.to_string()))?;
        } else {
            memory_repo::add_memory(&conn, memory)?;
        }

        Ok(())
    }

    /// 评估对话轮次，决定是否需要记忆操作
    pub fn evaluate_turn(&self, user_message: &str, assistant_message: &str) -> TurnEvaluation {
        let mut score: f32 = 0.3;

        // 基于消息长度评估
        let total_len = user_message.len() + assistant_message.len();
        if total_len > 200 { score += 0.2; }
        if total_len > 500 { score += 0.2; }

        // 基于关键词评估
        let emotional_keywords = ["喜欢", "讨厌", "开心", "难过", "生气", "害怕", "爱", "恨", "重要", "记住"];
        let fact_keywords = ["我是", "我叫", "住在", "工作", "生日", "电话", "地址", "习惯"];

        for keyword in &emotional_keywords {
            if user_message.contains(keyword) { score += 0.15; break; }
        }
        for keyword in &fact_keywords {
            if user_message.contains(keyword) { score += 0.2; break; }
        }

        // 基于问句评估
        if user_message.contains("？") || user_message.contains("?") {
            score += 0.1;
        }

        TurnEvaluation {
            score: score.min(1.0),
            should_remember: score >= 0.5,
            should_consolidate: score >= 0.8,
            category: self.detect_category(user_message),
        }
    }

    /// 检测记忆类别
    fn detect_category(&self, text: &str) -> MemoryCategory {
        let preference_keywords = ["喜欢", "讨厌", "偏好", "最爱", "不喜欢"];
        let fact_keywords = ["是", "叫", "在", "有", "会"];
        let event_keywords = ["今天", "昨天", "上周", "刚才", "刚才"];
        let emotion_keywords = ["开心", "难过", "生气", "害怕", "感动"];
        let habit_keywords = ["每天", "总是", "经常", "习惯"];

        for kw in &habit_keywords {
            if text.contains(kw) { return MemoryCategory::Habit; }
        }
        for kw in &preference_keywords {
            if text.contains(kw) { return MemoryCategory::UserPreference; }
        }
        for kw in &emotion_keywords {
            if text.contains(kw) { return MemoryCategory::Emotion; }
        }
        for kw in &event_keywords {
            if text.contains(kw) { return MemoryCategory::Event; }
        }
        for kw in &fact_keywords {
            if text.contains(kw) { return MemoryCategory::Fact; }
        }

        MemoryCategory::Fact
    }

    /// 压缩/整合记忆
    pub fn consolidate(&self, db: &Database, persona_id: &str) -> Result<Vec<MemoryEntry>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        let all_memories = memory_repo::list_memories(&conn, persona_id)?;

        // 按类别分组
        let mut grouped: std::collections::HashMap<String, Vec<&MemoryEntry>> = std::collections::HashMap::new();
        for mem in &all_memories {
            let key = format!("{:?}", mem.category);
            grouped.entry(key).or_default().push(mem);
        }

        let mut consolidated = Vec::new();

        // 对每类记忆进行整合
        for (category, memories) in grouped {
            if memories.len() < 3 {
                continue;
            }

            // 合并相似记忆
            let merged_content: Vec<&str> = memories.iter().map(|m| m.content.as_str()).collect();
            let summary = format!("[整合] {}: {}", category, merged_content.join("; "));

            let new_memory = MemoryEntry {
                id: helpers::new_uuid(),
                persona_id: persona_id.to_string(),
                content: summary,
                category: MemoryCategory::ConversationSummary,
                importance: memories.iter().map(|m| m.importance).fold(0.0f32, |a, b| a.max(b)),
                source: "consolidation".to_string(),
                event_time: None,
                event_place: None,
                event_people: None,
                event: None,
                scene: None,
                details: None,
                relationships: None,
                created_at: helpers::now(),
                last_accessed: helpers::now(),
                access_count: 0,
                is_active: true,
            };

            // 停用旧记忆
            for mem in &memories {
                let _ = conn.execute(
                    "UPDATE memories SET is_active = 0 WHERE id = ?1",
                    rusqlite::params![mem.id],
                );
            }

            memory_repo::add_memory(&conn, &new_memory)?;
            consolidated.push(new_memory);
        }

        Ok(consolidated)
    }

    /// 获取记忆池块（用于注入提示词）
    pub fn get_pool_block(&self, db: &Database, persona_id: &str, session_id: &str) -> MemoryPoolBlock {
        let conn = match db.conn.lock() {
            Ok(c) => c,
            Err(_) => return MemoryPoolBlock {
                core_block: String::new(),
                detail_block: String::new(),
                total_token_estimate: 0,
            },
        };

        let memories = memory_repo::list_memories(&conn, persona_id).unwrap_or_default();

        // 核心记忆（高重要性）
        let core_memories: Vec<&MemoryEntry> = memories.iter()
            .filter(|m| m.importance >= 0.7)
            .take(10)
            .collect();

        // 详细记忆（中等重要性）
        let detail_memories: Vec<&MemoryEntry> = memories.iter()
            .filter(|m| m.importance >= 0.3 && m.importance < 0.7)
            .take(20)
            .collect();

        let core_block = if core_memories.is_empty() {
            String::new()
        } else {
            let items: Vec<String> = core_memories.iter()
                .map(|m| format!("- [{}] {}", m.category_as_str(), m.content))
                .collect();
            format!("【核心记忆】\n{}", items.join("\n"))
        };

        let detail_block = if detail_memories.is_empty() {
            String::new()
        } else {
            let items: Vec<String> = detail_memories.iter()
                .map(|m| format!("- {}", m.content))
                .collect();
            format!("【相关记忆】\n{}", items.join("\n"))
        };

        let total_text = format!("{} {}", core_block, detail_block);
        let total_token_estimate = helpers::estimate_tokens(&total_text);

        MemoryPoolBlock {
            core_block,
            detail_block,
            total_token_estimate,
        }
    }

    /// 获取详细记忆块
    pub fn get_detail_block(&self, db: &Database, persona_id: &str) -> String {
        let conn = match db.conn.lock() {
            Ok(c) => c,
            Err(_) => return String::new(),
        };

        let memories = memory_repo::list_memories(&conn, persona_id).unwrap_or_default();
        let items: Vec<String> = memories.iter()
            .take(20)
            .map(|m| format!("- {}", m.content))
            .collect();

        if items.is_empty() {
            String::new()
        } else {
            format!("【记忆详情】\n{}", items.join("\n"))
        }
    }

    /// 添加记忆事实
    /// 上限 200 条，与原 Android 一致
    pub fn add_memory_fact(&self, db: &Database, persona_id: &str, content: &str, category: MemoryCategory, importance: f32) -> Result<MemoryEntry, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        // 检查记忆数量上限（原 Android：上限 200 条）
        let count: i32 = conn.query_row(
            "SELECT COUNT(*) FROM memories WHERE persona_id = ?1 AND is_active = 1",
            rusqlite::params![persona_id],
            |row| row.get(0),
        ).unwrap_or(0);

        if count >= 200 {
            // 删除最不重要且最久未访问的记忆
            conn.execute(
                "DELETE FROM memories WHERE id = (
                    SELECT id FROM memories WHERE persona_id = ?1 AND is_active = 1
                    ORDER BY importance ASC, last_accessed ASC LIMIT 1
                )",
                rusqlite::params![persona_id],
            ).map_err(|e| crate::db::database::DbError::QueryFailed(e.to_string()))?;
        }

        drop(conn); // 释放锁，让 add_or_update 重新获取

        let memory = MemoryEntry {
            id: helpers::new_uuid(),
            persona_id: persona_id.to_string(),
            content: content.to_string(),
            category,
            importance,
            source: "conversation".to_string(),
            event_time: None,
            event_place: None,
            event_people: None,
            event: None,
            scene: None,
            details: None,
            relationships: None,
            created_at: helpers::now(),
            last_accessed: helpers::now(),
            access_count: 0,
            is_active: true,
        };

        self.add_or_update(db, &memory)?;
        Ok(memory)
    }

    /// 搜索记忆
    pub fn search_memories(&self, db: &Database, persona_id: &str, query: &str) -> Result<Vec<MemorySearchResult>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        let memories = memory_repo::search_memories(&conn, persona_id, query)?;

        Ok(memories.into_iter().map(|m| {
            let relevance = if m.content.contains(query) { 0.9 } else { 0.5 };
            MemorySearchResult { memory: m, relevance }
        }).collect())
    }

    /// 添加难忘时刻
    /// 仅 score >= 8.0（换算为 0.8）时保存，与原 Android 一致
    pub fn add_memorable_moment(&self, db: &Database, persona_id: &str, session_id: &str, content: &str, score: f32, emotion: Option<&str>) -> Result<(), crate::db::database::DbError> {
        // 原 Android：仅 score≥8 保存（满分10），换算为 0.8
        if score < 0.8 {
            tracing::debug!("难忘时刻分数 {:.1} 低于阈值 0.8，跳过保存", score);
            return Ok(());
        }

        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        let moment = MemorableMoment {
            id: helpers::new_uuid(),
            persona_id: persona_id.to_string(),
            session_id: session_id.to_string(),
            content: content.to_string(),
            score,
            emotion: emotion.map(|s| s.to_string()),
            created_at: helpers::now(),
        };

        memory_repo::add_memorable_moment(&conn, &moment)
    }

    /// 获取难忘时刻
    pub fn get_memorable_moments(&self, db: &Database, persona_id: &str, min_score: f32) -> Result<Vec<MemorableMoment>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        memory_repo::get_memorable_moments(&conn, persona_id, min_score)
    }

    /// 评估难忘时刻分数
    pub fn score_memorable_moment(&self, user_msg: &str, assistant_msg: &str) -> f32 {
        let mut score: f32 = 0.3;

        // 情感关键词加分
        let strong_emotions = ["爱", "喜欢", "讨厌", "恨", "感动", "哭", "笑", "开心", "难过"];
        for kw in &strong_emotions {
            if user_msg.contains(kw) || assistant_msg.contains(kw) {
                score += 0.2;
                break;
            }
        }

        // 重要信息加分
        let important_keywords = ["生日", "纪念日", "第一次", "永远", "承诺", "秘密"];
        for kw in &important_keywords {
            if user_msg.contains(kw) {
                score += 0.2;
                break;
            }
        }

        // 深度对话加分
        if user_msg.len() > 100 && assistant_msg.len() > 100 {
            score += 0.1;
        }

        score.min(1.0)
    }
}

/// 对话轮次评估结果
#[derive(Debug)]
pub struct TurnEvaluation {
    pub score: f32,
    pub should_remember: bool,
    pub should_consolidate: bool,
    pub category: MemoryCategory,
}

/// 会话上下文管理器，对应原 Android 的 ContextManager
pub struct ContextManager {
    /// 当前轮次计数
    turn_count: u32,
    /// 当前会话ID
    session_id: String,
    /// 上下文文本（累计）
    context_text: String,
}

impl ContextManager {
    pub fn new(session_id: String) -> Self {
        ContextManager {
            turn_count: 0,
            session_id,
            context_text: String::new(),
        }
    }

    /// 添加一轮对话
    pub fn add_turn(&mut self, user_msg: &str, assistant_msg: &str) {
        self.turn_count += 1;
        self.context_text.push_str(&format!("用户：{}\n助手：{}\n", user_msg, assistant_msg));
    }

    /// 是否应该评估记忆（每2轮）
    pub fn should_evaluate(&self) -> bool {
        self.turn_count > 0 && self.turn_count % 2 == 0
    }

    /// 评估并更新记忆
    pub fn evaluate_and_update_memory(
        &self,
        memory_service: &MemoryService,
        db: &Database,
        persona_id: &str,
    ) -> Result<(), crate::db::database::DbError> {
        if self.context_text.is_empty() {
            return Ok(());
        }
        let evaluation = memory_service.evaluate_turn(&self.context_text, "");
        if evaluation.should_remember {
            memory_service.add_memory_fact(
                db, persona_id, &self.context_text, evaluation.category, 0.5,
            )?;
        }
        Ok(())
    }

    /// 获取上下文块
    pub fn get_context_block(&self, max_chars: usize) -> String {
        if self.context_text.len() <= max_chars {
            self.context_text.clone()
        } else {
            // 保留最近的内容
            let start = self.context_text.len().saturating_sub(max_chars);
            self.context_text[start..].to_string()
        }
    }

    /// 是否需要新建会话
    pub fn needs_new_session(&self) -> bool {
        self.context_text.len() > 800
    }

    /// 创建新会话
    pub fn create_new_session(&mut self, session_id: String) {
        self.turn_count = 0;
        self.session_id = session_id;
        self.context_text.clear();
    }
}

/// 会话管理器，对应原 Android 的 SessionManager
pub struct SessionManager {
    /// 历史会话上下文（最多20个）
    history: Vec<SessionSummary>,
    /// 当前会话ID
    current_session_id: String,
}

/// 会话摘要
#[derive(Debug, Clone)]
struct SessionSummary {
    session_id: String,
    summary: String,
    turn_count: u32,
}

impl SessionManager {
    pub fn new() -> Self {
        SessionManager {
            history: Vec::new(),
            current_session_id: crate::utils::helpers::new_uuid(),
        }
    }

    /// 增加轮次
    pub fn increment_turn(&mut self) -> u32 {
        // 返回当前历史长度作为轮次参考
        self.history.len() as u32 + 1
    }

    /// 检查记忆限制（800字）
    pub fn check_memory_limit(&self, context: &str) -> bool {
        context.len() > 800
    }

    /// 创建新会话（先日记→压缩→归档→新建）
    pub fn create_new_session(&mut self, current_summary: &str, current_turns: u32) -> String {
        // 归档当前会话
        if !current_summary.is_empty() {
            self.history.push(SessionSummary {
                session_id: self.current_session_id.clone(),
                summary: current_summary.to_string(),
                turn_count: current_turns,
            });
        }

        // 最多保留20个历史会话
        if self.history.len() > 20 {
            self.history.remove(0);
        }

        // 创建新会话
        self.current_session_id = crate::utils::helpers::new_uuid();
        self.current_session_id.clone()
    }

    /// 获取继承的记忆
    pub fn get_inherited_memory(&self) -> String {
        self.history
            .iter()
            .rev()
            .take(5) // 最近5个会话的摘要
            .map(|s| s.summary.as_str())
            .collect::<Vec<&str>>()
            .join("\n")
    }
}

/// MemoryEntry的辅助方法
impl MemoryEntry {
    /// 获取类别字符串
    pub fn category_as_str(&self) -> &str {
        match self.category {
            MemoryCategory::UserPreference => "偏好",
            MemoryCategory::Fact => "事实",
            MemoryCategory::Event => "事件",
            MemoryCategory::Emotion => "情感",
            MemoryCategory::ConversationSummary => "摘要",
            MemoryCategory::WorldKnowledge => "知识",
            MemoryCategory::Habit => "习惯",
        }
    }
}
