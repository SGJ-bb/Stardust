// SQLite连接管理+迁移执行

use rusqlite::Connection;
use std::path::Path;
use std::sync::Mutex;
use thiserror::Error;

use super::migrations;

/// 数据库错误类型
#[derive(Debug, Error)]
pub enum DbError {
    #[error("数据库连接失败: {0}")]
    ConnectionFailed(String),
    #[error("迁移执行失败: {0}")]
    MigrationFailed(String),
    #[error("查询执行失败: {0}")]
    QueryFailed(String),
    #[error("数据未找到: {0}")]
    NotFound(String),
    #[error("数据已存在: {0}")]
    AlreadyExists(String),
}

/// 数据库封装
#[derive(Debug)]
pub struct Database {
    pub conn: Mutex<Connection>,
}

impl Database {
    /// 打开或创建数据库
    pub fn open(db_path: &Path) -> Result<Self, DbError> {
        let conn = Connection::open(db_path)
            .map_err(|e| DbError::ConnectionFailed(e.to_string()))?;

        // 启用外键约束
        conn.execute_batch("PRAGMA foreign_keys = ON;")
            .map_err(|e| DbError::ConnectionFailed(e.to_string()))?;

        // 启用WAL模式提升并发性能
        conn.execute_batch("PRAGMA journal_mode = WAL;")
            .map_err(|e| DbError::ConnectionFailed(e.to_string()))?;

        // 启动时执行checkpoint，恢复上次可能未完成的数据
        conn.execute_batch("PRAGMA wal_checkpoint(TRUNCATE);")
            .map_err(|e| DbError::ConnectionFailed(
                format!("WAL checkpoint 失败: {}", e)
            ))?;

        let db = Database {
            conn: Mutex::new(conn),
        };

        // 执行迁移
        db.run_migrations()?;

        Ok(db)
    }

    /// 在内存中创建数据库（用于测试）
    pub fn open_in_memory() -> Result<Self, DbError> {
        let conn = Connection::open_in_memory()
            .map_err(|e| DbError::ConnectionFailed(e.to_string()))?;

        conn.execute_batch("PRAGMA foreign_keys = ON;")
            .map_err(|e| DbError::ConnectionFailed(e.to_string()))?;

        let db = Database {
            conn: Mutex::new(conn),
        };

        db.run_migrations()?;

        Ok(db)
    }

    /// 执行所有迁移
    fn run_migrations(&self) -> Result<(), DbError> {
        let conn = self.conn.lock()
            .map_err(|e| DbError::ConnectionFailed(e.to_string()))?;

        // 创建迁移版本表
        conn.execute_batch(
            "CREATE TABLE IF NOT EXISTS _migrations (
                version INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                applied_at TEXT NOT NULL DEFAULT (datetime('now'))
            );"
        ).map_err(|e| DbError::MigrationFailed(e.to_string()))?;

        // 获取当前版本
        let current_version: i32 = conn.query_row(
            "SELECT COALESCE(MAX(version), 0) FROM _migrations",
            [],
            |row| row.get(0),
        ).unwrap_or(0);

        // 按版本号执行迁移
        let migrations = migrations::get_all_migrations();
        for migration in migrations {
            if migration.version > current_version {
                tracing::info!("执行数据库迁移 v{}: {}", migration.version, migration.name);

                // 每个迁移在独立事务中执行
                let tx = conn.unchecked_transaction()
                    .map_err(|e| DbError::MigrationFailed(
                        format!("开启事务 v{} 失败: {}", migration.version, e)
                    ))?;

                tx.execute_batch(&migration.up_sql)
                    .map_err(|e| DbError::MigrationFailed(
                        format!("迁移 v{} 失败: {}", migration.version, e)
                    ))?;

                tx.execute(
                    "INSERT INTO _migrations (version, name) VALUES (?1, ?2)",
                    rusqlite::params![migration.version, migration.name],
                ).map_err(|e| DbError::MigrationFailed(
                    format!("记录迁移版本 v{} 失败: {}", migration.version, e)
                ))?;

                tx.commit().map_err(|e| DbError::MigrationFailed(
                    format!("提交迁移事务 v{} 失败: {}", migration.version, e)
                ))?;

                tracing::info!("数据库迁移 v{} 完成", migration.version);
            }
        }

        Ok(())
    }

    /// 执行WAL checkpoint，将WAL数据合并到主数据库
    pub fn checkpoint(&self) -> Result<(), DbError> {
        let conn = self.conn.lock()
            .map_err(|e| DbError::ConnectionFailed(e.to_string()))?;
        conn.execute_batch("PRAGMA wal_checkpoint(TRUNCATE);")
            .map_err(|e| DbError::QueryFailed(
                format!("WAL checkpoint 失败: {}", e)
            ))?;
        Ok(())
    }
}

impl Drop for Database {
    fn drop(&mut self) {
        // 关闭时执行WAL checkpoint，确保数据持久化到主数据库文件
        if let Ok(conn) = self.conn.lock() {
            let _ = conn.execute_batch("PRAGMA wal_checkpoint(TRUNCATE);");
        }
    }
}
