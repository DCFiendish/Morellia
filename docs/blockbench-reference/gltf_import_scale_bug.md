# Blockbench's native glTF importer mis-scales matrix-transformed nodes

Found 2026-08-30 importing TastyTony's "Low-Poly Kar98K" (Sketchfab, CC-BY 4.0). **Relevant to every
model from this creator/collection** — TastyTony has several other rifles in the same "Low-Poly
Rifles" series we plan to use, very likely exported from Blender the same way, so expect this same
bug on those too. Two separate gotchas, both confirmed on this file:

## Gotcha 1: scale bug on matrix-transformed nodes (the actual geometry damage)

Blockbench (current version, native `import_gltf` action — no marketplace plugin needed anymore,
contrary to the older `research-todo` note that assumed the "glTF Importer" marketplace plugin was
required) has a **real bug**: some nodes come in wildly oversized, others come in correct, and
there's no error/warning either way.

**Root cause**: a glTF node's transform can be encoded either as separate `translation`/`rotation`/
`scale` fields, or as a single 4x4 `matrix`. Blockbench's importer appears to mis-extract the scale
component specifically for `matrix`-form nodes. Nodes using plain TRS fields (or identity matrices)
import fine.

**Symptoms in-viewport**: a few parts render as huge slabs/blobs sticking out of the model, or as
long thin pieces floating disconneted from where they should attach, while the rest of the model
looks basically right. It reads as "random giant things sticking out" and "stuff not proportioned" —
easy to mistake for a bad/corrupt source file, but it isn't.

**How to confirm it's this bug, not a bad source file**, before spending time on it:
1. Compare Sketchfab's own stated triangle/vertex counts against what lands in Blockbench
   (`list_outline` / sum of `Object.keys(mesh.faces).length` across `Mesh.all` via `risky_eval`).
   If they match exactly, the *data* imported faithfully — any visual wrongness is a
   transform/positioning problem, not lost/corrupted geometry.
2. Parse the raw `.glb`'s JSON chunk directly (see script below) and check `gltf.skins` — rule out
   an armature/skin problem first (Blockbench genuinely cannot import armatures, but that failure
   mode is different: it would just drop the mesh or leave it unposed, not blow up scale).
3. For a specific suspicious part, decompose its raw node's transform (`THREE.Matrix4.decompose`)
   and compute the **correct** world-space bounding box by walking its parent chain by hand.
   Compare that against the part's actual bounding box in Blockbench (`Mesh.all.find(...)`, min/max
   over `.vertices`). A ratio of ~10x-50x between them (not a subtle rounding difference) confirms
   the bug.

**The fix that actually works — don't patch individual bad parts, bypass the importer entirely**:
Blockbench's own `import_gltf` internally uses Three.js's `GLTFLoader` to parse the file *correctly*
first (that parse step is fine — it's Blockbench's own subsequent conversion into its internal
mesh/group format that's buggy). So: call `THREE.GLTFLoader` directly yourself inside `risky_eval`,
walk the resulting (correctly-computed) `scene`, and build fresh Blockbench `Mesh` objects straight
from each Three.js mesh's `matrixWorld` + `geometry.attributes.position`. This sidesteps Blockbench's
broken step completely rather than trying to find and fix every individual bad node.

```js
// Run inside Blockbench via the MCP risky_eval tool.
// 1) Read the raw file and hand its ArrayBuffer to THREE's own loader.
var fs = require('fs');
var buf = fs.readFileSync('<path to .glb>');
var arrayBuffer = buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength);

window.__rebuildStatus = 'loading';
new THREE.GLTFLoader().parse(arrayBuffer, '', function (gltf) {
  window.__parsedGltf = gltf;
  gltf.scene.updateMatrixWorld(true); // forces correct matrixWorld on every node
  window.__rebuildStatus = 'loaded';
}, function (err) { window.__rebuildStatus = 'error: ' + err; });
// poll window.__rebuildStatus in a follow-up risky_eval call until it says 'loaded'

// 2) Clear whatever Blockbench's own (buggy) import produced, then rebuild from the parsed scene.
Outliner.root.slice().forEach(function (el) { if (el.remove) el.remove(); });

var GLOBAL_SCALE = 1; // set once you know the target size, see Gotcha 2 below
var rootGroup = new Group({ name: 'Sketchfab_model' }).init();

window.__parsedGltf.scene.traverse(function (child) {
  if (!child.isMesh) return;
  var geom = child.geometry, posAttr = geom.attributes.position, idxAttr = geom.index;
  var mWorld = child.matrixWorld;
  var weldMap = {}, cornerToVertUuid = new Array(posAttr.count);
  var mesh = new Mesh({ name: child.name, vertices: {} });

  for (var i = 0; i < posAttr.count; i++) {
    var v = new THREE.Vector3(posAttr.getX(i), posAttr.getY(i), posAttr.getZ(i));
    v.applyMatrix4(mWorld).multiplyScalar(GLOBAL_SCALE);
    var key = v.x.toFixed(5) + ',' + v.y.toFixed(5) + ',' + v.z.toFixed(5);
    if (weldMap.hasOwnProperty(key)) { cornerToVertUuid[i] = weldMap[key]; }
    else {
      var uuid = mesh.addVertices([v.x, v.y, v.z])[0];
      weldMap[key] = uuid;
      cornerToVertUuid[i] = uuid;
    }
  }

  var triCount = idxAttr ? idxAttr.count / 3 : posAttr.count / 3;
  var faceObjs = [];
  for (var f = 0; f < triCount; f++) {
    var a = idxAttr ? idxAttr.getX(f * 3) : f * 3;
    var b = idxAttr ? idxAttr.getX(f * 3 + 1) : f * 3 + 1;
    var c = idxAttr ? idxAttr.getX(f * 3 + 2) : f * 3 + 2;
    var va = cornerToVertUuid[a], vb = cornerToVertUuid[b], vc = cornerToVertUuid[c];
    if (va === vb || vb === vc || va === vc) continue; // drop degenerate triangles
    faceObjs.push(new MeshFace(mesh, { vertices: [va, vb, vc] }));
  }
  mesh.addFaces.apply(mesh, faceObjs);
  mesh.addTo(rootGroup).init();
});
```

Note the "weld by rounded world-space position" step — it's there because Blockbench's own vertex
count is always *less* than the raw glTF position accessor's count (the raw file stores one entry
per triangle corner; shared corners collapse into one Blockbench vertex only if their final baked
positions match). Welding this way reproduced Blockbench's own original per-mesh vertex counts
exactly when checked against this file, confirming it's the same convention Blockbench itself uses.

## Gotcha 2: the file's own "meters" don't match real-world scale, and don't matter anyway

After the fix above, the whole model came out ~0.8 units long — technically "correct" relative to
whatever the source Blender file called a meter, but that's irrelevant for an item model. There's no
reason to treat glTF's meter convention as authoritative here. **Match the existing models' own
convention instead**: measure a comparable already-working model's bounding box (e.g.
`springfield.json`'s elements span ~40.8 units along its long axis) and rescale the new import
uniformly to match, based on how physically similar the two real-world weapons are. For the Kar98k
(real length ~1.11m, essentially identical to the Springfield 1903's ~1.10m), that meant scaling the
freshly-imported model until its long axis also read ~40 units — a single `vertex *= factor` pass
over every mesh after the rebuild above, not a re-import.

## Gotcha 3 (separate, cosmetic): no color/texture comes through

TastyTony's models (at least this one) encode per-part flat colors via the **deprecated
`KHR_materials_pbrSpecularGlossiness` glTF extension** (`diffuseFactor`, linear RGB) instead of an
actual image texture or the standard metallic-roughness workflow. Blockbench's importer doesn't read
this extension at all, so everything comes in with zero materials/textures assigned — this is
unrelated to Gotcha 1 and doesn't cause any geometry damage, just a blank/gray model. Read
`gltf.materials[i].extensions.KHR_materials_pbrSpecularGlossiness.diffuseFactor` directly from the
parsed JSON to recover the real intended colors per material, and `gltf.meshes[n].primitives[0].material`
to know which material each mesh/node used — see the "texturing" work this same session for the
follow-up script that bakes these into an actual palette texture.
