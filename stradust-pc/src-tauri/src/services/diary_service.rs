// 日记生成，对应原diary/DiaryManager.kt
// 使用 LLM 生成日记内容，而非硬编码模板

use crate::db::database::Database;
use crate::db::diary_repo;
use crate::models::diary::{DiaryEntry, DiaryStyle};
use crate::models::settings::ProviderProfile;
use crate::services::llm_service::LlmService;
use crate::utils::helpers;

/// 日记服务
pub struct DiaryService;

impl DiaryService {
    pub fn new() -> Self {
        DiaryService
    }

    /// 列出日记
    pub fn list_diaries(&self, db: &Database, persona_id: &str) -> Result<Vec<DiaryEntry>, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        diary_repo::list_diaries(&conn, persona_id)
    }

    /// 仅生成日记内容（不保存到数据库，避免跨await持有锁）
    /// 供命令层调用，命令层负责在await之后单独保存
    pub async fn generate_diary_content(
        &self,
        persona_id: &str,
        persona_name: &str,
        date: &str,
        style: DiaryStyle,
        conversation_summary: &str,
        provider: &ProviderProfile,
        llm_service: &LlmService,
    ) -> Result<DiaryEntry, crate::db::database::DbError> {
        let now = helpers::now();

        // 将风格转为中文字符串供 LLM 理解
        let style_str = match style {
            DiaryStyle::Daily => "日常记录",
            DiaryStyle::Emotional => "感性随笔",
            DiaryStyle::Narrative => "故事叙述",
            DiaryStyle::Poetic => "诗歌体",
        };

        // 调用 LLM 生成日记内容
        let content = match llm_service.generate_diary_content(
            provider, persona_name, date, conversation_summary, style_str,
        ).await {
            Ok(text) => text,
            Err(e) => {
                tracing::warn!("LLM日记生成失败，回退到模板: {}", e);
                // 回退到模板方式
                match style {
                    DiaryStyle::Daily => format!(
                        "{}\n\n今天和主人聊了很多，感觉时间过得好快。{}",
                        date, conversation_summary
                    ),
                    DiaryStyle::Emotional => format!(
                        "心绪如风，吹过今天的每一个瞬间...\n\n{}",
                        conversation_summary
                    ),
                    DiaryStyle::Narrative => format!(
                        "【{}的故事】\n\n{}",
                        date, conversation_summary
                    ),
                    DiaryStyle::Poetic => format!(
                        "晨光微露，又一日。\n{}\n暮色渐浓，思绪未央。",
                        conversation_summary
                    ),
                }
            }
        };

        Ok(DiaryEntry {
            id: helpers::new_uuid(),
            persona_id: persona_id.to_string(),
            title: format!("{}的日记", date),
            content,
            mood: None,
            tags: vec!["自动生成".to_string()],
            date: now,
            is_auto_generated: true,
            created_at: now,
            updated_at: now,
        })
    }

    /// 生成日记（基于对话历史，调用 LLM 生成内容）
    /// 包含保存到数据库的完整流程
    pub async fn generate_diary(
        &self,
        db: &Database,
        persona_id: &str,
        persona_name: &str,
        date: &str,
        style: DiaryStyle,
        conversation_summary: &str,
        provider: &ProviderProfile,
        llm_service: &LlmService,
    ) -> Result<DiaryEntry, crate::db::database::DbError> {
        let now = helpers::now();

        // 将风格转为中文字符串供 LLM 理解
        let style_str = match style {
            DiaryStyle::Daily => "日常记录",
            DiaryStyle::Emotional => "感性随笔",
            DiaryStyle::Narrative => "故事叙述",
            DiaryStyle::Poetic => "诗歌体",
        };

        // 调用 LLM 生成日记内容
        let content = match llm_service.generate_diary_content(
            provider, persona_name, date, conversation_summary, style_str,
        ).await {
            Ok(text) => text,
            Err(e) => {
                tracing::warn!("LLM日记生成失败，回退到模板: {}", e);
                // 回退到模板方式
                match style {
                    DiaryStyle::Daily => format!(
                        "{}\n\n今天和主人聊了很多，感觉时间过得好快。{}",
                        date, conversation_summary
                    ),
                    DiaryStyle::Emotional => format!(
                        "心绪如风，吹过今天的每一个瞬间...\n\n{}",
                        conversation_summary
                    ),
                    DiaryStyle::Narrative => format!(
                        "【{}的故事】\n\n{}",
                        date, conversation_summary
                    ),
                    DiaryStyle::Poetic => format!(
                        "晨光微露，又一日。\n{}\n暮色渐浓，思绪未央。",
                        conversation_summary
                    ),
                }
            }
        };

        let diary = DiaryEntry {
            id: helpers::new_uuid(),
            persona_id: persona_id.to_string(),
            title: format!("{}的日记", date),
            content,
            mood: None,
            tags: vec!["自动生成".to_string()],
            date: now,
            is_auto_generated: true,
            created_at: now,
            updated_at: now,
        };

        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        diary_repo::create_diary(&conn, &diary)?;

        Ok(diary)
    }

    /// 删除日记
    pub fn delete_diary(&self, db: &Database, id: &str) -> Result<(), crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;
        diary_repo::delete_diary(&conn, id)
    }

    /// 导出日记
    pub fn export_diaries(&self, db: &Database, persona_id: &str) -> Result<crate::models::diary::DiaryExport, crate::db::database::DbError> {
        let diaries = self.list_diaries(db, persona_id)?;
        Ok(crate::models::diary::DiaryExport {
            version: "1.0".to_string(),
            exported_at: helpers::now(),
            diaries,
        })
    }

    /// 导入日记
    pub fn import_diaries(&self, db: &Database, export: &crate::models::diary::DiaryExport) -> Result<u32, crate::db::database::DbError> {
        let conn = db.conn.lock()
            .map_err(|e| crate::db::database::DbError::ConnectionFailed(e.to_string()))?;

        let mut count = 0u32;
        for diary in &export.diaries {
            if let Ok(()) = diary_repo::create_diary(&conn, diary) {
                count += 1;
            }
        }

        Ok(count)
    }
}
