// 昵称管理，对应原ui/NicknameManager.kt

use crate::db::database::Database;
use crate::db::settings_repo;

/// 昵称服务
pub struct NicknameService;

impl NicknameService {
    pub fn new() -> Self {
        NicknameService
    }

    /// 获取用户昵称
    pub fn get_nickname(&self, db: &Database, persona_id: &str) -> Result<Option<String>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        let key = format!("nickname_{}", persona_id);
        settings_repo::get_setting(&conn, &key)
    }

    /// 设置用户昵称
    pub fn set_nickname(&self, db: &Database, persona_id: &str, nickname: &str) -> Result<(), crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        let key = format!("nickname_{}", persona_id);
        settings_repo::set_setting(&conn, &key, nickname)
    }

    /// 获取AI对用户的称呼
    pub fn get_user_call_name(&self, db: &Database, persona_id: &str) -> String {
        self.get_nickname(db, persona_id)
            .ok()
            .flatten()
            .unwrap_or_else(|| "主人".to_string())
    }
}
