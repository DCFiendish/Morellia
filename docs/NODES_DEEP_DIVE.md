# Aechronis `nodes` — Deep Technical Reference & Bug Audit

Companion to [RESEARCH.md](RESEARCH.md), split out because this is a much deeper, code-level document: the goal is to understand `nodes` mechanically well enough to catch bugs before they hit production at 200+ concurrent players, not to plan features. Built from (1) the original `phonon/minecraft-nodes` documentation at nodes.soy — the design `nodes` is ported from — and (2) three independent, exhaustive read-throughs of the actual `Aechronis/nodes` source (master branch), each explicitly tasked with hunting for bugs, races, and edge cases rather than just describing behavior.

Every finding below is cited to a real file/class/function. Severity labels (CRITICAL/HIGH/MEDIUM/LOW) reflect likely production impact at 200+ concurrent players, not theoretical severity.

> **Superseded 2026-09-02.** A follow-up pass checked every CRITICAL/HIGH/MEDIUM item below directly against current `modules/nodes` source (not just re-read the write-up) — the fixes below all landed via the `DCFiendish/nodes` fork history that got subtree-imported into this monorepo (`b1d47f77`), not as a separate pass in this repo. Result: **all 3 CRITICAL, 6 of 8 HIGH, and 15 of 20 MEDIUM items are fixed.** Fixed items have been condensed to one-line confirmations below (full original write-ups are in git history / `git log -p` on this file if needed); only genuinely still-open items keep full detail. LOW items and Part 4 were **not** re-checked this pass — treat those as unverified, not confirmed-open.
> - Still true from the 2026-07-30 pass: **Part 4 divergences are resolved** — power-point claiming economy and peace-treaty/truce mechanics are both confirmed absent (grep for `power`/`claimCost`/`unclaimPenalty`/`resourceConstant` and `peace`/`truce` both return zero matches).
> - Not re-checked 2026-09-02, so still open questions from the 2026-07-30 pass: `Plot.at`'s unsynchronized linear scan (same shape as the now-fixed H3, but in code added after the original audit), `MinimapMarkerRenderer`/`MinimapIcons`' hardcoded 4/12-scale `when` blocks (same trap as the LOW item below).
> - A latent risk worth flagging for whoever eventually revisits war-restart behavior: `FlagWar.loadAttack()` calls `createAttack()`, which unconditionally re-places the flag block and regenerates the sky beacon — a naive restart-reload path that just calls `loadAttack` per saved attack would double-place/regenerate structures rather than reattaching to what's already physically there from before the restart. Confirm the actual fix (now wired up per C2 below) accounts for this.

---

## Part 1 — Original design (`phonon/minecraft-nodes`, via nodes.soy)

This is the design `nodes` was ported from. Not all of it necessarily carried over — see Part 4 for confirmed divergences.

**Territories & claiming**: map is pre-segmented into chunk-grid territories, each with fixed resources. Towns claim territories via a **power-point economy**: base power 30, +5 per player, +up to 20/player accumulating at 1/hour (rewards sustained engagement, discourages alt-account spam). Claim cost formula: `cost = base + rc + rs × a × chunks`, where `rc`/`rs` (resource constant/scale) vary by territory resources — rare resources (diamonds) cost far more than common ones (wheat). Unclaiming imposes a decaying power penalty equal to the territory's cost; exceeding a town's power budget imposes a 50% resource penalty across all its territories.

**Diplomacy & war**: admin-controlled war mode, declared via `/war`. FlagWar is the capture mechanic: place a fence-block "flag" (must see sky, can't be underground/underwater) from a territory edge/captured chunk/ally territory; undefended flags auto-capture their chunk after a timer; capturing the **home chunk** captures the whole territory. Captured territory becomes **occupied** (occupier taxes income/ore/crops/breeding, cannot build/access chests) until **annexed** (folded into the town permanently, no more power cost, full rights, no more tax). Peace treaties (`/peace`) create a 48-hour truce. Allied towns/nations cannot declare war on each other, but existing alliances don't auto-join a declared war.

**Town protections**: protects specific containers (chests/trapped chests/furnaces) via `/t protect`, not whole plots — **the original design explicitly has no per-chunk/per-territory/per-player permissions**, only five uniform town-wide groups (Trusted, Town, Nation, Ally, Outsider) across six permission types (Interact, Build, Destroy, Chests, Items [inactive], Income).

**Resource nodes**: four categories — Income (periodic scheduled yield), Ore (hidden drop-rate-on-mining-stone), Crops (growth speed), Animals (breeding success rate). Fractional yields (<1.0) are a random roll (`random() < amount` → 1 item); whole yields (≥1.0) give that integer amount; occupation taxes round fractional income up (`Math.ceil`). Ore yields support a simple `"item": rate` or advanced `"item": [rate, min, max]` format. Neighbor-territory effects (`neighbor_income`, `neighbor_ore`, etc.) let adjacent territories boost each other; same-property rates from multiple sources sum, multipliers apply by priority order.

---

## Part 2 — How Aechronis' Minestom port actually works

### 2.1 Core domain objects

- **`Coord`/`TerritoryId`** (`objects/Coord.kt`): plain chunk-coordinate key types, fine as map keys.
- **`TerritoryChunk`** (`objects/TerritoryChunk.kt`): holds a **direct mutable reference** to its owning `Territory`, plus `var attacker`/`var occupier`. The class's own doc comment admits this must be manually kept in sync whenever a `Territory` is swapped at runtime — there's no abstraction enforcing that (see bug catalog).
- **`Territory`** (`objects/Territory.kt`): a `data class` carrying two genuinely mutable fields, `var town: Town?` / `var occupier: Town?` (in-source TODO: "find way to get rid?"), written from many call sites with no synchronization. Also owns `TerritoryResources` (income/ore rate accumulation with neighbor-modifier propagation) and `attackerTimeMultiplier`/`defenderTimeMultiplier` (feed directly into FlagWar's attack-time formula — this is the confirmed hook point for the territory-tier system planned in RESEARCH.md §15).
- **`Town`** (`objects/Town.kt`): `leader: Resident?`, `officers: HashSet<Resident>`, `residents: HashSet<Resident>`, `spawnpoint: Pos`, `territories`/`annexed`/`captured: HashSet<TerritoryId>`, `home: TerritoryId`, `income: IncomeInventory`, `nation: Nation?`, `playersOnline`, `plots: LinkedHashMap<String, Plot>`, `protectedBlocks`, `applications` (pending join requests), `color`, `name`, `uuid`. **No tier/level field exists** (confirmed — the tier *design* lives only in `Aechronis/guides`, unimplemented; see RESEARCH.md §15).
- **`Nation`** (`objects/Nation.kt`): towns, capital, allies/enemies (nation-scoped, not town-scoped despite the deserializer parsing town-level allies/enemies too — see §2.2 dead-code finding).
- **`Resident`** (`objects/Resident.kt`): per-player state — town/nation membership, trusted flag, waypoints, ignored-players list.
- **`Plot`** (`objects/Plot.kt`): sub-territory claims with per-player and per-group ACLs (`PermissionsGroup.TOWN/NATION/ALLY/TRUSTED/OUTSIDER`), validated against the parent territory's current owner at creation/redefinition time only (not re-validated on ownership change — see bug catalog).
- **`IncomeInventory`** (`objects/IncomeInventory.kt`): the actual town treasury — a `Map<Material, Int>` presented as a chest GUI (`/town income`), the game's real material-based economy (§9/§11 of RESEARCH.md).
- **`OreSampler`/`OreDeposit`/`OreBlockCache`** (`objects/OreSampler.kt` etc.): the hidden-ore-on-mining-stone system RESEARCH.md §11 is built around. `OreSampler` builds a per-territory weighted item table (Vose's-alias-method) from `OreDeposit(material, dropChance, minAmount, maxAmount, ymin, ymax)` entries; `OreBlockCache` is the anti-exploit guard preventing "place block, mine it, repeat" farming (see bug catalog — this is one of the most serious findings in the whole audit).
- **`ResourceNode`** (`objects/ResourceNode.kt`): cleanly implemented as an immutable copy-on-write fold over attribute application — one of the better-engineered files in the domain layer, no findings.

### 2.2 Persistence

`nodes` uses **hand-rolled JSON**, no database: `Serializer.kt` builds JSON via manual `StringBuilder`/string-template concatenation (not a library like Gson's builder), `Deserializer.kt` reads it back via manual `JsonObject.get(...)?.as...` calls with per-field null-fallback logic. This is single-process by construction (confirmed earlier in RESEARCH.md §7a) — no locking, no versioning, nothing that would let two JVMs share this data safely.

A `SaveState` memoization pattern (`needsUpdate()` dirty flag + a lazily-built, supposedly-immutable snapshot per `Town`/`Nation`/`Resident`) avoids re-serializing unchanged entities on every autosave tick — reasonable design, but entirely dependent on every mutation path remembering to call `needsUpdate()` (unenforced by the compiler; a regression risk for any future change, not a currently-confirmed bug). `SaveManager`/`IncomeManager` (`tasks/`) schedule periodic saves (dirty-flag + fixed period, explicitly designed in-source to avoid disk-I/O spam from command mashing) and hourly income runs (wall-clock-hour-boundary triggered).

### 2.3 War/siege system (`war/`)

`FlagWar` is a singleton holding all live siege state (per-player attack lists, chunk→attacker maps, occupied-chunk set). `beginAttack()` validates flag placement (not-already-attacked, not-already-captured, enemy ownership, border/edge rules, sky visibility, per-player attack cap) and delegates to `createAttack()`, which computes attack duration from a global base multiplied by wilderness/home/attacker-vs-defender multipliers (the exact formula already confirmed in RESEARCH.md §15's tier-system hook). `Attack` is a self-scheduling repeating task with a packet-only text display and boss bar. `finishAttack()` handles capture/annex/liberate/defend outcomes. Alliances (`war/Alliance.kt`) are nation-level, request/accept gated, and you cannot ally an active enemy.

### 2.4 World-interaction listeners

- **`NodesWorldListener.kt`**: the permission-gating engine for block break/place/interact, already documented in RESEARCH.md §6 (plot ACL → town permission matrix → occupier override → war override) and §11 (hidden-ore sampling + anti-exploit cache).
- **`NodesPlayerDamageListener.kt`**: friendly-fire cancellation by nation/ally relationship, already documented in RESEARCH.md §6.
- **`NodesPlayerMoveListener.kt`**: chunk-cross territory announcements + home-teleport-warmup cancellation on movement, already documented in RESEARCH.md §12/§15.

### 2.5 Minimap system

Already documented in RESEARCH.md §15 — packet-only text-display entity, RGB-channel-encoded territory/waypoint data via a custom font, refreshed on chunk-boundary crossing and hash-gated to avoid redundant packets. `Resident.renderMinimaps()` is the global force-refresh hook, called from a wide range of war/diplomacy/territory mutation sites (see bug catalog — this fan-out is a real scaling concern).

### 2.6 Command surface

Already inventoried in RESEARCH.md §15 (11+ command groups). The argument layer (`commands/arguments/`) defends against JSON-breaking input via `ArgumentSanitizedString` (rejects `"`/`{`/`}`, escapes `$`/`%`) applied consistently everywhere a player names something persisted (towns, nations, plots, ports) — see bug catalog for the one confirmed gap in this defense.

---

## Part 3 — Confirmed bugs & design flaws, by severity

### CRITICAL — all fixed

- **C1** (port-naming bug) — FIXED. `NodesAdminCommand.kt`'s port-create syntax now reads `context[nameArg]` (comment in-source explains the old shadowing bug).
- **C2** (war-attack persistence lost on restart) — FIXED. `WarDeserializer` now reads the `"attacks"` key and calls `FlagWar.loadAttack()`.
- **C3** (ore anti-dupe cache in-memory/capped) — FIXED. `OreBlockCache` is now an uncapped `ConcurrentHashMap.newKeySet()`, persisted to `ore_cache.json`, loaded on boot via `hiddenOreInvalidBlocks.load(...)` in `Nodes.loadWorld()`.

### HIGH

**Fixed:**
- **H1** (non-local `return` truncating `loadTerritories`) — FIXED, now `return@forEach` with an in-source comment explaining the old bug.
- **H2** (uncaught `Nation.load` exception corrupting saves) — FIXED. `Nodes.loadWorld()` now wraps the whole load in `try/catch`, so a broken in-memory state can no longer get autosaved over the last good files. (`Nation.load` itself still throws rather than returning `Result`, but the catastrophic consequence — silent partial-state persistence — is closed.)
- **H3** (thread-safety mismatch) — FIXED. `Territory.town`/`.occupier` are now `@Volatile`; `FlagWar.attackers`/`blockToAttacker` are now `ConcurrentHashMap`/`CopyOnWriteArrayList`.
- **H5** (double-town-membership) — FIXED. `Town.addResident` now guards: `if (resident.town != null || Nodes.towns.values.any { it.residents.contains(resident) }) return false`.
- **H6** (quit-handler `ConcurrentModificationException`) — FIXED. `onPlayerQuit` now iterates `attacks.toList()`, with a comment explaining the original mutate-during-iterate bug.
- **H7** (town lifecycle not coordinating with war state) — FIXED. `Town.destroy()` now cancels attacks tied to the town before tearing it down; `Town.unclaim()` now purges the losing town's plots in the same territory.

**Still open:**

**H4. No player-facing town leadership transfer exists at all — an inactive or banned leader permanently soft-locks their town.** The only leadership-change path is `/nodesadmin town leader`, admin-only. Compounding this: the leader is **explicitly blocked from leaving** their own town ("you must transfer leadership before leaving"), and only the leader (not officers) can promote/demote. If a leader goes permanently inactive, quits, or is banned, no officer can promote anyone, no one can be demoted, and the town can never get new leadership without staff manually intervening — a routine, entirely avoidable support burden at 200+ players. **Confirmed still true 2026-09-02** — no `transferleader`/`setleader` player command exists anywhere in `commands/`.

**H8. `Resident.renderMinimaps()` is an unconditional, un-batched full broadcast to every online player, called from a wide range of individual war/diplomacy events — a real render-storm risk during large sieges.** Every single flag-attack lifecycle event (not just territory-level outcomes — every chunk capture) force-refreshes **every online player's minimap**, regardless of relevance or distance, with no coalescing/debounce. In a large coordinated push where dozens of chunks flip in a short window (realistic at 200-player scale, especially combined with the lack of any per-territory attack cap — see M-series below), each chunk capture queues up to 200 fresh async render computations onto the shared default thread pool, meaning this contention risk compounds under exactly the load conditions a real siege would create. **Confirmed still true 2026-09-02** — `Resident.renderMinimaps()` is unchanged: a plain `for` loop over every online player, no batching/debounce/distance filter.

### MEDIUM

**Fixed:** M1 (`TownIncomeCommand` now reuses the real `hasTownPermissions` evaluator instead of a bespoke copy), M2 (`Town.destroy()` now invalidates outstanding invites pointing at it), M3 (`TownProtectShowCommand` now cancels any prior task for the resident before starting a new one), M4 (unclaim now purges the losing town's plots in the territory), M7 (the batch admin commands now report real per-item success/failure counts), M8 (color RGB args now `.between(0, 255)`), M9 (silk-touch check now defaults the missing-enchantment case to level 0 explicitly), M15 (war-save path now has real exception handling), M16 (`WarSerializer` now builds fresh local buffers per call instead of sharing mutable singleton state), M17 (territory-chunk overlap at load time now logs a warning instead of silently overwriting), M18 (world-height constants updated to `-64..320`, matching modern Overworld bounds), M19 (`TownSaveState.permissions` now deep-copies each `EnumSet` instead of sharing the live instance), M20 (unrecognized income `Material` keys now log to stderr instead of silently vanishing).

**Not checked this pass, assume still as originally documented:** M5, M6, M11, M14.

**Confirmed still open 2026-09-02:**

- **M10** — `PlayerBlockInteractEvent` (doors/levers/redstone) skips wilderness permission checks entirely, unlike break/place — an explicit in-source comment (`// DO NOT USE WILDERNESS PERMISSIONS`) suggests this is intentional, but it means interactive blocks in unclaimed wilderness can never be protected regardless of server config, an undocumented asymmetry with the rest of the permission model. Unchanged.
- **M12** — Home-teleport warmup only cancels on a full block-coordinate change, not any movement — sub-block strafing, small knockback, or edge-standing doesn't reset a pending teleport, weakening the anti-combat-log guarantee in exactly the PvP context this matters most for. Unchanged (`onPlayerMove` still compares `blockX`/`blockY`/`blockZ` only); `onPlayerTeleport` also still doesn't cancel a pending warmup at all.
- **M13** — No FlagWar/occupation awareness in the friendly-fire damage listener — it only checks static nation/alliance relationships (`Town.relationshipOfPlayerToPlayer`), so two players legitimately fighting over an active siege (attacker vs. defender) can be unable to damage each other in melee if they happen to share a nation/alliance. Unchanged.

### LOW

*Not re-checked in the 2026-09-02 sweep — given how much of CRITICAL/HIGH/MEDIUM turned out already fixed, don't assume these are still open either; verify against current code before acting on any of them.*

- Non-atomic `townNametagIdCounter++` increment — concurrent `Town` construction could produce duplicate scoreboard-team nametag IDs, causing wrong ally/enemy color display.
- `Town.destroy()` doesn't clean up `plots`/`protectedBlocks`/pending `applications` (the applications' 60s expiry task still fires later against an orphaned map — harmless but a small resource leak).
- `Town.setHome` silently overwrites any custom `/town setspawn` with no warning whenever the home territory changes.
- `Nation.removeTown`'s new-capital selection after the old capital leaves is `HashSet.first()` — deterministic per UUID but essentially arbitrary from a game-design perspective, with no seniority/vote mechanism.
- `Resident.fromName`/waypoint-visibility cleanup do full linear scans over all residents — an O(n) cost on routine command paths that will add up over a server's lifetime.
- `Plot.validate` doesn't clamp Y-bounds against actual instance world height, only against the configured max plot dimensions.
- `IncomeInventory`'s own in-source comment acknowledges an unresolved item-dupe race between clearing the visual inventory and persisting to disk on a crash — a known, documented, still-open vector.
- Dead/vestigial code: per-town `allies`/`enemies` are parsed from JSON but never written (alliances are nation-scoped only) — harmless today, but could mislead a future maintainer into thinking town-level overrides are supported.
- `/nation help`'s text lists a `/nation color` command that doesn't exist as a player command (it's admin-only) — and disagrees with the top-level `/nation` help text about what commands exist at all.
- `ArgumentTerritory` (singular) uses an unguarded `.toInt()` instead of `.toIntOrNull()` (unlike its array-argument sibling, which got this right) — a bad `/territory <id>` argument throws a raw `NumberFormatException` instead of the intended graceful syntax error.
- The player's own Minecraft username is the one persisted name that never goes through the `ArgumentSanitizedString` defense (because it isn't a command argument) — safe under normal Mojang online-mode auth, but a real gap if this server or a proxy in front of it ever runs offline-mode.
- Sky beacon (the tall, far-visible siege marker) silently fails to render for any legal flag placed roughly Y 206–252 due to an off-by-one in the beacon-height range calculation — a very plausible altitude for mountain fortresses, with no error or log.
- No per-territory/per-defender cap on simultaneous attacks — a numerically larger side can trivially field dozens of simultaneous attackers against one territory's border chunks, more a balance question than a bug given the 200-player target.
- Sub-block movement doesn't cancel a pending teleport warmup on plugin-driven teleports (`EntityTeleportEvent`) at all, only on client movement — a minor double-teleport consistency risk.
- No cooldown on territory-crossing chat announcements — rubber-banding or edge-riding across a chunk border can spam repeated enter/leave messages to oneself.
- `OreSampler`'s Y-range fill loop has a genuine off-by-one that can leave the single topmost configured Y-level unsampled (never yields ore) depending on how ore ranges are configured relative to the world-height ceiling.
- No cap on simultaneous outstanding town applications — a player can spam `/t apply` against every town on the server with no per-player/per-town throttle, messaging every town's leadership each time.
- `TownInviteCommand`'s self-invite error message is copy-pasted from an unrelated scenario and is misleading.
- A handful of now-unreachable null checks in command code (`ArgumentResident`/`ArgumentTown`/etc. can never actually resolve to null given how they're implemented) — harmless, but a sign of copy-paste from an older resolution model, worth cleaning up so it isn't misread as meaningful defensive logic later.
- `WarSerializer` keys occupied-chunk records by town *name* (a string) — if towns can be renamed, an occupied-chunk record could silently fail to resolve back to its town on the next load, reverting the territory to unoccupied with no liberation message.
- Hardcoded exhaustive `when` blocks on the two current minimap zoom scales (4/12) will throw if a third scale is ever added without updating every site — a maintenance trap, not a current bug.

---

## Part 4 — Divergences from the original (phonon) design

- **The original's power-point claiming economy does not appear to exist in the Aechronis port.** Nothing in the reviewed source implements a power budget, claim cost formula, or unclaim penalty — territory assignment in `nodes` (Aechronis) appears to happen purely through `NodesAdminTownCreateCommand`/`addTerritory`/FlagWar capture, not a player-driven power-based claiming system. Worth explicitly confirming this is an intentional design change (e.g., admin-curated territory assignment replacing organic claiming, consistent with RESEARCH.md §9's "curated roster" decision) rather than a gap.
- **Peace treaties/truces (48-hour default) from the original docs were not confirmed present** in the reviewed war code — the audit passes focused on FlagWar's attack/capture mechanics and didn't find an equivalent `/peace` truce system, but this wasn't exhaustively ruled out either. Worth a direct follow-up check if peace mechanics matter to the design.
- **The territory-tier system** (income/mining bonuses, 1–10, staff-assigned) is documented in `Aechronis/guides` but genuinely unimplemented in `nodes` itself — already covered in depth in RESEARCH.md §15, cross-referenced here since it's directly relevant to this document's territory/attribute findings (the `attackerTimeMultiplier`/`defenderTimeMultiplier` hook point noted in §2.1/§2.3 above is exactly where a tier-driven capture-time bonus would plug in).

---

## Part 5 — Priority order if hardening this before launch

**Updated 2026-09-02 — all CRITICAL and most HIGH/MEDIUM items from the original list are fixed (see sections above).** Remaining priority order:

1. **H8 (minimap render-storm)** — still an unconditional, un-batched full broadcast on every war/diplomacy event; worth load-testing specifically once the flagship shard load-testing (RESEARCH.md §7) happens, since it's exactly the "big war is happening" scenario that most needs the server not to fall over.
2. **H4 (no player-facing leader transfer)** — a routine, foreseeable support burden at real player counts; fix before any real community relies on the town system.
3. **M13 (friendly-fire listener has no FlagWar awareness)** — can make legitimate siege combatants unable to damage each other if they share a nation/alliance; worth fixing before real PvP siege content.
4. **M10/M12** — narrower correctness gaps (wilderness-interact asymmetry, sub-block teleport-warmup cancel); real but lower urgency.
5. **M5/M6/M11/M14 and all LOW items** — not re-verified this pass; re-check against current code before treating any of them as still open, then backlog whatever's confirmed.
