-- Initial schema for Passerelle backend
CREATE TABLE IF NOT EXISTS share_links (
    id TEXT PRIMARY KEY NOT NULL,
    slug TEXT NOT NULL UNIQUE COLLATE NOCASE,
    relative_path TEXT NOT NULL,
    target_name TEXT NOT NULL,
    is_directory INTEGER NOT NULL,
    password_hash TEXT,
    expires_at TEXT,
    max_downloads INTEGER,
    download_count INTEGER NOT NULL DEFAULT 0,
    is_revoked INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    last_accessed_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_share_links_slug ON share_links(slug);
CREATE INDEX IF NOT EXISTS idx_share_links_relative_path ON share_links(relative_path);

CREATE TABLE IF NOT EXISTS api_sessions (
    id TEXT PRIMARY KEY NOT NULL,
    refresh_token_hash TEXT NOT NULL UNIQUE,
    device_name TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_api_sessions_token_hash ON api_sessions(refresh_token_hash);
