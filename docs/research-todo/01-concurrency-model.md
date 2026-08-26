# Concurrency & Threading Model

The single biggest unresolved question across the whole project. `COMBAT_DEEP_DIVE.md` (bug H1), `NODES_DEEP_DIVE.md` (bug H3), and `VANILLA_DEEP_DIVE.md` (the "global scheduler thread mutates per-instance state" MEDIUM item) each independently ran into this and each punted on it — all three recommend "a single project-wide concurrency pass" instead of per-library fixes, but none of the three docs actually answers the underlying question. Nearly every dupe exploit, corruption bug, and race condition flagged in the three deep-dive docs is downstream of not knowing this.

## Questions to resolve

- What are Minestom's actual guarantees for **event-dispatch threading** — does a given `EventNode` always fire on the same thread as the triggering action, or can dispatch happen on a worker-pool thread? Verify against the exact pinned version (`Minestom 2026.07.12-26.2` per `RESEARCH.md` §2).
- What are Minestom's guarantees for **command execution threading** — main thread only, or can `Command` handlers run concurrently across players? (`NODES_DEEP_DIVE.md` H3 explicitly says this is unconfirmed and should be checked directly.)
- What are Minestom's guarantees for **scheduler/task threading** — is `MinecraftServer.getSchedulerManager()` (or equivalent) tied to the instance tick thread, or does it run on a separate pool? This is the root cause behind the recurring "global scheduler thread mutates per-instance state" pattern across `vanilla`'s `Crops`/`Saplings`/`EnvironmentalDamage`/`Food`/`TreeFeller`.
- What does `Acquirable`/`Instance` thread-affinity actually mean in practice — which operations require acquiring the instance's lock, and which of `nodes`/`combat`/`vanilla`'s current code paths skip that?

## ANSWERED (2026-07-30) — confirmed against Minestom's own threading docs

Fetched `minestom.net/docs/thread-architecture/acquirable-api/inside-the-api` directly, cross-referenced against a community threading-model writeup (mintlify.wiki mirror) that quotes the same doc set. Confirmed model:

- **Chunks are partitioned to worker threads via `ThreadDispatcher`/`ThreadProvider`** (default: round-robin). Entities are assigned to the same thread as their containing chunk; moving chunks triggers an `ElementUpdate` reassignment.
- **Event handlers ARE automatically synchronized** — a listener like `PlayerMoveEvent`/`PlayerInteractEvent` runs on the correct tick thread for the entity/instance involved, and mutating *that entity's own state* inside the handler is safe with no extra locking needed.
- **This guarantee is scoped to the entity/chunk being ticked — it does NOT extend to shared global state.** Two different players on two different chunk-threads can have their event handlers running truly concurrently. If both handlers touch the *same* global/shared collection (a static `HashMap` on a singleton/companion object — exactly the pattern of `nodes`' `Nodes.hiddenOreInvalidBlocks`, `combat`'s vehicle/aim-state maps, `vanilla`'s `Commands.ignored`/`lastLocation`), that access is **not** covered by the event-handler synchronization guarantee and needs its own thread-safe collection or explicit locking.
- **The global scheduler (`MinecraftServer.getSchedulerManager()`) runs tasks on a separate thread pool, NOT the tick thread.** This is a direct, confirmed answer to the recurring "global scheduler thread mutates per-instance state" pattern flagged independently in all three deep-dive docs (`vanilla`'s `Crops`/`Saplings`/`EnvironmentalDamage`/`Food`/`TreeFeller`, and the equivalent pattern in `combat`/`nodes`) — **these are real, confirmed bugs, not false alarms.** Any code using the global scheduler to touch per-instance/per-entity state needs either its own synchronization or should switch to `instance.scheduleNextTick()` / `entity.scheduler().scheduleNextTick()`, both of which **are** tick-aware and safe per the same docs.
- **Direct chunk access from outside the tick thread requires explicit `synchronized(chunk)`.** This directly confirms `combat`'s `Explosion.kt` bug (block-clearing via `CompletableFuture.runAsync(ForkJoinPool.commonPool())`, unsynchronized) as a real violation, not a stylistic nitpick — tick threads must also never block on I/O, so the fix isn't "make it synchronous," it's "dispatch the actual block mutation back onto the instance's tick thread via `scheduleNextTick()` (or equivalent), off whatever async work triggered it."
- **RESOLVED (2026-08-06) — commands share the exact same per-player tick-thread guarantee as events.** Confirmed by reading Minestom's actual source (`Minestom/Minestom` @ `2026.07.12-26.2`), not just docs:
  - `PlayerSocketConnection.processPackets()` checks each incoming packet against a small `IMMEDIATE_PROCESS_PACKETS` set (handshake/login/status/keepalive/config only — `ClientCommandChatPacket`/`ClientSignedCommandChatPacket` are NOT in it). Anything not in that set — including both command packet types — takes the `else` branch: `player.addPacketToQueue(packet)`, explicitly commented `// To be processed during the next player tick`.
  - `Player.interpretPacketQueue()` drains that queue (`this.packets.drain(...)`, calling `PacketListenerManager.processClientPacket` per packet) and is itself annotated `@ApiStatus.Internal` with an explicit source comment: `// This method is NOT thread-safe` — i.e. it is only ever safe to call from one consistent thread, which is the player's own tick, same as any other queued player packet (movement, block placement, etc.).
  - So a command fired by a player is queued on packet-receipt (network I/O thread) and actually executed later, synchronously, on that player's own tick thread — identical mechanism and identical guarantee to `PlayerMoveEvent`/`PlayerInteractEvent`. `NODES_DEEP_DIVE.md` H3's "unconfirmed either way" is now resolved: confirmed same-guarantee, not a gap.
  - Also checked the cross-instance/cross-chunk transfer edge case flagged below: `ThreadDispatcherImpl.updateAndAwait()` drains all pending partition/element reassignments (`updates.drain(...)`) in one synchronized batch *before* that cycle's `thread.startTick()` calls go out — so an entity switching chunks/instances is ticked deterministically on either its old or new thread for a given cycle, never both, never neither. No race window there either.

## Resulting project-wide policy

- **Any collection reachable from more than one entity/chunk's event handlers (i.e. anything on a singleton/companion/manager object, not scoped to a single entity instance) must be a thread-safe collection** (`ConcurrentHashMap`, etc.) — this is not "some of these need it," it's the default rule now. This resolves the ambiguity behind the inconsistent partial fixes already seen in the code (e.g. `nodes`' `FlagWar.chunkToAttacker`/`occupiedChunks` converted to `ConcurrentHashMap` while sibling `blockToAttacker` was left plain) — those were correct instincts applied inconsistently, not a case where some maps genuinely didn't need it.
- **Any code that needs to mutate per-instance/per-entity/per-chunk state from a scheduled task must use `instance.scheduleNextTick()`/entity-scoped schedulers, never the global `MinecraftServer.getSchedulerManager()`,** unless the task is provably touching only thread-safe shared state.
- **Any code that touches chunks/blocks from off the tick thread (async callbacks, `CompletableFuture`, `ForkJoinPool`) must dispatch the actual mutation back onto the owning instance's tick thread**, not do it inline on the async thread.
- Go-forward rule for new code in this project (combat-tag, voucher system, territory-tier modifiers): default to thread-safe collections for anything shared across entities, and default to instance/entity-scoped schedulers over the global one, unless a specific single-thread guarantee is confirmed and documented inline at the point of use.

## Status: fully resolved (2026-08-06)

Both previously-open items (command-threading, and a source-level rather than docs-only check of
`ThreadDispatcher`) are now answered above with direct citations into Minestom's own source at the
pinned version. Nothing left to verify — this doc's job (unblocking correct triage of the thread-safety
findings across `combat`/`nodes`/`vanilla`) is done. As of the same date, `nodes` and `vanilla`
(both forked) have had essentially every CRITICAL/HIGH finding from their respective deep-dive docs
fixed and are pinned at those fixed commits in `server/build.gradle.kts` — see each fork's README for
the categorized changelog. `combat` is still consumed directly from upstream `Aechronis/combat`
(unforked, per project convention) and upstream has not addressed the CRITICAL/HIGH findings in
`COMBAT_DEEP_DIVE.md` (their recent commits are vehicle-mechanics fixes, not the async-explosion/
ammo-dupe/raycast/melee-reach/explosion-LOS issues) — this is now the single biggest concrete
correctness gap left in the three-library stack, and unlike `nodes`/`vanilla` it can't be fixed by
this project without either forking `combat` too (a policy change) or waiting on upstream.
