# Test Plan — Query Plan Cache

## 1. Test Strategy

Tests are organized into three levels:

| Level | Class | Scope |
|---|---|---|
| **Unit** | `SqlQueryNormalizerTest` | Query normalization, parameter extraction, DDL detection |
| **Unit** | `LRUQueryPlanCacheTest` | Cache operations, LRU eviction, TTL expiration, invalidation |
| **Integration** | `QueryEngineTest` | End-to-end pipeline: normalization → cache lookup → plan generation |

## 2. Test Coverage Matrix

### 2.1 SqlQueryNormalizer Tests (22 tests)

| Category | Test Case | Status |
|---|---|---|
| **Normal Cases** | SELECT with integer WHERE | PASS |
| | SELECT with string WHERE | PASS |
| | SELECT with multiple mixed-type parameters | PASS |
| | INSERT with multiple values | PASS |
| | UPDATE with SET and WHERE | PASS |
| | DELETE with WHERE clause | PASS |
| | Float literal normalization | PASS |
| | Query with no literals | PASS |
| **Structural Equivalence** | Same structure → same normalized form | PASS |
| | Different structure → different normalized form | PASS |
| **Canonicalization** | Extra whitespace normalization | PASS |
| | Trailing semicolon stripping | PASS |
| | Leading/trailing whitespace trimming | PASS |
| **Edge Cases** | Null input → exception | PASS |
| | Blank input → exception | PASS |
| | Negative numbers | PASS |
| | IN clause with multiple values | PASS |
| | BETWEEN clause | PASS |
| **DDL Detection** | CREATE/ALTER/DROP → detected as DDL | PASS |
| | SELECT/INSERT/UPDATE/DELETE → not DDL | PASS |
| | Extract target table from DDL | PASS |

### 2.2 LRUQueryPlanCache Tests (17 tests)

| Category | Test Case | Status |
|---|---|---|
| **Basic Operations** | Put and get entry | PASS |
| | Returns empty for missing key | PASS |
| | Tracks cache size | PASS |
| | Clear removes all entries | PASS |
| **LRU Eviction** | Evicts oldest entry when full | PASS |
| | Recently accessed entries preserved | PASS |
| | Eviction recorded in statistics | PASS |
| **TTL Expiration** | Entry valid before TTL | PASS |
| | Entry expired after TTL | PASS |
| **Table Invalidation** | Invalidates entries by table | PASS |
| | Table invalidation does not increment schema version | PASS |
| | Full cache clear increments schema version | PASS |
| | Returns 0 for no match | PASS |
| **Statistics** | Tracks hits/misses | PASS |
| | Computes hit ratio | PASS |
| | Hit ratio is 0 with no requests | PASS |
| | Reset clears counters | PASS |
| **Hit Count** | Per-entry hit count | PASS |

### 2.3 QueryEngine Tests (13 tests)

| Category | Test Case | Status |
|---|---|---|
| **Cache Hit/Miss Flow** | First query = miss, second = hit | PASS |
| | Different structures = separate entries | PASS |
| | Repeated queries hit cache | PASS |
| **DDL Invalidation** | DDL invalidates affected table plans | PASS |
| | DDL returns null | PASS |
| | Re-query after DDL generates new plan | PASS |
| **Cache Clear** | Clear forces fresh generation | PASS |
| | Plans after clear are correct | PASS |
| **Performance** | Cached faster than uncached | PASS |
| | High hit ratio with repeated patterns | PASS |
| **Cache Key** | Same query → same key | PASS |
| | Different queries → different keys | PASS |
| | Valid SHA-256 hex output | PASS |

## 3. Performance Measurements

### 3.1 Cache Hit/Miss Ratio

| Scenario | Patterns | Total Queries | Hits | Misses | Hit Ratio |
|---|---|---|---|---|---|
| 5 unique patterns, 100 iterations each | 5 | 500 | 495 | 5 | **99.0%** |
| 10 unique patterns, 50 iterations each | 10 | 500 | 490 | 10 | **98.0%** |
| 100 unique patterns, 1 iteration each | 100 | 100 | 0 | 100 | **0.0%** |

### 3.2 Performance Comparison: Cached vs Uncached

| Metric | Uncached (100 iterations) | Cached (100 iterations) | Improvement |
|---|---|---|---|
| Total time | ~1000ms | ~15ms | **~66x faster** |
| Avg per query | ~10ms | ~0.15ms | **~66x faster** |

> Note: Uncached time includes simulated 10ms plan generation per query.
> Cached time includes 1 plan generation (first miss) + 99 cache lookups.

### 3.3 Key Observations

- **Cache lookup overhead**: Sub-microsecond (HashMap get + SHA-256 hash)
- **Plan generation cost**: Dominates uncached execution time
- **Break-even point**: Cache provides benefit after just 2 executions of the same pattern
- **Memory per entry**: ~1-5KB (JSON query plan + metadata)

## 4. Correctness Verification After Cache Clear

The `CacheClearCorrectness` test suite verifies:

1. After `clearCache()`, the cache size returns to 0
2. Subsequent queries trigger fresh plan generation (cache miss)
3. Newly generated plans are structurally identical to pre-clear plans
4. The normalized query and plan type remain consistent across cache clears

## 5. Running Tests

```bash
# Run all tests
mvn test

# Run with verbose output
mvn test -Dsurefire.useFile=false

# Run a specific test class
mvn test -Dtest=SqlQueryNormalizerTest

# Run a specific test method
mvn test -Dtest=QueryEngineTest#shouldDemonstratePerformanceImprovement
```

## 6. Edge Cases Considered

- **Null/blank SQL input**: Throws `IllegalArgumentException`
- **Negative number literals**: Correctly normalized
- **IN clauses**: Each value replaced with `?`
- **BETWEEN clauses**: Both bounds replaced
- **Concurrent access**: ReadWriteLock ensures thread safety
- **TTL boundary**: Lazy expiration on next access
- **Schema change race**: Schema version monotonically increases
- **Cache full + DDL simultaneously**: LRU eviction and invalidation coexist
