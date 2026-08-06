// 语音命令：start_voice_record, stop_voice_record, speak

use crate::state::AppState;
use tauri::State;

/// 开始录音
#[tauri::command]
pub async fn start_voice_record(state: State<'_, AppState>) -> Result<(), String> {
    state.voice_service.start_record()
        .map_err(|e: crate::services::voice_service::VoiceError| e.to_string())
}

/// 停止录音
#[tauri::command]
pub async fn stop_voice_record(state: State<'_, AppState>) -> Result<String, String> {
    state.voice_service.stop_record(None, None)
        .await
        .map_err(|e: crate::services::voice_service::VoiceError| e.to_string())
}

/// 语音合成
#[tauri::command]
pub async fn speak(state: State<'_, AppState>, text: String) -> Result<(), String> {
    state.voice_service.speak(&text, None, None, None)
        .await
        .map(|_| ())
        .map_err(|e: crate::services::voice_service::VoiceError| e.to_string())
}
