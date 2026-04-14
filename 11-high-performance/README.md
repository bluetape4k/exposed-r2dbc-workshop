> 한국어 버전: [README.ko.md](README.ko.md)

# 11. High Performance

A collection of examples for improving performance and scalability in Exposed R2DBC environments.
The examples in this directory cover topics closer to production environments — such as caching, routing, and read/write separation — rather than simple CRUD.

## High-Performance Strategy Overview

```mermaid
%%{init: {"theme": "neutral"}}%%
flowchart TD
    App["Application"] --> CF["DynamicRoutingConnectionFactory"]
    CF --> Strat{"Routing Strategy"}
    Strat -->|Write request| PDB["Primary DB (Write)"]
    Strat -->|Read request| RDB["Replica DB (Read)"]
    Strat -->|Tenant-based| TDB["Tenant-specific DB"]

    App --> Cache["Cache Layer"]
    Cache --> L1["L1: In-Memory (Caffeine)"]
    Cache --> L2["L2: Redis (Lettuce Coroutines)"]
    L1 -->|MISS| L2
    L2 -->|MISS| PDB

    classDef blue   fill:#E3F2FD,stroke:#90CAF9,color:#1565C0
    classDef green  fill:#E8F5E9,stroke:#A5D6A7,color:#2E7D32
    classDef purple fill:#F3E5F5,stroke:#CE93D8,color:#6A1B9A
    classDef orange fill:#FFF3E0,stroke:#FFCC80,color:#E65100
    classDef teal   fill:#E0F2F1,stroke:#80CBC4,color:#00695C
    classDef red    fill:#FFEBEE,stroke:#EF9A9A,color:#C62828

    class App blue
    class CF,Strat teal
    class PDB,RDB,TDB orange
    class Cache,L1,L2 green
```

## Cache Layer Structure (Class Diagram)

```mermaid
%%{init: {"theme": "neutral"}}%%
classDiagram
    class CacheStrategy {
        <<interface>>
        +get(key: K) V
        +put(key: K, value: V)
        +evict(key: K)
    }
    class L1Cache {
        <<Caffeine In-Memory>>
        -caffeineCache Cache~K, V~
        +get(key: K) V
        +put(key: K, value: V)
        +evict(key: K)
        +maximumSize: Long
        +expireAfterWrite: Duration
    }
    class L2Cache {
        <<Redis Lettuce Coroutines>>
        -redisClient RedisClient
        +get(key: K) V
        +put(key: K, value: V)
        +evict(key: K)
        +ttl: Duration
    }
    class TwoLevelCacheStrategy {
        -l1: L1Cache
        -l2: L2Cache
        +get(key: K) V
        +put(key: K, value: V)
        +evict(key: K)
    }
    class DatabaseSource {
        <<R2DBC Exposed>>
        +findById(id: K) V
        +save(value: V)
    }

    CacheStrategy <|.. L1Cache
    CacheStrategy <|.. L2Cache
    CacheStrategy <|.. TwoLevelCacheStrategy
    TwoLevelCacheStrategy --> L1Cache : L1 lookup first
    TwoLevelCacheStrategy --> L2Cache : L2 lookup on L1 MISS
    TwoLevelCacheStrategy --> DatabaseSource : DB lookup on L2 MISS

    style CacheStrategy fill:#E3F2FD,stroke:#90CAF9,color:#1565C0
    style L1Cache fill:#E8F5E9,stroke:#A5D6A7,color:#2E7D32
    style L2Cache fill:#F3E5F5,stroke:#CE93D8,color:#6A1B9A
    style TwoLevelCacheStrategy fill:#FFF3E0,stroke:#FFCC80,color:#E65100
    style DatabaseSource fill:#E0F2F1,stroke:#80CBC4,color:#00695C
```

## Cache Hit/Miss Processing Flow

```mermaid
sequenceDiagram
    participant App as Application
    participant L1 as L1 Cache (Caffeine)
    participant L2 as L2 Cache (Redis)
    participant DB as Primary DB (R2DBC)

    Note over App,DB: Cache Lookup Flow (Read Through)
    App ->> L1: get(key)
    alt L1 HIT
        L1 -->> App: Return cached value (immediate)
    else L1 MISS
        L1 -->> App: null
        App ->> L2: get(key)
        alt L2 HIT
            L2 -->> App: Return cached value
            App ->> L1: put(key, value)
        else L2 MISS
            L2 -->> App: null
            App ->> DB: SELECT (R2DBC suspendTransaction)
            DB -->> App: Query result
            App ->> L2: put(key, value, ttl)
            App ->> L1: put(key, value)
        end
    end

    Note over App,DB: Cache Invalidation (Write Through)
    App ->> DB: INSERT / UPDATE / DELETE
    DB -->> App: Done
    App ->> L2: evict(key)
    App ->> L1: evict(key)
```

## Learning Objectives

- Apply a Redis/Redisson-based cache layer together with Coroutines.
- Implement tenant + read/write routing using request context.
- Identify common bottleneck points when combining Spring WebFlux with Exposed R2DBC.

## Sub-modules

| Module | Topic | When to Read |
|---|---|---|
| [02-cache-strategies-r2dbc](./02-cache-strategies-r2dbc/README.md) | Read Through, Write Through, Write Behind, Read-Only cache strategies | When you want to understand patterns for reducing DB load with caching |
| [03-routing-datasource](./03-routing-datasource/README.md) | Tenant + read/write routing, Reactor Context propagation | When you want to learn the basics of read/write separation, sharding, and multi-tenant routing |

## Recommended Order

1. Start with `02-cache-strategies-r2dbc` to understand cache hit/miss flow.
2. Move to `03-routing-datasource` to understand request-context-based routing.
3. Optionally compare with the `09-spring` and `10-multi-tenant` modules to see the pattern differences.

## Running Tips

```bash
# Test the cache strategy module
./gradlew :02-cache-strategies-r2dbc:test

# Test the routing datasource module
./gradlew :03-routing-datasource:test
```

When running from the root, it is safer to use the actual Gradle project name:

```bash
./gradlew :exposed-r2dbc-11-high-performance-02-cache-strategies-r2dbc:test
./gradlew :exposed-r2dbc-11-high-performance-03-routing-datasource:test
```
