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
