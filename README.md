# Query Plan Cache for Interpreted Queries

A query plan caching mechanism that detects structurally similar SQL queries, normalizes them using ANTLR-based parsing concepts, and reuses cached execution plans to reduce parsing and optimization overhead.

## Project Structure

```
query-plan-cache/
├── DESIGN.md                          # Design document (strategy, architecture, decisions)
├── TEST-PLAN.md                       # Test plan with coverage matrix and results
├── README.md                          # This file
├── pom.xml                            # Maven build configuration
└── src/
    ├── main/java/com/queryplancache/
    │   ├── normalizer/
    │   │   ├── QueryNormalizer.java        # Interface for normalization strategies
    │   │   ├── SqlQueryNormalizer.java     # ANTLR-based SQL normalizer implementation
    │   │   └── ParameterBinding.java       # Normalized query + extracted parameters
    │   ├── cache/
    │   │   ├── QueryPlanCache.java         # Cache interface
    │   │   ├── LRUQueryPlanCache.java      # LRU cache with TTL and invalidation
    │   │   ├── CacheEntry.java             # Cache entry with metadata
    │   │   └── CacheStats.java             # Thread-safe statistics tracker
    │   ├── plan/
    │   │   ├── QueryPlan.java              # JSON-serializable execution plan
    │   │   ├── PlanOperation.java          # Single operation in the plan tree
    │   │   └── QueryPlanGenerator.java     # Simulated plan generator
    │   └── engine/
    │       └── QueryEngine.java            # Main engine (normalize → cache → generate)
    └── test/java/com/queryplancache/
        ├── normalizer/
        │   └── SqlQueryNormalizerTest.java # 22 tests: normalization, DDL, edge cases
        ├── cache/
        │   └── LRUQueryPlanCacheTest.java  # 16 tests: LRU, TTL, invalidation, stats
        └── engine/
            └── QueryEngineTest.java        # 13 tests: integration, performance, correctness
```

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+

### Build & Test
```bash
# Compile
mvn compile

# Run all tests
mvn test

# Run tests with output visible
mvn test -Dsurefire.useFile=false
```

## How It Works

### 1. Query Normalization (ANTLR Concepts)

Raw SQL queries are normalized by replacing all literal values with `?` placeholders:

```
Input:  SELECT * FROM orders WHERE customer_id = 101
Output: SELECT * FROM orders WHERE customer_id = ?
Params: [101]
```

This uses ANTLR Visitor pattern concepts:
- **Lexer** tokenizes SQL into STRING_LITERAL, INTEGER_LITERAL, FLOAT_LITERAL, etc.
- **Parser** builds an AST from the token stream
- **Visitor** walks the AST, replacing literal nodes with placeholders

See `SqlQueryNormalizer.java` for the full implementation with ANTLR pseudo-code in Javadoc.

### 2. Cache Lookup & Reuse

```
Raw SQL → Normalize → SHA-256 Hash → Cache Lookup
                                      ├── HIT:  Return cached plan
                                      └── MISS: Generate plan → Store → Return
```

The `LRUQueryPlanCache` provides:
- **O(1) lookup** via `LinkedHashMap`
- **LRU eviction** when capacity is exceeded
- **TTL expiration** (configurable, default 30 min)
- **Table-level invalidation** on DDL detection

### 3. Cache Invalidation

When DDL statements are detected (CREATE, ALTER, DROP):
1. Extract the affected table name
2. Remove all cached plans referencing that table
3. Increment the global schema version

## Key Design Decisions

| Decision | Rationale |
|---|---|
| SHA-256 for cache keys | Fixed-length, collision-resistant, O(1) lookup |
| LinkedHashMap for LRU | Built-in access-order tracking, O(1) eviction |
| ReadWriteLock | Concurrent reads, exclusive writes for thread safety |
| Lazy TTL expiration | Avoids background thread overhead, checked on access |
| Table-level invalidation | Granular — doesn't flush unrelated plans |

## Performance Results

| Metric | Value |
|---|---|
| Cache hit ratio (5 patterns, 500 queries) | **99.0%** |
| Speedup (cached vs uncached) | **~66x** |
| Cache lookup overhead | **< 1 microsecond** |

## Deliverables

1. **[DESIGN.md](DESIGN.md)** — Caching strategy, normalization, query plan reuse logic, cache management
2. **Source code** — Pseudo Java code using ANTLR library concepts (see `SqlQueryNormalizer.java`)
3. **[TEST-PLAN.md](TEST-PLAN.md)** — Test coverage, performance improvements, correctness checks

## AI Usage Documentation

AI tools (Windsurf Cascade) were used to accelerate development:
- **Architecture scaffolding**: Generated initial project structure and Maven POM
- **ANTLR pattern documentation**: Helped document the ANTLR visitor pattern pseudo-code
- **Test case generation**: Assisted in generating comprehensive edge case tests
- All code was reviewed, understood, and validated by the developer
