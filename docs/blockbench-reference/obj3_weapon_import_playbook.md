# Playbook: importing a Sketchfab rifle model through the obj³ pipeline

Step-by-step replication guide, distilled from the Kar98K import (2026-08-30). Follow this in
order for the next model — one of TastyTony's other "Low-Poly Rifles" series entries, or any
similar glTF/.glb rifle from a different creator. Read
[`gltf_import_scale_bug.md`](gltf_import_scale_bug.md) first for the two import bugs referenced
in step 2 — this doc assumes you already know what they are and just tells you when to apply them.

## 0. Prerequisites (one-time, already done in this repo)

- The `obj-cubed` Blockbench plugin must be loaded, and its **version must match the shader tag
  merged into the resource pack** (currently plugin `0.7.0` ↔ git tag `26.2`, matching this
  project's target Minecraft build). Mismatched versions cause a client-side `ShaderManager`
  crash on connect ("Required resource pack was not loaded"), not a subtle rendering bug — so
  check `Plugins.installed` shows `id: 'objcubed', version: '0.7.0'` before exporting anything.
  - Load via Blockbench's File → Plugins → Load Plugin from File. The **file's basename must
    exactly match the plugin's internal `id`** or Blockbench silently rejects the load — this
    project's copy is named `objcubed.js` for that reason, not `objcubed-26.2.js`.
- The matching shader base pack (`assets/minecraft/shaders/...` from the `26.2` tag of
  `JagerMeistars/obj-cubed`) must be merged into the final resource pack alongside every export.
  It's a one-time merge, not regenerated per model.

## 1. Import the source file

Use Blockbench's native `import_gltf` action (no marketplace plugin needed on this Blockbench
version). If the file picker dialog doesn't respond to normal form-fill, set
`Dialog.stack[0].form.form_data.file.value` to the path directly via `risky_eval` and call
`.confirm()`.

Try the plain scale value first (start at `1`, adjust by powers of the glTF's stated unit scale
if the model imports absurdly large/small). If the geometry looks fine at a glance but doesn't
match this project's unit convention (rifles should be about the same Blockbench-unit length as
`springfield.json`, ~40 units), that's a **separate, later** rescale step (step 3) — don't fight
the importer's own scale field to fix that.

## 2. Check for the two known creator-family bugs

Both were found on TastyTony's Kar98K and are architecture-level (Blender→glTF export habits),
not specific to that one file — check for both on every new model from any creator before trusting
the import:

1. **Matrix-transform scale bug**: any part that looks like a giant slab or a floating
   disconnected chunk while the rest of the model looks right. Confirm via the diagnostic in
   `gltf_import_scale_bug.md` (checking which glTF nodes use a raw `matrix` vs. separate
   `translation`/`rotation`/`scale`). If present, don't try to fix it part-by-part in the
   Blockbench UI — rebuild the whole model from scratch via the reusable script in that doc
   (drives `THREE.GLTFLoader` directly, bypassing Blockbench's importer entirely).
2. **Missing color** (model imports geometrically correct but totally untextured/grey): check the
   glTF's `materials` array for `KHR_materials_pbrSpecularGlossiness`. Blockbench doesn't read
   this deprecated extension at all. Extract each material's `diffuseFactor` manually, gamma
   correct (linear → sRGB: `srgb = linear <= 0.0031308 ? linear*12.92 : 1.055*linear^(1/2.4) -
   0.055`), and bake into a small flat-swatch palette texture. Map each mesh to its original
   material via `gltf.meshes[n].primitives[0].material`, point every face's UV at that mesh's
   swatch. No real UV unwrap needed since these are flat colors, not image textures.

If a new creator's model doesn't show either bug (uses plain TRS transforms and a standard
`pbrMetallicRoughness` material with an actual base-color texture), skip straight to step 3 — this
whole step is conditional, not mandatory ceremony.

## 3. Rescale to this project's convention and save

Match `springfield.json`'s existing scale — real-world rifle lengths in this era (Kar98K,
Springfield 1903, Karabiner 98k) are close enough that reusing the same target Blockbench-unit
length is a reasonable default; don't re-derive from meters. Save the working file as a `.bbmodel`
under `resourcepack/assets/morellia/models/item/` (this is a **scratch/working file**, not the
finished asset — the real deliverable is the obj³ export in step 4).

## 4. Export through obj³

In the obj³ export dialog, set the **third-person right-hand display transform now** if you
already have a rough estimate (see step 5 for how to actually solve it) — but note the dialog has
its own separate local Vue fields (`vd.dThirdTX/TY/TZ`, etc.) that do **not** stay in sync with
`Project.display_settings` automatically. Set both explicitly before every `doExport()` call:

```js
Project.display_settings['thirdperson_righthand'].translation = [x, y, z];
Project.display_settings['thirdperson_righthand'].rotation = [rx, ry, rz];
DisplayMode.loadThirdRight();
DisplayMode.updateDisplayBase();
vd.dThirdTX = x; vd.dThirdTY = y; vd.dThirdTZ = z;
vd.dThirdRX = rx; vd.dThirdRY = ry; vd.dThirdRZ = rz;
vd.doExport();
```

The export produces `assets/objcubed/models/item/<name>_{default,ground,on_shelf,
thirdperson_righthand}.json`, `assets/objcubed/textures/item/<name>.png`, plus a vanilla
`assets/minecraft/items/<base_item>.json` selector keyed on a `custom_model_data` string.

**Known limitation, not yet solved**: the `gui` display context isn't handled by the generated
selector — it falls through to the `fallback` case (the `default` obj³ model), which doesn't
render through the shader in the inventory/hotbar rendering path, so the item shows as blank in
GUI. Fixing this needs an explicit `"when": "gui"` case pointing at a conventional flat icon model
— not yet done for any of these obj³ weapons, deferred by design so far.

## 5. Solve the third-person hand transform with math, not sliders

Blockbench's Display-mode gizmo *looks* like the right tool for this, and normally is — but on
this project's unofficial Minecraft build (26.2), the bundled player-model reference Blockbench
previews against does **not** match the real client's actual hand-bone position (confirmed: the
obj³ shader source itself has a "relocated entity geometry" comment for this build). That means
the live preview can be trusted for **rotation** but not for absolute **translation** — validate
translation against the real client, not the Blockbench preview.

The transform composes as `world = T + R·local` (rotate first around the model's own origin, then
translate in world space) — confirmed by comparing Blockbench's live `display_base` matrix against
an independently-built `THREE.Matrix4.compose(position, quaternion, scale)`. This means **a 90°+
yaw silently remaps which world axis a given translation component actually moves along** — don't
adjust X/Y/Z by feel one at a time when a large rotation is already set; a change intended to pull
the gun "closer" can visually shift it sideways instead.

Correct method once you've picked a rotation (start from an existing working gun's rotation, e.g.
Kar98K's `[60, 90, 0]`, if the new model is a similar-shaped bolt-action rifle):

1. Estimate the desired **grip point** `G` in the model's own local coordinates — where the
   trigger/wrist-of-stock sits, from the bounding box of that part.
2. Build the rotation-only matrix `R` for the chosen Euler rotation (XYZ order, degrees).
3. Solve `T = -R·G` — this is the translation that places `G` at the world origin, i.e. at the
   hand anchor.
4. Apply, export, rebuild the pack, redeploy, and check against the **real client**. If the height
   looks wrong even though the math is correct, that's the known preview/real-client mismatch —
   nudge the translation's world-Y empirically against real footage rather than recomputing G
   (this project's Kar98K needed roughly +20 units of Y beyond the pure math solution for this
   reason — treat that offset as a per-build constant to try first on the next model, not
   something to re-derive from scratch).
5. If the trigger/grip still isn't quite at the hand after that, refine `G` (you likely
   underestimated or overestimated where the wrist-of-stock sits relative to the model's origin)
   and re-solve — don't fall back to blind per-axis dragging once you have working math.

## 6. Rebuild and redeploy the local test pack

- Build the zip with `jar cf resourcepack.zip -C <merge-dir> .` — **not** PowerShell's
  `Compress-Archive`, which produces backslash path separators the game rejects.
- The merge directory needs three things layered together: this project's existing
  `resourcepack/assets`, the new export's `assets/objcubed` + `assets/minecraft/items/*.json`, and
  the one-time shader base pack's `assets/minecraft/shaders`.
- The server hashes `resourcepack.zip` once at boot (`ResourcePack.kt`) — **restart the server
  after every rebuild**, a reload alone won't pick up the new hash.
- Give the test item a `custom_model_data` string matching what the export's selector keys on, via
  a temporary `DataComponents.CUSTOM_MODEL_DATA` on a throwaway item stack (see `DevLoadout.kt`'s
  `TEMP` block from the Kar98K import for the pattern) — don't wire into the real `Gun`/
  `TestWeapons.kt` pipeline until the pose is fully confirmed.
- If a dev client's saved inventory from a previous test session is stale, it can silently
  overwrite the spawn-time test item — move the player's `.dat` file aside
  (`server/morellia-data/vanilla/playerdata/<uuid>.dat`) if the test item doesn't appear.

## 7. Wrap up

- Add a `resourcepack/CREDITS.md` entry for the source model (creator, license, URL, pull date).
- Once the pose is confirmed, remove the temporary `DevLoadout.kt` scaffolding and wire the
  finished obj³ model into the real weapon-definition pipeline the same way `springfield.json`/
  `karabiner.json` are wired (check `TestWeapons.kt`).
- Merge the export's `assets/objcubed/...` files for real into `resourcepack/` (review the diff —
  especially the atlas/shader files — rather than copying the scratch merge wholesale).
