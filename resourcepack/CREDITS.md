# Asset credits

Per `docs/research-todo/10-asset-sourcing-and-licensing.md`'s policy: source + license per asset,
recorded here as they're added.

## Kar98K (`resourcepack/assets/nodisium/models/item/kar98k-import.bbmodel`, exported via obj³)

- **Source**: "Low-Poly Kar98K" by TastyTony —
  https://sketchfab.com/3d-models/low-poly-kar98k (glTF/.glb download)
- **License**: CC-BY 4.0 (attribution required — credit TastyTony wherever this asset is credited
  publicly)
- **Pulled**: 2026-08-30
- **Notes**: reimported from scratch (not via Blockbench's native glTF importer, which mis-scales
  some parts on this file — see `docs/blockbench-reference/gltf_import_scale_bug.md`), rescaled to
  match `springfield.json`'s unit convention, recolored from the source file's
  `KHR_materials_pbrSpecularGlossiness` `diffuseFactor` values (Blockbench doesn't read this
  extension natively). Exported through the obj³ pipeline rather than a traditional item model —
  see `docs/blockbench-reference/obj3_weapon_import_playbook.md` for the full process. TastyTony
  has several more rifles in the same series; the same import fixes are expected to apply.

## Lebel M1886 (`resourcepack/assets/nodisium/models/item/lebel-m1886-import.bbmodel`, exported via obj³)

- **Source**: "Low-Poly Lebel M1886" by TastyTony —
  https://sketchfab.com/3d-models/low-poly-lebel-m1886-0515c4d0454c430ab7f9a93f1671428c (glTF/.glb
  download; same creator/series as the Kar98K above)
- **License**: CC-BY 4.0 (attribution required — credit TastyTony wherever this asset is credited
  publicly)
- **Pulled**: 2026-08-31
- **Notes**: rebuilt from the raw glTF (same matrix-transform scale bug as the Kar98K — see
  `docs/blockbench-reference/gltf_import_scale_bug.md`), but this file's materials use standard
  `pbrMetallicRoughness.baseColorFactor` rather than the deprecated specular-glossiness extension,
  so no manual color-extraction was needed beyond gamma-correcting the same way. Rescaled to ~42.3
  Blockbench units (real-world Lebel M1886 length vs. Kar98K's, scaled from Kar98K's own convention,
  then reduced 10% on user review). This model's raw axes differ from the Kar98K's (barrel along
  local Y, not X) — permuted via a proper rotation (no mirroring) to match the Kar98K's axis
  convention before reusing its exact `[75, 90, 0]` display rotation and the closed-form translation
  formula from `docs/blockbench-reference/obj3_weapon_import_playbook.md`. First of the 7
  newly-downloaded TastyTony models to go through the pipeline (musket/Springfield/Karabiner
  memava-pack models dropped for quality — see `TestWeapons.kt`/`DevLoadout.kt`).

## Fedorov Avtomat, Mossberg Patriot, SKS, Springfield 1873, VPO-102, Beretta Model 57 (exported via obj³)

- **Source**: TastyTony, same "Low-Poly Rifles" Sketchfab series as the Kar98K/Lebel M1886 above
  (`low-poly_federov_avtomat.glb`, `low-poly_mossberg_patriot.glb`, `low-poly_sks.glb`,
  `low-poly_springfield_model_1873.glb`, `low-poly_vpo-102.glb`, `low-poly_beretta_model_57.glb` —
  exact per-model Sketchfab URLs not recorded, pulled into `Downloaded glb assets/` before this
  session; fill in if/when re-sourced).
- **License**: CC-BY 4.0 assumed (same creator/series as the Kar98K/Lebel M1886, attribution
  required — credit TastyTony wherever these assets are credited publicly). Verify per-model before
  shipping publicly.
- **Pulled/processed**: 2026-09-01
- **Notes**: all six run through the same obj³ pipeline as the Kar98K/Lebel M1886 (see
  `docs/blockbench-reference/obj3_weapon_import_playbook.md`), rebuilt directly from each raw glTF's
  `matrixWorld` (bypassing Blockbench's native importer — same scale bug family, see
  `docs/blockbench-reference/gltf_import_scale_bug.md`) rather than checked file-by-file for which
  nodes are affected. Colors sourced from each model's own materials, not guessed: Springfield 1873
  used the deprecated `KHR_materials_pbrSpecularGlossiness` extension (diffuseFactor extracted and
  gamma-corrected, same as the Kar98K); the other five used standard
  `pbrMetallicRoughness.baseColorFactor`, read directly off the Three.js-parsed material with no
  extra correction needed. Each model's raw barrel axis was detected per-file and permuted onto the
  Kar98K's local-X convention with a proper (non-mirroring) rotation before reusing its exact
  `[75, 90, 0]` third-person display rotation and closed-form translation formula. Grip point
  (local origin) placed at each model's trigger-guard cluster, found by geometry (lowest-Y vertices
  in the receiver region) and confirmed with a marker-cube screenshot per model, not by eye alone.
  Scaled per-model off estimated real-world lengths against the Kar98K's ~41-unit convention (SMG
  and rifle lengths only, not measured precisely). "Beretta Model 57" is visually an M12-style
  submachine gun with a wood stock and stick magazine, not the historical semi-auto pistol the name
  suggests — treated as a two-handed long gun like the rest of this batch, not a pistol grip.
  **Verified in Blockbench only** (silhouette, color, and grip-marker screenshots per model) — none
  of these six have been checked against the real Minecraft client yet; do that before wiring any of
  them into `TestWeapons.kt` for real.

## US M1918 Mk1 trench knife (`assets/nodisium/models/item/us_trench_knife.json`, `.../us_trench_knife.png`)

- **Source**: same pack (memava's "WWI & WWII rifles", v8.5), MIT license.
- **Pulled**: 2026-08-26
- **Notes**: **placeholder substitution, not the actual weapon** — the pack has no standalone trench
  knife models, only rifle bayonets. Reused the pack's US M1892 (Krag) bayonet model
  (`item/bayonets/usm1892.json`/`.png`) as the closest available US WWI-era blade silhouette. Replace
  with a real M1918 Mk1 trench-knife model when one is sourced.

## Nahkampfmesser (`assets/nodisium/models/item/nahkampfmesser.json`, `.../nahkampfmesser.png`)

- **Source**: same pack (memava's "WWI & WWII rifles", v8.5), MIT license.
- **Pulled**: 2026-08-26
- **Notes**: **placeholder substitution** — reused the pack's German S84/98 (Mauser/Kar98k) bayonet
  model (`item/bayonets/s8498.json`/`.png`) as the closest available German-made blade. Replace with a
  real Nahkampfmesser model when one is sourced.

## Couteau Poignard Modele 1916 (`assets/nodisium/models/item/couteau_poignard.json`, `.../couteau_poignard.png`)

- **Source**: same pack (memava's "WWI & WWII rifles", v8.5), MIT license.
- **Pulled**: 2026-08-26
- **Notes**: **placeholder substitution** — reused the pack's French "Rosalie" bayonet model
  (`item/bayonets/rosalie.json`/`.png`, the French Lebel rifle's cruciform bayonet, nicknamed "Rosalie"
  by WWI soldiers) as the closest available French WWI blade, even though the real Modele 1916 is a
  separate one-handed push-dagger design, not this rifle bayonet. Replace with a real model when
  sourced.

## Musket fire sound (`assets/nodisium/sounds/guns/musket/fire.ogg`)

- **Source**: "Gunshot Sounds" pack by Vincent Sevedge —
  https://opengameart.org/content/gunshot-sounds (`mosin.wav` from `sounds.zip`)
- **License**: CC BY 3.0 Unported (per the pack's own bundled `creativecommons.txt` — the
  OpenGameArt listing page itself says CC0, but the file actually shipped inside the download
  states CC BY 3.0 with copyright to Vincent Sevedge, 2009; going with the bundled statement as
  authoritative). Attribution: "Gunshot sound by Vincent Sevedge (CC BY 3.0)."
- **Pulled**: 2026-08-26
- **Notes**: `mosin.wav` is a ~14.7s multi-shot recording (target shooting session); the specific
  gunshot at ~0.45-1.7s was trimmed out and converted to `.ogg` (see
  `docs/research-todo/10-asset-sourcing-and-licensing.md`'s sound-sourcing section for the
  trim/convert method). User picked this take over two other trimmed candidates from the same
  recording.

## Musket reload sound (`assets/nodisium/sounds/guns/musket/reload.ogg`)

- **Source**: "Equipment Clicks III" by an OpenGameArt contributor —
  https://opengameart.org/content/equipment-clicks-iii (`equipment_clicks3.wav`)
- **License**: CC0 (Public Domain), per the OpenGameArt listing page.
- **Pulled**: 2026-08-26
- **Notes**: source file is a ~22.9s recording mixing three different real objects being clicked
  (a bolt-action rifle, a stapler, a tape measure); the first multi-click cluster (~0.1-2.5s),
  identified as the bolt-action-cocking segment, was trimmed and converted. Not musket-specific —
  this is a generic "bolt-action rifle" sound, reusable as-is for any future bolt-action gun until/
  unless a gun-specific reload sound is sourced (see the asset-sourcing doc's category-reuse note).

## Kar98k fire/reload sound (`assets/nodisium/sounds/guns/kar98k/{fire,reload}.ogg`)

- **Source**: same underlying files as the musket's fire/reload sounds above (Vincent Sevedge's
  Mosin Nagant gunshot / the generic bolt-action-cocking clip), copied into a per-gun folder --
  same license/attribution as those two entries. Kar98k is the first gun wired with real combat
  stats since musket/Springfield/Karabiner were dropped for asset quality (see docs/HANDOFF.md);
  no Kar98k-specific recording has been sourced yet, and the fire sound in particular is a real
  Mosin Nagant (not a Mauser) -- placeholder, replace when a dedicated recording is found.

## ADS sight vignette (`assets/minecraft/textures/misc/pumpkinblur.png`)

- **Source**: original, generated with [`make_sight_vignette.py`](make_sight_vignette.py) (Pillow
  script, checked in so future guns' sight shapes can be regenerated/tweaked the same way rather
  than hand-edited in an image editor). Overrides vanilla's own
  `pumpkinblur.png` — the full-screen mask vanilla renders when a carved pumpkin is worn as a
  helmet (a peripheral-vision-blocking multiply mask: white = visible, black = blocked). ADS
  spoofs the player's own helmet-equipment packet to a carved pumpkin only while aiming a Gun
  (see `AimingListener.kt`), so this texture is what actually renders during that effect.
- **License**: n/a (original work, not sourced from a third party).
- **Pulled/created**: 2026-08-26
- **Notes**: not a copy of any third-party texture. The *technique* (overriding this specific
  vanilla file to reskin the pumpkin-vision-restriction effect into a weapon sight) was found by
  checking `Aechronis/aechronis`'s own `resource-pack/assets/minecraft/textures/misc/pumpkinblur.png`
  for prior art — theirs draws a circular scope reticle for their scoped sniper rifles. Ours draws
  an original tall window with a front-sight-post silhouette and a rear-sight notch instead, since
  the musket has iron sights, not a telescopic scope. First pass — reshape/resize as needed once
  seen in-game.
