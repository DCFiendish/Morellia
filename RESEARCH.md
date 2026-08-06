# Aechronis Minestom Server — Research Summary

Research only — no money spent, no accounts created, no code written yet.

## 1. What the Aechronis org actually is

`github.com/Aechronis` is a set of **Kotlin libraries for Minestom** (not a single server project). Minestom is a from-scratch, lightweight Java Minecraft server framework — no Bukkit/Paper plugin ecosystem, no default gameplay. You build one server binary yourself and wire in libraries as Gradle dependencies.

| Repo | What it is |
|---|---|
| **nodes** | Territory/nation library — direct Kotlin port of `phonon/minecraft-nodes`'s "Frontier Wars" system: `Territory → Town → Nation`, capturable resource nodes (ore/farms/ports), `FlagWar`/siege capture mechanic, plots, alliances. |
| **nodes-map** | Static JS (deck.gl/Webpack) map viewer that polls JSON (`world.json`, `towns.json`, `war.json`, `buildings.json`) exported by `nodes` — a Dynmap-style overlay, no backend of its own. |
| **combat** | **Gun + melee combat library** — this is your Agadir Crisis-era weapons answer, see below. |
| **vanilla** | Reimplements standard vanilla MC features Minestom doesn't ship with by default. |
| **worldedit** | WorldEdit-equivalent for Minestom. |
| **logger** | Player action logging. |
| **utils** | Shared helpers used by all the above. |
| **library** | Minimal scaffold/template for a new Minestom library — shows Aechronis's standard project shape (single `Main.kt`, `init()`, GitHub Packages publish setup). |
| **resource-pack** | Resource pack assets. |
| **guides** | Docs website. |

**CORRECTED (2026-07-30):** earlier research here (and in `08-testing-qa-and-legal.md`) concluded "no private Aechronis server repo exists," based on `gh repo list Aechronis` showing only the 10 known public repos. That check was too narrow — it only enumerates repos owned by the `Aechronis` org itself, not repos under the dev's personal account or a different org. The actual Aechronis dev has now directly confirmed a **private repo does exist** that ties `nodes`/`combat`/`vanilla`/`utils` together into the real running server — the public repos genuinely are "just repos" (individual libraries), not the full picture. We still don't have access to it and aren't trying to get it (this project's own server, `morellia-server`, is being scaffolded independently per below) — but it means any future "is X already solved somewhere" question can't be fully answered from the public repos alone the way earlier research assumed.

No public "server"/"core"/"proxy" repo exists that ties these into one runnable server for outside consumers — a new server project needs to be scaffolded that depends on `nodes` + `combat` + `vanilla` (+ `utils`) together, following the `library` template's shape. All libraries publish to **GitHub Packages** (`maven.pkg.github.com/Aechronis/...`) at pinned commit-SHA versions, requiring a GitHub token in the consuming project's Gradle repositories block.

## 2. Agadir Crisis-era PvP — already solved by `Aechronis/combat`

**Theme decided (2026-08-05, revised same day): the Agadir Crisis (1911), alternate history.** The real 1911 Agadir Crisis was a diplomatic/gunboat standoff over Morocco that got resolved without actual war (France ceded parts of French Equatorial Africa to Germany in exchange for a free hand in Morocco) — this project uses it as the flashpoint for an alternate history where it escalates into real fighting instead. Superseded the earlier First Balkan War framing the same day it was written; the technical facts below carry over unchanged, since 1911 vs. 1912–13 is functionally the same arms technology (bolt-action repeating rifles, early Maxim-type machine guns, horse-drawn field artillery — not flintlock muskets).

**Nation roster** (tier labels and headcount targets given by the user are not being used as a design input, per direct instruction — recorded here purely as the participant list): **Majors** — Germany, France, United Kingdom. **Mediums** — Spain, Italy, Morocco. **Minors** — Switzerland, Portugal, Netherlands, Belgium (the last two included for now, may be cut later). **Open design question**: Morocco was floated as "maybe a subgroup of France most likely" rather than a fully independent power — `nodes`' data model (per §1/§9) is `Territory → Town → Nation` with no vassal/subordinate-nation relationship confirmed anywhere in the source (nations relate to each other only as ally/enemy/neutral, not hierarchically) — so representing Morocco as a France-subordinate polity, if that's the final call, would need either a from-scratch hierarchy concept or a workaround (e.g. Morocco as its own small/weak `Nation` permanently allied to France, not a real parent-child relationship). Not resolved, just flagged.

No need to build a gun plugin from scratch or bolt on an external one (nothing era-specific exists off the shelf anyway — checked Modrinth/Bukkit plugins for reference only). `combat`'s `Gun` is a **data-class item**: every stat (`damage`, `automatic`, `cooldown`, `reloadTime`, `spreadMin/Max`, `recoilMin/Max`, sounds, particles) is a constructor parameter. A period bolt-action rifle is just one `Gun(...)` instance configured as:

- `automatic = false` (bolt-action, one round per trigger pull — not literally single-shot-then-reload like a musket, but the same "can't just hold the trigger" balance property)
- moderate `reloadTime` (faster than a muzzle-loader, still slow relative to semi-auto), high `damage` (one/two-shot kill), low `spreadMin` (accurate when standing still), spread scaling up with movement speed (built in via `spread(speed)`)
- optional `bulletTrailParticle` for a visible tracer, custom fire/reload sounds

The same data-class flexibility covers the rest of the era's weapon roster without new code: `automatic = true` with a fast `cooldown` and larger ammo pool models an early Maxim gun, and a very high-damage/low-`cooldown`/short-range instance can stand in for close-support artillery until/unless a dedicated explosive-shell mechanic is designed separately.

Mechanically: hit detection is **hitscan raycasting** (`Ray.kt`, segment-AABB intersection against blocks/entities/vehicles), not physical ballistic projectiles — fine for this era's rifle feel, though it means no travel-time/drop, which is the one gap versus true ballistic ammunition if that matters. Ammo is tracked via item `DataComponents.DAMAGE` (not NBT) specifically to avoid triggering item-swap animations; reload is a repeating 100ms task with a title progress bar, cancelable on item switch. Melee combat in the same library is a from-scratch modern-vanilla-accurate reimplementation (attack cooldown scaling, crits, sweep, sprint knockback) — usable as-is for bayonet-style melee too, which was standard-issue for this war's infantry.

Integration reference: `CombatTest.kt` in the repo shows the real usage pattern — construct `Ammo`, `Gun`, `Melee` instances, `Item.registerItems(...)`, then `Combat.initialize()`. `combat` depends on `Minestom 2026.07.12-26.2` and `Aechronis/utils` only — no external combat framework.

## 3. Scaling to 200+ players across shards

Minestom itself has **no built-in clustering** — every multi-server Minestom network is custom, but the pattern is well established (mirrors the open-source `Swofty-Developments/HypixelSkyBlock`, a real 13-server-type Minestom network):

- **1 Velocity proxy** in front (Minestom has native `Auth.Velocity("secret")` support for modern forwarding)
- **N independent Minestom shard processes**, each a JVM running your server jar (nodes+combat+vanilla), sized by game type (e.g., a nations/PvP world shard vs. a hub/lobby shard)
- **Redis** for cross-shard realtime messaging (chat, party, alerts) and **MongoDB/SQL** for persistent state (accounts, nodes/territory data, stats)
- Player transfer between shards today is via the **BungeeCord plugin-messaging channel** (`bungeecord:main` → `Connect`), which Velocity honors — the newer native 1.20.5+ "transfer packet" exists in the protocol but isn't yet a first-class Minestom API, so stick with BungeeCord-style transfer unless you want to hand-roll packet handling.
- No official per-shard player cap; Minestom's bottleneck is tick-time from entity/AI logic, not raw player count — community benchmarks show thousands of entities costing only a couple % of tick time. **Rough starting point: 3–5 shards of 40–70 players each**, then profile and rebalance based on your actual gamemode logic (nodes' territory/siege logic and combat's projectile ticking are your main CPU costs, not player count itself).
- HypixelSkyBlock's reference sizing is ~16GB RAM / 6+ cores per deployment host — but that's a much larger feature set than a single nations+gun-PvP server; a leaner Aechronis stack likely needs less per shard.

## 4. Hosting cost — cheap and free-tier options

**Straight "stack a bunch of free-tier resources" is not recommended as the primary architecture.** Findings:

- **Oracle Cloud Always Free (Ampere A1)** — **RESOLVED (2026-07-30), confirmed directly with Oracle support**: PAYG (upgraded/paid) tenancies get the full **4 OCPU/24GB** Always Free Ampere A1 allotment, unaffected by the ~June 15, 2026 halving (4 OCPU/24GB → 2 OCPU/12GB) that applies only to free-tier-only tenancies. This was already suspected per an earlier user correction (2026-07-28) and is now confirmed as a hard fact via direct Oracle support contact, not just community reporting — the earlier "keep an eye on billing in case it's silently different" caveat no longer applies; treat 4 OCPU/24GB on PAYG as settled. "Out of capacity" errors on new ARM instance creation can still happen regardless of tier (a provisioning availability issue, not a billing one). Oracle Always Free egress is still capped at 10TB/month tenancy-wide — a real constraint at 200+ concurrent players streaming chunk/entity data, and unaffected by this resolution.
- **Google Cloud / Azure**: no genuinely perpetual free compute suitable for a game server — only time-limited trial credits. Not viable as a standing free shard.
- **Multiple free accounts across providers**: gray-area ToS territory (free tiers are meant for individual/trial use, not stitched into one production service), plus real networking overhead — separate free VMs from different clouds need WireGuard/VPN tunnels between them and the proxy, with no private network between providers. No strong community precedent found for this exact approach working well at scale.
- **Realistic cheap paid option**: Minestom's low per-player memory footprint (vs. Paper's ~50–300MB/player) means small/cheap VPS tiers go further than usual. **Hetzner CX22** (~€4.5/mo, 2vCPU/4GB, 20TB egress) or **Contabo** (~€3.60–4.50/mo, 4vCPU/8GB, unmetered inbound) are good per-shard building blocks — e.g. 4–5 Contabo/Hetzner boxes as shards + one small box as the Velocity proxy would likely cost **under €25–30/month total** for a 200-player network, which is a far more reliable foundation than free-tier stacking.
- **Suggested realistic-cheap approach (updated for PAYG's 4 OCPU/24GB)**: with the full 4 OCPU/24GB confirmed available on a PAYG tenancy, Oracle Always Free can do more than just host the proxy — you can split the pool into e.g. 1 small instance for Velocity + Redis, plus 1–2 A1 instances sized as real Minestom game shards (each comfortably covering the earlier 40–70 player/shard estimate). That could cover a large chunk of the 200-player target at $0, with 2–3 cheap paid VPS (Hetzner/Contabo, ~€4–5/mo each) filling in the remaining shards for headroom and redundancy — likely **well under €15–20/month total** for the full network rather than €25–30. Still keep an eye on Oracle billing given the PAYG overage ambiguity above, and don't rely on the free pool alone for 100% of capacity in case Oracle capacity/eligibility shifts again.

## 5. Open items for a follow-up planning pass (once you're ready to design, not just research)

- Whether an Aechronis "server"/core repo exists privately that already wires `nodes`+`combat`+`vanilla` together — worth checking with GitHub auth if you have org access.
- Whether you want hitscan (current `combat` Gun model) or true ballistic-drop rifle/artillery rounds — hitscan is what's built, ballistic would be a fork/extension of `Projectile.kt`.
- Concrete Velocity+Redis+Mongo scaffold and shard-count decision once you have a target player-per-shard number from real load testing.

## 6. Permissions: friendly fire, build access, combat mode, command gating

Already solved by `nodes` (no work needed, just config):
- **Territory build/break/interact gating**: `NodesWorldListener.kt` fully implements this — plot-level per-player and per-group ACLs (`plot.playerPermission`/`groupPermission`), falling back to town-wide `TownPermissions` (`INTERACT/BUILD/DESTROY/CHESTS/USE_ITEMS/INCOME`) by relationship group (`TOWN/NATION/ALLY/TRUSTED/OUTSIDER`), plus wartime/occupier overrides during a FlagWar capture. Chest access has its own `trusted`-resident check. This already covers "where they can and can't place blocks" per nation/town/plot — configure it, don't rebuild it.
- **Friendly fire**: `NodesPlayerDamageListener.kt` cancels PvP damage based on `Town.relationshipOfPlayerToPlayer(victim, attacker)` — same-town/same-nation damage blocked unless `Nodes.config.allowNationFriendlyFire`, ally damage blocked unless `Nodes.config.allowAllyFriendlyFire`. Both are config toggles, already wired.

Gap — needs custom building:
- **`Aechronis/combat`'s damage/health system is NOT team-aware** (`Combat.canDamage` only tracks a 500ms per-entity invincibility window, no team/town concept). Friendly fire currently only works because `nodes` cancels the `EntityDamageEvent` before combat's own damage math — this is an integration point, not something combat provides itself. Fine as-is since nodes already covers it, but any new damage source added directly through `combat` (not routed through the same event) would bypass the friendly-fire check unless you make sure all damage flows through the same event pipeline.
- **Combat-tag / "combat mode" does not exist in either library** — no PvP-tag state, no teleport/logout-prevention-while-tagged mechanic anywhere in `nodes` or `combat`. This needs to be built: typically a `Map<UUID, Long>` of "last took/dealt PvP damage" timestamps, checked before allowing teleport commands (nodes' home-teleport already has an unrelated movement-cancels-warmup mechanic you'd extend) and optionally blocking `/nation`/`/town` warps or plugin-message server transfers while tagged.

Command access control:
- Aechronis has its own thin permission layer in **`utils/Permissions.kt`** (`Player.hasPermission(String)`) and a base **`utils/Command.kt`** class (adds a `permission` field + `canExecute()` hook that `nodes`' `NodesCommand` extends for "must be a resident/in a town/in a nation" preconditions). This is the actual mechanism gating `/town`/`/nation` subcommands by leader/officer status today (hand-rolled per-subcommand checks in `TownCommand.kt`, not a generic rank system).
- `Permissions.kt` calls **LuckPerms's API directly** (`LuckPermsProvider.get().userManager...checkPermission(...)`). **LuckPerms has no official Minestom platform** — a community PR ([#3521](https://github.com/LuckPerms/LuckPerms/pull/3521)) was closed/abandoned in Aug 2024, and the tracking issue ([#3077](https://github.com/LuckPerms/LuckPerms/issues/3077)) is still open. So Aechronis must be running an unofficial/community LuckPerms-Minestom bridge (candidates: a fork like `jasonw4331/LuckPerms`, or a self-maintained one) — **this is a real maintenance risk worth verifying directly** (check `combat`/`utils`/the eventual server project's `build.gradle.kts` for which LuckPerms artifact/fork is actually pinned) before betting hundreds of concurrent players' permission checks on it.
- Alternative if the LuckPerms bridge proves unreliable: Minestom's own built-in `Permission`/`hasPermission`/`Command.setCondition` system is real but has no groups/inheritance — you'd need a lighter Minestom-native permissions library instead (e.g. `JustPermissions`, `NextPermissions`, `BrickPermissions`) if you want to drop the LuckPerms dependency.

**Bottom line**: building permissions ≈ don't build, just configure `nodes`' existing territory/friendly-fire system. Command permissions ≈ already wired via LuckPerms, but verify the specific bridge dependency is healthy. Combat-tag/"combat mode" ≈ the one piece that needs to be designed and built from scratch.

## 7. Concrete hosting topology plan (target: 200+ on one authoritative world)

**Revised per §7a below: `nodes` cannot safely run on more than one JVM at a time (plain in-memory state, hand-rolled JSON save, zero cross-process locking — see §8).** So there is exactly **one** flagship world shard holding the real nations/territory/PvP game; other boxes are hub/lobby/utility processes, not additional copies of the world. "200 on one shard" is the target, treated as something to verify with a real load test, not assumed — see §8's open item.

**Node layout:**

| Node | Where | Spec | Role |
|---|---|---|---|
| Control plane | Oracle A1 (PAYG Always Free pool) | ~1 OCPU / 8GB | Velocity proxy, Redis, MongoDB |
| **Flagship world shard** | Oracle A1 (same free pool, remainder) | ~3 OCPU / 16GB | The one authoritative nations/PvP world — sole owner of `nodes`' data, target ~200 players, to be confirmed by load testing |
| Hub/lobby shard(s) | Paid VPS (Hetzner CX22 or Contabo) or spare Oracle capacity | ~2vCPU/4GB each | Spawn/menu area, low CPU (no `nodes`/`combat` ticking) — can run more than one of these freely since they hold no authoritative state, purely for connection/queue capacity, not world capacity |
| Utility box(es) | 2x free Oracle AMD micro (E2.1.Micro, always-free regardless of tier) | 1/8 OCPU / 1GB each | Serve the static `nodes-map` viewer, lightweight monitoring/uptime dashboard |
| Fallback: 2nd realm shard | Paid VPS, held in reserve | sized like flagship if needed | Only stood up if load testing shows the single flagship can't reach 200 — a fully independent parallel copy of nodes+combat+vanilla (its own separate towns/wars), not a split of the same world |

This still spends the Always Free A1 pool on 2 instances (control plane + flagship). The hub/utility boxes are cheap regardless of count since they're stateless relative to `nodes`. Do not provision the fallback realm shard preemptively — it's a contingency, not a default part of the topology, so it isn't part of the baseline cost estimate below.

**Networking:**
- Control plane + flagship shard sit in the same **Oracle VCN**, talking over private IPs — free, low-latency, and only the Velocity port needs to be public.
- Attach a **Reserved Public IP** (free, but must be explicitly reserved so it survives instance stop/start) to the control-plane instance; OCI Security List/NSG allows inbound only on Velocity's port (25565 or 25577) from the internet, nothing else.
- Paid hub-shard VPS (different providers, no shared private network) expose their Minestom port but lock it down via the provider's cloud firewall (Hetzner Cloud Firewall / Contabo firewall) to accept inbound **only from the control plane's reserved IP** — this replaces the earlier-flagged "no private network between clouds" risk with simple IP allowlisting instead of a full VPN mesh, since the only traffic that needs to cross clouds is Velocity ⇄ shard.
- For Redis/MongoDB reachability from hub shards (needed for cross-shard chat/party/stats sync — hub shards never touch `nodes`' own data directly, only Redis/Mongo), run a lightweight **WireGuard hub-and-spoke VPN**: control plane is the hub, each hub-shard VPS is a spoke. Redis/Mongo bind only to the WireGuard interface, never a public IP. This is a small, manageable config, not a full multi-cloud mesh (nothing needs shard-to-shard connectivity, only shard-to-hub).
- Enforce Minestom's Velocity modern-forwarding (`Auth.Velocity`) on every shard so a connection that doesn't come through the proxy (and carry the signed forwarding payload) gets rejected, even if a shard's port is technically reachable.
- Given this is a nations-PvP server (a genre that reliably attracts grief/DDoS attempts), consider a free-tier passthrough proxy like **TCPShield** in front of the control plane's public IP for basic DDoS absorption — cheap insurance, no cost at the free tier.

**Player routing:** Velocity is the single public entry point; shard transfer uses the BungeeCord plugin-messaging channel (per §3) since Minestom doesn't yet have first-class native-transfer-packet support.

**DNS:** point your domain's A record (or an SRV record if you want a custom port without players typing it) at the control plane's reserved public IP. *Open item — need to know if you already own a domain or want to use IP:port for now.*

**Backups:** cron MongoDB dumps to Oracle Object Storage (10GB free tier); periodic block-volume snapshots of the flagship shard's world data **and, critically, its local `nodes/` JSON data directory** — that folder (not the Minecraft world save) is the actual source of truth for the whole nations game, so it needs to be backed up at least as carefully as chunk data.

**Cost estimate:** $0 Oracle (PAYG 4 OCPU/24GB Always Free allotment confirmed with Oracle support, no billing ambiguity remaining) + ~€4-9/month for 1-2 hub-shard VPS + free-tier TCPShield ≈ **under €10/month baseline**, only rising toward the earlier €15-20 estimate if the fallback realm shard ends up needed after load testing.

### 7a. Why this changed from the original multi-shard estimate

The first hosting pass (§7, original) assumed `nodes` could simply run as several independent world shards like a typical stateless Minestom workload. Digging into `nodes`' actual source (§8) showed it holds all territory/town/nation state in plain in-memory Kotlin collections, saved via a hand-rolled JSON serializer on a timer, with **no locking, no versioning, no cross-process coordination of any kind** — two JVMs pointed at the same data directory would race and silently corrupt each other's writes. That rules out splitting one shared nations world across multiple processes. The fix is architectural, not a workaround: one flagship process owns `nodes` outright, and everything else (hub, lobby, minigames, utility) is a separate process that either doesn't touch `nodes` at all, or only reads its exported JSON snapshots (the same files `nodes-map` already consumes) — never writes.

## 8. Server architecture: module layout, init order, config

**Per-library facts that drive this (confirmed from source):**

| Library | Entrypoint | Config | Persistence |
|---|---|---|---|
| `vanilla` | `object Vanilla { fun init(c: VanillaConfig = VanillaConfig()) }` | Kotlin data class, feature-flagged | own `PlayerData`/`Storage` managers |
| `combat` | `Combat.initialize()` | (constructed programmatically) | in-memory only, no save/load |
| `nodes` | `object Nodes { fun initialize(config: NodesConfig = NodesConfig()) }` | `NodesConfig` (paths, save/backup periods) | hand-rolled JSON to a local `nodes/` dir, single-process only (§7a) |
| `worldedit` | `class MinestomWorldEdit { fun init(config: WorldEditConfig = WorldEditConfig()) }` (instantiate, not singleton) + `fun shutdown()` | `WorldEditConfig(dataFolder: File = File("worldedit"))` | file-based |
| `logger` | `object Logger { fun init(config: LoggerConfig) }` (no default — required) | `LoggerConfig`, guards against double-init | embedded **H2** file DB via HikariCP — also single-process (file-lock) |
| `utils` | n/a (extension functions + `Permissions.kt` LuckPerms bridge + base `Command` class) | n/a | n/a |

None of these libraries read their own config files — every `XConfig` is a plain Kotlin data class you construct and pass in at `init()`/`initialize()` time. **There is no shared config format across the org**, so the composed server owns that layer itself (recommend one YAML or HOCON file per server process, parsed into a top-level `ServerConfig`, which builds each library's `XConfig` from it).

**Recommended repo layout** — a new Gradle multi-project build (not inside the Aechronis org itself unless you want it there), consuming the Aechronis libraries as GitHub Packages dependencies rather than vendoring their source:

```
your-server/
├── settings.gradle.kts          # modules: common, world-server, hub-server
├── gradle.properties            # GH Packages creds via env vars, not committed
├── common/                      # shared bootstrap code, both jars depend on this
│   └── ConfigLoader.kt          # parses YAML → typed ServerConfig → each library's XConfig
│   └── LuckPermsBootstrap.kt    # must run before anything checks permissions
├── world-server/                # the one flagship jar — owns `nodes`
│   └── WorldMain.kt
│   └── config.yml
└── hub-server/                  # lightweight jar(s) — never touches `nodes` directly
    └── HubMain.kt
    └── config.yml
```

**World-server init order** (each step assumes the previous one's baseline is already registered):
1. `MinecraftServer.init()`, configure `Auth.Velocity(secret)` — secret from an env var, never committed.
2. LuckPerms bridge bootstrap — must be up before any listener can call `Player.hasPermission()`.
3. `Vanilla.init(VanillaConfig(...))` — establishes baseline block/damage/player-data mechanics the other libraries assume exist.
4. `Combat.initialize()` — guns/melee; this is also where your custom combat-tag layer (§6 gap) hooks in, since it needs to wrap the same damage event pipeline.
5. `Nodes.initialize(NodesConfig(path = "nodes", ...))` — territory/nation system; its listeners run at high priority specifically to override/cancel vanilla and combat's defaults (block break, PvP damage), so it must come after both are registered.
6. `MinestomWorldEdit().init(WorldEditConfig(...))` — admin tool, order-independent relative to gameplay.
7. `Logger.init(LoggerConfig(...))` — action logging; typically listens at monitor priority so init order doesn't matter much, but do it after the gameplay libraries are live so there's something to log.
8. Load/create the actual `InstanceContainer` (world), spawn point, chunk source.
9. `MinecraftServer.start(host, port)`.

**Hub-server** is deliberately thin: `Vanilla.init()` only (no `nodes`/`combat`/`worldedit`/`logger`), a small static/pre-built world, and — for things like a "/nation info" command in the hub — **read the same `world.json`/`towns.json` files `nodes` exports for `nodes-map`, directly and read-only**, rather than loading `Nodes.initialize()` itself. This is exactly the boundary those export files already exist for; it's what lets a hub display live nation data without becoming a second writer of `nodes`' state.

**Secrets** (Velocity forwarding secret, Mongo/Redis connection strings, LuckPerms bridge config) belong in environment variables or a gitignored `.env`, not the YAML config files that get committed.

**Open item:** confirm which LuckPerms-Minestom bridge/fork is actually usable before locking this in (§6) — `LuckPermsBootstrap.kt` in `common/` is the one piece of this architecture that depends on an unofficial community integration.

## 9. Economy & onboarding

**Confirmed from source:** `nodes` has no currency of any kind — no `Economy`/`Balance`/`Vault`/`Bank` concept anywhere in the repo (checked exhaustively). "Income" from `Farm`/`Port`/`OreDeposit` is literally raw `Material` items, accumulated hourly (`IncomeManager`, tax-adjusted if the territory is occupied) into a `Town.income` chest-GUI inventory (`objects/IncomeInventory.kt`), withdrawn via `/town income` (permission-gated). It's a pure material economy — towns fight over land because it produces goods, not gold.

**Decision: hold off on a currency layer for now.** No add-on economy system will be designed until this is revisited — noted here as an open/deferred decision, not a "no." If a currency layer is wanted later (e.g. for shops/vendors), the right approach is a thin listener on the same income-distribution event that credits a spendable balance per material type, *without* modifying `nodes` itself — keeps the upgrade path open without committing to it now.

**Confirmed from source — town/nation creation is admin-only, not self-serve:**
- Players can `/town apply <name>` (60s expiry if no online officer answers) or receive `/town invite`, then `/town accept`/`/town deny`. There is **no player-facing `/town create`** — `Town.create()` is only reachable via `/nodesadmin town create`.
- Nations are locked down further — `create`/`rename`/`delete`/membership are 100% `nodes.admin`-gated, no player nation commands exist at all.
- Townless players are **unrestricted by default** (`canInteractInEmpty`/`canInteractInUnclaimed` both default `true`) — they can freely build/roam in wilderness while deciding what to do, no forced funnel into a town.

**Decision: curated roster at launch.** The political map (nations + starting towns) gets pre-designed and admin-created before launch, rather than growing organically from player-founded towns. Ongoing expansion afterward should stay rare and hand-picked rather than routine — this is a deliberate choice for balance/lore control, closest to a designed war-of-factions experience rather than an open land-rush.

**Onboarding flow implied by the above:**
1. New player connects → Velocity → hub shard (per §8) → intro/tutorial (nations concept, how to apply to a town) → transfer to the flagship world shard.
2. Player spawns townless in the pre-seeded world — free to explore/build in wilderness/unclaimed land immediately, no gate.
3. To join the curated political landscape, they browse existing towns (the hub can read `nodes`' exported `towns.json` read-only, per §8, to show a live roster without running `Nodes` itself) and `/town apply` or wait for an invite.

**UX gap worth flagging:** applications auto-expire in 60 seconds if no town officer is online to answer — for a new player's first impression, that's a real risk of silently failing with no feedback if leadership happens to be offline. Worth building a lightweight notification hook later (in-game alert queue, or a Discord webhook ping to town leadership) so applications don't just vanish. Not designing this now, just flagging it so it doesn't get missed.

**Open/unconfirmed:** whether `Town` has any "recruiting open/closed" status flag — relevant if the hub is meant to show which towns are actively accepting applicants. Not yet checked against source.

## 10. Anti-cheat

**No mature drop-in anti-cheat exists for Minestom** — unlike Bukkit's NoCheatPlus/Matrix/Vulcan ecosystem, the options are a handful of small/immature community projects (`Mangolise/mango-anti-cheat` — 27 stars, checks Flight/Speed/Teleport/CPS/Reach/IntOverflow; `LooFifteen/MinestomAirConditioner`; a couple of smaller ones) or a paid cross-platform product (Negativity V2). None have Vulcan-level trust. Minestom itself ticks physics server-side but does **not** validate movement packets against theoretical max speed — `PlayerMoveEvent` just reflects whatever position the client claims; that validation is left entirely to the developer by design.

**What Aechronis already has (confirmed from source):**
- **Gunfire cooldown is properly server-side** — `Gun.fire()` checks `Combat.playerLastActionTimes` against `cooldown` and rejects/ignores fast-fire attempts regardless of what the client sends. Reload is a scheduled server task, not client-timed either. This part is solid already.

**Confirmed gaps (nothing exists for these anywhere in `vanilla`/`combat`/`utils`):**
- **No melee reach-distance check** — `MeleeListener.kt` only checks a sweep-target distance, not attacker-to-target range on the primary hit. A reach hack would work today.
- **No movement validation at all** — no speed-hack, fly-hack, or no-fall check anywhere. `FallDamageListener.kt` trusts the client's reported Y-position with no anomaly detection. Flight is even easier to abuse here than usual since it doubles as a scouting/escape tool against the combat-tag system we still need to build (§6).
- **No anti-x-ray** — nothing Orebfuscator/Paper-anti-xray-equivalent exists for Minestom that was found. ~~This matters more than usual for this specific game...~~ **Superseded by §11 — the chosen ore-mining design (no physical ore blocks at all) makes x-ray largely moot on its own, no obfuscation system needed.**

**Recommended priority if/when this gets built** (not building now, just ordering the work for later): melee reach check first (cheap, closes a gap in code that already exists), basic movement/speed/no-fall sanity checks second (moderate effort, catches the most common blatant hacks and closes the combat-tag-evasion angle). Anti-x-ray dropped per §11. Worth spiking `mango-anti-cheat` as a reference for check *design* (its check list maps almost exactly onto the remaining gaps) even if its low commit count/star count means it shouldn't be trusted wholesale as a production dependency for 200+ concurrent players without review.

## 11. Mining/ore economy mechanic

Design: no traditional ore blocks underground, just stone; breaking stone has a chance to drop ore materials, boosted while standing in an ore resource-node territory, further boosted by redeemable time-limited vouchers.

**This is essentially already built into `nodes` — not a from-scratch mechanic.** Confirmed from source:
- `Territory.ores: OreSampler` — a per-territory weighted ore table (Vose's-alias-method sampler), built from that territory's `OreDeposit(material, dropChance, minAmount, maxAmount, ymin, ymax)` attributes.
- `NodesWorldListener.onBlockBreakSuccess` already implements exactly this flow: checks whether the broken block is in `Nodes.config.oreBlocks` (a configurable block-type set), dedupes against a place-and-break duplication exploit (`OreBlockCache`/`Nodes.hiddenOreInvalidBlocks`), respects silk-touch/ownership/nation gating, then calls `territory.ores.sample(blockY)` for the actual drop.
- Location-based boosts already exist too: `ResourceAttributeOreMultiplier`/`ResourceAttributeTotalOreMultiplier` plus neighbor-territory modifier accumulation (`TerritoryResources.accumulateNeighborModifiers/applyNeighborModifiers`) — this is the "boosted inside an ore resource node" part, already there.

**What's actually needed:**
1. **Configuration, not code**: add `STONE` (and whatever stone variants the custom map uses — deepslate etc.) to `Nodes.config.oreBlocks`. This is the whole "mine stone instead of ore blocks" behavior, just a config value.
2. **One integration detail to get right**: `vanilla`'s block-drop system (`PlayerBreakListener` + `VanillaConfig.blockDrops`) is a separate, independent fixed 1:1 drop table with no awareness of `nodes`. Left alone, breaking a configured ore-block-eligible stone would fire *both* systems — vanilla's normal cobblestone/stone drop *and* nodes' probabilistic ore roll, double-dropping. Needs either excluding those block types from `vanilla`'s `blockDropsEnabled`/`blockDrops` map, or confirming listener priority makes one authoritative and cancels the other. Small fix, not a rebuild — but needs to be verified before launch, easy to miss.
3. **Voucher system — genuine from-scratch gap, but simpler than first framed.** Nothing resembling a redeemable time-limited buff exists anywhere in `vanilla`/`nodes`/`utils`. **Scope clarified: a redeemed voucher boosts either every player server-wide, or every player in one nation — not an individual player.** That reframes the integration cleanly: since `nodes` already accumulates ore-chance modifiers at the *territory* level (`ResourceAttributeOreMultiplier`/`ResourceAttributeTotalOreMultiplier`, the same mechanism behind the permanent "boosted inside a resource node" effect), a nation-scoped voucher is naturally just a **temporary extra multiplier applied to every territory owned by that nation**, and a global voucher is the same thing applied server-wide — reusing `nodes`' existing multiplier-accumulation system rather than inventing a parallel per-player mechanism. The expiry/cleanup half still borrows `vanilla/managers/Combat.kt`'s `ConcurrentHashMap<UUID/Nation, Long>`-with-cleanup-task pattern, just keyed by nation (or a global flag) instead of by player. **Remaining open item**: unconfirmed whether territory attribute modifiers are recomputed live on every `sample()` call or cached/baked whenever territory attributes change — that determines whether a voucher's temporary modifier would need to trigger a recompute, or would just be picked up automatically. Worth checking when this is actually built, not blocking the plan.

**Anti-x-ray correction (updates §10):** since ore is never a placed, visually-distinct block — mining plain stone triggers a hidden probability roll, not something an x-ray texture pack can target — the anti-x-ray priority flagged in §10 is no longer warranted by this design. One less thing to build. `nodes` also already anti-exploits the obvious "place stone, break it, farm the roll" abuse via `OreBlockCache`.

**Economy tie-in (§9):** this active per-block mining income is a second, player-driven layer on top of `nodes`' existing passive hourly `IncomeManager` distribution from `Farm`/`Port`/`OreDeposit` nodes — both land in the same item-based economy, no currency needed for either, consistent with the "hold off on currency" decision.

## 12. Combat-tag system

**CORRECTED (2026-07-30): this was wrongly marked "nothing to adopt" — a working combat-tag mechanic already exists in `vanilla`, not just a reusable pattern.** The original research below (Bukkit-ecosystem reference survey, `TogAr2/MinestomPvP` check) only searched `nodes`/`combat` and outside libraries — it never actually opened `vanilla`'s own `managers/Combat.kt`/`listeners/CombatListener.kt`, which turns out to implement most of what this section previously scoped as a from-scratch build. Re-read directly against current source to correct the record:

**What's already built and working, right now, in `vanilla`:**
- `managers/Combat.kt`: `Combat.tag(a, b)` marks both participants with an expiry timestamp (`ConcurrentHashMap<UUID, Long>`, default duration `VanillaConfig.combatDurationSeconds = 10L`, refreshed on every new hit — exactly the "refreshes on each hit" behavior the Bukkit-plugin survey below describes), plus a live boss-bar countdown (red, `"Combat: Ns"`) shown to the player for the duration, driven by a repeating global-scheduler task (`combatTickSeconds`, default 1s).
- `listeners/CombatListener.kt`: `onDamage` tags both parties on any non-self `EntityDamageEvent`; `onDisconnect` checks `Combat.isInCombat(player)` and calls `player.kill()` if true — **this is already the exact "disconnect-while-tagged = instant-kill" mechanic**, not a decision still waiting to be built.
- **Multi-shard-transfer bypass risk is very likely already closed for free, not something needing new restriction-list code**: since the hosting plan (§7) runs the world shard and hub as separate processes with real Velocity-mediated transfer, a shard-hop looks like a genuine TCP disconnect to the origin shard and fires `PlayerDisconnectEvent` — which `CombatListener.onDisconnect` already handles. **Still needs confirming once shard-transfer is actually implemented** (verify `PlayerDisconnectEvent` really fires on a Velocity-initiated transfer packet and isn't special-cased/suppressed anywhere), but the previous framing of this as unbuilt work was wrong — at most it's a verification task now.

**What's genuinely still missing (the real remaining gap, smaller than previously scoped):**
- **No loot drop on death anywhere, for any death — not just combat-tag.** Checked all three death-adjacent listeners that exist (`combat/listeners/PlayerDeathListener.kt` — handles death messages + instant-respawn-skip only; `nodes/listeners/NodesPlayerJoinQuitListener.kt`'s `onPlayerDeath` — only resets respawn point to town spawn; `vanilla`'s `CombatListener.onDisconnect` — calls `player.kill()` with no inventory handling at all). Minestom ships no default death-inventory-drop behavior (confirmed: Minestom is a from-scratch framework, unlike Paper/Spigot which drop by default) — so as the code stands today, **dying does not drop or clear the player's inventory anywhere in the stack**, combat-tag or otherwise. This is the actual remaining build item, and it's bigger in scope than "combat-tag's loot drop" — it's a general death-handling gap that combat-tag's instant-kill just inherits.
- **No command/teleport/warp/ender-pearl/flight restriction while tagged.** `Combat.isInCombat()` exists as a check function but nothing outside `vanilla`'s own Combat.kt/CombatListener.kt calls it — no gate exists anywhere on `/home`, `/warp`, or any other command while a player is tagged. This part of the Bukkit-reference pattern (see below) genuinely isn't built yet.
- **Friendly-fire/tag-ordering correctness is unverified, not just "must hook after" future advice — this is a live-today question against the current code.** `nodes/listeners/NodesPlayerDamageListener.onDamage` cancels `EntityDamageEvent` for ally/nation friendly fire (when disabled by config) via `event.isCancelled = true`, registered on `Nodes.eventNode` with default priority. `vanilla`'s `CombatListener.onDamage` is registered on a *separate* child node (`EventNode.all("vanilla-combat").setPriority(1000)`) and **does not check `event.isCancelled` at all** before calling `Combat.tag()`. Since these are two independent event-node trees (not the same node with relative priority), it's unconfirmed whether Minestom's dispatch order guarantees `nodes`' cancellation is visible to `vanilla`'s listener before it runs — if not, **allied sparring with friendly-fire intentionally enabled would still incorrectly combat-tag both players**, since `vanilla`'s tag-on-damage doesn't check relationship or cancellation state at all. Needs a direct test against the pinned Minestom build, not assumption either way.
- **Thread-safety**: `Combat.tick()` runs on the global scheduler (confirmed separate from the tick thread per `research-todo/01-concurrency-model.md`) and directly calls `player.showBossBar`/`hideBossBar`/`sendMessage` — now tracked as a concrete instance of the recurring "global scheduler thread mutates per-player state" pattern in `VANILLA_DEEP_DIVE.md`, not just a hypothetical.

**Bukkit-ecosystem reference pattern** (CombatTagPlus, PvPManager, CombatLogX — consistent across all of them, kept for comparison against what's actually built above): 10–15s tag duration that refreshes on each new hit (✅ already matches); while tagged, block most commands, teleport/`/home`/`/warp`, ender pearls, flight, and often gamemode changes (❌ not built); staff/creative exempt (❌ not built); a "safe zone" force-field prevents tagged players walking or pearling into protected areas (probably unnecessary here — see below); untagging is timer-only (✅ matches, `tick()` only clears on natural expiry). Disconnect-while-tagged handling varies by plugin — no single standard: instant-kill (✅ this is what's built), inventory/XP drop without killing the account, a killable NPC stand-in, or staff-review logging (all not applicable, instant-kill was already chosen and built).

**Safe zones are likely still unnecessary here**: `nodes`' own territory/war permission system (§6) already gates *where* hostile PvP can even happen (friendly-fire cancellation, occupier/war permissions) — a lot of what a bolt-on "safe zone force-field" exists to do in Bukkit plugins is already handled contextually by the territory system itself, independent of the combat-tag correction above.

**Revised remaining work, now that the mechanic itself is confirmed built**: (1) build general death-inventory-drop handling (bigger than just combat-tag — affects every death), (2) build the command/teleport/warp restriction gate using the existing `Combat.isInCombat()` check, (3) resolve the friendly-fire/tag-ordering question against the pinned Minestom build, (4) fix the thread-safety issue in `Combat.tick()` per whatever policy comes out of `01-concurrency-model.md`, (5) confirm shard-transfer really does fire `PlayerDisconnectEvent` once that's implemented. None of this requires building the tag/timer/instant-kill core — that part is done.

## 13. Ops layer: staff log review, whitelist/vetting, Discord

**Staff log review — already fully built, no work needed.** `Aechronis/logger` is not a write-only sink; it ships a complete CoreProtect-style toolset via `/logger`:
- `lookup` (`u:<user> t:<time> r:<radius> a:<action> ...`) — answers both "what happened at this location" and "what has this player done," across block changes and a "feature log" covering kills and item pickup/drop.
- `inspect` — toggle mode, breaking/placing/interacting a block shows its history instead of editing it.
- `rollback`/`restore`/`undo`/`redo` — a real rollback engine with preview, confirm/cancel tokens, and a `recover` command for resuming after a crash mid-rollback.
- `snapshot` — views a player's inventory at time of death/logout.
All output is in-game chat components — no web dashboard or HTTP API exists (no Ktor/Javalin dependency, purely Minestom-native), so log review is an in-game staff workflow, not a web tool. That's fine for this scale; if a web view is ever wanted later, it'd need to be built from scratch since nothing like `nodes-map`'s export pattern exists for `logger`.
- **Gap worth noting**: it does **not** log chat, join/leave, or command usage — only block changes, entity spawn/despawn, kills, loot, inventory changes, and container access. If chat moderation history matters (likely, at this player count), that's a separate small listener to add, following the same table/writer pattern the existing listeners use.

**Whitelist/vetting — two distinct questions:**
1. *Server-level access* — **RESOLVED (2026-07-30): open server, no whitelist.** Anyone with the IP/domain can connect; no beta/load-testing gating phase planned. This means the username-sanitization gap in `nodes` (`ArgumentSanitizedString` never used — see `03-anti-cheat-and-security.md`) has its full exposure surface live from day one, with no whitelist acting as an incidental vetting layer — makes confirming online-mode/Velocity-modern-forwarding (the actual mitigation) more important, not less.
2. *Nation/town membership vetting* — **RESOLVED (2026-07-30): no review process.** Joining a nation/town does not go through a Discord application/ticket or any staff/leadership review step — the existing in-game `/town apply`/`/town invite` flow (§9) stands as-is, no additional vetting layer on top of it. This closes the "should this route through Discord" question; Discord structure (below) doesn't need a membership-gating role/bot for this reason, only whatever general community-structure purpose it ends up serving.

**Discord structure**: not yet designed. Given no MCP connector or bot access exists (see above), this would be delivered as a written recommendation (channel/role/bot layout) for you to implement yourself, or via confirmed step-by-step browser actions if you want me to drive it directly later.

## 14. Console / Pterodactyl

**Pterodactyl still works here, and fits the multi-provider shard topology well** — its Panel/Wings architecture is decoupled by design (confirmed): one central Panel manages many Wings-daemon **Nodes**, each an independent host anywhere (an Oracle A1 instance, a separate Hetzner/Contabo VPS), grouped into Locations purely for organization. So one Panel can give a single unified web console across the flagship shard, hub shards, and proxy even though they're spread across different clouds — same idea as the Paper-network Pterodactyl setups you've used before, just pointed at more varied hosts.

**Two things Minestom doesn't give you for free that Paper does — both need to be built, not configured:**
1. **No console/stdin command handling built in.** Minestom's `CommandManager` only exposes `execute()`/`executeServerCommand()` — there's no built-in loop reading `System.in` and dispatching it, unlike vanilla/Paper/Bukkit. Pterodactyl's web console can still *show* live output either way (that's just reading stdout), but *typing commands into it* requires the server jar itself to run a small stdin-reading loop (a `Thread` + `BufferedReader`/`Scanner` calling `MinecraftServer.getCommandManager().executeServerCommand(line)`) — a small, standard addition, but it has to be written into `world-server`/`hub-server`'s `Main.kt`, not assumed.
2. **No Pterodactyl/Pelican egg exists for Minestom anywhere** (checked `parkervcp/eggs`, `pelican-eggs/eggs`/`pelican-eggs/minecraft` — ~26 Java server types listed, Minestom isn't one). A custom egg needs to be authored: start command (your Gradle-built shaded jar, `java -jar server.jar` — no download/launcher flow like Paper has), a "done" detection regex tied to whatever line your own startup code logs (Minestom has no fixed "Done" message the way vanilla does — you choose it), and a stop mechanism — either a `stop` command sent through the stdin loop from #1 wired to `MinecraftServer.stopCleanly()` (mirrors how Paper eggs stop servers), or falling back to a SIGTERM/kill signal if a graceful stop-via-command isn't built.

**No custom work needed for the proxy**: Velocity has an official, well-established Pterodactyl egg (`parkervcp/eggs` and the official eggs.pterodactyl.io catalog) — drop-in exactly like Paper-network setups.

**Redis/MongoDB don't belong in Pterodactyl** — they're not Minecraft-protocol server processes with a console in the way Pterodactyl models "servers," so simplest is running them as plain system services/Docker containers directly on the control-plane host, managed over SSH, rather than as Pterodactyl-managed eggs.

**Relevant existing knowledge**: there's already a `minecraft-hosting` skill in this environment covering Oracle A1 + Pterodactyl setup end-to-end (wings install, node config, egg setup) — built around Paper hosting, but the Panel/Wings/Oracle-A1 mechanics carry over directly; only the egg (Minestom-specific, per above) and the stdin console loop are genuinely new work beyond what that skill already covers.

**Decision — keep Pterodactyl, don't switch.** Considered dropping it given the two gaps above, concluded against it:
- The stdin-loop requirement isn't actually a Pterodactyl-specific cost — *any* console tool needs the server process itself to read stdin for typed commands to work at all, so this work exists regardless of what manages the console.
- The missing egg is a one-time JSON config, not a structural problem — write once, reuse (with different jar/config) for both `world-server` and `hub-server`.
- What raw SSH/systemd would sacrifice matters specifically for this project: a **single unified console across providers** (shards are deliberately split across Oracle + Hetzner/Contabo per §7 — Pterodactyl's Panel/Wings split exists exactly to keep that manageable from one dashboard), and **scoped staff console access without handing out SSH keys** to production boxes (relevant given §13's ops/staff layer).
- **Alternative considered and rejected for now**: Portainer (Docker-based) would sidestep the "no egg" problem entirely and keep multi-host console + its own RBAC, at the cost of containerizing the server and learning a second tool instead of reusing existing Pterodactyl familiarity. Worth reconsidering only if the custom-egg approach turns out to be more painful in practice than expected.

## 15. Player UI / UX — improving `nodes`

**Correction to earlier research: `nodes` has a real in-game minimap, not just the external `nodes-map` website.** Missed in the original org survey (§1), confirmed now:
- `objects/Minimap.kt` — a packet-only text-display entity attached to the player as a virtual passenger, rotated/updated per yaw.
- `objects/MinimapMarkerRenderer.kt` — encodes chunk position, territory relationship (town/nation/ally/enemy/neutral), border edges, and waypoint distance directly into the RGB channels of Adventure text-component colors, rendered via a custom font (`aechronis:minimap`) — a font-glyph pixel canvas, not a Minecraft map item.
- `MinimapIcons.kt` — codepoint table for waypoint/nation/ally/enemy/core/player icons at two zoom levels.
- Backed by real resource-pack assets (`assets/aechronis/font/minimap.json`, territory/icon textures, plus overridden `text.fsh/vsh`/`rendertype_lines.vsh` shaders needed to render the encoded-color font correctly).

`Aechronis/resource-pack` (default branch `template`, plus `a-new-millenium`/`event` branch variants) turns out to be multi-purpose: minimap font/textures, an NTSC-style retro CRT post-processing shader chain (unrelated to mapping), and a military-roleplay weapon/vehicle item-model set (AK-47/AK-74, M4A1, M9, RPG, ammo, tank parts, F-16, drone, truck, faction armor) — relevant to §2/weapon-visuals planning later.

There's also a third, lighter mapping layer: `NodesPlayerMoveListener.kt` fires an action-bar/chat announcement on crossing into a new chunk ("Territory Name (Town Name)", relationship-colored, occupied/captured tags, or "Wilderness").

**Current player-facing command/UX surface** (baseline to improve from, `nodes` master):

| Command | Surface | What it does |
|---|---|---|
| `/town` | text | help, promote, demote, apply, invite, accept/deny, leave, kick, spawn, setspawn, list, info, online, permissions, protect(+show), trust/untrust, fly |
| `/town income` | **GUI** (chest) | treasury withdrawal — the only GUI besides waypoints |
| `/town plot` | text | toggle corner-select, create, redefine, permissions, list, delete |
| `/nation` | text | help, list, online, info (no player create/join/leave — admin-only, §9) |
| `/territory` | text | info at current location or by ID |
| `/ally`, `/unally` | text | propose/accept, break alliances |
| `/port` | text | list, info, warp (boat travel) |
| `/waypoint` (`/wp`) | **GUI** | browse menu, create menu |
| `/player` | text | self/other player info |
| `/globalchat`, `/townchat`, `/nationchat`, `/allychat` | text | channel toggles |

**Observation, not yet a plan**: almost everything is text/chat-command driven — only treasury withdrawal and waypoints have real GUIs. Plot management, territory info, member/permission management are all chat-output-based, which tracks with "generally very complicated" for a system with this much surface area (11+ command groups, permission groups, plot ACLs, relationship types).

### Town Overview GUI (officer-facing) — first concrete UX improvement

**Confirmed data sources on `Town` (`objects/Town.kt`)** — real fields to pull from, nothing assumed:

| Field | Type | Use in overview |
|---|---|---|
| `residents` | `HashSet<Resident>` | member list |
| `playersOnline` | `MutableSet<Player>` | online-now subset |
| `income` | `IncomeInventory` | income summary (or link to the existing `/town income` GUI) |
| `territories` | `HashSet<TerritoryId>` | node/territory count via `.size` |
| `annexed` / `captured` | `HashSet<TerritoryId>` | separate territory-status sets, distinct from owned `territories` — worth showing separately if wartime status matters to officers |
| `spawnpoint` | `Pos` | town spawn coordinates (field is named `spawnpoint`, not `spawn`) |
| `home` | `TerritoryId` | home territory |
| `nation`, `color`, `name`, `uuid` | | identity |

**Correction: no tier *code* exists, but a full documented design does.** A broader search (all branches, all repos, prompted by correctly doubting the first narrow check — same lesson as the minimap) found `Aechronis/guides/src/territory-tiers.md`: a real, detailed **Territory Tiers** spec, just never implemented. Per that doc:
- Tiers 1–10, assigned manually by staff via a "nodesweep" evaluation (build quality/quantity/accuracy review) — matches the "admin manually assigns tier" decision.
- Income + mining-rate bonuses scale by tier: **Tier 1 = +50% income/+30% mining, up to Tier 10 = +300% income/+200% mining.** Also unlocks additional buildings at higher tiers.
- **Scoped to individual territories, not towns** — explicitly stated to apply "even when a build spans multiple territories."

**Decisions made**: follow the doc's scope (tier lives on `Territory`, not `Town`) rather than diverging to town-level; add a new capture-time bonus on top of the documented income/mining bonuses (not in the original doc, a deliberate extension).

**Implementation hook, confirmed already in place**: `Territory` (`objects/Territory.kt`) already has `attackerTimeMultiplier: Double`/`defenderTimeMultiplier: Double` fields, multiplied directly into `FlagWar.createAttack()`'s attack-time formula (`war/FlagWar.kt`) — a tier-derived defender-time bonus can very plausibly feed into the *existing* `defenderTimeMultiplier` value (set at tier-assignment time from a tier→multiplier lookup, mirroring the doc's income/mining percentage table) rather than adding new fields or touching `FlagWar.kt`'s formula at all — though `Territory` is a final immutable object, so `defenderTimeMultiplier`'s current initialization path needs checking before assuming it's safe to just repoint at a tier lookup versus needing a new dedicated field alongside it. The ore/income side has no generic modifier interface to plug into (confirmed: `TerritoryResources` in `Territory.kt` is a flat data class, every multiplier — `accumulatedNeighborTotalOresMultiplier`, `accumulatedNeighborNeighborOresMultiplier` per-material, income equivalents — is a hardcoded field wired manually into `accumulateNeighborModifiers`/`applyNeighborModifiers`), so a tier income/ore bonus follows the same copy-paste-a-new-field pattern, not a clean plugin point.

**Consequence for the GUI**: since tier is per-territory, the Town Overview GUI can't show one single "tier" number — it needs a tile summarizing tier across all of `town.territories` (e.g. a list/breakdown, or an average), not a flat field lookup.

**Serialization**: `nodes` uses the same hand-rolled JSON pattern for `Territory` as confirmed for `Town` (manual `StringBuilder`/string-template writes in `Serializer.kt`, manual `JsonObject.get(...)?.as...` reads in `Deserializer.kt`) — adding `tier: Int` to `Territory` means the same three-touch-point update (constructor, save-state serialization, deserialization), just in `Territory`'s section of those files rather than `Town`'s.

**Established GUI pattern to follow**: `WaypointMenu`'s fixed-slot layout + its `namedItem()` helper (icon + colored custom name + lore lines) — this is the right template, not `IncomeInventory`'s material-storage-slot model, since an overview is a display/navigation dashboard, not an item container. Access gate matches the codebase's existing idiom exactly: `resident === town.leader || town.officers.contains(resident)`.

**Planned layout** (single fixed-slot screen, no pagination needed — this is a dashboard, not a list): tiles for member list (residents + online count), income summary, territory/node count with a per-territory tier breakdown (+ annexed/captured if shown), spawn coordinates, nation. Each built via `namedItem()` with lore lines pulling the fields above.

**Behavior — navigation hub, not read-only.** Decided: clicking a tile performs the related action rather than just displaying text — click income → opens the existing `/town income` GUI, click spawn coordinates → teleports the officer there, click territories → drills into the per-territory tier breakdown. Directly serves the "reduce complexity" goal from earlier in §15 by replacing several memorized commands with one dashboard.

## 16. Redis / MongoDB — corrected scope

**§7's original justification was wrong in part** — it assumed generic multi-server-MMO needs (party/guild/friends sync, modeled on the `HypixelSkyBlock` reference architecture) without checking whether this server actually has those features. It doesn't: no party/friends system exists anywhere in `vanilla`, `nodes`, or `combat`. Correcting scope now that more of the stack has been checked:

**Confirmed single-process-only, need nothing shared (no change from before):** `nodes` (local JSON), `logger` (embedded H2), `combat` (in-memory).

**Newly confirmed single-process-only — this is the real finding:** `vanilla`'s `PlayerData` manager persists per-player state (health, food/saturation, kill-shop points, gamemode, flight state, position, main inventory, ender chest, cursor item, ignored-players list) as one gzip-compressed NBT file per UUID, loaded on spawn / saved on disconnect. **Hardcoded local filesystem, no external-DB option in `VanillaConfig` at all** — same situation as `nodes`. This matters more than the others because, unlike territory/logging/combat state (which only ever needs to exist on the flagship world shard), **players actually move between the hub and world shards** — so if `PlayerData` stays local-file-only, a player's health/inventory/position won't correctly follow them across a shard transfer. This is the strongest, most concrete driver for a shared database in the whole architecture.

**MongoDB — recommended scope: `PlayerData`, and only `PlayerData`.** Mirror `PlayerDataSerializer`'s existing fields as one document per UUID (health/food/gamemode/flight/position/inventory/enderchest/cursor-item/ignored-list). This isn't a config toggle — `vanilla`'s `PlayerData.kt` would need modifying to read/write Mongo instead of local gzip NBT files, since no external-DB hook exists to configure instead. Simple per-UUID document shape; no relational complexity needed.

**LuckPerms storage — real open item, unconfirmable from Aechronis source.** `utils/Permissions.kt` is just a ~20-line `hasPermission()` call into `LuckPermsProvider` — there's no storage-backend configuration anywhere in Aechronis code, because that lives entirely in whatever unofficial LuckPerms-Minestom fork/bridge is actually deployed (§6/§8's flagged risk), outside this org's repos. **This needs to be checked operationally before launch**: if that fork defaults to per-process embedded storage (H2/SQLite), permissions would silently desync across shards — a player promoted to officer on the world shard wouldn't show as one from a hub-shard process reading a different local file. Recommend pointing whatever LuckPerms config that fork exposes at the same shared database as `PlayerData` (LuckPerms officially supports MySQL/MongoDB upstream) rather than trusting per-process defaults.

**Redis — smaller scope than originally assumed, chat scope now decided:**
- Confirmed gap: `vanilla`'s `list`/`invsee` commands only see `MinecraftServer.getConnectionManager().onlinePlayers` — the *local process's* players only. In a multi-shard setup, staff running `/invsee` for a player on a different shard, or wanting a true network-wide online count, currently wouldn't work. A lightweight shared registry (Redis key per UUID → current shard, or pub/sub) would fix "who's online where"; actually viewing a *live* inventory cross-shard is harder and would need either a small inter-shard RPC or accepting a slightly-stale view via the Mongo `PlayerData` documents instead.
- **Decided: chat spans the whole network** — global/town/nation/ally channels (§15) need to reach players regardless of which shard they're actually connected to (hub, the flagship world shard, or an optional PvP-events shard, see below). Design: one Redis pub/sub channel per broadcast scope (e.g. `chat:global`), every shard process subscribes and publishes into it. For town/nation/ally chat specifically, a non-world shard (hub, PvP-events) doesn't run `Nodes.initialize()` and so doesn't inherently know a connected player's town/nation — but it doesn't need to: it can resolve that from the same read-only `towns.json` export each shard already reads for hub display purposes (§8), reusing an existing pattern rather than adding new infrastructure just for chat filtering.
- No other confirmed Redis need exists beyond these two — the original parties/guilds justification doesn't apply since those features don't exist in this stack.

**Shard topology clarified, no change to §7's core decision**: confirmed still one authoritative flagship world shard. A "PvP-events" shard (separate open-PvP/arena space, not territory-based) is conditional — only stood up if running events alongside normal nations gameplay turns out to strain the flagship, not committed upfront. Architecturally it's just another instance of the same non-authoritative-shard category as the hub shards (doesn't own `nodes`' data), so this doesn't reopen §7a's single-process constraint at all.
