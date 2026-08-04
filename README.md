# nodes

Fork of [Aechronis/nodes](https://github.com/Aechronis/nodes) — a land-protection and nation
simulation library for [Minestom](https://minestom.net). All credit for the original design and
implementation belongs to Aechronis; this fork carries a series of concurrency, data-loss,
security, and performance fixes found and verified while running the library in production.

## Changes in this fork

### Concurrency & thread-safety
- `FlagWar`'s attacker-tracking maps were plain `HashMap` while a sibling map with an identical
  lifecycle was already `ConcurrentHashMap` — flag place/break events dispatch per-chunk on
  Minestom's chunk-owner threads, making the mismatch a real concurrent read/write hazard.
  Converted both, plus the per-player attacker lists, to their concurrent equivalents.
- Applied the same fix to `Nodes.territories` against its sibling `territoryChunks`, and marked
  the relevant town/occupier/attacker fields `@Volatile` for cross-thread visibility.
- Fixed a mutate-during-iterate bug where a player's live attack list was iterated while each
  cancellation removed itself from that same list — now iterates a snapshot.
- Fixed the same class of hazard in the war-save path by building fresh local buffers per save
  instead of reusing shared mutable singletons across concurrent saves.

### Security
- Closed an ore-duplication exploit: the anti-dupe cache was a fixed 2000-entry LRU that silently
  reopened the place-then-rebreak dupe once evicted under normal mining load — and wasn't
  persisted, so a restart reopened it unconditionally. Rebuilt as an unbounded, persistent cache.

### Data-loss & persistence
- War state didn't survive a restart: the deserializer never called the restore path that already
  existed for it, silently dropping every in-progress siege attack and orphaning its placed blocks
  on any restart mid-war.
- A bare `return` inside a Kotlin `forEach` in territory loading was a non-local return from the
  whole function — one malformed territory silently truncated loading of every territory after it.
- A failed world-reload had no error handling, leaving town/nation state partially cleared and
  letting the next scheduled autosave persist that broken state over the last good save.
- Fixed dual-town membership: nothing checked whether a resident already belonged to a town before
  adding them to a second one, corrupting state silently instead of rejecting the action.

### Correctness
- Silk Touch detection treated a missing enchantment component the same as Silk Touch being
  present, silently disabling hidden-ore drops for any unenchanted tool.
- Unclaiming a territory left stale per-player plot permissions behind ("ghost plots") that
  silently reactivated if the territory was reclaimed later.
- Leaving a town didn't revoke that resident's plot permission grants.
- An income-permission check on `/town income` used a bespoke, incomplete evaluator instead of the
  real one, silently ignoring nation/ally/outsider access levels leaders had configured.
- Several admin batch commands (add/remove territory, add/remove officer) unconditionally reported
  full success regardless of each item's actual result.
- A handful of smaller correctness fixes: an off-by-one in ore-distribution sampling that excluded
  the topmost Y-level, unbounded RGB input on color commands, a raw exception instead of a graceful
  syntax error on non-numeric territory arguments, and unlogged failures in a couple of silent-drop
  paths (unrecognized income material keys, overlapping territory claims on load).
- Cherry-picked two additional fixes from upstream's own unreleased work: a nation-membership index
  that could desync from actual membership, and a stale respawn point left behind for players who
  had left their town.

### Performance
- `Nametag.updateAllText()` rebuilt every town's member list by rescanning all online players once
  per *(viewer × town)* pair, every second — O(players² × towns) per tick. Membership doesn't
  depend on the viewer; now built once per call.
- `/town protect show` spawned a brand-new repeating broadcast task on every invocation with no
  dedup or cooldown, so repeated calls stacked unboundedly. Added a per-resident task handle so a
  new call cancels any still-running one.

### Tooling
- Added a public `Nodes.enableWar()` entry point for headless/programmatic startup — the existing
  admin command only worked from a real player sender, with no way to enable war state before any
  player logs in. Used to drive automated load testing (see
  [rust-mc-bot](https://github.com/DCFiendish/rust-mc-bot)).

## Verification

Fixes were re-checked against current upstream source before patching (not applied blind), and
validated via `./gradlew check` — full compile, ktlint, and test suite — before each commit.

## License

GPL-3.0, inherited from upstream. See [`LICENSE`](LICENSE).
