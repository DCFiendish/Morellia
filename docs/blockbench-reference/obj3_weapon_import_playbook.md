# Playbook: importing any low-poly mesh (glTF/.glb/.obj) through the obj³ pipeline

Step-by-step replication guide, distilled from the Kar98K import (2026-08-30 through 2026-08-31).
Follow this in order for the next model — one of TastyTony's other "Low-Poly Rifles" series
entries, a similar rifle from a different creator, or any other low-poly Blender/glTF model that
needs to be held/worn correctly in-game. Nothing in this pipeline is Kar98K-specific or
weapon-specific; every step below is either a property of the **obj-cubed plugin itself** (applies
to any mesh it exports) or a **generic technique** you re-derive per model (grip point, rotation).
Read [`gltf_import_scale_bug.md`](gltf_import_scale_bug.md) first for the two import bugs
referenced in step 2 — this doc assumes you already know what they are and just tells you when to
apply them.

## What actually changes the raw geometry, vs. what's just a per-slot transform

Easy to conflate these — they're different kinds of operations, done at different stages:

1. **Fixing a broken import (conditional, only if the creator-family bug in step 2 is present)**:
   rebuilds the mesh from each glTF node's correct `matrixWorld`, which bakes in that node's own
   rotation *and* translation *and* scale. This is the only step that can look like "rotating
   parts of the model" — it's not a deliberate creative rotation, it's Blockbench's importer having
   discarded the correct transform and this step putting it back.
2. **Recentering the grip point (step 3.5, always do this)**: a straight **translation** of every
   vertex by a fixed offset, so the point you want the hand to hold sits at local `(0,0,0)`. No
   rotation involved — this only moves the model, doesn't reorient it.
3. **The `display.*.rotation`/`translation` values (step 5)**: not a geometry change at all — these
   never touch the `.bbmodel`'s vertices. They're a per-context runtime transform Minecraft applies
   at render time (rotate the whole model around its own origin, then translate). This is the
   "rotate it so the barrel points forward" step, and it's solved with the formula in step 5, not
   by eyeballing sliders.

So for a typical new model: expect one conditional geometry rebuild (step 2, only if that bug is
present), one mandatory geometry translation (step 3.5), and one mandatory *display*-rotation solve
(step 5) that never touches the mesh itself.

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

## 3.5. Recenter the grip point to local origin

**Do this for every model, always** — it's what makes step 5's math work without guessing.

The obj³-decoded frame is block-centre relative (the shader adds your mesh's local coordinates
directly onto a fixed hand/world anchor, no smart re-centering). That means whatever sits at your
mesh's local `(0,0,0)` is the point that lands at the hand. If you leave the model at wherever the
raw import happened to place it, you'd have to solve for a translation that both (a) points the
model correctly and (b) drags some arbitrary far-off point into the hand — much harder than it
needs to be, and it's exactly the kind of multi-axis coupled guessing that ate hours on the Kar98K
before this technique.

Instead, shift every vertex once so the intended grip (where a hand would wrap the stock/grip,
typically right around the trigger) sits at exactly `(0,0,0)`:

```js
// Determine the shift by inspection first (list_outline / bounding boxes / a visual check —
// see below), then apply it permanently to the .bbmodel:
const shift = [dx, dy, dz]; // moves your chosen grip point to (0,0,0)
Outliner.elements.forEach(el => {
  Object.keys(el.vertices).forEach(key => {
    for (let i = 0; i < 3; i++) el.vertices[key][i] += shift[i];
  });
});
```

**Verify visually before trusting it** — don't just guess the shift and move on. Drop a small
temporary marker cube at `(0,0,0)` (`new Cube({from:[-0.5,-0.5,-0.5], to:[0.5,0.5,0.5]}).init()`),
take an orthographic side-view screenshot, and confirm the marker sits right in the grip/trigger
area, not just "close." Remove the marker afterward — it's a visual aid only, not part of the
export. Getting this point right matters more than any other single step in the whole pipeline;
every error we chased on the Kar98K that looked like a broken formula turned out to trace back to
either this point being wrong, or the export offset in step 5 not compensating for it correctly.

## 4. Export through obj³

**Set "Model Offset Y" to `+0.5` before exporting** (the export dialog's `offsetY` field,
`Transform` section). The obj-cubed encoder always bakes a fixed `Y - 0.5` into every exported
position, because it assumes your model is built "on the floor" (local Y=0 = the bottom of the
block, geometry rising upward) like a normal vanilla JSON model. Once you've recentered to the grip
point in step 3.5, your model's Y=0 is the grip, not the floor — and the encoder still subtracts
that half-block unconditionally. Left uncorrected, this produces a **constant half-block downward
error that hits every display context at once** (third-person, first-person, ground, GUI — they all
decode the same baked PNG), which is exactly the kind of "everything looks too low, including
contexts I didn't touch" bug that cost the most time on the Kar98K. Setting `offsetY = 0.5` cancels
the plugin's built-in subtraction exactly, so the grip point (now at local origin) decodes to true
zero, matching what the translation formula in step 5 assumes. `offsetX`/`offsetZ` stay `0` — only
Y has this floor-relative convention (X/Z are already block-centre relative with no correction
needed, matching the plugin's own "horizontal origin = block centre" convention).

**Every hand-related display context you want in-game must be present in the export dialog's own
Vue fields before you call `doExport()` — they do NOT read live from `Project.display_settings`
on their own.** This is the single biggest gotcha in this whole pipeline (found the hard way on the
Kar98K: third-person right-hand was tuned correctly and exported fine, but first-person right-hand
and both left-hand contexts were left at their all-zero default and got baked that way — not a
Blockbench-preview-vs-real-client mismatch, just missing data. In-game this showed up as the
first-person/left-hand poses silently falling back to the untransformed `default` obj³ model
instead of "not loading" or erroring.).

The dialog's Vue component exposes one field-group per context — `dThird*` (thirdperson_righthand),
`dLeft*` (thirdperson_lefthand), `dFpr*` (firstperson_righthand), `dFpl*` (firstperson_lefthand),
plus `dHead*`/`dGui*`/`dGround*`/`dFixed*`. It also has a `_loadFromDisplaySettings()` method that
copies **all** of these from `Project.display_settings` in one call — use that instead of hand-
copying individual fields (the old `vd.dThirdTX = ...` pattern shown in earlier versions of this doc
only ever touched third-person and is why the bug above happened):

```js
// 1. Author every context you care about in Project.display_settings first (rotation/translation
//    triples, degrees + Blockbench units) — via Blockbench's own Display-mode UI/gizmo, or directly:
Project.display_settings['thirdperson_righthand'].rotation = [rx, ry, rz];
Project.display_settings['thirdperson_righthand'].translation = [x, y, z];
// ...same for thirdperson_lefthand / firstperson_righthand / firstperson_lefthand.
// See the mirroring note below for deriving the two lefthand entries from their righthand pair.

// 2. Open the export dialog, then sync ALL context fields from Project.display_settings in one call:
BarItems.objcubed_export.click();
const dlg = Dialog.stack.find(d => d.id === 'objcubed_dialog').content_vue;
dlg._loadFromDisplaySettings();
dlg.offsetY = 0.5; // cancel the plugin's built-in floor-relative -0.5 bake — see above

// 3. Point the export at a scratch folder (not resourcepack/ directly), then export:
dlg.resourcePackDir = '<scratch path>';
dlg.doExport(); // async — poll dlg.status / dlg.statusKind / dlg.running rather than awaiting directly
```

Sanity-check before merging: open each generated `assets/objcubed/models/item/<name>_<context>.json`
and confirm its own `display.<context>` entry is non-zero (all four hand-context files carry a full
copy of every context's transform, not just their own — check the one matching the filename).

**Mirroring right-hand to left-hand**: Minecraft's client renders the off-hand item by flipping the
model across local X before applying the `*_lefthand` transform, so the correct mirror of a tuned
righthand transform is: negate X-translation, keep X-rotation, negate Y/Z-rotation. I.e. for
`rotation: [rx, ry, rz]`, `translation: [tx, ty, tz]` on `*_righthand`, the matching `*_lefthand`
entry is `rotation: [rx, -ry, -rz]`, `translation: [-tx, ty, tz]`. This is derived from the
conjugation `M·R·M` / `M·T` where `M = diag(-1,1,1)` — not empirically tuned, so **verify it in-game
with the player's Main Hand setting switched to Left** before trusting it for a new model. Not yet
done for the Kar98K as of this writing — the mirrored values have been exported and merged, but
only right-hand has been visually confirmed against the real client. Confirm left-hand before
calling any model's pose fully done.

The export produces `assets/objcubed/models/item/<name>_{default,ground,on_shelf,
thirdperson_righthand,thirdperson_lefthand,firstperson_righthand,firstperson_lefthand}.json`,
`assets/objcubed/textures/item/<name>.png`, plus a vanilla `assets/minecraft/items/<base_item>.json`
selector — the selector only gets an explicit `display_context` case for whichever of these you
actually populated non-zero data for before export (confirmed: exporting with only third-person set
produces a selector with only a `thirdperson_righthand` case; exporting after syncing all four hand
contexts produces cases for all four automatically — no manual selector editing needed either way).

**Known limitation, not yet solved**: the `gui` display context isn't handled by the generated
selector — it falls through to the `fallback` case (the `default` obj³ model), which doesn't
render through the shader in the inventory/hotbar rendering path, so the item shows as blank in
GUI. Fixing this needs an explicit `"when": "gui"` case pointing at a conventional flat icon model
— not yet done for any of these obj³ weapons, deferred by design so far.

## 5. Solve the hand transform with a closed-form formula, not sliders

**Superseded 2026-08-31**: earlier versions of this doc solved `T = -R·G` and then patched the
remaining error with an empirically-found "+20 units of Y" fudge, attributed to a preview/
real-client mismatch. That fudge was actually masking two real, fixable bugs (the anchor-swing
effect and the missing `offsetY` export setting below) — once both are handled, **no empirical
fudge factor is needed at all**. Don't reuse "+20" or any other leftover constant from an older
model; re-derive from the formula below every time.

**Why simple `T = -R·G` isn't enough**: Minecraft bakes each display slot's rotation+scale into a
tiny placeholder quad at model-load time, and the obj³ shader reconstructs your model's rotation by
reading that quad's own baked corners back out. That placeholder's own anchor corner sits at block
**centre** (`0.5, 0.5, 0.5` in block units), not at your model's local origin — so rotating the
model also swings that anchor through an arc, on top of whatever translation you apply. At small
rotations this is easy to miss; at the large rotations a rifle typically needs (60-90°+ to go from
"lying flat as imported" to "held naturally"), the swing dominates and makes translation nudges
behave counter-intuitively (push it back, it also drifts sideways) — this is what actually derailed
the Kar98K for hours before we read the shader source directly.

**The fix — cancel the swing exactly, for any chosen rotation**:

1. Recenter the grip point to local origin first (step 3.5) — this formula assumes `G = (0,0,0)`.
2. Pick the barrel-forward rotation `R` you want (Euler XYZ, degrees — start from an existing
   working gun's rotation as a first guess for a similarly-shaped weapon, e.g. the Kar98K's
   `[75, 90, 0]`, but expect to need a different value for a differently-shaped model).
3. Compute the translation that keeps the (now-centred) grip point fixed at the hand anchor for
   that rotation, using Blockbench's own matrix utilities (not hand arithmetic — sign/order
   mistakes are easy):
   ```js
   const e = new THREE.Euler(r[0]*Math.PI/180, r[1]*Math.PI/180, r[2]*Math.PI/180, 'XYZ');
   const R = new THREE.Matrix4().makeRotationFromEuler(e);
   const anchor = new THREE.Vector3(0.5, 0.5, 0.5);
   const rotatedAnchor = anchor.clone().applyMatrix4(new THREE.Matrix4().extractRotation(R));
   const T = anchor.clone().sub(rotatedAnchor).multiplyScalar(16); // display.translation units
   ```
   This is `T = 16·(I−R)·(0.5,0.5,0.5)`. It's exact, not an approximation — it holds regardless of
   how large the rotation is.
4. **Also set the export dialog's `offsetY = 0.5`** (step 4) — this formula assumes the recentered
   grip decodes to exactly `(0,0,0)`, which is only true once the plugin's own floor-relative
   `-0.5` bake is cancelled. Skipping this reintroduces a constant half-block-low error that looks
   like the formula is wrong when it isn't.
5. Apply to `Project.display_settings`, export, rebuild the pack, redeploy, and check against the
   real client. If it's still off after both fixes above, the grip point itself (step 3.5) is the
   most likely culprit — verify it with the marker-cube screenshot before touching this formula
   again.

Confirmed on the Kar98K: after both fixes, third-person right-hand needed no further hand-tuning at
all beyond picking the rotation — translation came directly from the formula.

## 5.5. Optional: aiming (ADS) pose — translation only, no new rotation

Confirmed working on the Kar98k (2026-09-01). This gives the gun a second, distinct firstperson
pose ("peering down the barrel" when the player aims) — it's a separate concern from section 5's
hip-fire pose and only needs doing once the hip-fire pose is already confirmed good.

**No swing-cancellation math needed here** — unlike section 5, the aiming pose reuses the *same*
rotation as hip-fire (no new rotation means no new anchor-swing to cancel), and just adds a
translation delta on top of the already-correct hip-fire translation. This is a plain
[`Gun.customModelDataAiming`](../../modules/combat/src/main/kotlin/net/nodisium/combat/objects/Gun.kt)
swap in `CUSTOM_MODEL_DATA` — same mechanism `Gun.itemModelAiming` already used for the older
item_model-pipeline guns (musket/springfield/karabiner), extended to also cover this obj³
`custom_model_data`-selected pipeline (see `Gun.refreshModel`).

**Generate it**: `node tools/gen-obj3-aiming-pose.js <base_custom_model_data> [--dx=N] [--dy=N] [--dz=N]`
— clones the existing `<name>_firstperson_{righthand,lefthand}.json` (same mesh, only
`display.firstperson_*` changes) into `<name>_aiming_firstperson_*.json`, and inserts/replaces a
`<name>_aiming` case in `iron_ingot.json`'s selector (reusing the base gun's thirdperson/ground/
on_shelf/fallback models unchanged — aiming only touches the two firstperson hand poses). Re-running
for the same name replaces its files/case in place, so iterating is just re-run + rebuild + restart.

**Defaults are the Kar98k-confirmed deltas** (`dx=-8.5, dy=2.5, dz=5`), not a promise for a
different-shaped gun:
- `dx` (horizontal, mirrored sign for left hand) corrects for Mojang's fixed client-side
  right-hand render anchor — the same constant for *every* item — so it tends to transfer across
  guns whose mesh is roughly left-right symmetric around its own local origin (check the mesh's
  bounding box before trusting it blindly on a very asymmetric model).
- `dy` (raise) / `dz` (pull closer to camera) depend on the gun's own length/proportions — treat
  these as a much weaker prior and expect to re-tune per gun.

**There is no way to preview this inside Blockbench** — checked directly: Blockbench's native
Display-preset preview (`Formats.java_block`/`bedrock_block`, `display_mode: true`) requires
`meshes: false` (cubes only), and the only format that can hold an obj³ baked mesh (`free`) has no
display preview at all. No format supports both. Plain viewport screenshots are just Blockbench's
editor camera, not Minecraft's hand-render matrix — don't trust them for this. The real client is
the only ground truth; **binary-search `--dx`** against it (adjust, rebuild, restart, reconnect,
look, repeat) rather than guessing blindly — converges in a handful of rounds once you have one
data point in each direction.

Once the pose is confirmed, wire it into the real weapon definition:
`customModelDataAiming = "<name>_aiming"` on the `Gun(...)` call in `TestWeapons.kt`.

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
- Launching `gradlew.bat` for either the server or `morellia-testclient` from a Bash tool's
  `cmd //c` wrapper silently fails with "not recognized as an internal or external command" when
  the working directory path contains a space (e.g. `Minecraft Dev\morellia-testclient`) — `cd`,
  `dir`, and `where` all work fine from the same wrapper, only the actual `gradlew.bat` invocation
  breaks, for reasons not fully root-caused. Use the PowerShell tool instead
  (`Start-Process -FilePath ".\gradlew.bat" -ArgumentList ... -RedirectStandardOutput ...`) for
  anything launched from a path with a space in it.

## 7. Wrap up

- Add a `resourcepack/CREDITS.md` entry for the source model (creator, license, URL, pull date).
- Once the pose is confirmed, remove the temporary `DevLoadout.kt` scaffolding and wire the
  finished obj³ model into the real weapon-definition pipeline the same way `springfield.json`/
  `karabiner.json` are wired (check `TestWeapons.kt`).
- Merge the export's `assets/objcubed/...` files for real into `resourcepack/` (review the diff —
  especially the atlas/shader files — rather than copying the scratch merge wholesale).
