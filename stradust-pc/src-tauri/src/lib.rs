// 星尘AI桌宠 - Tauri 2 桌面应用入口
// 注册所有commands和plugins

mod commands;
mod db;
mod models;
mod plugins;
mod services;
mod state;
mod utils;
mod agents;

use state::AppState;
use utils::helpers;
use utils::logger;
use tauri::Manager;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    // 初始化日志
    logger::init_logger();

    tracing::info!("星尘 Stradust 启动中...");

    // 确保数据目录存在
    let data_dir = utils::helpers::get_app_data_dir();
    if let Err(e) = std::fs::create_dir_all(&data_dir) {
        tracing::error!("创建数据目录失败: {}", e);
    }

    // 打开数据库
    let db_path = utils::helpers::get_db_path();
    let database = match db::database::Database::open(&db_path) {
        Ok(db) => {
            tracing::info!("数据库初始化成功: {:?}", db_path);
            db
        }
        Err(e) => {
            tracing::error!("数据库初始化失败: {}", e);
            std::process::exit(1);
        }
    };

    // 构建Tauri应用
    tauri::Builder::default()
        .plugin(tauri_plugin_autostart::init(
            tauri_plugin_autostart::MacosLauncher::LaunchAgent,
            None,
        ))
        .plugin(tauri_plugin_clipboard_manager::init())
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_process::init())
        // 注册 live2d:// 自定义协议，用于加载本地 Live2D 模型文件（必须在 setup 之前）
        .register_asynchronous_uri_scheme_protocol("live2d", move |_app, request, responder| {
            let uri_str = request.uri().to_string();
            let path = uri_str.strip_prefix("live2d://").unwrap_or(&uri_str);
            // 安全：只允许访问 F:\stradust\vtuber 目录下的文件
            let base_dir = std::path::PathBuf::from(r"F:\stradust\vtuber");
            let full_path = base_dir.join(path.replace('/', "\\").trim_start_matches('\\'));
            // 路径遍历防护
            if !full_path.starts_with(&base_dir) {
                let resp = http::Response::builder()
                    .status(403)
                    .body(std::borrow::Cow::Borrowed::<[u8]>(&[]))
                    .unwrap();
                let _ = responder.respond(resp);
                return;
            }
            match std::fs::read(&full_path) {
                Ok(data) => {
                    let mime = helpers::mime_guess_from_path(&full_path);
                    let resp = http::Response::builder()
                        .status(200)
                        .header("Content-Type", mime)
                        .body(std::borrow::Cow::Owned(data))
                        .unwrap();
                    let _ = responder.respond(resp);
                }
                Err(_) => {
                    let resp = http::Response::builder()
                        .status(404)
                        .body(std::borrow::Cow::Borrowed::<[u8]>(&[]))
                        .unwrap();
                    let _ = responder.respond(resp);
                }
            }
        })
        .setup(|app| {
            // 创建全局状态
            let app_handle = app.handle().clone();
            let state = AppState::new(database, app_handle);
            app.manage(state);

            // 监听窗口关闭事件，确保数据库正确checkpoint
            let window = app.get_webview_window("main").unwrap();
            window.on_window_event(move |event| {
                if let tauri::WindowEvent::CloseRequested { .. } = event {
                    tracing::info!("窗口关闭，执行数据库checkpoint...");
                }
                // AppState的Drop会在app退出时自动触发Database的Drop，
                // Drop中会执行WAL checkpoint
            });

            tracing::info!("星尘 Stradust 启动完成！");
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            // 聊天
            commands::chat::send_chat,
            commands::chat::send_chat_stream,
            commands::chat::clear_chat_history,
            commands::chat::toggle_favorite,
            commands::chat::get_favorites,
            commands::chat::trigger_proactive_interaction,
            commands::chat::evolve_personality,
            commands::chat::get_memorable_moments,
            commands::chat::get_nickname,
            commands::chat::set_nickname,
            commands::chat::import_from_android,
            commands::chat::build_rag_index,
            // 角色
            commands::persona::list_personas,
            commands::persona::get_persona,
            commands::persona::create_persona,
            commands::persona::update_persona,
            commands::persona::delete_persona,
            commands::persona::export_personas,
            commands::persona::import_personas,
            // 记忆
            commands::memory::list_memories,
            commands::memory::add_memory,
            commands::memory::delete_memory,
            commands::memory::search_memories,
            commands::memory::get_memory_pool,
            // 语音
            commands::voice::start_voice_record,
            commands::voice::stop_voice_record,
            commands::voice::speak,
            // 设置
            commands::settings::get_settings,
            commands::settings::update_settings,
            commands::settings::test_llm_connection,
            commands::settings::list_providers,
            commands::settings::create_provider,
            commands::settings::delete_provider,
            // 群聊
            commands::group_chat::list_group_chats,
            commands::group_chat::create_group_chat,
            commands::group_chat::delete_group_chat,
            commands::group_chat::send_group_message,
            // 虚拟世界
            commands::virtual_world::get_virtual_world,
            commands::virtual_world::update_world_config,
            commands::virtual_world::run_world_tick,
            commands::virtual_world::toggle_world,
            commands::virtual_world::get_story_events,
            // 插件
            commands::plugin::list_plugins,
            commands::plugin::toggle_plugin,
            commands::plugin::execute_plugin,
            // 日记
            commands::diary::list_diaries,
            commands::diary::generate_diary,
            commands::diary::delete_diary,
            commands::diary::export_diaries,
            commands::diary::import_diaries,
            // 朋友圈
            commands::moments::list_moments,
            commands::moments::create_moment,
            commands::moments::delete_moment,
            commands::moments::add_comment,
            commands::moments::toggle_like,
            // 表情包
            commands::sticker::list_stickers,
            commands::sticker::search_stickers,
            commands::sticker::add_sticker,
            commands::sticker::delete_sticker,
            // 成就
            commands::achievement::list_achievements,
            commands::achievement::check_in,
            commands::achievement::get_affection,
            commands::achievement::update_affection,
            // 日历
            commands::calendar::list_events,
            commands::calendar::add_event,
            commands::calendar::delete_event,
            // 时光胶囊
            commands::capsule::list_capsules,
            commands::capsule::create_capsule,
            commands::capsule::open_capsule,
            // 纪念相册
            commands::album::list_album_entries,
            commands::album::add_album_entry,
            commands::album::delete_album_entry,
            // Live2D模型
            commands::model::list_models,
            commands::model::import_model,
            commands::model::delete_model,
            commands::model::scan_models,
            // 像素宠物
            commands::pixelpet::list_pixel_pets,
            commands::pixelpet::get_pixel_pet,
            commands::pixelpet::get_active_pixel_pet,
            commands::pixelpet::create_pixel_pet,
            commands::pixelpet::update_pixel_pet,
            commands::pixelpet::set_active_pixel_pet,
            commands::pixelpet::delete_pixel_pet,
            commands::pixelpet::list_pet_actions,
            commands::pixelpet::create_pet_action,
            commands::pixelpet::update_pet_action,
            commands::pixelpet::delete_pet_action,
            commands::pixelpet::update_frame_status,
            commands::pixelpet::get_pixel_gen_config,
            commands::pixelpet::update_pixel_gen_config,
            commands::pixelpet::save_generated_frame,
            commands::pixelpet::test_pixel_gen_api,
            // 系统
            commands::system::show_overlay,
            commands::system::set_overlay_position,
            commands::system::set_overlay_size,
            commands::system::toggle_overlay_always_on_top,
            commands::system::get_active_window,
            commands::system::search_web,
            commands::system::get_app_version,
            // Agent 智能体
            commands::agent::list_agent_skills,
            commands::agent::get_skill_definitions_by_category,
            commands::agent::agent_chat,
            commands::agent::create_agent_session,
            commands::agent::list_agent_sessions,
            commands::agent::check_cli_tool,
        ])
        .run(tauri::generate_context!())
        .expect("运行星尘应用时出错");
}
