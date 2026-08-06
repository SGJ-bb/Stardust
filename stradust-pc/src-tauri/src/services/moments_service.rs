// 朋友圈，对应原moments/MomentsManager.kt

use crate::db::database::Database;
use crate::db::moments_repo;
use crate::models::moments::{Moment, MomentVisibility, CreateMomentRequest, LikeRecord, Comment};
use crate::utils::helpers;

/// 朋友圈服务
pub struct MomentsService;

impl MomentsService {
    pub fn new() -> Self {
        MomentsService
    }

    /// 列出朋友圈
    pub fn list_moments(&self, db: &Database, persona_id: &str) -> Result<Vec<Moment>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        moments_repo::list_moments(&conn, persona_id)
    }

    /// 创建朋友圈
    pub fn create_moment(&self, db: &Database, req: &CreateMomentRequest) -> Result<Moment, crate::db::database::DbError> {
        let now = helpers::now();
        let moment = Moment {
            id: helpers::new_uuid(),
            persona_id: req.persona_id.clone(),
            content: req.content.clone(),
            mood: req.mood.clone(),
            images: req.images.clone().unwrap_or_default(),
            likes: Vec::new(),
            comments: Vec::new(),
            is_auto_generated: false,
            visibility: req.visibility.clone().unwrap_or(MomentVisibility::Public),
            created_at: now,
            updated_at: now,
        };

        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        moments_repo::create_moment(&conn, &moment)?;

        Ok(moment)
    }

    /// 删除朋友圈
    pub fn delete_moment(&self, db: &Database, id: &str) -> Result<(), crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        moments_repo::delete_moment(&conn, id)
    }

    /// 添加评论
    pub fn add_comment(&self, db: &Database, moment_id: &str, author_id: &str, author_name: &str, content: &str, reply_to: Option<&str>) -> Result<Comment, crate::db::database::DbError> {
        let comment = Comment {
            id: helpers::new_uuid(),
            author_id: author_id.to_string(),
            author_name: author_name.to_string(),
            content: content.to_string(),
            reply_to: reply_to.map(|s| s.to_string()),
            created_at: helpers::now(),
        };

        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        moments_repo::add_comment(&conn, moment_id, &comment)?;

        Ok(comment)
    }

    /// 切换点赞
    pub fn toggle_like(&self, db: &Database, moment_id: &str, liker_id: &str, liker_name: &str) -> Result<bool, crate::db::database::DbError> {
        let like = LikeRecord {
            liker_id: liker_id.to_string(),
            liker_name: liker_name.to_string(),
            liked_at: helpers::now(),
        };

        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        moments_repo::toggle_like(&conn, moment_id, &like)
    }
}
