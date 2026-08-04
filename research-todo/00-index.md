# Aechronis — Pre-Setup Research Index

Master checklist for everything worth resolving before real server scaffolding begins. This folder only contains **new** research write-ups. Most bug-level and design-level open items already live in the existing docs — this index tells you where to look instead of repeating them.

> **Important scope correction (2026-07-30):** "Aechronis" is a **separate, third-party Minestom server** — this project is a *different*, not-yet-named server that's adopting Aechronis's `nodes`/`combat`/`vanilla`/`utils` libraries as technical infrastructure (land claims, gun/melee combat, vanilla-feature reimplementation) because no better-maintained modern alternative to the old `nodes` plugin exists. **This project's own game design is explicitly not copying Aechronis's** (musket-era theme, including wagons/cannons, vs. Aechronis's modern-military theme) — so anywhere below that references Aechronis's own Discord, domain (`aechronis.net`), Territory Tier bonus curve, or Buildings/oil/vehicle-factory economy, treat that as **reference context explaining the code's origin, not a build target for this project.** The bug-level findings in `COMBAT_DEEP_DIVE.md`/`NODES_DEEP_DIVE.md`/`VANILLA_DEEP_DIVE.md` remain fully relevant regardless — this project inherits that code's bugs either way. Confirmed in `02-cross-library-integration.md`: `combat`'s `Vehicle`/`Car`/`Tank` class hierarchy is generic (tunable physics numbers, not modern-specific), so it's being reused as the base for wagons/cannons — the vehicle bug list stays fully in scope, it's not dead code.

## Already tracked elsewhere (don't duplicate — go read these)

| Topic | Where it's tracked |
|---|---|
| Combat library bugs (ammo dupe, shoot-through-walls, vehicle bugs, hitscan-vs-ballistic decision, weapon/vehicle visual-asset integration) | [`COMBAT_DEEP_DIVE.md`](../COMBAT_DEEP_DIVE.md) Part 2 (severity list) + Part 3 (priority order); hitscan-vs-ballistic + visual-asset questions also in `RESEARCH.md` §2 |
| Nodes library bugs (war-state-loss on restart, ore-dupe cache, town-leadership-transfer, mutate-during-iterate, etc.) | [`NODES_DEEP_DIVE.md`](../NODES_DEEP_DIVE.md) Part 3 (severity list) + Part 4 (phonon-design divergences) + Part 5 (priority order) |
| Territory Tier system (1–10, income/mining bonuses, undocumented in code) | `RESEARCH.md` §15; confirmed unimplemented per `NODES_DEEP_DIVE.md` Part 4 |
| Vanilla library bugs (barrel dupe, killshop double-spend, save-race corruption, missing ore-drop table) | [`VANILLA_DEEP_DIVE.md`](../VANILLA_DEEP_DIVE.md) Part 2 (severity list) + Part 3 (priority order) |
| Ore double-drop integration bug (nodes ore-sampler vs. vanilla block-drops) | `RESEARCH.md` §11 |
| Anti-cheat framing (melee reach, movement validation, priority order) | `RESEARCH.md` §10 (high-level) + per-bug detail in `COMBAT_DEEP_DIVE.md` — see [`03-anti-cheat-and-security.md`](03-anti-cheat-and-security.md) here for the consolidated version pulling both together |
| Combat-tag — **already built and working** in `vanilla/managers/Combat.kt`+`CombatListener.kt` (tag-on-hit, boss-bar timer, disconnect-while-tagged=instant-kill). Real remaining gaps: no loot-drop-on-death anywhere in the stack, no command/teleport restriction while tagged, unverified friendly-fire/tag-ordering correctness, a global-scheduler-thread-safety issue in `Combat.tick()` | `RESEARCH.md` §12 (corrected 2026-07-30) |
| Currency/economy deferral, mining/ore config | `RESEARCH.md` §9, §11 |
| Hosting topology, Oracle A1 billing ambiguity, shard sizing | `RESEARCH.md` §4, §7, §7a |
| Permissions / friendly-fire / LuckPerms bridge risk | `RESEARCH.md` §6, §8 |
| Ops layer (logger, staff tooling scope), Discord not yet designed | `RESEARCH.md` §13 |
| Console/Pterodactyl egg gap | `RESEARCH.md` §14 |
| Player UI/UX (Town Overview GUI, minimap) | `RESEARCH.md` §15 |
| Redis/Mongo scope decision | `RESEARCH.md` §16 |
| Dependency version drift (`utils` SHA mismatch, Kotlin plugin mismatch) | `minestom-server-setup/07-aechronis-server-scaffolding.md` |
| World-gen/persistence format (Polar vs. Anvil), JVM/GC tuning, memory footprint, SLF4J binding | `minestom-server-setup/03-runtime-ops-and-logging.md`, `04-world-generation-and-persistence.md` |
| Protocol version / multi-client support | Resolved — locked to `2026.07.12-26.2` (MC 1.26.2), no ViaVersion work needed |

## New research write-ups in this folder

1. [`01-concurrency-model.md`](01-concurrency-model.md) — **the single biggest unresolved question.** All three deep-dive docs independently flag inconsistent thread-safety fixes and none of them ever nails down what Minestom actually guarantees. Do this first — it changes how you triage everything else.
2. [`02-cross-library-integration.md`](02-cross-library-integration.md) — the seams between `combat`/`nodes`/`vanilla`/`utils` that no single per-library doc owns.
3. [`03-anti-cheat-and-security.md`](03-anti-cheat-and-security.md) — consolidates the anti-cheat gaps scattered across `RESEARCH.md` §10 and the three deep-dives into one buildable list.
4. [`04-world-and-data-architecture.md`](04-world-and-data-architecture.md) — map/world-height decisions plus reconciling the aspirational Redis/Mongo architecture against the current JSON/NBT/H2 reality.
5. [`05-hosting-and-ops.md`](05-hosting-and-ops.md) — consolidates scattered infra passing-mentions (TCPShield, Brickstom, ops dashboard) and adds the load-testing methodology gap.
6. [`06-economy-and-progression.md`](06-economy-and-progression.md) — currency, voucher system, territory-tier build cost, mining-integration tuning — the economy-adjacent items pulled together from `RESEARCH.md` §9/§11/§15.
7. [`07-community-and-onboarding.md`](07-community-and-onboarding.md) — Discord structure, staff tooling, onboarding UX gaps, whitelist/access decision.
8. [`08-testing-qa-and-legal.md`](08-testing-qa-and-legal.md) — load-testing execution, CI/QA process, EULA/legal light pass — genuinely untouched ground.
9. [`09-network-and-ddos-security.md`](09-network-and-ddos-security.md) — DDoS/volumetric protection, proxy/backend firewalling, bot/join-flood mitigation — network-layer security, distinct from `03`'s gameplay-layer anti-cheat. Prompted by this project sitting in the same DDoS-prone "geopol" server genre as Aechronis.

## Suggested execution order

1. `01-concurrency-model.md` — unblocks correct triage of nearly every bug in the three deep-dive docs.
2. CRITICAL/HIGH items in `COMBAT_DEEP_DIVE.md`, `NODES_DEEP_DIVE.md`, `VANILLA_DEEP_DIVE.md`, plus `RESEARCH.md` §11's ore double-drop bug — these are launch-blocking correctness bugs, not research questions, so "research" here means confirm-repro-and-scope-the-fix.
3. ~~`RESEARCH.md` §4's Oracle billing ambiguity~~ — **resolved 2026-07-30**, confirmed with Oracle support: PAYG tenancies keep the full 4 OCPU/24GB Always Free A1 allotment. See `05-hosting-and-ops.md`.
4. `03-anti-cheat-and-security.md`, `RESEARCH.md` §12 (combat-tag), `06-economy-and-progression.md`, `07-community-and-onboarding.md` — the genuine from-scratch design work.
5. `04-world-and-data-architecture.md`, `02-cross-library-integration.md` — needed before scaffolding `world-server`/`hub-server` for real.
6. `05-hosting-and-ops.md`'s load-testing section, `08-testing-qa-and-legal.md` — once there's something to actually load-test.
7. `08-testing-qa-and-legal.md`'s legal subsection — light pass, whenever convenient.
8. `09-network-and-ddos-security.md` — resolve the Layer 1 vendor pick and Layer 3 build-vs-adapt decision before launch; not blocking earlier setup work, but should land before the server is publicly reachable.
