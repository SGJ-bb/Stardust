// 群聊逻辑，对应原groupchat/GroupChatManager.kt

use crate::db::database::Database;
use crate::db::group_chat_repo;
use crate::models::group_chat::{GroupChat, GroupMessage, CreateGroupChatRequest};
use crate::utils::helpers;

/// 群聊服务
pub struct GroupChatService;

impl GroupChatService {
    pub fn new() -> Self {
        GroupChatService
    }

    /// 列出群聊
    pub fn list_group_chats(&self, db: &Database) -> Result<Vec<GroupChat>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        group_chat_repo::list_group_chats(&conn)
    }

    /// 创建群聊
    pub fn create_group_chat(&self, db: &Database, req: &CreateGroupChatRequest) -> Result<GroupChat, crate::db::database::DbError> {
        let now = helpers::now();
        let group = GroupChat {
            id: helpers::new_uuid(),
            name: req.name.clone(),
            persona_ids: req.persona_ids.clone(),
            description: req.description.clone(),
            is_active: true,
            created_at: now,
            updated_at: now,
        };

        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        group_chat_repo::create_group_chat(&conn, &group)?;

        Ok(group)
    }

    /// 删除群聊
    pub fn delete_group_chat(&self, db: &Database, id: &str) -> Result<(), crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        group_chat_repo::delete_group_chat(&conn, id)
    }

    /// 发送群聊消息
    pub fn send_group_message(&self, db: &Database, group_id: &str, persona_id: &str, content: &str) -> Result<GroupMessage, crate::db::database::DbError> {
        let msg = GroupMessage {
            id: helpers::new_uuid(),
            group_id: group_id.to_string(),
            persona_id: persona_id.to_string(),
            content: content.to_string(),
            emotion: None,
            action: None,
            timestamp: helpers::now(),
        };

        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        group_chat_repo::save_group_message(&conn, &msg)?;

        Ok(msg)
    }

    /// 获取群聊消息
    pub fn get_group_messages(&self, db: &Database, group_id: &str, limit: Option<u32>) -> Result<Vec<GroupMessage>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        group_chat_repo::get_group_messages(&conn, group_id, limit)
    }
}
