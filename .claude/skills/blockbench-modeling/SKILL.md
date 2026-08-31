---
name: blockbench-modeling
description: Build or edit Minecraft models (items, blocks, custom weapons/props) in Blockbench via the blockbench MCP tools for the Morellia resource pack. Use whenever placing cubes, texturing, or exporting a model through mcp__blockbench__* tools.
---

Distilled from the Blockbench MCP plugin author's own agent guidance (see `blockbench-reference/prompts/` in this repo for the verbatim source files, pulled from https://github.com/jasonjgardner/blockbench-mcp-plugin).

**Two different workflows live in this repo — pick the right one before starting:**
- Building a model from scratch out of cubes (a simple item/block/prop) → the cube-placement
  workflow below (`java_block`/`bedrock_block` formats).
- Importing an existing low-poly Blender/glTF/`.glb`/`.obj` mesh (any creator, any model — this is
  not specific to any one weapon or source) and getting it held/worn correctly in-game → **stop
  here and follow
  [`blockbench-reference/obj3_weapon_import_playbook.md`](../../docs/blockbench-reference/obj3_weapon_import_playbook.md)
  instead**, cross-referencing
  [`blockbench-reference/gltf_import_scale_bug.md`](../../docs/blockbench-reference/gltf_import_scale_bug.md)
  for known importer bugs. That pipeline (the `obj-cubed` plugin, mesh recentering, the
  swing-cancellation translation formula, the `offsetY=0.5` export fix) is a completely different
  mechanism from the cube/`display`-transform workflow below and the two should not be mixed.

## Hard format rules (violate these and the model silently fails to load or gets rejected)

**Java Edition block/item models** (`format: "java_block"`):
- Element rotation is limited to **22.5° steps** (`-45, -22.5, 0, 22.5, 45`) and **one axis per element**. Anything else and Minecraft's parser throws on load. Always round rotations to the nearest valid step before calling `place_cube`/`modify_cube`.
- Geometry is capped at **3×3×3 blocks**, i.e. every `from`/`to` coordinate must stay within **-16 to 32** on every axis. Oversized handheld items (long guns, spears) should be scaled up only via the `display` transforms (`firstperson_righthand`, `thirdperson_righthand`, `gui`, `ground`, `fixed`), not by exceeding this range. Check `validator://status` (MCP resource) after building — it flags cubes over the limit.

**Bedrock Edition block models** (`format: "bedrock_block"`):
- Total size capped at **30 pixels** per axis, offsettable up to **7 pixels** from block center.

## Workflow order (per the plugin's own `model_creation_ui` guidance)

1. `list_outline` — check current project state before touching anything.
2. `create_project` — only if no project is open or a fresh one is asked for.
3. `add_group` — organize parts into logical bones/assemblies before placing geometry.
4. `place_cube` / `modify_cube` — build and adjust. `remove_element` to delete.
5. For anything more than a handful of simple parts, prefer constructing the whole thing as a **`.geo.json` string and importing it in one shot via `from_geo_json`** instead of incrementally eyeballing `place_cube` offsets — coordinates get computed once as structured data instead of guessed interactively, which is far less error-prone for multi-part builds.
6. `risky_eval` is available for direct Blockbench/Node/Electron API access (e.g. inspecting `Project.display_settings`, `DisplayMode.slots`) when no dedicated tool exists — see `blockbench_native_apis.md` and `blockbench_code_eval_safety.md` for the v5.0+ permission model (`requireNativeModule()`, `SystemInfo`, no bare `fs`/`os`/`child_process`). Never use it to make system changes.

## Matching a reference image — lessons from getting this wrong

A reference sheet's "cube blueprint" table usually gives only the **overall bounding box** per named part (e.g. "STOCK: X:12 Y:4 Z:3") — that is not the actual silhouette. Building one rectangular cube per bounding box produces something that reads as a "plank with a tube," not the object in the hero render/exploded view.

Before placing cubes:
- Look at the hero render AND exploded-parts view, not just the dimension table. Identify where the silhouette **steps or tapers** (e.g. a gunstock's comb → wrist → lock transition, a hilt's guard → grip → pommel) and plan multiple cubes per labeled "part" to capture that profile.
- Keep it voxel/blocky (axis-aligned cubes only, per the build notes convention) — don't reach for curves — but do vary cube dimensions along the length to imply the shape, the way vanilla Minecraft tools/weapons do.

After placing cubes, always self-check before calling it done:
- Take an **orthographic side view** (`set_camera_angle` with `projection: "orthographic"`, camera position perpendicular to the model's long axis) and a 3/4 perspective view.
- Compare directly against the reference's own orthographic views (top/side/front) at the same angle — silhouette-to-silhouette, not just "does it have the right parts."
- Check `validator://status` for format violations before considering the model finished.

## Display transforms

`Project.display_settings` is empty by default (no per-slot overrides); Blockbench's `java_block` codec falls back to sane vanilla defaults on export even with nothing set. Only reach for `risky_eval` to set explicit `firstperson_righthand`/`thirdperson_righthand`/`gui`/`ground`/`fixed` transforms when the default in-hand scale/position actually looks wrong for an oversized item (e.g. a full-length musket) — verify with a screenshot before assuming it's needed.
