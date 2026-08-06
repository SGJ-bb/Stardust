// 加密工具，基于aes-gcm

use aes_gcm::{
    aead::{Aead, KeyInit, OsRng},
    Aes256Gcm, Nonce,
};
use base64::{engine::general_purpose::STANDARD as BASE64, Engine};
use rand::RngCore;
use thiserror::Error;

/// 加密错误类型
#[derive(Debug, Error)]
pub enum CryptoError {
    #[error("加密失败: {0}")]
    EncryptFailed(String),
    #[error("解密失败: {0}")]
    DecryptFailed(String),
    #[error("无效的密钥")]
    InvalidKey,
}

/// 生成随机密钥
pub fn generate_key() -> [u8; 32] {
    let mut key = [0u8; 32];
    OsRng.fill_bytes(&mut key);
    key
}

/// AES-256-GCM 加密
pub fn encrypt(plaintext: &str, key: &[u8; 32]) -> Result<String, CryptoError> {
    let cipher = Aes256Gcm::new_from_slice(key)
        .map_err(|_| CryptoError::InvalidKey)?;

    // 生成随机nonce
    let mut nonce_bytes = [0u8; 12];
    OsRng.fill_bytes(&mut nonce_bytes);
    let nonce = Nonce::from_slice(&nonce_bytes);

    let ciphertext = cipher
        .encrypt(nonce, plaintext.as_bytes())
        .map_err(|e| CryptoError::EncryptFailed(e.to_string()))?;

    // 格式: base64(nonce + ciphertext)
    let mut combined = Vec::with_capacity(12 + ciphertext.len());
    combined.extend_from_slice(&nonce_bytes);
    combined.extend_from_slice(&ciphertext);

    Ok(BASE64.encode(&combined))
}

/// AES-256-GCM 解密
pub fn decrypt(encrypted: &str, key: &[u8; 32]) -> Result<String, CryptoError> {
    let cipher = Aes256Gcm::new_from_slice(key)
        .map_err(|_| CryptoError::InvalidKey)?;

    let combined = BASE64
        .decode(encrypted)
        .map_err(|e| CryptoError::DecryptFailed(e.to_string()))?;

    if combined.len() < 12 {
        return Err(CryptoError::DecryptFailed("数据长度不足".to_string()));
    }

    let (nonce_bytes, ciphertext) = combined.split_at(12);
    let nonce = Nonce::from_slice(nonce_bytes);

    let plaintext = cipher
        .decrypt(nonce, ciphertext)
        .map_err(|e| CryptoError::DecryptFailed(e.to_string()))?;

    String::from_utf8(plaintext)
        .map_err(|e| CryptoError::DecryptFailed(e.to_string()))
}

/// SHA-256 哈希
pub fn sha256_hash(data: &str) -> String {
    use sha2::{Sha256, Digest};
    let mut hasher = Sha256::new();
    hasher.update(data.as_bytes());
    let result = hasher.finalize();
    format!("{:x}", result)
}

/// 生成随机字符串
pub fn random_string(length: usize) -> String {
    use rand::Rng;
    const CHARSET: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    let mut rng = rand::thread_rng();
    (0..length)
        .map(|_| {
            let idx = rng.gen_range(0..CHARSET.len());
            CHARSET[idx] as char
        })
        .collect()
}
