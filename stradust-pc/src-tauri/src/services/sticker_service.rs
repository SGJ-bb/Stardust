// 表情包管理，对应原sticker/StickerManager.kt

use crate::db::database::Database;
use crate::db::sticker_repo;
use crate::models::sticker::Sticker;
use crate::utils::helpers;

/// 表情包服务
pub struct StickerService;

impl StickerService {
    pub fn new() -> Self {
        StickerService
    }

    /// 列出表情包
    pub fn list_stickers(&self, db: &Database, category: Option<&str>) -> Result<Vec<Sticker>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        sticker_repo::list_stickers(&conn, category)
    }

    /// 搜索表情包
    pub fn search_stickers(&self, db: &Database, query: &str) -> Result<Vec<Sticker>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        sticker_repo::search_stickers(&conn, query)
    }

    /// 添加表情包
    pub fn add_sticker(&self, db: &Database, name: &str, file_path: &str, tags: Vec<String>, category: Option<&str>) -> Result<Sticker, crate::db::database::DbError> {
        let sticker = Sticker {
            id: helpers::new_uuid(),
            name: name.to_string(),
            file_path: file_path.to_string(),
            thumbnail: None,
            tags,
            category: category.map(|s| s.to_string()),
            is_animated: false,
            width: None,
            height: None,
            file_size: None,
            created_at: helpers::now(),
        };

        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        sticker_repo::add_sticker(&conn, &sticker)?;

        Ok(sticker)
    }

    /// 删除表情包
    pub fn delete_sticker(&self, db: &Database, id: &str) -> Result<(), crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        sticker_repo::delete_sticker(&conn, id)
    }
}
