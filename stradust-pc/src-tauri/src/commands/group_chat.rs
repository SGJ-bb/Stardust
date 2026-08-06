// 群聊命令：list_group_chats, create_group_chat, delete_group_chat, send_group_message

use crate::state::AppState;
use crate::models::group_chat::{GroupChat, GroupMessage, CreateGroupChatRequest};
use tauri::State;

/// 列出群聊
#[tauri::command]
pub async fn list_group_chats(state: State<'_, AppState>) -> Result<Vec<GroupChat>, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.group_chat_service.list_group_chats(&db)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 创建群聊
#[tauri::command]
pub async fn create_group_chat(state: State<'_, AppState>, request: CreateGroupChatRequest) -> Result<GroupChat, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.group_chat_service.create_group_chat(&db, &request)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 删除群聊
#[tauri::command]
pub async fn delete_group_chat(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.group_chat_service.delete_group_chat(&db, &id)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}

/// 发送群聊消息
#[tauri::command]
pub async fn send_group_message(
    state: State<'_, AppState>,
    group_id: String,
    persona_id: String,
    content: String,
) -> Result<GroupMessage, String> {
    let db = state.db.lock().map_err(|e| e.to_string())?;
    state.group_chat_service.send_group_message(&db, &group_id, &persona_id, &content)
        .map_err(|e: crate::db::database::DbError| e.to_string())
}
