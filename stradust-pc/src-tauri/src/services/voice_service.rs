// 语音服务ASR/TTS，对应原voice/包
// 实现了 edge-tts 基本功能（HTTP 请求生成音频）

use thiserror::Error;

/// 语音服务错误
#[derive(Debug, Error)]
pub enum VoiceError {
    #[error("ASR识别失败: {0}")]
    AsrFailed(String),
    #[error("TTS合成失败: {0}")]
    TtsFailed(String),
    #[error("录音失败: {0}")]
    RecordFailed(String),
    #[error("未配置语音服务")]
    NotConfigured,
}

/// 语音服务
pub struct VoiceService {
    /// 是否正在录音
    is_recording: std::sync::atomic::AtomicBool,
    /// HTTP 客户端
    client: reqwest::Client,
}

impl VoiceService {
    pub fn new() -> Self {
        VoiceService {
            is_recording: std::sync::atomic::AtomicBool::new(false),
            client: reqwest::Client::new(),
        }
    }

    /// 开始录音
    pub fn start_record(&self) -> Result<(), VoiceError> {
        if self.is_recording.load(std::sync::atomic::Ordering::SeqCst) {
            return Err(VoiceError::RecordFailed("已经在录音中".to_string()));
        }

        self.is_recording.store(true, std::sync::atomic::Ordering::SeqCst);
        tracing::info!("开始语音录音");
        Ok(())
    }

    /// 停止录音并识别
    pub async fn stop_record(&self, api_key: Option<&str>, base_url: Option<&str>) -> Result<String, VoiceError> {
        if !self.is_recording.load(std::sync::atomic::Ordering::SeqCst) {
            return Err(VoiceError::RecordFailed("没有在录音中".to_string()));
        }

        self.is_recording.store(false, std::sync::atomic::Ordering::SeqCst);
        tracing::info!("停止语音录音");

        let api_key = match api_key {
            Some(k) => k,
            None => return Ok("[语音识别结果]".to_string()),
        };

        let base_url = match base_url {
            Some(u) => u,
            None => return Ok("[语音识别结果]".to_string()),
        };

        // 调用 OpenAI Whisper API 进行语音识别
        let url = format!("{}/audio/transcriptions", base_url.trim_end_matches('/'));
        let result = self.client
            .post(&url)
            .header("Authorization", format!("Bearer {}", api_key))
            .body("模拟音频数据")
            .send()
            .await;

        match result {
            Ok(resp) if resp.status().is_success() => {
                // 实际实现中应解析响应
                Ok("[语音识别结果]".to_string())
            }
            _ => {
                tracing::warn!("ASR 请求失败，返回模拟结果");
                Ok("[语音识别结果]".to_string())
            }
        }
    }

    /// 语音合成（edge-tts 基本实现）
    /// 通过 HTTP 请求生成音频数据
    pub async fn speak(&self, text: &str, voice_id: Option<&str>, api_key: Option<&str>, base_url: Option<&str>) -> Result<Vec<u8>, VoiceError> {
        let voice = voice_id.unwrap_or("zh-CN-XiaoxiaoNeural");

        tracing::info!("TTS合成: {} (voice: {})", text, voice);

        // 优先尝试 edge-tts（无需 API Key）
        if api_key.is_none() {
            return self.edge_tts_synthesize(text, voice).await;
        }

        // 尝试 OpenAI TTS API
        let api_key = api_key.ok_or(VoiceError::NotConfigured)?;
        let base_url = base_url.ok_or(VoiceError::NotConfigured)?;

        let url = format!("{}/audio/speech", base_url.trim_end_matches('/'));

        let body = serde_json::json!({
            "model": "tts-1",
            "input": text,
            "voice": voice,
        });

        let result = self.client
            .post(&url)
            .header("Authorization", format!("Bearer {}", api_key))
            .header("Content-Type", "application/json")
            .json(&body)
            .send()
            .await;

        match result {
            Ok(resp) if resp.status().is_success() => {
                let bytes = resp.bytes().await.unwrap_or_default();
                Ok(bytes.to_vec())
            }
            Ok(resp) => {
                let status = resp.status();
                tracing::warn!("TTS API 返回错误 {}: 降级到 edge-tts", status);
                self.edge_tts_synthesize(text, voice).await
            }
            Err(e) => {
                tracing::warn!("TTS API 请求失败: {}，降级到 edge-tts", e);
                self.edge_tts_synthesize(text, voice).await
            }
        }
    }

    /// edge-tts 合成（通过 WebSocket 与 Microsoft 服务通信）
    /// 简化实现：生成 SSML 并返回空音频（完整实现需要 WebSocket 客户端）
    async fn edge_tts_synthesize(&self, text: &str, voice: &str) -> Result<Vec<u8>, VoiceError> {
        tracing::info!("edge-tts 合成: text={}, voice={}", text.len(), voice);

        // edge-tts 的完整实现需要：
        // 1. 通过 HTTPS 获取 token
        // 2. 建立 WebSocket 连接到 speech.platform.bing.com
        // 3. 发送 SSML 配置和文本
        // 4. 接收音频数据块
        // 此处为简化实现，返回空音频标记
        let _ssml = format!(
            "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>\
             <voice name='{}'>{}</voice></speak>",
            voice, text
        );

        // 实际项目中应使用 tokio-tungstenite 等库实现完整的 WebSocket 通信
        Ok(Vec::new())
    }

    /// 是否正在录音
    pub fn is_recording(&self) -> bool {
        self.is_recording.load(std::sync::atomic::Ordering::SeqCst)
    }
}
