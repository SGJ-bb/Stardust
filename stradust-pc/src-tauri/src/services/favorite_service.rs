// 收藏管理，对应原ui/FavoriteManager.kt

use crate::db::database::Database;
use crate::db::chat_repo;
use crate::models::chat::ChatMessage;

/// 收藏服务
pub struct FavoriteService;

impl FavoriteService {
    pub fn new() -> Self {
        FavoriteService
    }

    /// 切换收藏状态
    pub fn toggle_favorite(&self, db: &Database, message_id: &str) -> Result<bool, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        chat_repo::toggle_favorite(&conn, message_id)
    }

    /// 获取收藏列表
    pub fn get_favorites(&self, db: &Database, persona_id: &str) -> Result<Vec<ChatMessage>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        chat_repo::get_favorite_messages(&conn, persona_id)
    }
}
