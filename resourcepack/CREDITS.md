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
