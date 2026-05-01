# Query Plan Cache — Design Document

## 1. Overview

This document describes the design of a **Query Plan Cache** for interpreted SQL queries. The cache reduces redundant parsing and optimization overhead by detecting structurally identical queries, normalizing them into canonical forms, and reusing previously generated execution plans.

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         QueryEngine                             │
│                                                                 │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────────────┐   │
│  │  Raw SQL  │───▶│ ANTLR Parser │───▶│  Query Normalizer    │   │
│  │  Query    │    │  (Lexer +    │    │  (Visitor Pattern)   │   │
│  └──────────┘    │   Parser)    │    │                      │   │
│                  └──────────────┘    │  - Replace literals   │   │
│                                      │    with placeholders  │   │
│                                      │  - Extract bindings   │   │
│                                      │  - Canonicalize       │   │
│                                      └──────────┬───────────┘   │
│                                                  │               │
│                                      ┌───────────▼───────────┐   │
│                                      │  Normalized Query Key │   │
│                                      │  (SHA-256 Hash)       │   │
│                                      └───────────┬───────────┘   │
│                                                  │               │
│                                  ┌───────────────▼────────────┐  │
│                                  │    QueryPlanCache (LRU)    │  │
│                                  │                            │  │
│                                  │  Cache Hit?                │  │
│                                  │  ├─ YES → Return plan      │  │
│                                  │  └─ NO  → Generate plan,   │  │
│                                  │           store in cache    │  │
│                                  └────────────────────────────┘  │
│                                                                  │
│  Cache Invalidation Triggers:                                    │
│  - Schema change (DDL detected)                                  │
│  - Manual invalidation API                                       │
│  - TTL expiration                                                │
│  - LRU eviction when capacity exceeded                           │
└──────────────────────────────────────────────────────────────────┘
```

## 3. Query Normalization Strategy

### 3.1 Problem

Two queries with the same structure but different literal values produce identical execution plans:

```sql
SELECT * FROM orders WHERE customer_id = 101;
SELECT * FROM orders WHERE customer_id = 202;
```

Without normalization, each would be treated as a unique query, wasting cache space and plan generation time.

### 3.2 Approach: ANTLR-Based AST Visitor

We use the **ANTLR visitor pattern** to walk the parsed SQL Abstract Syntax Tree (AST) and:

1. **Replace all literal values** (integers, strings, floats, booleans) with positional placeholders (`?`).
2. **Extract parameter bindings** — store the original literal values in order for potential re-binding.
3. **Canonicalize whitespace and case** — normalize `SELECT` vs `select`, collapse multiple spaces.

### 3.3 Normalization Rules

| Original SQL | Normalized Form | Parameters |
|---|---|---|
| `SELECT * FROM orders WHERE id = 101` | `SELECT * FROM orders WHERE id = ?` | `[101]` |
| `SELECT name FROM users WHERE age > 25 AND city = 'NYC'` | `SELECT name FROM users WHERE age > ? AND city = ?` | `[25, "NYC"]` |
| `INSERT INTO logs VALUES (1, 'error', 1724684407)` | `INSERT INTO logs VALUES (?, ?, ?)` | `[1, "error", 1724684407]` |

### 3.4 Cache Key Generation

After normalization, we compute a **SHA-256 hash** of the normalized query string to produce a fixed-length cache key. This avoids storing long query strings as keys and provides constant-time lookups.

```
Cache Key = SHA-256("SELECT * FROM orders WHERE id = ?")
          = "a3f2b8c1..."
```

## 4. Caching Strategy

### 4.1 Cache Structure — LRU with TTL

- **Data Structure**: `LinkedHashMap` with access-order ordering (Java's built-in LRU support), wrapped in a `ConcurrentHashMap`-backed thread-safe implementation.
- **Eviction Policy**: Least Recently Used (LRU) — when the cache exceeds `maxSize`, the least recently accessed entry is evicted.
- **Time-To-Live (TTL)**: Each entry has a configurable TTL (default: 30 minutes). Expired entries are lazily evicted on access.

### 4.2 Cache Entry

Each cache entry stores:

```json
{
  "normalizedQuery": "SELECT * FROM orders WHERE id = ?",
  "cacheKey": "a3f2b8c1...",
  "queryPlan": { /* JSON execution plan */ },
  "createdAt": 1724684407000,
  "lastAccessedAt": 1724684500000,
  "hitCount": 42,
  "referencedTables": ["orders"]
}
```

### 4.3 Lookup and Reuse Flow

```
1. Receive raw SQL query
2. Parse with ANTLR → AST
3. Normalize AST → normalized query string + parameter bindings
4. Compute cache key (SHA-256 of normalized string)
5. Lookup in cache:
   a. CACHE HIT:
      - Verify entry is not expired (TTL check)
      - Increment hit counter
      - Return cached QueryPlan with new parameter bindings
   b. CACHE MISS:
      - Generate execution plan (external plan generator)
      - Store plan in cache with metadata
      - Return newly generated plan
```

## 5. Cache Invalidation Strategy

### 5.1 Invalidation Triggers

| Trigger | Scope | Description |
|---|---|---|
| **DDL Detection** | Table-specific | When a DDL statement (`ALTER TABLE`, `DROP INDEX`, `CREATE INDEX`) is detected, invalidate all cached plans referencing the affected table. |
| **TTL Expiration** | Per-entry | Entries older than `ttlMillis` are lazily evicted on next access. |
| **LRU Eviction** | Global | When cache reaches `maxSize`, the least recently used entry is removed. |
| **Manual Invalidation** | Global or table-specific | API to explicitly clear the entire cache or entries for a specific table. |
| **Schema Version Tracking** | Global | A monotonically increasing schema version counter. Each cache entry stores the schema version at creation time. If the current schema version is newer, the entry is stale. |

### 5.2 DDL Detection

The normalizer classifies incoming SQL into DML (SELECT, INSERT, UPDATE, DELETE) and DDL (CREATE, ALTER, DROP). DDL statements trigger:

1. Extract the affected table name from the DDL.
2. Iterate the cache and remove all entries whose `referencedTables` set includes the affected table.
3. Increment the global schema version counter.

## 6. Thread Safety

- Cache operations are synchronized using `ReadWriteLock`:
  - **Read lock** for `get()` / cache hits (concurrent reads allowed)
  - **Write lock** for `put()` / `invalidate()` / eviction
- `ConcurrentHashMap` is used for the underlying storage.
- `AtomicLong` counters for hit/miss statistics.

## 7. Performance Considerations

- **O(1) cache lookup** via hash map.
- **O(1) LRU eviction** via doubly-linked list (LinkedHashMap).
- **Normalization cost** is amortized — ANTLR parsing is the primary cost, but it's significantly cheaper than full plan generation.
- **Memory**: Each cached plan is a JSON object (~1-10KB). With a default cache size of 1000 entries, memory footprint is ~1-10MB.

## 8. Limitations and Future Work

- **Prepared statements**: This cache targets ad-hoc/interpreted queries. Prepared statements already benefit from server-side plan caching.
- **Statistics-dependent plans**: If the optimizer uses table statistics (cardinality estimates), cached plans may become suboptimal after significant data changes. Future work could integrate statistics versioning.
- **Multi-tenant isolation**: In a multi-tenant system, cache keys should include tenant ID to prevent cross-tenant plan reuse.
