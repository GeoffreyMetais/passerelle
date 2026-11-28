use crate::domain::errors::DomainError;
use chrono::{DateTime, Utc};
use sqlx::SqlitePool;
use sqlx::sqlite::{SqliteConnectOptions, SqliteJournalMode, SqlitePoolOptions, SqliteSynchronous};
use std::path::Path;
use std::str::FromStr;
use std::time::Duration;

pub async fn init_sqlite_pool(state_dir: &Path) -> Result<SqlitePool, DomainError> {
    tokio::fs::create_dir_all(state_dir)
        .await
        .map_err(|e| DomainError::Internal(format!("Impossible de cr\u{e9}er STATE_DIR: {e}")))?;

    let db_path = state_dir.join("passerelle.db");
    let db_url = format!("sqlite://{}", db_path.display());

    let opts = SqliteConnectOptions::from_str(&db_url)
        .map_err(|e| DomainError::Internal(format!("SQLite URL error: {e}")))?
        .create_if_missing(true)
        .journal_mode(SqliteJournalMode::Wal)
        .synchronous(SqliteSynchronous::Normal)
        .busy_timeout(Duration::from_secs(5))
        .foreign_keys(true);

    let pool = SqlitePoolOptions::new()
        .max_connections(8)
        .connect_with(opts)
        .await
        .map_err(|e| DomainError::Internal(format!("SQLite connect error: {e}")))?;

    sqlx::migrate!("./migrations")
        .run(&pool)
        .await
        .map_err(|e| DomainError::Internal(format!("SQLite migration error: {e}")))?;

    Ok(pool)
}

pub(crate) fn fmt_dt(dt: DateTime<Utc>) -> String {
    dt.to_rfc3339()
}

pub(crate) fn parse_dt(s: &str) -> Result<DateTime<Utc>, DomainError> {
    DateTime::parse_from_rfc3339(s)
        .map(|dt| dt.with_timezone(&Utc))
        .map_err(|e| DomainError::Internal(format!("Invalid timestamp '{s}': {e}")))
}
