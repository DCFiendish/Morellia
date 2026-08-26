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

## Sound sourcing (added 2026-08-26, musket is the reference implementation)

Scoped for ~30 guns eventually, so the pattern is designed to stay mechanical as it repeats, not
just to work once for the musket:

- **File/event convention**: `resourcepack/assets/morellia/sounds/guns/<gun_name>/<event>.ogg`
  (e.g. `guns/musket/fire.ogg`, `guns/musket/reload.ogg`). This matches `Gun.kt`'s
  `soundFire`/`soundReload` defaults exactly (`${Tags.NAMESPACE}:$name.fire`/`.reload`), so a new
  gun needs zero code changes to get sound — just matching files in the right folder.
- **`sounds.json` is generated, never hand-edited**: run
  `python resourcepack/generate_sounds_json.py` (from anywhere, paths are self-relative) after
  adding/removing any `.ogg` under `sounds/guns/` — it walks that tree and regenerates the whole
  file. Run this before every `jar cf` resourcepack build, same step as the model/texture files.
- **No system ffmpeg needed**: `pip install imageio-ffmpeg` pulls a static ffmpeg binary with no
  admin rights required — `python -c "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())"`
  gives its path. Useful for trimming/converting any source recording to the final `.ogg`.
- **ffmpeg gotcha hit doing the musket sounds**: on this ffmpeg build, `-ss <nonzero> -t <dur>`
  placed *after* `-i` (output-side seeking) combined with an `-af` filter and a non-zero start
  silently produced a fully-zeroed/silent output for some (not all) trims, no error printed —
  looked like real content until checked with `-af volumedetect`. Putting `-ss`/`-t` *before* `-i`
  (input-side seeking) fixed it for every case tried. Always sanity-check a trimmed clip's
  `mean_volume`/`max_volume` aren't `-91.0 dB` before trusting it, especially when scripting many
  extractions unattended.
- **Source recordings are usually long and unsegmented** (a whole target-shooting session, a whole
  "several objects clicking" demo reel) — don't assume a clean single clip is available. A short
  Python script computing short-window RMS envelope (numpy on the raw PCM, no extra deps) and
  greedily picking non-overlapping high-energy windows finds the individual transients (shots,
  clicks) reliably; merge adjacent/overlapping windows into clusters to find multi-part sounds
  (e.g. a bolt-action cycle is 2-3 clicks close together, vs. a single stapler snap).
- **Claude can't listen to audio** — after finding candidate segments, send them to the user (as
  small trimmed clips, not the whole source file) to pick from rather than guessing which
  transient is the right one, especially when a source recording mixes several unrelated objects
  (the bolt-action-cocking source used for the musket reload also had stapler and tape-measure
  snaps mixed into the same file).
- **Verify a source's actual license from what's bundled in the download, not just the hosting
  site's category/badge** — hit a real mismatch doing this: OpenGameArt's "Gunshot Sounds" page
  listed the pack as CC0, but the zip's own `creativecommons.txt` inside said CC BY 3.0 (attribution
  to Vincent Sevedge, 2009). Went with the bundled file as authoritative and credited accordingly
  in `CREDITS.md` — the page-level badge can't be trusted alone.
- **Category-level reuse for scaling to 30 guns**: rather than re-sourcing a dedicated sound for
  every future gun, prefer a handful of well-picked CC0/CC-BY *category* sources (bolt-action rifle,
  semi-auto rifle, machine gun, pistol, artillery) reused across every gun in that category via
  `sounds.json`'s multiple-event-names-can-point-at-one-file-path support, until/unless a
  gun-specific sound is worth sourcing individually. Not built yet (only one gun has sound so far)
  but the folder convention above doesn't block it — a shared clip can simply be copied into each
  new gun's own `sounds/guns/<name>/` folder (small files, no reason to dedupe on disk).
- **OpenGameArt's "The Free Firearm Sound Library"** (CC0, ~194MB, covers bolt-action/semi-auto/
  automatic rifles, shotguns, and handguns in one pack —
  https://opengameart.org/content/the-free-firearm-sound-library) is a strong first place to check
  before a fresh web search once more than a couple more guns need sourcing — one download instead
  of one search per gun.

## Still open

- The 30-gun plan means most of the sourcing work described above hasn't happened yet — only the
  musket has real sound. No manifest file exists yet either; add one once there are enough sourced
  assets (models + textures + sounds together) to need tracking beyond ad-hoc per-folder notes.
