# Handoff — Morellia project status (2026-08-06, reverted 2026-08-25, nodes/vanilla ported 2026-08-25/26, monorepo migration 2026-08-26)

Deep background (library internals, design rationale) is in `RESEARCH.md`, `NODES_DEEP_DIVE.md`,
`VANILLA_DEEP_DIVE.md`, `COMBAT_DEEP_DIVE.md`, and `research-todo/*.md` — not repeated here. This
doc is: what's actually live in production right now, what changed most recently, and the
credentials/IDs needed to keep going without re-discovering them.

Supersedes the old 2026-07-31 version of this file and folds in `WARFLAG_HANDOFF.md` (deleted —
that entire thread, including the flat two-territory dev fixture it was written around, is now
fully resolved and superseded by the real territory data described below).

## Status update (2026-08-25): replanning, real map + custom models abandoned

Everything below the theme section describing real Anvil/procedural terrain and deployed
1911-borders territory data is **no longer accurate** — it's kept for history, marked accordingly.
Current state:

- **Real terrain is gone.** Both terrain sources tried (`AgadirWorld.kt`'s `AnvilLoader` over a
  trimmed Anvil download, `EuropeTerrain.kt`'s procedural NOAA/WWF-heightmap generator) are deleted,
  along with the ~1.5GB `morellia-data/world/` region files and the `europe/heightmap.bin`/
  `biome.bin` resources. The server runs on plain flat stone superflat (`StoneFlatTerrain`) as the
  deliberate baseline while terrain gets replanned. See `research-todo/04-world-and-data-architecture.md`.
- **The real-geodata border/territory pipeline never touched this repo's local data anyway** — it
  only ever wrote to the production server's own `nodes` JSON files over SSH. Local
  `morellia-data/nodes/` still holds (and keeps) the original small 2-town flat-world test fixture.
- **The custom Blockbench musket model is abandoned.** `models/flintlock_musket/` is being replaced
  by sourcing public/CC0 models for a full asset overhaul (weapons, vehicles, buildings, uniforms).
  It was never actually wired into the game either way (`TestWeapons.kt` always fell back to base
  `Material`).
- **`ResourcePack.kt` no longer points at the production VM** — moved to a localhost URL; the
  resource pack itself will be rebuilt once sourced assets exist.
- **Guardrail for this phase: no deploys to the Oracle VM (0.0.0.0).** Everything is
  local-only until the replan is far enough along to redeploy. GitHub pushes are unaffected.
- The 1911 Agadir Crisis **theme/design intent itself is not abandoned** — the theme section right
  below still describes the target setting. What's gone is the concrete real-world terrain and
  hand-typed/geodata border implementation, plus the from-scratch modeling workflow.

## Status update (2026-08-25/26): nodes/vanilla ported and published, NOT yet in Morellia

Separate from the terrain/model revert above. Context: `Aechronis/nodes`/`Aechronis/vanilla`/
`Aechronis/combat`/`Aechronis/utils` — the standalone repos `DCFiendish/nodes`/`DCFiendish/vanilla`
forked from, and what Morellia's `server/build.gradle.kts` actually pins — were abandoned by
upstream on 2026-08-02 in favor of developing directly in a new monorepo, `Aechronis/aechronis`.
3.5 weeks of real fixes piled up there with no path back to the split repos. This pass ported the
data-integrity/correctness subset into the forks (not the two large net-new features found along
the way — a warzone system and a trains/building system — those are a deliberate follow-up, not
done yet), plus merged the user's own previously-stuck waypoint/Xaero-integration work.

**Landed, merged to master, published for real** (confirmed via each repo's own CI going green,
not just a local build):
- `DCFiendish/vanilla` master → `96b593f`. New `BlockPlacementCooldownListener`: real server-side
  enforcement of a block-placement cooldown on foreign claims (the first version, still in nodes,
  only sent a cosmetic client packet that did nothing to actually stop placement). Gated behind
  `VanillaConfig.blockPlacementCooldownEnabled` (default true).
- `DCFiendish/nodes` master → `6f1f9dd`. Includes:
  - The waypoint/Xaero-client-integration work (shared-waypoint creation broadcasts, a
    `/waypoint nativedisplay` opt-out toggle, `/waypoint list`) — was sitting on an unmerged branch
    in this fork and as a dead PR against the abandoned `Aechronis/nodes`, now just in master.
  - New `AtomicFiles`/`SerialSaveQueue` + a rewritten `Nodes.saveWorld()`: fixes a real
    check-then-act race on `needsSave` (previously read/reset with **no lock at all**), adds a save
    revision counter so an in-flight save can represent newer state without blocking every caller,
    and restores `needsSave` on failure instead of silently dropping the pending change.
  - Income-inventory GUI reworked from one-way push to bidirectional diffing
    (`IncomeInventory.synchronizeFromInventory`/`snapshot`) — fixes income sitting in an open GUI
    getting lost or serialized stale if a player disconnects mid-session.
  - The block-placement-cooldown fix above (nodes side calls into vanilla's real enforcement).
  - Several smaller fixes: block-type equality bug that broke interact/protect checks on
    property-varying blocks (e.g. a rotated chest), plot-corner selection not registering in
    creative/instant-break mode.
  - **`nodes` now depends on `vanilla` for the first time** — previously independent sibling
    libraries. New `implementation("net.aechronis:vanilla:96b593f")` +
    a new `maven.pkg.github.com/DCFiendish/vanilla` repository block in `nodes/build.gradle.kts`
    (didn't exist before, nodes never needed it).

**Skipped, with reasons** (don't re-attempt without a reason to revisit):
- The AI-towns/colonization feature — dropped per explicit decision, not wanted.
- `cdc232d`/`c3e14d9` ("town flight fix") — the fork already has its own independently-built
  `TownFlyCommand`; upstream's rework is a different design, not worth swapping in.
- `00230ce` (a territory-load-truncation fix) — already independently fixed in the fork
  (`714b19a`), same bug, different description.
- Gem-transaction and minimap-integration-command features, and a separate custom-shader-based
  minimap rendering feature — all net-new, all deferred.

**Done as of 2026-08-26** (items 1-2 from the original "not done yet" list here — superseded by the
monorepo migration below, which changes what "bump the pin" even means going forward):
1. Morellia's pins were bumped to `net.aechronis:nodes:6f1f9dd`/`net.aechronis:vanilla:96b593f` and
   the local boot check re-run and confirmed clean (Minestom started, Vanilla modules loaded, the
   local 2-town test fixture loaded, income ticked). Moot now — the monorepo migration below
   replaced both pins with `project(...)` dependencies, so there's no SHA to track anymore.
2. `C:\Users\USER\.gradle\gradle.properties` was empty (0 bytes) at the start of this session,
   blocking local resolution of anything not already cached (`net.aechronis:vanilla:96b593f` failed
   with "Username must not be null!"). The user populated `gpr.user`/`gpr.token` themselves (Claude
   does not enter credentials into files, even when given the value directly — see feedback memory).
   It's still needed for `net.aechronis:utils` and (until replaced) `net.aechronis:combat`, both
   still consumed from GitHub Packages.

**Still genuinely open:**
- **The deferred follow-up pass**: the warzone system (layers on top of the exact `FlagWar`/
  `Attack.kt` code `LoadTestBots.kt` depends on — needs real review before touching, not a
  drop-in), the trains/building system, and the smaller remaining Tier-3 items (admin
  bypass/nda-style commands, tier income display, haste/mining boosts, relationship-color
  hitboxes) from `modules/nodes`'/`modules/vanilla`'s post-2026-08-02 history in the
  `Aechronis/aechronis` monorepo.
- `Aechronis/aechronis`'s `modules/utils` post-2026-08-02 history was never audited — only
  `nodes`/`vanilla` were. Worth checking before assuming `utils` has nothing worth porting too.

## Status update (2026-08-26): monorepo migration — nodes/vanilla folded into this repo

Separate decision from the port above: `DCFiendish/nodes`/`DCFiendish/vanilla` (the fork repos)
have been folded directly into `DCFiendish/Morellia` as real subprojects, mirroring what
`Aechronis/aechronis` itself did. Motivation: every dependency edge between `server`/`nodes`/
`vanilla` used to require push-to-fork → wait for that fork's own CI to publish to GitHub
Packages → bump a commit-SHA pin → rebuild — exactly the friction hit firsthand doing the
nodes/vanilla pin bump above. A plain Gradle project dependency removes all of that for code this
project actually owns.

- **New layout**: repo root is now the Gradle root project (`settings.gradle.kts`,
  `build.gradle.kts`, the Gradle wrapper all moved here from `server/`). `server` is a subproject;
  `modules/nodes` and `modules/vanilla` are new subprojects holding each fork's **full commit
  history**, imported via `git subtree add --prefix=modules/<name> <fork-url> master` (not
  squashed — `git log`/`git blame` still resolve through the import).
- **Dependency wiring**: `server/build.gradle.kts` now depends on `project(":modules:nodes")` and
  `project(":modules:vanilla")` instead of `net.aechronis:nodes:<sha>`/`net.aechronis:vanilla:<sha>`;
  `modules/nodes` depends on `project(":modules:vanilla")` the same way. Each module's own
  `maven-publish` config, `DCFiendish/*` GitHub Packages repo blocks, and per-module Gradle wrapper/
  `settings.gradle.kts` were removed as dead weight. `net.aechronis:utils` and (still, for now)
  `net.aechronis:combat` remain external GitHub Packages dependencies — deliberately not folded in,
  since `utils` is Aechronis's shared foundation, not something being diverged from.
- **Verified end-to-end**: `./gradlew projects` shows the correct tree; `./gradlew compileKotlin`
  builds all three in dependency order (vanilla → nodes → server); `./gradlew test` passes both
  forks' existing suites; booted the real server and confirmed via its live classpath that it's
  running `modules/nodes/build/libs/nodes-local.jar`/`modules/vanilla/build/libs/vanilla-local.jar`,
  not the old published jars.
- **Housekeeping caught along the way**: root `.gitignore` had no `build/`/`.gradle/` coverage
  (only each module's own nested `.gitignore` did) — added `/build/`, `/.gradle/`, `/.kotlin/`,
  `/modules/build/` before this could accidentally get committed.
- **Not yet decommissioned**: `DCFiendish/nodes`/`DCFiendish/vanilla` still exist as live repos on
  GitHub — plan is to archive (not delete) them once this migration itself is confirmed solid, but
  that's a separate, explicit-confirmation step, not done as part of this pass.
- **Not yet updated**: `morellia-ops`'s `SKILL.md` deploy playbook still describes the old
  push-fork → wait-for-CI → bump-pin flow for nodes/vanilla — needs rewriting to match (the
  `Aechronis/combat`/`utils` external-dependency steps are unaffected).

## Status update (2026-08-26): `combat` has the same stale-fork problem, decision pending

While starting on gun/melee/vehicle work, checked `Aechronis/combat`'s current state (the repo
Morellia's `net.aechronis:combat:2c63782` pin points to) — same situation nodes/vanilla were in
before their port: master has been stale since 2026-08-01 (`542e3c2`), while **40+ real commits**
have landed in the `Aechronis/aechronis` monorepo's `modules/combat` since then (most recently
`556fd05`, 2026-08-24), including two fixes with real security relevance (`COMBAT_DEEP_DIVE.md`'s
H8 cross-instance vehicle entry — genuinely fixed via a new per-instance `VehicleCollisionIndex` —
and H5's F-key-swap cooldown bypass, fixed for guns specifically). Full comparison against every
`COMBAT_DEEP_DIVE.md` finding was done directly against the monorepo source; not repeated here.

**Decision, not yet executed**: rather than forking `Aechronis/combat` the way `nodes`/`vanilla`
were forked, the plan is to build a **from-scratch `modules/combat`** inside the new monorepo,
modeled on Aechronis's `Gun`/`Melee`/`Vehicle` API shape but independently implemented — motivated
both by `Aechronis/combat` being AGPL-3.0 licensed (same as `vanilla`/`utils`, which Morellia
already depends on, but not something to extend further by choice) and by the chance to design
around the known CRITICAL/HIGH bugs from the start rather than inheriting and re-fixing them.
Scope agreed so far: start with just guns and melee (era-appropriate for Agadir Crisis
bolt-actions/machine guns), not the full vehicle/drone/plane/tank/boat suite. **Nothing has been
built yet** — this is the actual next step once the monorepo migration above is committed.

## Theme: the Agadir Crisis (1911), alternate history — locked in

The real 1911 Agadir Crisis (a diplomatic/gunboat standoff over Morocco, resolved historically
without war) is reimagined here as escalating into real fighting. Ten nations: **Germany, France,
United Kingdom, Spain, Italy, Morocco, Switzerland, Netherlands, Belgium, Portugal.** Weapons era
is bolt-action rifles, early Maxim-type machine guns, horse-drawn field artillery — `Aechronis/combat`'s
`Gun`/`Melee`/`Vehicle` data classes cover this directly (see `RESEARCH.md` §2), no new combat code
needed for the era fit.

## World: real trimmed terrain, live in production (historical — abandoned, see status update above)

The server no longer runs on a flat stone test world. A real Minecraft Anvil world (sourced from a
"Rise of Rome" terrain download covering Europe, trimmed to a box spanning Britain through Morocco
— roughly `x: -8192..2559, z: -5632..3071` in block coordinates) is loaded via `AnvilLoader` in
[server/src/main/kotlin/net/morellia/server/AgadirWorld.kt](server/src/main/kotlin/net/morellia/server/AgadirWorld.kt),
wired into `Main.kt`. Anything outside the trimmed box falls through to `StoneFlatTerrain`'s
generator (flat stone), so the world never has unrendered holes.

Spawn is `(-3000.5, 70.0, -1500.5)` (central France, verified on solid ground — the earlier flat-world
test coordinate was over water in real terrain and was replaced).

**Why Anvil directly, not Polar** (updates the recommendation in
`minestom-server-setup/04-world-generation-and-persistence.md`): the original research recommended
converting to Polar for production. In practice the trimmed box was loaded straight via `AnvilLoader`
with no conversion step — simpler pipeline (no `AnvilPolar` conversion needed), and the trimmed
world's size (~1.5GB) hasn't shown a load-time problem worth the extra conversion step. Revisit only
if Anvil's slower load time becomes a measured problem.

## Borders: 1911 territories drawn and deployed (rebuilt 2026-08-06, v2) (historical — abandoned, see status update above)

All ten nations have real in-game territory. This is the second full pipeline — the first version
(grid-cell tiling on hand-typed border polygons) shipped, then got replaced same-day after feedback
that territories looked like a repeating grid/diamond lattice, had a coastal chunk misclassified as
"land" that sealed off the whole North Sea/Channel from ocean-flood-fill (so a ~7,300-chunk sea pocket
got claimed as territory), and had nation borders that were only roughly historically accurate.
Current pipeline (all scratchpad tooling, not committed):

1. **Borders**: real geodata, not hand-typed polygons. Natural Earth 1:50m admin-0 country boundaries,
   clipped to the trimmed box's lat/lon extent (33–58.5°N, -10–14.5°E) and simplified to ~0.008°
   (below chunk resolution). Alsace-Lorraine carved from France into Germany using the real Bas-Rhin/
   Haut-Rhin/Moselle French department boundaries (`gregoiredavid/france-geojson`) as the standard
   modern proxy for the 1871–1918 annexed territory. UK = Great Britain + Northern Ireland + all of
   Ireland (Channel Islands/Isle of Man correctly excluded, they were never part of the UK). ~3,800
   polygon vertices total vs. ~150 in the original hand-typed version.
2. **Land/water classification**: every chunk in the trimmed box sampled for land vs. water (181,960
   land, 155,961 water). Enclosed water reachable from the box's outer edge is real ocean; anything
   else is flood-filled and folded into land *unless* the enclosed pocket exceeds ~500 chunks (real
   lakes here top out around 100–180, so anything bigger is a sea/strait/bay that got falsely dammed
   by a single mis-sampled coastal chunk, not an actual lake) — that's the North Sea fix.
3. **Country assignment**: point-in-polygon against the real borders, same as before.
4. **Tiling**: geodesic (graph-distance, not straight-line) multi-source Dijkstra partition over the
   chunk adjacency graph, seeded densely (~48-chunk initial cells) then merged up to the 75–115 target
   using a merge-candidate scorer that directly optimizes shape (inverse of `n/(π·max_reach²)` for
   spikes, `1 − area/convex_hull_area` for bends/crescents) — and refuses to force a merge that would
   produce a bad shape, leaving a piece undersized instead. Straight Euclidean-distance Voronoi was
   tried first and rejected: it ignores real land connectivity, so a seed near a winding coast can
   "claim" a strand of chunks that's only close by straight line, producing long coastal tendrils.
   Validated against `Aechronis/nodes-map`'s own committed real-server `world.json`: same-size-range
   solidity (area vs. convex hull) is 0.845 median / 0.576 worst-case here, vs. 0.834 median / 0.164
   worst-case there — on par with or better than actual production territory shapes.
5. **Output**: 1,564 territories, generated into `nodes` `world.json`/`towns.json` (one town+nation
   per country, capital-nearest home territory, verified on-land spawn per nation) the same way as
   before.

Deployed via the usual stop→swap→start sequence (see gotchas below); pre-deploy `.bak` copies exist
both on the Pterodactyl volume and in `/opt/nodes-map/nodes/` as
`{world,towns}.json.pre-compact-real-borders.bak`, alongside the original
`{world,towns}.json.pre-agadir-borders-backup` from the very first (flat-world) territory rollout.

**Known limitation, accepted not fixed**: Portugal's territory count is low (~234 chunks) because its
real westernmost coast sits right at the trimmed box's already-confirmed western edge — genuine
geography, not a bug. Re-trimming the world to include more of Portugal would be a large, disruptive,
unrequested change; not doing it unless asked.

**Open, not yet decided**: all ten nations currently start neutral — no alliance/enemy relationships
are pre-set. Morocco's "maybe subordinate to France" idea (RESEARCH.md §2) is still just a flagged
design question, not resolved — `nodes`' data model has no vassal/parent-nation concept, only
ally/enemy/neutral between equal `Nation`s.

## nodes-map: live territory viewer (historical — describes the production VM only, untouched but not part of current local-only work; see status update above)

`DCFiendish/nodes-map` (fork of `Aechronis/nodes-map`) is built and deployed to the production VM at
`http://0.0.0.0:8888`, served via a systemd unit (`nodes-map.service`, `python3 -m http.server
8888` from `/opt/nodes-map`). `js/app.js`'s `PAN_BOUNDS` was updated to the real trimmed-box extent.
Firewalled open at both the Oracle NSG layer and the VM's own iptables (`netfilter-persistent`
persisted) — both layers required, opening one alone doesn't make the port reachable. It currently
shows territory-color overlays only, no base terrain tile imagery (that would need a rendered webp
tile pyramid — not built, flagged as a future nice-to-have, not requested).

## Access / credentials (unchanged from before)

- **SSH to the Oracle box**: `ssh -i C:\Users\USER\.ssh\id_ed25519 ubuntu@0.0.0.0`
- **This is the user's own Oracle VM** ([HOSTING-BUSINESS-NAME] business), shared by multiple of the user's
  own projects — `REDACTED-PROJECT-2`, `morellia` (this project), `REDACTED-PROJECT` — plus at least one other
  tenant's service the user has hosted as a favor ("REDACTED-THIRDPARTY-SERVICE" on port 8090, not part of this
  project, never modified — only ever viewed read-only to identify what was already running on the
  shared box before picking nodes-map's own port). **Do not touch anything on this box beyond
  Morellia's own container/files without explicit confirmation** — it is genuinely multi-tenant.
- **GitHub**: `gh` CLI already authenticated as `DCFiendish`. Repos:
  - `DCFiendish/nodes` — fork of `Aechronis/nodes`; as of 2026-08-26 its history lives in
    `modules/nodes` here too (subtree-imported), and this is now the stale copy — see the monorepo
    migration status update. Still live on GitHub, not yet archived.
  - `DCFiendish/vanilla` — fork of `Aechronis/vanilla`; same situation as `DCFiendish/nodes` above,
    now `modules/vanilla` here.
  - `DCFiendish/nodes-map` — fork of `Aechronis/nodes-map`, new this session
  - `DCFiendish/rust-mc-bot` — fork of `Eoghanmc22/rust-mc-bot`
  - `Aechronis/utils`, `Aechronis/combat` — used directly, not forked, no local changes
- **GitHub Packages token**: `C:\Users\USER\.gradle\gradle.properties` (`gpr.user`/`gpr.token`) —
  was empty at the start of the 2026-08-25/26 session (blocked local resolution of anything not
  already cached), populated by the user since. Only still needed for `net.aechronis:utils` and
  `net.aechronis:combat` now that `nodes`/`vanilla` are in-tree `project(...)` dependencies (see
  the 2026-08-26 monorepo migration status update).
- **This repo (`C:\Users\USER\Aechronis`) is a real git repo now** (was flagged as a gap in the old
  version of this doc — resolved, it's tracked and pushed).

## Server identifiers

- Pterodactyl server UUID: `00000000-0000-0000-0000-000000000000`
- Volume path: `/var/lib/pterodactyl/volumes/00000000-0000-0000-0000-000000000000` → `/home/container`
  in the container
- Docker container ID changes across restarts — always re-fetch via `sudo docker ps`, never reuse
  one from a prior session; verify by volume UUID, not by assuming the first `docker ps` row is
  Morellia (this box runs multiple containers)
- Port: 25567 (tcp + udp), offline-mode auth (`Auth.Offline()`)
- `server.jar` inside the volume is owned by uid/gid `998:998`

## Gotchas worth remembering (also in `.claude/skills/morellia-ops/SKILL.md`)

- **Never `docker restart` right after hand-editing `nodes`' own JSON save files**
  (`world.json`/`towns.json`/`war.json`) — the old process's shutdown hook silently re-saves its
  stale in-memory state over whatever you just deployed. Use `stop` (confirm it actually exited),
  deploy, then `start`.
- **Oracle NSG rules AND the VM's own iptables INPUT chain both gate inbound traffic
  independently** — opening a port needs a rule at both layers, confirmed the hard way while
  standing up nodes-map's port 8888.
- **`oci network security-list update --ingress-security-rules` replaces the whole rule list**, not
  an incremental add — always fetch-and-append the full existing rule set before submitting.

## What's genuinely still open (not urgent, not touched recently)

- **New, 2026-08-25/26, actually next up**: Morellia's own `server/build.gradle.kts` pins are stale
  against what's now on `nodes`/`vanilla` master (`40b2270`/`a074e09` vs. the current `6f1f9dd`/
  `96b593f`) — see the nodes/vanilla status update above for the exact bump + follow-up work.
- **`combat`'s CRITICAL/HIGH bugs are still live** (async unsynchronized explosion terrain mutation,
  reload ammo-theft dupe, raycast wall-passthrough, no melee reach check, explosion ignoring
  line-of-sight — see `COMBAT_DEEP_DIVE.md`). Unlike `nodes`/`vanilla` (both forked, and both had
  essentially every CRITICAL/HIGH finding from their deep-dives fixed by 2026-08-05 — see each fork's
  README), `combat` is consumed directly from upstream `Aechronis/combat`, unforked, per project
  convention — upstream's recent commits are vehicle-mechanics fixes, not these. This is the single
  biggest concrete correctness gap left before real PvP/vehicle playtesting; needs either forking
  `combat` too (a policy change) or these fixed upstream. See
  `research-todo/01-concurrency-model.md` (resolved 2026-08-06) for the thread-safety model these
  bugs were triaged against.
- Alliance/enemy relationships between the 10 nations (currently all neutral) — moot until the
  terrain/border replan lands real nation territory again.
- **New, 2026-08-25**: real terrain needs replanning from scratch (both prior attempts abandoned —
  see status update above). No source/approach chosen yet.
- **New, 2026-08-25**: asset sourcing for weapons/vehicles/buildings/uniforms — see
  `research-todo/10-asset-sourcing-and-licensing.md` once written; no licensing policy existed
  before this.
- `LoadTestBots.kt` / `rust-mc-bot`'s hardcoded territory IDs (440/275) are correct again now that
  real territories are gone — they were always local-fixture IDs, never the production real-map ones.
- Portugal's clipped territory — moot, the map it applied to no longer exists.
- A real webp tile-imagery base layer for `nodes-map` — moot for now (local-only, no real terrain).
- Morocco's nation-hierarchy question — moot until nations exist again.
- `War-Comms` GitHub repo (empty, 0 bytes) — `gh` lacked the `delete_repo` scope to remove it via
  API; user would need `gh auth refresh -h github.com -s delete_repo` or delete it manually via the
  GitHub UI. Unconfirmed whether this was ever done — check before assuming either way.
- Task #13 from an earlier session's list: replace the nodes-map loading-screen logo with Morellia
  branding — blocked on the user producing artwork.
