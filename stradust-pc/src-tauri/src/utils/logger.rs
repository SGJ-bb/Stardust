// 日志工具，基于tracing

use tracing_subscriber::EnvFilter;

/// 初始化日志系统
pub fn init_logger() {
    let filter = EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| EnvFilter::new("info,stradust_pc_lib=debug"));

    tracing_subscriber::fmt()
        .with_env_filter(filter)
        .with_target(true)
        .with_thread_ids(false)
        .with_file(false)
        .with_line_number(false)
        .init();

    tracing::info!("日志系统初始化完成");
}

/// 记录错误日志
pub fn log_error(module: &str, error: &str) {
    tracing::error!(module = module, error = error);
}

/// 记录警告日志
pub fn log_warn(module: &str, msg: &str) {
    tracing::warn!(module = module, msg = msg);
}

/// 记录信息日志
pub fn log_info(module: &str, msg: &str) {
    tracing::info!(module = module, msg = msg);
}

/// 记录调试日志
pub fn log_debug(module: &str, msg: &str) {
    tracing::debug!(module = module, msg = msg);
}
