# Handoff — Morellia project status (2026-08-06)

Deep background (library internals, design rationale) is in `RESEARCH.md`, `NODES_DEEP_DIVE.md`,
`VANILLA_DEEP_DIVE.md`, `COMBAT_DEEP_DIVE.md`, and `research-todo/*.md` — not repeated here. This
doc is: what's actually live in production right now, what changed most recently, and the
credentials/IDs needed to keep going without re-discovering them.

Supersedes the old 2026-07-31 version of this file and folds in `WARFLAG_HANDOFF.md` (deleted —
that entire thread, including the flat two-territory dev fixture it was written around, is now
fully resolved and superseded by the real territory data described below).

## Theme: the Agadir Crisis (1911), alternate history — locked in

The real 1911 Agadir Crisis (a diplomatic/gunboat standoff over Morocco, resolved historically
without war) is reimagined here as escalating into real fighting. Ten nations: **Germany, France,
United Kingdom, Spain, Italy, Morocco, Switzerland, Netherlands, Belgium, Portugal.** Weapons era
is bolt-action rifles, early Maxim-type machine guns, horse-drawn field artillery — `Aechronis/combat`'s
`Gun`/`Melee`/`Vehicle` data classes cover this directly (see `RESEARCH.md` §2), no new combat code
needed for the era fit.

## World: real trimmed terrain, live in production

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

## Borders: 1911 territories drawn and deployed (rebuilt 2026-08-06, v2)

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

## nodes-map: live territory viewer

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
  - `DCFiendish/nodes` — fork of `Aechronis/nodes`
  - `DCFiendish/vanilla` — fork of `Aechronis/vanilla`
  - `DCFiendish/nodes-map` — fork of `Aechronis/nodes-map`, new this session
  - `DCFiendish/rust-mc-bot` — fork of `Eoghanmc22/rust-mc-bot`
  - `Aechronis/utils`, `Aechronis/combat` — used directly, not forked, no local changes
- **GitHub Packages token**: `C:\Users\USER\.gradle\gradle.properties` (`gpr.user`/`gpr.token`)
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

- Alliance/enemy relationships between the 10 nations (currently all neutral).
- Stale `LoadTestBots.kt` / `rust-mc-bot` attack-target coordinates from the old flat test world —
  superseded now that real territories exist; would need regenerating against the real map if
  load-testing the war system again.
- Portugal's clipped territory (see above) — accepted limitation, not planned to fix.
- A real webp tile-imagery base layer for `nodes-map` (currently overlay-only).
- Morocco's nation-hierarchy question (see above).
- `War-Comms` GitHub repo (empty, 0 bytes) — `gh` lacked the `delete_repo` scope to remove it via
  API; user would need `gh auth refresh -h github.com -s delete_repo` or delete it manually via the
  GitHub UI. Unconfirmed whether this was ever done — check before assuming either way.
- Task #13 from an earlier session's list: replace the nodes-map loading-screen logo with Morellia
  branding — blocked on the user producing artwork.
