// 数据迁移，对应原migration/DataMigrationManager.kt

use crate::db::database::Database;
use crate::db::{settings_repo, persona_repo, memory_repo, chat_repo};
use crate::models::persona::CreatePersonaRequest;
use crate::models::memory::{MemoryEntry, MemoryCategory};
use crate::models::chat::{ChatMessage, ChatRole};
use crate::utils::helpers;

/// 迁移服务
pub struct MigrationService;

impl MigrationService {
    pub fn new() -> Self {
        MigrationService
    }

    /// 检查是否需要迁移
    pub fn needs_migration(&self, db: &Database) -> bool {
        let conn = match db.conn.lock() {
            Ok(c) => c,
            Err(_) => return false,
        };

        settings_repo::get_setting(&conn, "migration_completed")
            .unwrap_or(None)
            .is_none()
    }

    /// 执行迁移
    pub fn run_migration(&self, db: &Database) -> Result<(), crate::db::database::DbError> {
        tracing::info!("开始数据迁移检查...");

        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        // 标记迁移完成
        settings_repo::set_setting(&conn, "migration_completed", "true")?;
        settings_repo::set_setting(&conn, "migration_version", "1")?;
        settings_repo::set_setting(&conn, "migration_date", &helpers::format_datetime(helpers::now()))?;

        tracing::info!("数据迁移完成");
        Ok(())
    }

    /// 导入Android数据
    /// 实际将数据写入数据库，而非仅计数
    /// 注意：此方法为同步操作，不包含异步调用
    pub fn import_from_android(&self, db: &Database, data: &serde_json::Value) -> Result<MigrationResult, crate::db::database::DbError> {
        let mut result = MigrationResult::default();

        // 导入角色
        if let Some(personas) = data.get("personas").and_then(|v| v.as_array()) {
            let conn = db.conn.lock()
                .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

            for persona_data in personas {
                match Self::import_persona(&conn, persona_data) {
                    Ok(()) => result.personas_imported += 1,
                    Err(e) => {
                        tracing::warn!("导入角色失败: {}", e);
                        result.errors.push(format!("角色导入失败: {}", e));
                    }
                }
            }
        }

        // 导入聊天记录
        if let Some(messages) = data.get("messages").and_then(|v| v.as_array()) {
            let conn = db.conn.lock()
                .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

            for msg_data in messages {
                match Self::import_message(&conn, msg_data) {
                    Ok(()) => result.messages_imported += 1,
                    Err(e) => {
                        tracing::warn!("导入消息失败: {}", e);
                        result.errors.push(format!("消息导入失败: {}", e));
                    }
                }
            }
        }

        // 导入记忆
        if let Some(memories) = data.get("memories").and_then(|v| v.as_array()) {
            let conn = db.conn.lock()
                .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

            for mem_data in memories {
                match Self::import_memory(&conn, mem_data) {
                    Ok(()) => result.memories_imported += 1,
                    Err(e) => {
                        tracing::warn!("导入记忆失败: {}", e);
                        result.errors.push(format!("记忆导入失败: {}", e));
                    }
                }
            }
        }

        // 导入设置
        if let Some(settings) = data.get("settings").and_then(|v| v.as_object()) {
            let conn = db.conn.lock()
                .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

            for (key, value) in settings {
                let value_str = match value {
                    serde_json::Value::String(s) => s.clone(),
                    other => other.to_string(),
                };
                match settings_repo::set_setting(&conn, key, &value_str) {
                    Ok(()) => result.settings_imported += 1,
                    Err(e) => {
                        tracing::warn!("导入设置失败: key={}, err={}", key, e);
                        result.errors.push(format!("设置导入失败 [{}]: {}", key, e));
                    }
                }
            }
        }

        tracing::info!("Android数据导入完成: {:?}", result);
        Ok(result)
    }

    /// 导入单个角色
    fn import_persona(conn: &rusqlite::Connection, data: &serde_json::Value) -> Result<(), crate::db::database::DbError> {
        let name = data.get("name").and_then(|v| v.as_str()).unwrap_or("未命名角色");
        let req = CreatePersonaRequest {
            name: name.to_string(),
            description: data.get("description").and_then(|v| v.as_str()).map(|s| s.to_string()),
            avatar: data.get("avatar").and_then(|v| v.as_str()).map(|s| s.to_string()),
            system_prompt: data.get("system_prompt").and_then(|v| v.as_str()).map(|s| s.to_string()),
            personality: data.get("personality").and_then(|v| v.as_str()).map(|s| s.to_string()),
            speaking_style: data.get("speaking_style").and_then(|v| v.as_str()).map(|s| s.to_string()),
            background_story: data.get("background_story").and_then(|v| v.as_str()).map(|s| s.to_string()),
            world_lore: data.get("world_lore").and_then(|v| v.as_str()).map(|s| s.to_string()),
            default_emotion: data.get("default_emotion").and_then(|v| v.as_str()).map(|s| s.to_string()),
            model_id: data.get("model_id").and_then(|v| v.as_str()).map(|s| s.to_string()),
            voice_id: data.get("voice_id").and_then(|v| v.as_str()).map(|s| s.to_string()),
            live2d_model: data.get("live2d_model").and_then(|v| v.as_str()).map(|s| s.to_string()),
        };
        persona_repo::create_persona(conn, &req)?;
        Ok(())
    }

    /// 导入单条消息
    fn import_message(conn: &rusqlite::Connection, data: &serde_json::Value) -> Result<(), crate::db::database::DbError> {
        let role_str = data.get("role").and_then(|v| v.as_str()).unwrap_or("user");
        let role = match role_str {
            "user" => ChatRole::User,
            "assistant" => ChatRole::Assistant,
            "system" => ChatRole::System,
            "tool" => ChatRole::Tool,
            _ => ChatRole::User,
        };

        let msg = ChatMessage {
            id: data.get("id").and_then(|v| v.as_str()).unwrap_or(&helpers::new_uuid()).to_string(),
            persona_id: data.get("persona_id").and_then(|v| v.as_str()).unwrap_or("default").to_string(),
            session_id: data.get("session_id").and_then(|v| v.as_str()).unwrap_or("imported").to_string(),
            role,
            content: data.get("content").and_then(|v| v.as_str()).unwrap_or("").to_string(),
            emotion: data.get("emotion").and_then(|v| v.as_str()).map(|s| s.to_string()),
            action: data.get("action").and_then(|v| v.as_str()).map(|s| s.to_string()),
            tool_calls: None,
            timestamp: data.get("timestamp")
                .and_then(|v| v.as_str())
                .and_then(|s| helpers::parse_datetime(s))
                .unwrap_or_else(helpers::now),
            is_favorite: false,
        };
        chat_repo::save_message(conn, &msg)?;
        Ok(())
    }

    /// 导入单条记忆
    fn import_memory(conn: &rusqlite::Connection, data: &serde_json::Value) -> Result<(), crate::db::database::DbError> {
        let category_str = data.get("category").and_then(|v| v.as_str()).unwrap_or("fact");
        let category: MemoryCategory = serde_json::from_str(&format!("\"{}\"", category_str))
            .unwrap_or(MemoryCategory::Fact);

        let now = helpers::now();
        let memory = MemoryEntry {
            id: data.get("id").and_then(|v| v.as_str()).unwrap_or(&helpers::new_uuid()).to_string(),
            persona_id: data.get("persona_id").and_then(|v| v.as_str()).unwrap_or("default").to_string(),
            content: data.get("content").and_then(|v| v.as_str()).unwrap_or("").to_string(),
            category,
            importance: data.get("importance").and_then(|v| v.as_f64()).unwrap_or(0.5) as f32,
            source: data.get("source").and_then(|v| v.as_str()).unwrap_or("import").to_string(),
            event_time: None,
            event_place: None,
            event_people: None,
            event: None,
            scene: None,
            details: None,
            relationships: None,
            created_at: data.get("created_at")
                .and_then(|v| v.as_str())
                .and_then(|s| helpers::parse_datetime(s))
                .unwrap_or(now),
            last_accessed: now,
            access_count: 0,
            is_active: true,
        };
        memory_repo::add_memory(conn, &memory)?;
        Ok(())
    }
}

/// 迁移结果
#[derive(Debug, Default, serde::Serialize)]
pub struct MigrationResult {
    pub personas_imported: u32,
    pub messages_imported: u32,
    pub memories_imported: u32,
    pub settings_imported: u32,
    pub errors: Vec<String>,
}
