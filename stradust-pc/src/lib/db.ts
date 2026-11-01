/**
 * IndexedDB 轻量持久层
 * 为浏览器模式与 Tauri 模式提供统一的本地数据后端（替代 mock 层）。
 * 单库多 store：每个领域一个 objectStore，主键 id，常用二级索引 personaId / timestamp。
 */

export type Collection =
  | "personas"
  | "messages"
  | "sessions"
  | "favorites"
  | "memories"
  | "memoryPool"
  | "globalMemoryPool"
  | "diaries"
  | "moments"
  | "groupChats"
  | "stickers"
  | "capsules"
  | "albumEntries"
  | "achievements"
  | "affection"
  | "checkins"
  | "growth"
  | "milestones"
  | "emotionLog"
  | "nicknames"
  | "alarms"
  | "wakeTasks"
  | "skins"
  | "bubbleSkins"
  | "worlds"
  | "worldEvents"
  | "ilinkMessages"
  | "proactiveLog"
  | "stats"
  | "kv";

const DB_NAME = "stradust-web";
const DB_VERSION = 2;

let dbPromise: Promise<IDBDatabase> | null = null;

function openDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      const all: Collection[] = [
        "personas", "messages", "sessions", "favorites", "memories", "memoryPool",
        "globalMemoryPool", "diaries", "moments", "groupChats", "stickers", "capsules",
        "albumEntries", "achievements", "affection", "checkins", "growth", "milestones", "emotionLog",
        "nicknames", "alarms", "wakeTasks", "skins", "bubbleSkins", "worlds", "worldEvents",
        "ilinkMessages", "proactiveLog", "stats", "kv",
      ];
      for (const name of all) {
        if (!db.objectStoreNames.contains(name)) {
          const store = db.createObjectStore(name, { keyPath: "id" });
          // 常用索引（存在 id 之外可查询字段时按需使用）
          try { store.createIndex("byPersona", "personaId", { unique: false }); } catch { /* ignore */ }
          try { store.createIndex("byTime", "timestamp", { unique: false }); } catch { /* ignore */ }
        }
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return dbPromise;
}

function tx<T>(store: Collection, mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T> | void): Promise<T> {
  return openDb().then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const t = db.transaction(store, mode);
        const s = t.objectStore(store);
        let result: T = undefined as unknown as T;
        const r = fn(s);
        if (r) {
          (r as IDBRequest<T>).onsuccess = () => {
            result = (r as IDBRequest<T>).result;
          };
        }
        t.oncomplete = () => resolve(result);
        t.onerror = () => reject(t.error);
        t.onabort = () => reject(t.error);
      }),
  );
}

/** 可存储记录：必有 id + 开放字段（type 别名隐式兼容 interface，interface 自身无索引签名） */
export type StoredRecord = { id: string; [key: string]: unknown };

/** 获取单条 */
export async function getOne<T>(collection: Collection, id: string): Promise<T | null> {
  const r = await tx<T>(collection, "readonly", (s) => s.get(id));
  return r ?? null;
}

/** 全量获取 */
export async function getAll<T>(collection: Collection): Promise<T[]> {
  return (await tx<T[]>(collection, "readonly", (s) => s.getAll())) ?? [];
}

/** 按 personaId 索引获取 */
export async function getByPersona<T>(collection: Collection, personaId: string): Promise<T[]> {
  return (await tx<T[]>(collection, "readonly", (s) => s.index("byPersona").getAll(personaId))) ?? [];
}

/** upsert（存在则覆盖）；参数放宽为"必有 id 的任意对象"，兼容无索引签名的领域 interface */
export async function put<T extends { id: string }>(collection: Collection, value: T): Promise<T> {
  await tx(collection, "readwrite", (s) => s.put(value));
  return value;
}

/** 批量 upsert */
export async function putAll<T extends { id: string }>(collection: Collection, values: T[]): Promise<void> {
  await tx(collection, "readwrite", (s) => {
    values.forEach((v) => s.put(v));
  });
}

/** 删除一条 */
export async function remove(collection: Collection, id: string): Promise<void> {
  await tx(collection, "readwrite", (s) => s.delete(id));
}

/** 清空一个 store */
export async function clear(collection: Collection): Promise<void> {
  await tx(collection, "readwrite", (s) => s.clear());
}

/** 条件过滤（小数据集足够） */
export async function findWhere<T extends StoredRecord>(
  collection: Collection,
  predicate: (item: T) => boolean,
): Promise<T[]> {
  const all = await getAll<T>(collection);
  return all.filter(predicate);
}

// ─── kv（设置快照、游标、RAG向量缓存等） ───

export async function kvGet<T>(key: string): Promise<T | null> {
  const r = await getOne<{ id: string; value: T }>("kv", key);
  return r ? r.value : null;
}

export async function kvSet<T>(key: string, value: T): Promise<void> {
  await put("kv", { id: key, value });
}

// ─── 导入导出（数据迁移，对齐 Android DataMigrationManager） ───

const ALL_COLLECTIONS: Collection[] = [
  "personas", "messages", "sessions", "favorites", "memories", "memoryPool",
  "globalMemoryPool", "diaries", "moments", "groupChats", "stickers", "capsules",
  "albumEntries", "achievements", "affection", "checkins", "growth", "milestones", "emotionLog",
  "nicknames", "alarms", "wakeTasks", "skins", "bubbleSkins", "worlds", "worldEvents",
  "ilinkMessages", "proactiveLog", "stats",
];

export async function exportAll(): Promise<Record<string, StoredRecord[]>> {
  const out: Record<string, StoredRecord[]> = {};
  for (const c of ALL_COLLECTIONS) {
    out[c] = await getAll<StoredRecord>(c);
  }
  return out;
}

export async function importAll(data: Record<string, StoredRecord[]>, mode: "merge" | "replace" = "merge"): Promise<void> {
  for (const c of ALL_COLLECTIONS) {
    const items = data[c];
    if (!items) continue;
    if (mode === "replace") await clear(c);
    await putAll(c, items);
  }
}

export function newId(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}
