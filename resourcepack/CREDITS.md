# Asset credits

Per `docs/research-todo/10-asset-sourcing-and-licensing.md`'s policy: source + license per asset,
recorded here as they're added.

## Musket (`assets/morellia/models/item/musket.json`, `assets/morellia/textures/item/musket.png`)

- **Source**: "WWI & WWII rifles" resource pack by memava —
  https://modrinth.com/resourcepack/rifles (v9.11.1)
- **License**: MIT (as stated on the Modrinth listing)
- **Pulled**: 2026-08-26
- **Notes**: original asset is the pack's "Mosin Nagant" model (`item/rifles/mosinch.json` +
  `item/rifles/mosin.png`), extracted from its crossbow-rename-predicate wrapper and rewired to be
  referenced directly via our own `item_model` component. Texture reference inside the model JSON
  was changed from `item/rifles/mosin` (implicit `minecraft:` namespace) to `morellia:item/musket`;
  no other edits to the model geometry.
- `musket-aiming.json`/`.png` (same texture, reused) is a derivative of this model with only the
  `firstperson_righthand`/`firstperson_lefthand` display translation edited, for the ADS "peering
  down the barrel" pose -- see `AimingListener.kt`. No other changes.

## Springfield (`assets/morellia/models/item/springfield.json`, `assets/morellia/textures/item/springfield.png`)

- **Source**: "WWI & WWII rifles" resource pack by memava — https://modrinth.com/resourcepack/rifles
  (local copy used: v8.5, downloaded directly by the user rather than pulled from Modrinth)
- **License**: MIT (as stated on the Modrinth listing)
- **Pulled**: 2026-08-26
- **Notes**: original asset is the pack's Springfield model (`item/rifles/springfieldch.json` +
  `item/rifles/springfield.png`), same extraction pattern as the musket above (texture reference
  changed from `item/rifles/springfield` to `morellia:item/springfield`, no geometry edits).
- `springfield-aiming.json` is a derivative with only the first-person display translation edited
  for the ADS pose, same as the musket's aiming variant above.

## Karabiner (`assets/morellia/models/item/karabiner.json`, `assets/morellia/textures/item/karabiner.png`)

- **Source**: same pack as above (memava's "WWI & WWII rifles", v8.5), MIT license.
- **Pulled**: 2026-08-26
- **Notes**: original asset is the pack's Kar98k model (`item/rifles/kar98kch.json` +
  `item/rifles/kar98k.png`) — the mind-map plan names this weapon generically as "Karabiner"; this is
  the specific real-world rifle it maps to. Texture references changed from `item/rifles/kar98k` to
  `morellia:item/karabiner`, no geometry edits.
- `karabiner-aiming.json` is a derivative with only the first-person display translation edited for
  the ADS pose, same as the musket's aiming variant above.

## US M1918 Mk1 trench knife (`assets/morellia/models/item/us_trench_knife.json`, `.../us_trench_knife.png`)

- **Source**: same pack (memava's "WWI & WWII rifles", v8.5), MIT license.
- **Pulled**: 2026-08-26
- **Notes**: **placeholder substitution, not the actual weapon** — the pack has no standalone trench
  knife models, only rifle bayonets. Reused the pack's US M1892 (Krag) bayonet model
  (`item/bayonets/usm1892.json`/`.png`) as the closest available US WWI-era blade silhouette. Replace
  with a real M1918 Mk1 trench-knife model when one is sourced.

## Nahkampfmesser (`assets/morellia/models/item/nahkampfmesser.json`, `.../nahkampfmesser.png`)

- **Source**: same pack (memava's "WWI & WWII rifles", v8.5), MIT license.
- **Pulled**: 2026-08-26
- **Notes**: **placeholder substitution** — reused the pack's German S84/98 (Mauser/Kar98k) bayonet
  model (`item/bayonets/s8498.json`/`.png`) as the closest available German-made blade. Replace with a
  real Nahkampfmesser model when one is sourced.

## Couteau Poignard Modele 1916 (`assets/morellia/models/item/couteau_poignard.json`, `.../couteau_poignard.png`)

- **Source**: same pack (memava's "WWI & WWII rifles", v8.5), MIT license.
- **Pulled**: 2026-08-26
- **Notes**: **placeholder substitution** — reused the pack's French "Rosalie" bayonet model
  (`item/bayonets/rosalie.json`/`.png`, the French Lebel rifle's cruciform bayonet, nicknamed "Rosalie"
  by WWI soldiers) as the closest available French WWI blade, even though the real Modele 1916 is a
  separate one-handed push-dagger design, not this rifle bayonet. Replace with a real model when
  sourced.

## Musket fire sound (`assets/morellia/sounds/guns/musket/fire.ogg`)

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

## Musket reload sound (`assets/morellia/sounds/guns/musket/reload.ogg`)

- **Source**: "Equipment Clicks III" by an OpenGameArt contributor —
  https://opengameart.org/content/equipment-clicks-iii (`equipment_clicks3.wav`)
- **License**: CC0 (Public Domain), per the OpenGameArt listing page.
- **Pulled**: 2026-08-26
- **Notes**: source file is a ~22.9s recording mixing three different real objects being clicked
  (a bolt-action rifle, a stapler, a tape measure); the first multi-click cluster (~0.1-2.5s),
  identified as the bolt-action-cocking segment, was trimmed and converted. Not musket-specific —
  this is a generic "bolt-action rifle" sound, reusable as-is for any future bolt-action gun until/
  unless a gun-specific reload sound is sourced (see the asset-sourcing doc's category-reuse note).

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
