# Redis Device Connection Client

A Redis based implementation of Hono's `Cache` interface for storing device
connection information, used by the Redis variant of the Command Router
(`services/command-router-redis`).

## Requirements

| Requirement | Details |
| --- | --- |
| Redis server | Version 2.6 or later (`EVAL` support); tested against Redis 8 |
| Lua scripting | The `EVAL` command must be permitted for the configured user. When using Redis ACLs, the `@scripting` command category must not be denied. The cache verifies this at startup; if scripting is not permitted, the startup future fails and a descriptive error is logged. |

Atomic operations (compare-and-delete, bulk put with time-to-live) are
implemented as server-side Lua scripts instead of `WATCH`/`MULTI`
transactions. The vert.x Redis client recycles pooled connections without
resetting per-connection state, so transaction based implementations leak
`WATCH`/`MULTI` state into the connection pool and corrupt unrelated
operations. Scripts execute atomically server-side and keep connections
stateless.
