# Handoff — Morellia project status (2026-08-06, reverted 2026-08-25, nodes/vanilla ported 2026-08-25/26, monorepo migration + combat built 2026-08-26, combat hardened + spark added 2026-09-02)

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
- **Guardrail for this phase: no deploys to the Oracle VM** (see `.claude/skills/morellia-ops/SKILL.md`, gitignored, for connection details). Everything is
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
2. The user's global `~/.gradle/gradle.properties` was empty (0 bytes) at the start of this session,
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
bolt-actions/machine guns), not the full vehicle/drone/plane/tank/boat suite.

## Status update (2026-08-26): `modules/combat` built, guns/melee working end-to-end (no real assets yet)

Full plan lives at `.claude/plans/lets-plan-out-guns-merry-twilight.md` (guns, melee, vehicles,
resource pack pipeline). **Steps 1-4 of that plan are done and boot-tested; steps 5-8 (vehicles,
asset pipeline) are not started.** None of this is committed to git yet — it's sitting in the
working tree (`modules/combat/`, plus edits to `FlagWar.kt`, `TestWeapons.kt`, `Main.kt`,
`ResourcePack.kt`, and new `DevLoadout.kt`/`TestMeleeTarget.kt` in `server/`).

- **`modules/combat` is a from-scratch module**, package `net.morellia.combat` (own namespace, not
  AGPL-bound the way the forked `nodes`/`vanilla`/`utils` are) — `net.aechronis:combat` is now fully
  dropped, including its GitHub Packages repo block in the root `build.gradle.kts`.
- **Every named CRITICAL/HIGH bug from `COMBAT_DEEP_DIVE.md` that's in scope so far was designed out
  from the start**, not retrofitted: C2 (reload ammo-theft dupe — reload task captures+re-checks the
  stack's UUID tag every tick), C3 (raycast wall-passthrough — `Ray.firstBlock` fails *closed* on an
  unloaded chunk or unrecognized collision shape), C4 (no melee reach check — `MeleeListener` enforces
  `Melee.maxReach` server-side), H1 (thread safety — every per-player state map in `Combat.kt` is
  `ConcurrentHashMap` from the first line), H5 (cooldown-reset swap bypass — `PlayerSwapItemEvent`
  cancelled outright while holding any combat item).
- **Real deviation from the written plan** (the plan named `PlayerStartSneakingEvent`/dig-based
  firing — neither exists/works as assumed on this Minestom version, confirmed against the actual
  pinned jar): ADS uses `PlayerInputEvent.hasPressedShiftKey()`/`hasReleasedShiftKey()`; firing uses
  `PlayerHandAnimationEvent` (universal on every left-click, unlike dig events which need a targeted
  block within short range — a dealbreaker for a 128-block hitscan gun) with a timeout heuristic for
  the automatic/semi-auto distinction (`FireListener.kt`'s kdoc has the full reasoning).
- **Test content** (`TestWeapons.kt`, base `Material` rendering only — no real item models/textures
  yet): musket + musket ball (unrestricted), bayonet, and a zone-restricted field gun (only fires in
  wilderness or an actively-sieged chunk, via a `Gun.usableZones` predicate built from real
  `Territory`/`TerritoryChunk` data — required one new public accessor on `nodes`,
  `FlagWar.isEnabled`, previously `internal`).
- **Found and fixed a real pre-existing bug while boot-testing**: `ResourcePack.kt`'s
  `computeHashAndBuild().join()` threw uncaught when nothing served `localhost:8000/resourcepack.zip`
  (no pack has been built yet), which silently skipped everything after it in `main()` —
  `TickMonitor.init()`, `LoadTestBots.init()` (so the two test towns/nations never got created),
  `Nodes.enableWar()` — even though the Minestom tick loop itself kept running, so this was invisible
  unless you specifically checked for the towns/war state. Now just logs and continues.
- **New dev-only scaffolding for solo testing** (same category as `LoadTestBots.kt` — remove once
  real players take over): `DevLoadout.kt` gives every real (non-bot) player a full musket/bayonet/
  field-gun loadout on every spawn/respawn; `TestMeleeTarget.kt` spawns a stationary 500-HP zombie
  near spawn (no AI attached, so it just stands still) that respawns itself 3s after dying.
- **Live-tested for real, not just compiled**: a separate bare Fabric dev-client project (outside
  this repo — see the `morellia-testclient` reference memory for exact setup/gotchas) connected to
  the local server and loaded into the world with the loadout/dummy present. Fire/reload/melee/ADS
  themselves haven't been played through by a human yet — that's the actual next verification step,
  not asset work.
- **Genuinely next**: sourcing/building real assets (model + texture + sound) for the musket
  specifically first, per `research-todo/10-asset-sourcing-and-licensing.md`'s policy/format and
  sourcing order — that's asset-creation work, not code, and hasn't started. Vehicles (plan steps
  5-8) come after.

## Status update (2026-08-26): musket has a real model, resource pack pipeline stood up end-to-end

- **`resourcepack/` now exists** (repo root, not yet a `.gitignore`'d build output — it's the
  source tree, distinct from the *built* `server/resourcepack.zip`). Musket's model/texture are
  real: the Mosin Nagant from `memava`'s MIT-licensed "WWI & WWII rifles" pack
  (https://modrinth.com/resourcepack/rifles), extracted out of its original crossbow-rename-predicate
  wrapper. Source/license recorded in `resourcepack/CREDITS.md` — keep adding an entry there per
  asset per the sourcing doc's own policy.
- **Corrected a wrong assumption in `research-todo/10-asset-sourcing-and-licensing.md`** (that
  doc's "target format" section implied `item_model` points straight at a raw model). Confirmed
  against the actual Minecraft wiki: **`item_model`'s string value is an item-definition id**
  (`assets/<namespace>/items/<id>.json`, the `{"model": {"type": "model", "model": "<raw model
  path>"}}` wrapper format), not a raw-model path directly. Got this wrong on the first pass here
  too — wired `Gun.itemModel` straight at the raw model path, item rendered as Minecraft's generic
  purple/black missing-model placeholder, fixed by adding the `items/<id>.json` wrapper and
  pointing `itemModel` at its id instead. Apply this to every future `itemModel`/`itemModelEmpty`/
  `itemModelReloading`/`itemModelAiming` value — each needs its own `items/<id>.json` wrapper.
- **Build step**: `resourcepack/` (containing `pack.mcmeta` + `assets/`) gets jarred into
  `server/resourcepack.zip` via `jar cf` — not PowerShell's `Compress-Archive`, which writes
  backslash path separators into zip entries that Minecraft's loader can't read. `pack.mcmeta` uses
  `{"pack": {"min_format": 69, "max_format": 99, ...}}`, matching what the source pack itself
  shipped for 26.2 compatibility.
- **Serving it locally**: JDK 25 ships `jwebserver` (`jwebserver -p 8000 -b 127.0.0.1`, run from
  `server/`) — no Python needed. This process (like the Morellia server itself) has died
  unexpectedly mid-session more than once with no visible cause; if `ResourcePack.kt` logs
  "Couldn't reach http://localhost:8000/resourcepack.zip", check `jwebserver` is still alive before
  assuming anything else is wrong.
- **A stale `./gradlew.bat run` process silently held port 25567 across a `[killed]` log line at
  least once** — the log entry doesn't necessarily mean the OS process actually exited. If a fresh
  `run` fails with `BindException: Address already in use`, find and kill the real PID
  (`netstat -ano | findstr :25567`) before retrying, don't assume the port is free just because the
  backgrounded task reported as stopped.
- **Still open, exactly where the user left off**: sounds (no `.ogg`s exist anywhere yet, this pack
  had none), a visual reload indicator beyond the existing durability-bar ammo counter (already
  works via `Gun.setAmmo`), and ADS visual feedback (currently server-side spread/recoil only — no
  zoom/crosshair/held-pose change). None of these are scoped yet — first thing to nail down in
  whatever session picks this up next.

## Status update (2026-08-26): more weapon assets sourced; ADS "peering down the barrel" pose is BROKEN, root cause not found

New real models/textures added to `resourcepack/`, all from the same memava "WWI & WWII rifles"
pack (MIT), credited in `resourcepack/CREDITS.md`: **Springfield**, **Karabiner** (real rifle
models), and three melee knives (**US Trench Knife**, **Nahkampfmesser**, **Couteau Poignard**) —
the knives are all placeholder substitutions using the pack's bayonet models (no real trench-knife
assets exist in the source pack), flagged in CREDITS.md to replace later. All wired into
`TestWeapons.kt`/`DevLoadout.kt` and confirmed rendering correctly in the dev test client (see
`morellia-testclient` reference memory).

**ADS work, done and working:**
- `Gun.adsVignette` now defaults to `false` (was `true`) — the full-screen pumpkin-vignette
  tunnel-vision trick is a sniper-scope effect, not appropriate for iron-sight aiming on every gun.
  Matches how `Aechronis/combat`'s own `ModelManager.kt` gates it behind a `gun.sniper` flag we
  don't have (and don't need) yet. Confirmed working in-game: aiming no longer blacks out
  peripheral vision, only the existing movement-speed-based FOV zoom applies.
- The aim/crouch toggle, FOV zoom, and the underlying `itemModelAiming` swap mechanism itself are
  all confirmed working correctly server-side (see bug section below for exactly how this was
  verified).

**ADS work, BROKEN — this is where the user stopped for the day:**
Goal: aiming a gun should visually reposition the held model to look like peering down the
sights (centered under the crosshair, muzzle just below it), the way `Aechronis/aechronis`'s
`ak47-aiming.json` does it for their AK47 (repositioned `firstperson_righthand`/
`firstperson_lefthand` display transform, swapped in via `itemModelAiming`). Built
`musket-aiming.json`/`springfield-aiming.json`/`karabiner-aiming.json` (clones of the base model
with only the `display` block + a tiny geometry nudge changed) plus matching `items/*-aiming.json`
wrappers, wired via `itemModelAiming` in `TestWeapons.kt`. **The result never visually changes
in-game** — aiming the musket looks pixel-identical to the normal hip-fire hold, no matter what the
`display.firstperson_righthand` translation/rotation/scale values are set to.

Debugged extensively, ruled out the obvious causes:
- **Server-side swap is confirmed correct.** Added a temp `println` in `Gun.refreshModel` (still in
  the code, marked `TEMP DEBUG` — remove once this is fixed) that logs the exact model string being
  applied. Confirmed via live server log: `aiming=true currentComponent=morellia:musket
  targetModel=morellia:musket-aiming` fires correctly every time the player aims, and reverts
  correctly on release.
- **The resource pack served over HTTP genuinely contains the new transform values** — downloaded
  the live `resourcepack.zip` from `localhost:8000` directly and inspected the bytes inside
  `musket-aiming.json`, confirmed they matched what was authored (not a stale/cached copy).
- **The item-model swap mechanism itself works fine for a genuinely different target.** Two direct
  tests, both confirmed visually by the user: temporarily setting the musket's `itemModelAiming` to
  `minecraft:diamond_sword` correctly turned it into a diamond sword on aim; setting it to
  `morellia:us_trench_knife` (an existing, already-working custom model) correctly turned it into
  the knife. So swapping to *any other* model — vanilla or custom — renders correctly and
  immediately, no reconnect needed.
- **Only `musket-aiming.json` specifically — a near-byte-identical clone of `musket.json`'s own
  `elements`/`textures`, differing only in `display` and one coordinate nudged by 0.0001 — fails to
  render any differently from the base `musket.json`.** This is the actual unresolved mystery.

**Leading theory, not yet confirmed or disproven**: the client may be deduping/caching baked item
meshes by geometry content, so two item ids pointing at near-identical `elements` arrays collapse
onto the same baked model (including its baked-in display transform) despite having different item
ids and different `display` blocks in their source JSON. The 0.0001 nudge to one vertex was an
attempt to break this and did not help, but that may simply not have been a large-enough content
change to defeat whatever comparison the client is doing (rounding/quantization is plausible).
**Next things to try, in order**:
1. Make the aiming model's `elements` *substantially* different from the base (not a tiny nudge) —
   e.g. reuse the trench-knife or another already-working custom model's actual geometry, or hand-
   author a simple distinguishable placeholder shape — to conclusively confirm/deny the dedup-cache
   theory.
2. If that fixes it, the real fix is understanding exactly what content the client hashes/compares
   for this dedup (texture reference? element count? something else?) so the real aiming poses can
   be built without accidentally tripping it — likely means the aiming variant's geometry needs to
   diverge more than "same mesh, moved display transform" for this Minecraft build.
3. If a substantially different `elements` array *still* doesn't render differently, the dedup
   theory is wrong and this needs a fresh angle — worth re-checking whether this is a known bug/
   quirk specific to Minecraft 26.2's very recent, still-unofficial model pipeline (see the
   `morellia-testclient` reference memory — this version already has other known-unusual behavior:
   no official/Yarn mappings, `quickPlayMultiplayer` host/port parsing bug, etc.).
4. Also worth checking Aechronis's actual `ak47`/`ak47-aiming` pair for whether their two files'
   `elements` arrays are meaningfully geometrically different from each other (not just their
   `display` blocks) — if their working example also happens to diverge in geometry, not just
   display, that would support the dedup theory directly.

Other resourcepack-side findings from this investigation, still true regardless of the bug above:
- This mesh (extracted from a crossbow-predicate wrapper, not purpose-built) is unusually long
  along its local Z axis — pulling its `display` translation closer to camera has an outsized
  effect on apparent size (learned by way of massively oversizing it on the first two attempts).
  Any future aiming-pose tuning on these rifle models should expect that sensitivity.
- The base (non-aiming) `firstperson_righthand` pose's small residual rotation (a few degrees on
  x/y/z, e.g. musket's `[3.83,-5.03,3.44]`) does NOT explain the diagonal "held to the side" look on
  its own — zeroing it out produced no visible change either (before the dedup issue was even
  suspected), so that specific fix attempt is a dead end, not something to re-try.

## Status update (2026-08-27): ADS render bug root-caused and fixed, all three rifles centered

The "aiming pose never visually changes" bug from the entry above is **resolved**. The
mesh-dedup-by-geometry theory was wrong — root cause and fix:

- **Checked `Aechronis/aechronis`'s real, working `ak47`/`ak47-aiming` pair directly** (same
  `net.minestom:minestom:2026.07.12-26.2` pin Morellia uses). Their two models' `elements` arrays
  are **byte-identical** — only `display` differs. So geometry divergence was never required, and a
  same-tick diagnostic swap to `us_trench_knife`'s geometry (which *did* render, just tiny/wrong-
  scaled) was a red herring, not confirmation of the dedup theory.
- **The real difference**: Aechronis's `ModelManager.updateModel` (`modules/combat/.../tasks/
  ModelManager.kt`) reassigns the held item's model **unconditionally, every tick**, for every
  online player — not once on a state-transition edge the way Morellia's `AimingListener` did. A
  one-shot set on the aim-press edge was getting silently lost/stale client-side; continuous
  resend is what makes it actually stick.
- **Fix**: [Gun.kt](../modules/combat/src/main/kotlin/net/morellia/combat/objects/Gun.kt)'s
  `refreshModel` now writes the model component unconditionally (dropped the "only if changed"
  guard and the temp debug println from the entry above). New
  [ModelRefreshTask.kt](../modules/combat/src/main/kotlin/net/morellia/combat/tasks/ModelRefreshTask.kt)
  is a 1-tick repeating task (mirrors the existing `ActionBarManager` pattern) that calls
  `refreshModel` for every online player holding a `Gun`, wired into `Combat.initialize()`. This
  is gun-agnostic — it fixes ADS rendering for every current and future `Gun`, not just the musket.
- **Confirmed working in-game** by the user after the fix, then the `firstperson_righthand`/
  `firstperson_lefthand` `translation` in `musket-aiming.json` was hand-tuned live (restart →
  reconnect → eyeball → repeat) until centered: `x` went from the previous session's guess of `0`
  through `-6`, `-9`, `-7.5`, `-7.8`, `-8.2`, landing on **`-8.05`** (confirmed centered).
  `firstperson_lefthand.x` was kept at `firstperson_righthand.x - 1.5` throughout, matching the
  original file's own established delta — lefthand isn't the primary rendered view for a
  right-handed player so it was never independently tuned. **Musket ADS is fully done.**
- **Ported the same centering ratio to `springfield-aiming.json`/`karabiner-aiming.json`** as a
  starting estimate (`aim_x ≈ hip_x × -0.976`, i.e. musket's hip `firstperson_righthand.x` of `8.25`
  landing on aim `-8.05`, applied to each rifle's own hip `x`) — see the next status update for what
  happened when these were actually checked in-game.
- `resourcepack.zip` rebuilt (`jar cf`, from `resourcepack/`) and the local dev server restarted
  after every content change — necessary because `ResourcePack.kt` hashes the zip once at boot;
  editing the zip without restarting leaves the server serving a stale hash and the client never
  redownloads.

## Status update (2026-08-27, continued): Springfield/Karabiner in progress, Blockbench MCP notes

Direct continuation of the entry above, same day.

- **Springfield's estimated `-6.59` was wrong** — checked in-game, still visibly right of center.
  Rather than continue the restart-loop, **the user hand-tuned `springfield-aiming.json` directly in
  Blockbench's own UI** (not via MCP — see below) and saved it. Current on-disk value:
  `firstperson_righthand.translation` **`[-8.09, 3, -6]`**, `firstperson_lefthand.translation`
  **`[-8.09, 4.5, -3]`** (both hands the same `x` this time, not the `-1.5` delta convention used for
  the other two rifles — that's an intentional live edit, not a mistake, leave as-is). **Not yet
  confirmed centered in-game after this edit** — the resource pack was rebuilt and the server
  restarted to serve it (so it's ready to test), but no in-game check happened before this was
  written down. Check this first next session.
- **Karabiner is untouched since the initial estimate** (`-7.56`, from the same ratio that turned
  out wrong for Springfield) — genuinely unconfirmed, hasn't been looked at in-game at all yet.
- **Blockbench MCP plugin correction**: the earlier claim above ("MCP connections are established at
  session start, opening Blockbench mid-session doesn't help") turned out to be **incomplete, not
  fully wrong**. Mid-session, the user's `blockbench-mcp-plugin` (jasonjgardner, v1.6.1, installed in
  Blockbench via Plugins → Load from URL →
  `https://jasonjgardner.github.io/blockbench-mcp-plugin/mcp.js`, config: port `3000`, endpoint
  `/bb-mcp`, matches `http://localhost:3000/bb-mcp` in `.claude.json`) had its tool schemas briefly
  become visible to this session's `ToolSearch` (~90 tools: `place_cube`, `capture_screenshot`,
  `modify_cube`, etc.) — the same pattern as `docker`/`minestom` connecting mid-session earlier that
  day, proving mid-session MCP attach genuinely can happen. But every actual tool call
  (`get_project_info`) still failed with `"MCP server \"blockbench\" is not connected"`, even after
  confirming via `curl localhost:3000/bb-mcp` that the plugin's HTTP endpoint is alive and reachable.
  So the server side is fine; this specific session's MCP client never completed a real handshake,
  for reasons not root-caused (a stale schema cache from an earlier connection attempt is one
  plausible explanation, not confirmed). **Unresolved**: whether a fresh session reliably picks this
  up, or whether `/mcp` (an interactive terminal command Claude can't invoke itself, but the user
  can) would reconnect it mid-session without a full restart — neither was actually tried by end of
  session. Try `/mcp` first next time before assuming a restart is required.
- **If/when Blockbench MCP does connect**: it exposes real render feedback (`capture_screenshot`,
  `capture_app_screenshot`), not just blind coordinate edits — worth using it for any future
  display-transform tuning or model editing instead of the restart-and-eyeball loop this session
  relied on for the musket (six rounds of guess-and-check to converge on `-8.05`, and the same
  ratio-based estimate still ended up wrong for Springfield). Discussed but not attempted: whether
  Claude would be better at *editing* an already-decent existing model (small, bounded value/vertex
  nudges) than the from-scratch cube-by-cube model creation the user tried earlier and found
  "incredibly terrible" — the real determining factor either way is whether the tool call loop
  actually includes a screenshot/render Claude can see, not create-vs-edit per se.
- **Local dev environment, still running as of end of session** (all separate OS processes, survive
  regardless of Claude Code session state): the Morellia server (`:server:run`, restarted several
  times this session, currently serving the resource pack with Springfield's Blockbench-edited
  value), `jwebserver -p 8000` from `server/` serving `resourcepack.zip`, and a Fabric dev client
  (`morellia-testclient`, username `devtest`) connected to `localhost:25567`. Blockbench itself is
  also open, with its MCP plugin enabled and listening on port 3000.

## Status update (2026-08-29): WW1 weapon asset-sourcing research, no model work done yet

Pure research session, separate machine/chat from the Blockbench editing above — no in-game
testing happened. Also swept up and committed two pre-existing uncommitted changes found sitting
in the working tree from the 2026-08-27 Blockbench session (see the entry directly below this
one for what those actually are).

**Goal**: find higher-quality WW1 rifle models than the memava MIT pack currently in
`resourcepack/` (musket/Springfield/Karabiner) — user explicitly said memava's models "weren't
good enough."

**License landscape mapped, most options ruled out**:
- ZachoPixel's "World War I Texture Pack" (CurseForge/Modrinth) and `Fields of 1918` (90+ weapons,
  Battlefield-1-inspired, highest visual quality found) — both **ARR**, no stated server-use
  permission. Excluded.
- TACZ WW1/Great War gunpacks — high quality but **wrong format even if licensing were fine**:
  TACZ renders through its own mesh/GeckoLib pipeline requiring the TACZ Forge/Fabric mod
  client-side. Morellia serves plain vanilla protocol (Minestom, no mod loader), so players
  couldn't load these regardless of license.
- `github.com/Ligua999/Minecraft-Open-Weapons` (billed as free CC0 weapon models) — checked
  directly via GitHub API, repo contains **only a LICENSE file, no actual models**. Dead end.
- `ModularWarfare` (GitHub, open source) — also a Forge-mod-dependent format, same client-loader
  problem as TACZ.

**Confirmed usable (free + explicit permissive license + no mod dependency)**:
- [WWI & WWII Rifles](https://modrinth.com/resourcepack/rifles) (Modrinth, MIT) — 22 rifles
  including Kar98k/Mosin Nagant/Springfield 1903/Gewehr 98/two Lee-Enfields, vanilla item-model
  format. **User already rejected this on quality before this session started.**

**New direction found — real mesh geometry via `obj³` (objcubed), not cuboid tracing**:
[github.com/JagerMeistars/obj-cubed](https://github.com/JagerMeistars/obj-cubed), MIT licensed,
forked from Godlander's original `objmc`. Bakes actual OBJ/glTF mesh geometry (not Blockbench
cuboids) into a PNG texture; bundled core shaders decode it client-side at render time. **No mod
required — pure resource pack**, which is why it's viable for Morellia specifically. Requires
Blockbench 4.8.0+ desktop (Node.js, for its custom PNG encoder) and is GUI-only — confirmed via
its `package.json` that there is no standalone CLI, so this cannot be automated headlessly; it
needs a human driving the Blockbench UI. Shaders are version-tuned for MC 26.1.2–26.2, which
matches Morellia's actual target (see the pack.mcmeta note above) — not a blocker after all.

**Mesh source picked**: [Low-Poly Kar98K by TastyTony](https://sketchfab.com/3d-models/low-poly-kar98k-d0ffca9b52864541ae5adbafb8d14064)
— **CC-BY 4.0** (attribution required, commercial/redistribution use allowed), 3.5k triangles/1.9k
vertices, well under obj³'s ~50K-face-per-chunk-section crash threshold. Two backup candidates if
the Kar98K doesn't work out, both also confirmed-licensed: [Lee-Enfield MKIII](https://sketchfab.com/3d-models/lee-enfield-mkiii-f6a2160daf134071a84e3cae5f831875)
(CC-BY 4.0) and [Springfield M1903](https://sketchfab.com/3d-models/springfield-m1903-rifle-224bd15dc9c544afa78af6c3a46fe22f)
(**CC-BY-SA 4.0** — note share-alike: that specific baked asset should stay redistributable under
the same terms if ever shared standalone, unlike the rest of this all-rights-reserved repo).

**Actual progress made — genuinely minimal**: Sketchfab doesn't offer a raw `.obj` download for
the Kar98K (only `.blend`/`.usdz`/`.gltf`/`.glb`); settled on downloading the **`.glb`** and using
Blockbench's official "glTF Importer" marketplace plugin (imports as a Generic Model, meshes/
textures/groups supported, armatures not — irrelevant for a static rifle). **User has only gotten
as far as exporting/downloading the `.glb` from Sketchfab.** Nothing has been imported into
Blockbench yet, the `objcubed.js` plugin hasn't been loaded, and no obj³ export has been attempted.

**Exact next steps for whoever picks this up**:
1. Load `objcubed.js` into Blockbench (File → Plugins → Load Plugin from File) — download from
   `raw.githubusercontent.com/JagerMeistars/obj-cubed/main/objcubed.js`.
2. Install the "glTF Importer" plugin from Blockbench's own marketplace.
3. New project → Generic Model → File → Import → Import glTF Model → the downloaded Kar98K `.glb`.
4. Position per obj³'s conventions: grid floor (y=0) = block bottom, model centered on origin in
   X/Z, export from the Edit tab (not Display/Animate).
5. File → Export → Export as obj³. Set a custom-model-data name matching the existing
   springfield/karabiner naming convention (e.g. `karabiner`, since this is meant to replace/
   upgrade the placeholder Karabiner rather than the already-real Springfield). **Export to a
   scratch folder first**, not directly into `resourcepack/` — it writes/modifies
   `assets/objcubed/`, `assets/minecraft/items/*.json`, `assets/minecraft/atlases/blocks.json`, and
   `assets/minecraft/shaders/core/*`, and that diff needs review against the existing pack
   structure (particularly whether it clobbers the existing item-override entries documented in
   the 2026-08-26 "musket has a real model" status update above) before merging for real.
6. Test standalone in the `morellia-testclient` dev client before merging into `resourcepack/`.
7. Add a `resourcepack/CREDITS.md` entry for the Kar98K (TastyTony, CC-BY 4.0) per the existing
   per-asset credit policy — same pattern as the existing memava MIT-pack entries.

## Status update (2026-08-27, uncommitted work found and committed 2026-08-29): Springfield widened, two blank Blockbench scaffolds

Found sitting uncommitted in the working tree at the start of the 2026-08-29 research session
above — from the 2026-08-27 Blockbench session, never committed at the time. Committed as-is,
undated content preserved faithfully:

- **`springfield.json` elements widened**: every element's X-axis span roughly doubled outward
  from center (e.g. the barrel's `from`/`to` X went `[6.6, 7.3]` → `[5.83, 8.07]`), plus a
  `"format_version": "1.21.11"` field added at the top level (this is Blockbench's own internal
  schema-version stamp, unrelated to the actual Minecraft version — see the note earlier in this
  doc about pack.mcmeta's real 26.2 target). **Not verified in-game** — unknown whether this was a
  deliberate width-correction or an accidental side effect of some other Blockbench operation.
  Check this visually in the dev client before assuming it's correct.
- **`springfield-clean.bbmodel` and `springfield-final.bbmodel` are both empty scaffold saves**
  (`"elements":[]`, `"groups":[]`, `"textures":[]`, 420 bytes each) — not real model work, just
  blank Blockbench "New Project" saves that got written to disk. Safe to ignore or delete; keeping
  them for now since deleting someone else's file without being asked isn't this session's call.

## Status update (2026-08-29): real-terrain map generation restarted, scripted WorldPainter pipeline

Direct continuation of the "real terrain needs replanning from scratch" item flagged in the
2026-08-25 status update above. Full details, exact commands, and the complete open-items list
live in **`tools/agadir-mapgen/README.md`** — this entry is a summary, not a repeat.

- **New approach: WorldPainter's `wpscript` scripting host**, not a hand-rolled generator (both
  prior attempts, above, were hand-rolled and both failed/were abandoned) and not the GUI
  either — every step is a committed, reproducible script. Real elevation source is SRTM15+ (a
  single-request global DEM, ~450m/px, near-exact match for the approved 1:750 scale), covering
  the same trimmed box as the original abandoned attempt (Britain through Morocco).
- **Real, non-hypothetical bug found and fixed twice over**: WorldPainter's 26.1 export writes
  the invalid legacy block name `minecraft:grass` (real Minecraft renamed it to
  `minecraft:short_grass` around 1.20.5; Minestom 26.2's registry has no entry for the old name)
  for its own default grass-vegetation feature, on *every* export. This is almost certainly what
  actually killed the original `AgadirWorld.kt` attempt too, not a stale source-map theory.
  `tools/agadir-mapgen/patch_grass_names.py` fixes it post-export; must be re-run after every
  regeneration.
- **`modules/nodes/.../OreSampler.kt` and `OreDeposit.kt` fixed** for real modern world height
  (-64..320, matching Minestom's `DimensionType.OVERWORLD` — the user explicitly wants real
  deepslate-to-surface layering, not a simplified legacy 0-255 range). Was hardcoded to legacy
  0-255, a flat array indexed directly by `y` with no offset math, silently broken
  (index-out-of-bounds or dead below y=0) against real modern height. Fixed and confirmed
  compiling clean.
- **`AgadirWorld.kt` recreated** pointing at `morellia-data/world` (gitignored, ~230MB, not
  committed — regenerate via `tools/agadir-mapgen/` if missing). `Main.kt`'s spawn point is the
  real box's true center (confirmed by scanning exported `.mca` chunk headers directly — the
  box is **not** centered on the origin), not (0,0).
- **Verified working end-to-end**: booted locally, connected via the `morellia-testclient`
  Fabric dev client, confirmed real varying elevation (not the flat-stone fallback) at multiple
  points, confirmed clean boot with zero "Unknown block" errors after the grass-name patch.
- **User feedback mid-session, not yet acted on**: raw/unsmoothed terrain looked rough (fixed —
  Gaussian smoothing added to the heightmap conversion step); trees/ground/foliage all still
  look bad as of this commit — root-caused to `TreeLayer`'s density being a NIBBLE (0-15) value,
  not binary, and a fix (`agadir-import-v2.js`) is written but **not yet run/verified**. The
  user also wants a scripted way to place the big custom TreeForge tree schematics already
  downloaded (`tools/agadir-mapgen/treeforge-trees/`, 16 `.schem` files) instead of relying on
  WorldPainter's plain built-in tree exporter — not yet implemented, real open question about
  whether `wpscript`'s API supports painting custom objects the way it paints built-in layers.
- **Explicitly still open, see the README for the full prioritized list**: run+verify
  `agadir-import-v2.js`; a real custom-tree painting script; latitude-only biome zoning can't
  distinguish Morocco from Southern Spain (needs real country-boundary geodata); rivers
  (deferred from the start); border/territory painting is a fully separate, unstarted task with
  its own already-documented schema (see the "Borders" section below — historical/abandoned,
  but the `nodes` `world.json`/`towns.json` schema itself is unchanged and still accurate).
- **Unresolved user question**: whether continuing to have Claude script every visual-polish
  decision (texture, tree density, biome zoning) via blind elevation-threshold heuristics is
  actually the right approach, versus using WorldPainter's GUI directly now that real data is
  correctly imported at the right scale/position/version. User was pointed at this tradeoff
  explicitly; hasn't chosen a direction as of this commit.

## Status update (2026-08-30): custom-object-layer scripting confirmed working, community layers installed

Direct continuation of the WorldPainter pipeline work above, same day. Resolves the biggest open
unknown from the 2026-08-29 entry: **`wpscript` can load, paint, and export real custom-object
(Bo2) layers, not just WorldPainter's built-in procedural Deciduous/Pine.** Verified empirically,
not just from docs — `wp.getLayer().fromFile('x.layer').go()` loaded a real `Bo2Layer` instance,
`wp.applyLayer(...).toLevel(n).applyToSurface().setAlways().go()` painted it onto a throwaway test
world, and `wp.exportWorld(...)` baked real blocks (`jungle_log`/`oak_leaves` confirmed via
`anvil-parser2` scanning the output `.mca`) into the export — not a GUI-preview-only effect. Full
details in `tools/agadir-mapgen/README.md`'s new "Custom object layers — RESOLVED" section.

- **Sourced three real Bo2Layer files** from Lerfing's free WorldPainter tutorial content
  (Patreon post, not TreeForge): palm, taiga, and generic-swamp layers, now committed at
  `tools/agadir-mapgen/community-layers/`. License terms for these specific files aren't confirmed
  yet (Patreon itself is unreachable from this session's tooling — blocked/403) — treat as
  provisional until an explicit statement is found, unlike the memava/TastyTony assets which do
  have one.
- **Also installed Lerfing's Custom Brushes pack** (47 terrain-shaping greyscale brushes) into
  WorldPainter's real config-folder convention, `%APPDATA%\WorldPainter\brushes\Custom Brushes\`
  — confirmed via direct `WPGUI.jar`/`Configuration.class` bytecode inspection that this is
  actually where WorldPainter looks (Windows: `%APPDATA%\WorldPainter`; this differs by OS). Purely
  a GUI convenience for whoever picks up manual terrain painting — `wpscript` doesn't need this.
- **The TreeForge `.schem` files are still not wired into anything** — those are raw Sponge
  Schematics, not pre-built `.layer` files like Lerfing's, so the remaining open question is
  whether `.schem` → Bo2 object-collection conversion itself can be scripted or needs one manual
  GUI pass through WorldPainter's "Custom Objects" import wizard per species. See the README's
  open-items list, item 1.
- **Still not run**: `agadir-import-v2.js` itself (tree density/texture/latitude fixes) — the
  source SRTM15+ GeoTIFF and its derived heightmap/latitude/noise PNGs aren't on disk anymore
  (checked; not in `tools/agadir-mapgen/`, not in Downloads), so re-running it needs a fresh
  OpenTopography download first (needs the user's own free API key, portal.opentopography.org —
  not something Claude can do standalone). Wiring the new palm/taiga/swamp layers into the actual
  biome-zoning logic is a separate follow-up after that, still just a design decision now, not a
  technical blocker.

## Status update (2026-08-30, continued): `.schem` → `.layer` conversion also solved, no GUI needed

Same day, direct continuation. The remaining open question above — whether the TreeForge `.schem`
files specifically could become real paintable layers, or whether that conversion needed a manual
WorldPainter GUI pass — is now **also resolved, and also fully scriptable**, not just the
already-built Lerfing layers. `tools/agadir-mapgen/MakeLayer.java` (run via the new
`make-layer.sh`) converts any set of `.schem` files straight into a real `.layer` file using
WorldPainter's own jars as a library — no GUI involved at any point. Converted and verified both
`treeforge-oak.layer` and `treeforge-pine.layer` this way (confirmed real `oak_wood`/`oak_leaves`
blocks in a test export, same verification method as the Lerfing layer test above).

- **Real technical gotcha for whoever extends this**: WorldPainter has two different classes for
  reading tree/object schematics — the legacy `org.pepsoft.worldpainter.layers.bo2.Schematic`
  (old pre-1.13 MCEdit `.schematic` format, throws on a real modern `.schem`) and the modern
  `org.pepsoft.worldpainter.layers.bo2.Schem` (Sponge Schematic, what TreeForge and basically
  everything modern actually exports) — easy to grab the wrong one by name alone.
  `Schem.load(File)` handles decompression and returns a ready `WPObject`.
  `new Bo2ObjectTube(name, objects)` is the weighted-random multi-object container (confirmed via
  decompiling a real Lerfing `.layer` file that voxel data is embedded inline in these, not
  referenced by external path — `.layer` files are fully self-contained). Wrap in
  `new Bo2Layer(tube, name, color)`, serialize with `ObjectOutputStream`+`GZIPOutputStream` — that
  pairing is the entire `.layer` file format.
- **User feedback mid-session**: Lerfing's free layers are good quality but only cover 3
  biomes/features (palm/taiga/swamp) — confirmed directly, not just inferred. With this converter
  now working, filling the rest is unblocked from two directions: more TreeForge species (only
  Oak/Pine done; Ash/Birch/Cherry Grove/Palm/Weeping Willow/Spiral Spruce still available from the
  same already-vetted source), or
  [sijmenvb/worldpainter-trees](https://github.com/sijmenvb/worldpainter-trees) (MIT licensed, 16
  layers/11 biomes as ready `.schem` sets — badlands, jungle, savanna, roofed forest, swamp, spruce,
  rocks, etc. — cloned to a scratchpad for inspection this session, not yet copied into the repo).
  Full details and the exact open sub-items in `tools/agadir-mapgen/README.md`.

## Status update (2026-08-30): Kar98k glTF import — found and fixed a real Blockbench importer bug

Separate thread from the mapgen work above: continuing the obj³ weapon-asset pipeline from the
2026-08-29 research entry (asset-sourcing research, no model work done yet). Picked up where that
left off — the user had downloaded TastyTony's "Low-Poly Kar98K" `.glb` (Sketchfab, CC-BY 4.0) and
gotten as far as having it sitting in Downloads, nothing imported yet.

- **Blockbench now has a native glTF importer** (`import_gltf` action, "Import glTF" dialog with a
  Scale/Import Groups/Import Animations form) — the marketplace "glTF Importer" plugin the
  2026-08-29 research assumed was required is no longer necessary on this Blockbench version.
- **Found a real bug in that native importer, not a bad source file**: some nodes whose transform is
  encoded as a raw 4x4 `matrix` (vs. separate translation/rotation/scale fields) get their scale
  wildly mis-extracted on import — a barrel/rod piece came in ~11.5x too large, a small stud came in
  ~48x too large, while plain TRS-transformed nodes imported correctly. Symptom in-viewport: a few
  parts look like giant slabs or disconnected floating pieces while the rest of the model looks
  fine — easy to mistake for a broken/low-quality source file (this file's own Sketchfab listing has
  no description or comments about geometry issues, and it isn't an armature/skinning problem
  either — confirmed zero skins/joints in the file). Full root-cause writeup, the diagnostic method,
  and a working from-scratch reimport script (bypasses Blockbench's buggy conversion entirely by
  driving Three.js's own `GLTFLoader` directly via `risky_eval`) are in
  [`docs/blockbench-reference/gltf_import_scale_bug.md`](blockbench-reference/gltf_import_scale_bug.md) —
  **check that doc before importing any of this creator's other rifle models** (TastyTony has
  several more in the same "Low-Poly Rifles" series, likely exported the same way from the same
  Blender setup, so expect the same bug).
- Also confirmed (same doc, "Gotcha 3"): this model's colors are stored via the deprecated
  `KHR_materials_pbrSpecularGlossiness` extension, which Blockbench doesn't read at all, so nothing
  comes in textured/colored — unrelated to the scale bug, just means every import from this creator
  will need its color baked in separately from the raw `diffuseFactor` values.
- **Current state**: `resourcepack/assets/morellia/models/item/kar98k-import.bbmodel` has the
  corrected geometry (all 47 parts rebuilt with correct world-space positions, rescaled to match
  `springfield.json`'s existing unit convention — real Kar98k and Springfield 1903 are almost
  exactly the same real-world length, so the same target scale applies) and is now colored: baked
  the six `KHR_materials_pbrSpecularGlossiness` `diffuseFactor` values (linear RGB, gamma-corrected
  to sRGB) into a small palette texture, mapped each of the 47 meshes to its original material via
  `gltf.meshes[n].primitives[0].material`, and pointed every face's UV at a flat swatch (no real UV
  unwrap needed since every source material is a flat color, not an image). Reads as a correctly
  wood-stocked, dark-metal Kar98k now, matching the reference render's color separation. **Not yet
  exported through the obj³ pipeline, not yet wired into `resourcepack/`** — this is still a working
  `.bbmodel` file, not a finished in-game asset. That obj³ export (per the 2026-08-29 entry's
  "Exact next steps") is the next real step.

## Status update (2026-08-30, continued): obj³ export working end-to-end in the real client, hand pose still being tuned

Same day, direct continuation. The Kar98K now renders correctly — right shape, right colors —
when held in-game via the real obj³ export, verified in the actual Minecraft client (not just
Blockbench's preview). This is the first weapon in this project actually pushed through the full
obj³ pipeline rather than a traditional item model, so most of what follows is now captured as a
reusable process, not just a one-off fix:

- **Found the correct plugin/shader version pairing**: obj³ plugin `0.7.0` matches shader tag
  `26.2` of `JagerMeistars/obj-cubed` — pulling shaders from `main` instead silently drifts toward
  a newer Minecraft build's shader format and crashes the client on connect
  (`ShaderManager$1.applyImport` NPE, "Required resource pack was not loaded"). Also re-confirmed
  the plugin-filename-must-match-its-internal-`id` loading quirk from earlier obj³ research.
- **Full replication steps are now written up** in
  [`docs/blockbench-reference/obj3_weapon_import_playbook.md`](blockbench-reference/obj3_weapon_import_playbook.md)
  — covers import → bug fixes → obj³ export → pack merge/deploy → hand-pose solving, so the next
  rifle (another TastyTony model, or a different creator entirely) doesn't have to re-discover any
  of this.
- **Third-person hand pose — real progress, not yet fully done.** Root-caused *why* Blockbench's
  own Display-mode gizmo (the obvious built-in tool for this) couldn't be trusted here: this
  project's unofficial Minecraft build (26.2) has a real hand-bone position mismatch against
  Blockbench's bundled preview rig (confirmed via a literal "relocated entity geometry" comment in
  the 26.2-tagged shader source) — so the preview's rotation is trustworthy but its absolute
  translation is not. Solved the translation properly via matrix math instead of guessing
  per-axis (full method in the playbook doc, §5): picked a grip point `G` in local space, solved
  `T = -R·G` for the chosen rotation, and combined with an empirically-needed +~20-unit world-Y
  correction for the preview/real-client mismatch. Current values:
  `rotation: [60, 90, 0]`, `translation: [0, 25, -0.53]` — this gets the gun correctly oriented
  (pointing forward, right height, no longer floating at floor level or off to the side) but the
  **grip point itself needs one more refinement pass**: latest in-game check shows the pose is
  lined up correctly but the player's hand isn't quite at the trigger/wrist-of-stock yet, meaning
  the estimated `G = (2.4, 2, 0)` was a bit off. Next step per the playbook's §5 step 5: refine `G`
  from the model's actual stock/wrist bounding box and re-solve, rather than nudging translation
  by feel.
- **GUI/hotbar icon still shows blank** — known, deferred limitation, not a bug in this export
  specifically: the vanilla item selector's `gui` display_context isn't an explicit case, so it
  falls through to the shader-dependent `default` model, which doesn't render outside the
  entity/item-in-hand rendering path. Documented as a real follow-up in the playbook (§4) — needs
  an explicit `"when": "gui"` case pointing at a conventional flat icon, not the baked geometry.
- **Left in place for the next session**: `DevLoadout.kt`'s `TEMP` block (slot 22, an `iron_ingot`
  carrying the test `custom_model_data`) — intentionally not removed yet since the pose isn't
  confirmed final. Remove it and wire the model into `TestWeapons.kt` (matching the
  `springfield`/`karabiner` pattern) once the grip point is confirmed correct.
- **Not yet done**: `resourcepack/CREDITS.md` entry for the Kar98K model (TastyTony, CC-BY 4.0),
  and the real merge of the obj³ export into `resourcepack/assets/` itself (right now only the
  built `server/resourcepack.zip` has the merged output — the scratch export directories from this
  session were not copied into the tracked `resourcepack/` source tree).

**Exact next step for a fresh session**: re-estimate the grip point `G` (likely needs to move
further into +Z or adjust X — look at where the stock's wrist sits in the `.bbmodel` relative to
its own origin), re-solve `T = -R·G` with `R` for rotation `[60, 90, 0]`, re-export, rebuild
`resourcepack.zip`, restart the local server, and check in-client whether the player's hand now
sits on the trigger. The math/tooling is all in place — this is calibration, not new engineering.

## Status update (2026-08-30, continued): first-person + left-hand poses fixed, real root cause found

Direct continuation, same day. Blockbench MCP connected cleanly this session (unlike the prior
"installed but never handshakes" entry above) with the Kar98K project live — used it to diagnose
and fix a real bug, not just push numbers around blind.

- **User had hand-tuned third-person AND first-person right-hand poses directly in Blockbench**
  (`thirdperson_righthand`: rotation `[86,90,0]`, translation `[0.5,6.5,3.97]` — superseding the
  matrix-derived `[60,90,0]`/`[0,25,-0.53]` from the entry above; `firstperson_righthand`: rotation
  `[0,86,0]`, translation `[0,4.25,0]`) but first-person never showed up in-game.
- **Root-caused for real, not guessed**: this was never a Blockbench-preview-vs-real-client
  mismatch (unlike the earlier +20-unit-Y quirk, which genuinely is one). Unzipped the actual
  shipped `server/resourcepack.zip` and found `firstperson_righthand`/`thirdperson_lefthand`/
  `firstperson_lefthand` baked in as all-zero in every exported model file, despite
  `firstperson_righthand` being correctly non-zero in the live Blockbench project. Traced to the
  obj³ export dialog itself: its Vue component holds **separate per-context fields**
  (`dThird*`/`dLeft*`/`dFpr*`/`dFpl*`) that do **not** auto-sync from `Project.display_settings` —
  a `_loadFromDisplaySettings()` method exists to pull all of them in one shot, but the playbook's
  previously-documented export snippet only ever synced `dThird*` by hand, so first-person and both
  left-hand contexts silently exported as zero every time, every session, for every gun so far.
  This is a **systemic bug in the documented process itself**, not a one-off mistake on this model.
- **Fixed and verified for the Kar98K specifically**: computed the left-hand mirror of both tuned
  right-hand poses (Minecraft flips the model across local X for off-hand rendering, so the correct
  mirror is: negate X-translation, keep X-rotation, negate Y/Z-rotation — derived from first
  principles, not copied from an existing asset, since this project doesn't have a prior symmetric
  obj³ example to crib from). Wrote all four contexts into `Project.display_settings` via MCP,
  called `_loadFromDisplaySettings()` + `doExport()`, and confirmed the new export produces separate
  baked model files (`kar98k_lowpoly_{thirdperson,firstperson}_{right,left}hand.json`) each with the
  correct non-zero `display` entry, and that the item selector JSON now has explicit
  `display_context` cases for all four (previously only `thirdperson_righthand`/`ground`/`on_shelf`
  had cases; everything else silently fell through to the untransformed `default` model — this is
  what "doesn't load in that spot" actually looked like in-game, not an error or missing render).
- **Deployed to the local dev server**: merged the new export into a full pack copy (existing
  shaders/atlases/items layered with the new `assets/objcubed/*` + `iron_ingot.json`), rebuilt
  `resourcepack.zip` via `jar cf`, killed and restarted the `:server:run` process (confirmed clean
  boot, zero errors), confirmed `jwebserver` is serving the new 499KB zip. **Also merged the export
  for real into the tracked `resourcepack/assets/` source tree this time** (`objcubed/`, the shader
  base pack, `minecraft/items/iron_ingot.json`, `minecraft/atlases/blocks.json`) — closes the "not
  yet done" gap flagged in the entry above. Saved the live Blockbench project back over
  `kar98k-import.bbmodel` too (its `save_path` was empty — the open project was a fresh
  in-memory session, not bound to the tracked file — so this would otherwise have been lost on
  close).
- **Playbook doc rewritten** ([obj3_weapon_import_playbook.md](blockbench-reference/obj3_weapon_import_playbook.md)
  §4) with the corrected export procedure (`_loadFromDisplaySettings()`, not manual per-field
  sync) and the mirror-math formula, so the next weapon doesn't reproduce this bug.
- **Not yet done / genuinely next**: none of the new poses have been eyeballed in the real client
  yet this session — right-hand third/first-person should look like what the user already tuned,
  but that still needs an in-game glance since the export pipeline itself was the thing under
  suspicion; left-hand (both third and first person) needs the player's Main Hand setting switched
  to Left in-game to check at all, and the mirror formula is principled but unverified — first time
  this project has authored a left-hand transform for anything via obj³. Grip-point-only refinement
  (moving `G` to land the hand exactly on the trigger, the task this session started as) hasn't
  been resumed yet — right/first-person values changed since that math was last solved, so revisit
  once the current export is confirmed visually correct.
- `resourcepack/CREDITS.md` entry for the Kar98K and `DevLoadout.kt`'s `TEMP` block removal are
  both still outstanding, unchanged from the entry above — still gated on the pose being fully
  confirmed, not just exported correctly.

## Status update (2026-08-31): real root cause of "too low" found and fixed — pose confirmed close in-game

Direct continuation. The third-person pose from the entry above ("way too low, not out in front
tho") turned out to have a precise, provable cause, found by reading the obj-cubed **encoder**
source (`objcubed.js`), not just the shader — this is the actual resolution the whole
recentering/grip-point effort above was building toward.

- **Verified the grip-point recentering was geometrically sound first**: dropped a temporary marker
  cube at local `(0,0,0)` in the live `kar98k-import.bbmodel` project and screenshotted an
  orthographic side view — the marker sits exactly in the grip pocket at the trigger, confirming the
  earlier `(7,-3.5,0)` vertex shift found the right point. This ruled out "bad grip point" as the
  cause of the residual Y error before looking anywhere else.
- **Root cause, found in `objcubed.js` directly**: the encoder unconditionally bakes `Y - 0.5`
  (one full half-block) into every exported vertex position, because it assumes the model is built
  "on the floor" (local Y=0 = block bottom) like a normal vanilla JSON model — see its own comment
  at the position-baking step ("VERTICAL ORIGIN CONVENTION... bake Y - 0.5 here... a floor-built
  model rides half a block high everywhere" without it). Recentering the grip to local Y=0 (instead
  of the model's floor) broke that assumption: the encoder kept subtracting the half-block from the
  *grip* instead of the floor, producing a constant half-block-low error baked directly into the
  texture — independent of the display-rotation math, and affecting **every context that reads the
  same PNG at once** (third-person, first-person, ground, GUI), which is exactly why first-person
  also looked wrong even though its own `display` JSON values were never touched.
- **Fix**: the obj³ export dialog has a `Model Offset Y` field (`dlg.content_vue.offsetY`) meant
  for exactly this. Setting it to `0.5` before export cancels the encoder's built-in `-0.5`
  precisely, so the recentered grip point decodes to true zero — matching what the
  `T = 16·(I−R)·(0.5,0.5,0.5)` swing-cancellation formula already assumed. No change to the
  rotation/translation values themselves was needed, only this one export-dialog field.
- **Re-exported, merged, redeployed**: confirmed `_loadFromDisplaySettings()` still carried the
  already-correct `thirdperson_{right,left}hand` (`[75,90,0]` / `[0,-1.798,2.343]`) and
  `firstperson_{right,left}hand` (`[0,90,0]` / `[0,5,0]`) values through unchanged, exported with
  `offsetY=0.5`, merged into `resourcepack/assets/`, rebuilt `resourcepack.zip`, restarted the
  dev server (a stale detached-launch process from an earlier point in the session had to be killed
  first — `netstat`/`Get-Process` by port, not assumed), and relaunched the test client as
  `devtest2`.
  - **Tooling note**: launching either the server or the `morellia-testclient` gradle wrapper via
    the Bash tool's `cmd //c` wrapper silently fails ("gradlew.bat not recognized") whenever the
    working directory contains a space (`Minecraft Dev\morellia-testclient` specifically) — `cd`/
    `dir`/`where` all work fine through the same wrapper, only the actual batch-file execution
    breaks, root cause not fully identified. The PowerShell tool's `Start-Process` does not have
    this problem; use it for anything launched from a path with a space.
- **User-confirmed in the real client**: "it actually looks really close" — third-person right-hand
  pose is now correctly positioned and oriented with no further hand-tuning beyond the formula
  above. This is the first time this pipeline has produced a correct pose from math alone, with no
  empirical fudge factor layered on top.
- **Generalized into the playbook**:
  [`obj3_weapon_import_playbook.md`](blockbench-reference/obj3_weapon_import_playbook.md) was
  rewritten to present this as the standard process for *any* mesh import (not Kar98K-specific):
  §3.5 recentering technique (with the marker-cube verification step), §4's `offsetY=0.5` rule, and
  §5's closed-form swing-cancellation formula superseding the old "+20 units empirically" fudge
  (which was masking these two real bugs, not compensating for a genuine preview/client mismatch —
  that theory is now retired). Also added a pointer from
  [`.claude/skills/blockbench-modeling/SKILL.md`](../.claude/skills/blockbench-modeling/SKILL.md)
  (force-added despite the repo's blanket `.claude/` gitignore, at the user's explicit request) so
  a future session reaches for the right pipeline instead of the cube-placement one.
- **Not yet done**: left-hand (third and first person) still hasn't been visually confirmed in-game
  — only right-hand has been checked so far, same gap noted in the entry above. GUI/inventory
  rendering for obj³ items is still unhandled (falls through to the untransformed `default` model,
  per the known limitation in playbook §4). `resourcepack/CREDITS.md` entry for the Kar98K and
  `DevLoadout.kt`'s `TEMP` block removal remain outstanding, same as every entry above — still
  gated on the full pose (including left-hand) being confirmed.

## Status update (2026-09-01): combat feel fixes, sprint-based spread, and the first obj³ ADS pose (Kar98k) confirmed in-game

Direct continuation of `modules/combat` polish, plus the first real ADS pose for an obj³-pipeline
gun (the entry above only covered the hip-fire pose).

**Combat bug fixes** (all in `modules/combat`):
- `AimingListener`: switching to a *different* Gun mid-aim (no shift press/release edge fires on a
  hotbar swap) used to leave the old gun's `adsZoomStrength`/`adsVignette` stuck on. Split
  `startAiming` into a reusable `applyAimEffects(player, gun)` also called from
  `onHeldSlotChange` when the new held item is still a Gun.
- `Gun`'s `init` block now validates `recoilMax >= recoilMin`, `spreadMin >= 0`,
  `spreadMax >= spreadMin`, `sprintSpreadMultiplier >= 1` — previously a bad config (e.g. max <
  min) silently produced broken recoil/spread with no error.
- Pulling the trigger on an empty magazine with no reserve ammo used to be completely silent (no
  sound, no feedback at all) — added `Gun.soundEmpty` (defaults to vanilla
  `minecraft:block.lever.click`), played on that path.
- Friendly fire was already handled — `NodesPlayerDamageListener` hooks `EntityDamageEvent`
  globally at high priority and checks attacker/victim relationship; `Gun.fire`'s
  `Damage.fromProjectile` goes through that same event. No combat-module change needed.

**Sprint-based spread**: `MovementListener` now also tracks `Combat.sprintingPlayers` via
`PlayerInputEvent.isHoldingSprintKey()` (previously only WASD-held was tracked, with no walk/sprint
distinction). New `Gun.sprintSpreadMultiplier` (default `3f`) scales `spreadMin`/`spreadMax` while
sprinting; standing/crouched is still fully suppressed, unchanged. Kar98k tuned to
`spreadMin=4f, spreadMax=9f, sprintSpreadMultiplier=2.5f` — walking is "basically unusable" and
sprinting is "unusable even point-blank" (both explicit design goals, checked against
miss-distance-at-range math, not just vibes) while standing/crouched stays pinpoint.

**Kar98k ADS pose — first one done for the obj³ pipeline, confirmed in-game**:
- `Gun.refreshModel` previously only swapped `DataComponents.ITEM_MODEL` (the item_model
  pipeline's musket/springfield/karabiner) — obj³ guns select via `CUSTOM_MODEL_DATA` instead (see
  `Item.customModelData`'s kdoc) and had no aiming-variant support at all yet. Added
  `Gun.customModelDataAiming` (mirrors `itemModelAiming`'s convention) and `refreshModel` now swaps
  `CUSTOM_MODEL_DATA`'s string too when set, independent of the `ITEM_MODEL` swap.
- Confirmed dead end first: there is no way to preview an obj³ mesh's firstperson pose inside
  Blockbench. Its native Display-preset preview needs `Formats.*.display_mode: true`
  (`java_block`/`bedrock_block`), which requires `meshes: false` (cubes only); the only format that
  can hold an obj³ baked mesh (`free`) has no display preview at all. No format supports both.
  Plain viewport screenshots are Blockbench's editor camera, not Minecraft's hand-render matrix —
  confirmed not trustworthy for this. Real client is the only ground truth, same conclusion as the
  musket/springfield centering work.
- Reused the hip-fire pose's rotation unchanged (no new rotation = no new anchor-swing to cancel,
  so section 5's closed-form formula doesn't apply here) and only added a translation delta on top,
  live-tuned against the real client by binary search on the horizontal (X) component:
  `-2` (way right) → `-8` (almost centered, bit left) → `-9` (bit too far left) → `-8.5`
  (**confirmed perfect**). Final: `translation: [-8.5, 4, 5]` righthand /
  `[8.5, 4, 5]` lefthand (mirrored X), rotation unchanged from hip-fire (`[0,90,0]`/`[0,-90,0]`).
- **Generalized for the remaining 7 obj³ guns** (Lebel M1886, Fedorov Avtomat, Mossberg Patriot,
  SKS, Springfield 1873, VPO-102, Beretta 57 — none of these have real `Gun` stats yet, still
  `/testgun`-only placeholder items per `TestGunGive.kt`): new
  [`tools/gen-obj3-aiming-pose.js`](../tools/gen-obj3-aiming-pose.js) scaffolds the clone-and-patch
  step + selector wiring from a `--dx`/`--dy`/`--dz` translation delta (defaults to the Kar98k
  numbers above as a starting guess, not a promise), documented as playbook §5.5 in
  [`obj3_weapon_import_playbook.md`](blockbench-reference/obj3_weapon_import_playbook.md). `dx`
  (horizontal) should transfer reasonably across guns since it's compensating for a fixed
  client-side anchor shared by every item; `dy`/`dz` are weaker priors tied to each gun's own
  proportions and should be expected to need their own live-tuning pass.
- **Not yet done**: none of the other 7 guns have had this run yet (no real `Gun` stats to attach
  it to in the first place — that's the actual blocker, not the pose work). Kar98k's aiming pose
  hasn't been checked with `adsVignette`/zoom interaction beyond the swap-while-aiming fix above.

## Status update (2026-09-02): Kar98k damage rebalanced, real `modules/combat` bugs found+fixed, spark profiler added, dispatcher-threads bumped to 4

Direct continuation of `modules/combat` polish. Four separate asks in one session, landed as three
commits (`e5803812`, `92fe45ae`, `c429a29e`, `80f33762`) plus a live Panel-side config change —
each rebuilt, boot-tested locally via the `morellia-testclient` dev client and/or a bare local
server run, then deployed to the VM (jar swap + `.bak` backup + container restart + clean-boot log
check) before moving to the next.

- **Kar98k damage, iterated three times to the final ask**: first bumped `maxDamage` 10f→12.7f so a
  close-range hit does 6 hearts *through full leather armor* (7 armor/0 toughness — vanilla's
  formula lands on the flat `armor/5` reduction floor at this damage level, ~5.6%), computed by
  hand against Minecraft's real armor-reduction formula, not guessed. Then added a falloff tail
  (100→300 blocks, `minDamage` 8.9f = 4 hearts through leather at 300+). **Final ask changed
  direction**: falloff removed entirely — flat `maxDamage == minDamage == 12.7f` from 0 out to
  `maxRange = 512.0` (the render-distance ceiling this project might push to), by collapsing
  `DamageFalloff`'s start/end range to a single point rather than adding a separate flat-damage
  type. See [`TestWeapons.kt`](../server/src/main/kotlin/net/morellia/server/TestWeapons.kt)'s
  `kar98k` definition for the current numbers.
- **A `/code-review`-style pass over all of `modules/combat`** (not diff-scoped — the whole module,
  requested directly, effort auto-escalated to "high" by the loaded skill instructions) surfaced 4
  real findings, all fixed:
  1. **Gun/melee cooldown TOCTOU**: `Gun.fire`/`MeleeListener.onAttack` both did a plain
     read-check-then-write on their per-player `ConcurrentHashMap` — the map itself is
     thread-safe, the *compound* check-then-set operation on it wasn't. Two concurrent `fire()`
     calls for the same player (the auto-fire scheduled task racing a fresh
     `PlayerHandAnimationEvent`, running on genuinely different thread pools regardless of
     `dispatcher-threads`) could both pass the elapsed-time check before either wrote back,
     letting a gun fire faster than its configured `cooldownMs`. Fixed with a new
     `Combat.tryStartCooldown` built on `ConcurrentHashMap.compute` (atomic per-key), shared by
     both guns and melee — collapses what used to be duplicated cooldown logic into one place too.
  2. **`FireListener`'s auto-fire task registration** had the identical TOCTOU shape
     (`containsKey` then `put`) — switched to `computeIfAbsent`.
  3. **`AimingListener`'s ADS scope-vignette bug**: `applyAimEffects`, when called from a
     held-slot-change (switching guns mid-aim), read `player.itemInMainHand` to decide whether to
     show the vignette — confirmed via decompiling Minestom's own `PlayerHeldListener` bytecode
     that this is still the *outgoing* gun's stack at that point (`Player.setHeldItemSlot` only
     runs after the event returns uncancelled). Fixed by threading
     `PlayerChangeHeldSlotEvent.getItemInNewSlot()` through to `applyAimEffects` instead of letting
     it read the stale field itself.
  4. **No guard against a gun's falloff tail exceeding its own range** — `Gun`'s `init` block now
     `require`s `damageFalloff.falloffEndRange <= maxRange`, since a falloff tail past `maxRange`
     is silently dead (`fire()`'s hitscan ray never travels that far) — this is exactly the
     misconfiguration the Kar98k's own falloff range had mid-session, caught by hand, before
     `maxRange` was bumped alongside it.
- **Spark performance profiler added**: [`LooFifteen/spark`](https://github.com/LooFifteen/spark)'s
  Minestom port (`dev.lu15:spark-minestom:1.10-SNAPSHOT`, only version published to
  `repo.hypera.dev`), wired into `Main.kt` — self-registers `/spark` (profiler flame graphs via
  `spark.lucko.me`, `tps`/`health`/`gc` one-shot reports), gated behind the same `morellia.<node>`
  permission convention (backed by `net.aechronis:utils`'s `hasPermission`) every other admin
  command here already uses — no new permission model introduced. Confirmed on the VM's Linux
  container that it loads the real native `async-profiler` engine (better flame graphs than the
  Java-engine fallback Windows gets locally).
- **`minestom.dispatcher-threads` bumped 1 → 4** to actually use the VM's 4 cores for per-chunk
  tick parallelism (Minestom silently defaults to 1 — single-threaded chunk ticking — unless this
  JVM system property is set; it wasn't, anywhere in this project, until now). Applied by editing
  the *live* Pterodactyl server's startup command directly via the Panel's Application API (the
  user supplied a `ptla_...` key) — `PATCH /api/application/servers/3/startup` — since the actual
  container-level `STARTUP` env var isn't something `docker restart` can change; only a
  Panel-driven restart makes Wings recreate the container against the new command. Confirmed via a
  new container ID (`d0aa9ac8aa3b`) after the user restarted from the console, and the resolved
  `STARTUP` env showing `-Dminestom.dispatcher-threads=4`. `server/morellia-egg.json`'s own
  `startup` field updated to match, so a future re-import of the egg carries this forward.
  **Real risk flagged, not yet acted on**: this is exactly the condition under which non-thread-safe
  code elsewhere would start racing for real — see "still open" below.
- **`docs/research-todo/01-concurrency-model.md` is now the wrong resolved-status source for
  `modules/combat` specifically** — it documents the *design intent* (`ConcurrentHashMap`
  everywhere) correctly, but the TOCTOU bugs above show intent alone wasn't sufficient; the actual
  fix pattern (atomic `compute`/`computeIfAbsent`, not just a thread-safe collection type) isn't
  written up there yet.

**Still open, straight out of this session:**
- **`modules/nodes` and `modules/vanilla` have not had the same check-then-act audit
  `modules/combat` just got.** Now that `dispatcher-threads=4` is live on the VM (real per-chunk
  parallelism, not the old effectively-sequential default), any plain read-then-write on shared
  per-player/per-entity state in either module is a live risk, not a theoretical one — this is
  strictly more urgent than it would have been a day ago. Worth the same kind of pass `modules/combat`
  just got, focused specifically on state touched by both an event listener and a
  `MinecraftServer.getSchedulerManager()` task (the exact shape every bug found above had).
- `modules/utils` (external `net.aechronis:utils` dependency) — flagged since 2026-08-26, still
  true: its post-2026-08-02 upstream history was never audited for fixes worth porting, unlike
  `nodes`/`vanilla`. Separate from the concurrency point above since it's not code this project
  owns directly.
- The other 7 obj³ guns still have no real `Gun` stats to run a damage-falloff pass on (unchanged
  blocker from the 2026-09-01 entry above).

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
both on the Pterodactyl volume (see `.claude/skills/morellia-ops/SKILL.md`, gitignored) and in
`/opt/nodes-map/nodes/` as
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

`DCFiendish/nodes-map` (fork of `Aechronis/nodes-map`) is built and deployed to the production VM
(address in `.claude/skills/morellia-ops/SKILL.md`, gitignored) on port 8888, served via a systemd
unit (`nodes-map.service`, `python3 -m http.server 8888` from `/opt/nodes-map`). `js/app.js`'s
`PAN_BOUNDS` was updated to the real trimmed-box extent.
Firewalled open at both the Oracle NSG layer and the VM's own iptables (`netfilter-persistent`
persisted) — both layers required, opening one alone doesn't make the port reachable. It currently
shows territory-color overlays only, no base terrain tile imagery (that would need a rendered webp
tile pyramid — not built, flagged as a future nice-to-have, not requested).

## Access / credentials (unchanged from before)

- **SSH to the Oracle box**: connection details (host, key path) are in
  `.claude/skills/morellia-ops/SKILL.md` (gitignored, not in this public repo).
- **This is the user's own Oracle VM** (personal hosting), shared by multiple of the user's own
  other projects, plus at least one other tenant's service the user has hosted as a favor (not part
  of this project, never modified — only ever viewed read-only to identify what was already running
  on the shared box before picking nodes-map's own port). **Do not touch anything on this box beyond
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
- **GitHub Packages token**: lives in the user's global `~/.gradle/gradle.properties` (`gpr.user`/
  `gpr.token`), outside this repo — was empty at the start of the 2026-08-25/26 session (blocked
  local resolution of anything not already cached), populated by the user since. Only still needed
  for `net.aechronis:utils` and `net.aechronis:combat` now that `nodes`/`vanilla` are in-tree
  `project(...)` dependencies (see the 2026-08-26 monorepo migration status update).
- **This repo is a real git repo now** (was flagged as a gap in the old version of this doc —
  resolved, it's tracked and pushed).

## Server identifiers

Pterodactyl server UUID, volume path, and container ownership details are in
`.claude/skills/morellia-ops/SKILL.md` (gitignored, not in this public repo) — not repeated here.

- Docker container ID changes across restarts — always re-fetch via `sudo docker ps`, never reuse
  one from a prior session; verify by volume UUID, not by assuming the first `docker ps` row is
  Morellia (this box runs multiple containers)
- Port: 25567 (tcp + udp), offline-mode auth (`Auth.Offline()`)

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

- **New, 2026-08-29, actually next up for asset work**: pick up the obj³/Kar98K mesh-baking
  pipeline exactly where the 2026-08-29 status update above left it — nothing has been imported
  into Blockbench yet, only the `.glb` is downloaded. Also verify in-game whether `springfield.json`'s
  2026-08-27 width-doubling edit (see the entry above) was intentional.
- **Resolved 2026-08-27, but NOT fully done**: the ADS render bug itself (per-tick resend) is fixed
  and confirmed for good, musket centering is confirmed done. **Still open, pick up here next**:
  Springfield was hand-edited in Blockbench to `x=-8.09` but never actually checked in-game after
  that edit; Karabiner still has its original (Springfield-disproved) ratio estimate and hasn't been
  looked at in-game at all. See the "continued" status update above for exact values and the
  Blockbench MCP connection situation (installed, server reachable, but this session's tool calls
  never actually connected — try `/mcp` before assuming a fresh session is required).
- **New, 2026-08-25/26, actually next up**: Morellia's own `server/build.gradle.kts` pins are stale
  against what's now on `nodes`/`vanilla` master (`40b2270`/`a074e09` vs. the current `6f1f9dd`/
  `96b593f`) — see the nodes/vanilla status update above for the exact bump + follow-up work.
- **Superseded 2026-08-26**: `net.aechronis:combat` is no longer a dependency at all — replaced by
  the from-scratch `modules/combat` (see that status update above), which designs out the bug
  classes this bullet used to track (C2/C3/C4/H1/H5 confirmed fixed; C1/C5 and the remaining H-tier
  findings are moot since they were vehicle/explosion-specific and vehicles aren't built yet). See
  `research-todo/01-concurrency-model.md` (resolved 2026-08-06) for the thread-safety model
  `modules/combat`'s `ConcurrentHashMap`-everywhere design was built against.
- **Not yet committed**: all of `modules/combat` plus the `server/` changes that wire it in are
  sitting uncommitted in the working tree as of 2026-08-26 — commit them once fire/reload/melee/ADS
  have actually been played through by a human (see the status update above).
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
