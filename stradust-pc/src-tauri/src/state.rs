// 全局状态管理，替代原 AppContainer.kt

use std::sync::{Arc, Mutex};

use crate::db::database::Database;
use crate::plugins::plugin_registry::PluginRegistry;
use crate::services::llm_service::LlmService;
use crate::services::memory_service::MemoryService;
use crate::services::prompt_builder::PromptBuilder;
use crate::services::emotion_service::EmotionService;
use crate::services::affection_service::AffectionService;
use crate::services::voice_service::VoiceService;
use crate::services::rag_service::RagService;
use crate::services::virtual_world_service::VirtualWorldService;
use crate::services::diary_service::DiaryService;
use crate::services::search_service::SearchService;
use crate::services::safety_service::SafetyService;
use crate::services::humanizer::Humanizer;
use crate::services::interaction_engine::InteractionEngine;
use crate::services::stats_service::StatsService;
use crate::services::theme_service::ThemeService;
use crate::services::sticker_service::StickerService;
use crate::services::moments_service::MomentsService;
use crate::services::group_chat_service::GroupChatService;
use crate::services::nickname_service::NicknameService;
use crate::services::favorite_service::FavoriteService;
use crate::services::migration_service::MigrationService;

/// 应用全局状态
/// 替代原 Android 项目的 AppContainer.kt
/// 管理所有 Service 和 Repo 实例
pub struct AppState {
    /// 数据库连接
    pub db: Mutex<Database>,
    /// LLM服务
    pub llm_service: LlmService,
    /// 记忆服务（Arc共享，与PromptBuilder共用同一实例）
    pub memory_service: Arc<MemoryService>,
    /// 提示词构建器
    pub prompt_builder: PromptBuilder,
    /// 情绪服务
    pub emotion_service: EmotionService,
    /// 好感度服务
    pub affection_service: AffectionService,
    /// 语音服务
    pub voice_service: VoiceService,
    /// RAG服务（Arc共享，与PromptBuilder共用同一实例）
    pub rag_service: Arc<RagService>,
    /// 虚拟世界服务
    pub virtual_world_service: VirtualWorldService,
    /// 日记服务
    pub diary_service: DiaryService,
    /// 搜索服务
    pub search_service: SearchService,
    /// 安全服务
    pub safety_service: Mutex<SafetyService>,
    /// 人性化处理
    pub humanizer: Humanizer,
    /// 主动互动引擎（Mutex包装，支持set_intervals的可变性）
    pub interaction_engine: Mutex<InteractionEngine>,
    /// 统计服务
    pub stats_service: StatsService,
    /// 主题服务
    pub theme_service: ThemeService,
    /// 表情包服务
    pub sticker_service: StickerService,
    /// 朋友圈服务
    pub moments_service: MomentsService,
    /// 群聊服务
    pub group_chat_service: GroupChatService,
    /// 昵称服务
    pub nickname_service: NicknameService,
    /// 收藏服务
    pub favorite_service: FavoriteService,
    /// 迁移服务
    pub migration_service: MigrationService,
    /// 插件注册中心
    pub plugin_registry: PluginRegistry,
    /// Tauri应用句柄
    pub app_handle: tauri::AppHandle,
}

impl AppState {
    /// 创建新的应用状态
    pub fn new(db: Database, app_handle: tauri::AppHandle) -> Self {
        // 使用 Arc 共享 MemoryService 和 RagService 实例
        let memory_service = Arc::new(MemoryService::new());
        let rag_service = Arc::new(RagService::new());
        let prompt_builder = PromptBuilder::new(
            Arc::clone(&memory_service),
            Arc::clone(&rag_service),
        );

        // 初始化插件注册中心并注册所有插件
        let plugin_registry = PluginRegistry::new();
        plugin_registry.register(Box::new(crate::plugins::alarm_plugin::AlarmPlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::schedule_plugin::SchedulePlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::web_search_plugin::WebSearchPlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::memory_search_plugin::MemorySearchPlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::current_time_plugin::CurrentTimePlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::nickname_plugin::NicknamePlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::sticker_plugin::StickerPlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::image_gen_plugin::ImageGenPlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::alarm_at_time_plugin::AlarmAtTimePlugin::new()));

        // ===== Agent 智能体内置技能 (16个) =====
        // 办公效率类
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::office::doc_convert::DocConvertPlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::office::ppt_generate::PptGeneratePlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::office::excel_process::ExcelProcessPlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::office::pdf_merge::PdfMergePlugin::new()));
        // 媒体创作类
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::media::video_trim::VideoTrimPlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::media::audio_extract::AudioExtractPlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::media::subtitle_gen::SubtitleGenPlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::media::gif_create::GifCreatePlugin::new()));
        // 开发工具类
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::dev::code_run::CodeRunPlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::dev::git_operation::GitOperationPlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::dev::api_test::ApiTestPlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::dev::json_format::JsonFormatPlugin::new()));
        // AI助手类
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::ai::web_search_skill::WebSearchSkill::new()));
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::ai::summarize::SummarizePlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::ai::translate::TranslatePlugin::new()));
        plugin_registry.register(Box::new(crate::plugins::builtin_skills::ai::ocr_recognize::OcrRecognizePlugin::new()));

        AppState {
            db: Mutex::new(db),
            llm_service: LlmService::new(),
            memory_service,
            prompt_builder,
            emotion_service: EmotionService::new(),
            affection_service: AffectionService::new(),
            voice_service: VoiceService::new(),
            rag_service,
            virtual_world_service: VirtualWorldService::new(),
            diary_service: DiaryService::new(),
            search_service: SearchService::new(),
            safety_service: Mutex::new(SafetyService::new()),
            humanizer: Humanizer::new(),
            interaction_engine: Mutex::new(InteractionEngine::new()),
            stats_service: StatsService::new(),
            theme_service: ThemeService::new(),
            sticker_service: StickerService::new(),
            moments_service: MomentsService::new(),
            group_chat_service: GroupChatService::new(),
            nickname_service: NicknameService::new(),
            favorite_service: FavoriteService::new(),
            migration_service: MigrationService::new(),
            plugin_registry,
            app_handle,
        }
    }
}
