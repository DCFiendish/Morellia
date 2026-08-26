# Asset Sourcing & Licensing

New as of 2026-08-25, replacing the from-scratch Blockbench modeling workflow (`models/
flintlock_musket/`, now removed — it was never actually wired into the game; item models fell back
to base `Material`). Full asset overhaul in scope: weapons, vehicles, buildings/structures,
uniforms. No policy for any of this existed anywhere in the docs before this file — confirmed by a
repo-wide search that found zero mentions of licensing, CC0, or public domain.

## Policy

- **Prefer CC0/public-domain sources.** If a source's license is unclear or restrictive, don't use
  it without explicit confirmation — this will eventually be a public server, and re-doing an asset
  later because of a licensing problem is more expensive than checking up front.
- **Record source + license per asset** — a short note (in the asset's own folder, or a shared
  manifest once there are enough assets to need one) with: where it came from (URL), its stated
  license, and the date it was pulled. Enough to answer "are we actually allowed to use this" later
  without re-researching from scratch.
- Flag anything ambiguous (e.g. "free to use" with no explicit license, or a creator's personal
  terms that don't map to a standard license) before using it — don't assume permissive.

## Technical target format

Confirmed by decompiling `net.aechronis:combat:2c63782`'s `Item`/`Gun`/`Melee` classes — any sourced
model needs to end up as:

- A standard Minecraft Java item-model JSON (Blockbench-exportable) under a `morellia:item/<name>`
  resource location, referencing texture PNGs the same way.
- Wired into a `Gun`/`Melee`/`Item` construction via `itemModel` (the resource location string) and
  `material` (the vanilla base `Material` it renders as) — this is Minestom's modern `item_model`
  component, not the legacy `CustomModelData` int tag.
- For `Gun` specifically: three more optional resource-location strings for ammo/reload/aim visual
  states — `itemModelEmpty`, `itemModelReloading`, `itemModelAiming`.
- Packaged into `server/resourcepack.zip`, served locally for now (see `ResourcePack.kt` —
  `http://localhost:8000/resourcepack.zip`, run `python -m http.server 8000` from `server/`).

`blockbench-reference/` (vendored Blockbench MCP-plugin docs) is still useful here for
touching up/converting sourced models in Blockbench — only the from-scratch generation workflow
(`build.py`-style procedural cube-building) is what's being dropped.

## Sourcing order

Weapons first — the only category with confirmed live wiring in `combat` today (`TestWeapons.kt`).
Vehicles, buildings, and uniforms follow the same source → convert → wire pattern once weapons are
validated end-to-end locally.

## Still open

- No actual sourcing has started yet — this file only establishes the policy and target format.
- No manifest file exists yet; add one once there are enough sourced assets to need tracking beyond
  ad-hoc per-folder notes.
