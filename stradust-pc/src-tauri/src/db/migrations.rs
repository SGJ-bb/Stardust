// 所有建表SQL，按版本号顺序执行迁移

/// 单个迁移定义
pub struct Migration {
    pub version: i32,
    pub name: &'static str,
    pub up_sql: &'static str,
}

/// 获取所有迁移（按版本号顺序）
pub fn get_all_migrations() -> Vec<Migration> {
    vec![
        Migration {
            version: 1,
            name: "create_personas_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS personas (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    avatar TEXT,
                    system_prompt TEXT NOT NULL DEFAULT '',
                    personality TEXT NOT NULL DEFAULT '',
                    speaking_style TEXT NOT NULL DEFAULT '',
                    background_story TEXT,
                    world_lore TEXT,
                    default_emotion TEXT NOT NULL DEFAULT 'neutral',
                    model_id TEXT,
                    voice_id TEXT,
                    live2d_model TEXT,
                    is_default INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                );
            "#,
        },
        Migration {
            version: 2,
            name: "create_chat_sessions_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS chat_sessions (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    title TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    is_active INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
                CREATE INDEX IF NOT EXISTS idx_chat_sessions_persona ON chat_sessions(persona_id);
            "#,
        },
        Migration {
            version: 3,
            name: "create_chat_messages_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    session_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    emotion TEXT,
                    action TEXT,
                    tool_calls TEXT,
                    timestamp TEXT NOT NULL DEFAULT (datetime('now')),
                    is_favorite INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE,
                    FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
                );
                CREATE INDEX IF NOT EXISTS idx_chat_messages_session ON chat_messages(session_id);
                CREATE INDEX IF NOT EXISTS idx_chat_messages_persona ON chat_messages(persona_id);
            "#,
        },
        Migration {
            version: 4,
            name: "create_memories_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS memories (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    content TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT 'fact',
                    importance REAL NOT NULL DEFAULT 0.5,
                    source TEXT NOT NULL DEFAULT 'conversation',
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    last_accessed TEXT NOT NULL DEFAULT (datetime('now')),
                    access_count INTEGER NOT NULL DEFAULT 0,
                    is_active INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
                CREATE INDEX IF NOT EXISTS idx_memories_persona ON memories(persona_id);
                CREATE INDEX IF NOT EXISTS idx_memories_category ON memories(category);
            "#,
        },
        Migration {
            version: 5,
            name: "create_memory_pools_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS memory_pools (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    session_id TEXT NOT NULL,
                    core_memories TEXT NOT NULL DEFAULT '[]',
                    recent_memories TEXT NOT NULL DEFAULT '[]',
                    summary_memories TEXT NOT NULL DEFAULT '[]',
                    total_tokens INTEGER NOT NULL DEFAULT 0,
                    max_tokens INTEGER NOT NULL DEFAULT 4000,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
            "#,
        },
        Migration {
            version: 6,
            name: "create_memorable_moments_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS memorable_moments (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    session_id TEXT NOT NULL,
                    content TEXT NOT NULL,
                    score REAL NOT NULL DEFAULT 0.5,
                    emotion TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
            "#,
        },
        Migration {
            version: 7,
            name: "create_group_chats_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS group_chats (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    persona_ids TEXT NOT NULL DEFAULT '[]',
                    description TEXT,
                    is_active INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                );
            "#,
        },
        Migration {
            version: 8,
            name: "create_group_messages_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS group_messages (
                    id TEXT PRIMARY KEY,
                    group_id TEXT NOT NULL,
                    persona_id TEXT NOT NULL,
                    content TEXT NOT NULL,
                    emotion TEXT,
                    action TEXT,
                    timestamp TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (group_id) REFERENCES group_chats(id) ON DELETE CASCADE
                );
                CREATE INDEX IF NOT EXISTS idx_group_messages_group ON group_messages(group_id);
            "#,
        },
        Migration {
            version: 9,
            name: "create_virtual_worlds_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS world_configs (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    rules TEXT NOT NULL DEFAULT '[]',
                    era TEXT,
                    genre TEXT,
                    auto_tick_enabled INTEGER NOT NULL DEFAULT 0,
                    tick_interval_minutes INTEGER NOT NULL DEFAULT 60,
                    max_events_per_tick INTEGER NOT NULL DEFAULT 3,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
                CREATE TABLE IF NOT EXISTS world_states (
                    id TEXT PRIMARY KEY,
                    world_id TEXT NOT NULL,
                    persona_id TEXT NOT NULL,
                    current_situation TEXT NOT NULL DEFAULT '',
                    characters TEXT NOT NULL DEFAULT '{}',
                    environment TEXT NOT NULL DEFAULT '{}',
                    timeline TEXT NOT NULL DEFAULT '[]',
                    tick_count INTEGER NOT NULL DEFAULT 0,
                    last_tick_at TEXT NOT NULL DEFAULT (datetime('now')),
                    is_running INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (world_id) REFERENCES world_configs(id) ON DELETE CASCADE
                );
                CREATE TABLE IF NOT EXISTS story_events (
                    id TEXT PRIMARY KEY,
                    world_id TEXT NOT NULL,
                    tick_number INTEGER NOT NULL DEFAULT 0,
                    event_type TEXT NOT NULL DEFAULT 'daily',
                    title TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    participants TEXT NOT NULL DEFAULT '[]',
                    impact REAL NOT NULL DEFAULT 0.5,
                    timestamp TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (world_id) REFERENCES world_configs(id) ON DELETE CASCADE
                );
            "#,
        },
        Migration {
            version: 10,
            name: "create_diaries_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS diaries (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    mood TEXT,
                    tags TEXT NOT NULL DEFAULT '[]',
                    date TEXT NOT NULL,
                    is_auto_generated INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
                CREATE INDEX IF NOT EXISTS idx_diaries_persona ON diaries(persona_id);
            "#,
        },
        Migration {
            version: 11,
            name: "create_achievements_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS achievements (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    achievement_type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    icon TEXT,
                    is_unlocked INTEGER NOT NULL DEFAULT 0,
                    progress REAL NOT NULL DEFAULT 0.0,
                    target REAL NOT NULL DEFAULT 1.0,
                    unlocked_at TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
                CREATE TABLE IF NOT EXISTS check_ins (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    check_in_date TEXT NOT NULL,
                    streak_count INTEGER NOT NULL DEFAULT 1,
                    reward_exp INTEGER NOT NULL DEFAULT 10,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
                CREATE UNIQUE INDEX IF NOT EXISTS idx_check_ins_date ON check_ins(persona_id, check_in_date);
            "#,
        },
        Migration {
            version: 12,
            name: "create_affection_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS affection_data (
                    persona_id TEXT PRIMARY KEY,
                    level TEXT NOT NULL DEFAULT 'stranger',
                    exp INTEGER NOT NULL DEFAULT 0,
                    total_exp INTEGER NOT NULL DEFAULT 0,
                    trust_score REAL NOT NULL DEFAULT 0.0,
                    intimacy_score REAL NOT NULL DEFAULT 0.0,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
            "#,
        },
        Migration {
            version: 13,
            name: "create_settings_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                );
                CREATE TABLE IF NOT EXISTS provider_profiles (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    provider_type TEXT NOT NULL DEFAULT 'openai',
                    api_key TEXT,
                    base_url TEXT NOT NULL DEFAULT 'https://api.openai.com/v1',
                    model_name TEXT NOT NULL DEFAULT 'gpt-4',
                    temperature REAL NOT NULL DEFAULT 0.7,
                    max_tokens INTEGER NOT NULL DEFAULT 4096,
                    top_p REAL NOT NULL DEFAULT 1.0,
                    frequency_penalty REAL NOT NULL DEFAULT 0.0,
                    presence_penalty REAL NOT NULL DEFAULT 0.0,
                    is_default INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                );
            "#,
        },
        Migration {
            version: 14,
            name: "create_moments_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS moments (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    content TEXT NOT NULL,
                    mood TEXT,
                    images TEXT NOT NULL DEFAULT '[]',
                    likes TEXT NOT NULL DEFAULT '[]',
                    comments TEXT NOT NULL DEFAULT '[]',
                    is_auto_generated INTEGER NOT NULL DEFAULT 0,
                    visibility TEXT NOT NULL DEFAULT 'public',
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
            "#,
        },
        Migration {
            version: 15,
            name: "create_stickers_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS stickers (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    thumbnail TEXT,
                    tags TEXT NOT NULL DEFAULT '[]',
                    category TEXT,
                    is_animated INTEGER NOT NULL DEFAULT 0,
                    width INTEGER,
                    height INTEGER,
                    file_size INTEGER,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                );
            "#,
        },
        Migration {
            version: 16,
            name: "create_calendar_events_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS calendar_events (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    event_type TEXT NOT NULL DEFAULT 'schedule',
                    start_time TEXT NOT NULL,
                    end_time TEXT,
                    is_all_day INTEGER NOT NULL DEFAULT 0,
                    recurrence TEXT,
                    reminder_minutes INTEGER,
                    color TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
                CREATE INDEX IF NOT EXISTS idx_calendar_events_start ON calendar_events(start_time);
            "#,
        },
        Migration {
            version: 17,
            name: "create_time_capsules_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS time_capsules (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    mood TEXT,
                    images TEXT NOT NULL DEFAULT '[]',
                    sealed_at TEXT NOT NULL DEFAULT (datetime('now')),
                    open_at TEXT NOT NULL,
                    is_opened INTEGER NOT NULL DEFAULT 0,
                    opened_at TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
            "#,
        },
        Migration {
            version: 18,
            name: "create_album_entries_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS album_entries (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    image_path TEXT NOT NULL,
                    thumbnail TEXT,
                    tags TEXT NOT NULL DEFAULT '[]',
                    mood TEXT,
                    is_milestone INTEGER NOT NULL DEFAULT 0,
                    event_date TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
            "#,
        },
        Migration {
            version: 19,
            name: "create_rag_chunks_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS rag_chunks (
                    id TEXT PRIMARY KEY,
                    persona_id TEXT NOT NULL,
                    source_type TEXT NOT NULL DEFAULT 'conversation',
                    source_id TEXT,
                    content TEXT NOT NULL,
                    chunk_index INTEGER NOT NULL DEFAULT 0,
                    embedding BLOB,
                    metadata TEXT NOT NULL DEFAULT '{}',
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
                CREATE INDEX IF NOT EXISTS idx_rag_chunks_persona ON rag_chunks(persona_id);
            "#,
        },
        Migration {
            version: 20,
            name: "create_session_contexts_table",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS session_contexts (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    persona_id TEXT NOT NULL,
                    turn_count INTEGER NOT NULL DEFAULT 0,
                    inherited_memory TEXT NOT NULL DEFAULT '[]',
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
                );
                CREATE UNIQUE INDEX IF NOT EXISTS idx_session_contexts_session ON session_contexts(session_id);
            "#,
        },
        Migration {
            version: 21,
            name: "create_pixel_pet_tables",
            up_sql: r#"
                CREATE TABLE IF NOT EXISTS pixel_pets (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    reference_image_path TEXT,
                    base_prompt TEXT NOT NULL,
                    negative_prompt TEXT,
                    sprite_width INTEGER DEFAULT 64,
                    sprite_height INTEGER DEFAULT 64,
                    fps INTEGER DEFAULT 8,
                    scale REAL DEFAULT 3.0,
                    render_mode TEXT DEFAULT 'pixel_perfect',
                    is_active INTEGER DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS pet_actions (
                    id TEXT PRIMARY KEY,
                    pet_id TEXT NOT NULL REFERENCES pixel_pets(id) ON DELETE CASCADE,
                    name TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    description TEXT,
                    prompt TEXT NOT NULL,
                    frame_count INTEGER DEFAULT 4,
                    frame_duration INTEGER DEFAULT 125,
                    loop_mode TEXT DEFAULT 'loop',
                    is_builtin INTEGER DEFAULT 0,
                    trigger_events TEXT,
                    sort_order INTEGER DEFAULT 0,
                    created_at INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_actions_pet_id ON pet_actions(pet_id);
                CREATE TABLE IF NOT EXISTS pixel_frames (
                    id TEXT PRIMARY KEY,
                    action_id TEXT NOT NULL REFERENCES pet_actions(id) ON DELETE CASCADE,
                    frame_index INTEGER NOT NULL,
                    image_path TEXT NOT NULL,
                    image_hash TEXT,
                    prompt_used TEXT,
                    status TEXT DEFAULT 'generating',
                    generated_at INTEGER,
                    UNIQUE(action_id, frame_index)
                );
                CREATE INDEX IF NOT EXISTS idx_frames_action_id ON pixel_frames(action_id);
                CREATE TABLE IF NOT EXISTS pixel_gen_configs (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    provider TEXT DEFAULT 'custom',
                    api_url TEXT,
                    api_key TEXT,
                    model TEXT,
                    style_prompt TEXT DEFAULT '',
                    size TEXT DEFAULT '64x64',
                    steps INTEGER DEFAULT 20,
                    cfg_scale REAL DEFAULT 7.0,
                    batch_size INTEGER DEFAULT 1
                );
            "#,
        },
    ]
}
