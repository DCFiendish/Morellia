#!/usr/bin/env node
// Generates an ADS ("aiming") firstperson pose variant for an obj³-pipeline gun, reusing the
// existing rotation-cancelling hip-fire pose (see docs/blockbench-reference/obj3_weapon_import_playbook.md
// section 5) and layering an additional translation delta on top of it. No mesh/geometry edits --
// same trick as the pre-obj³ *-aiming.json guns (musket/springfield/karabiner): clone the
// firstperson model file(s), change only the display.firstperson_* translation.
//
// Usage:
//   node tools/gen-obj3-aiming-pose.js <base_custom_model_data> [--dx=-8.5] [--dy=2.5] [--dz=5]
//
// Example (this is exactly what produced the confirmed Kar98k aiming pose):
//   node tools/gen-obj3-aiming-pose.js kar98k_lowpoly --dx=-8.5 --dy=2.5 --dz=5
//
// The defaults ARE the Kar98k-confirmed deltas -- a reasonable starting guess for any gun, not a
// promise. Why: dx corrects for Mojang's fixed client-side right-hand render anchor, which is the
// SAME constant for every item, so it tends to transfer across guns whose mesh is roughly
// left-right symmetric around its own local origin (true for the Kar98k -- check with the bounding
// box before trusting it blindly for a very different-shaped weapon). dy/dz (raise + pull closer)
// depend on the gun's own length/proportions and should be treated as a much weaker prior --
// expect to re-tune them per gun.
//
// After running this: rebuild the pack (`jar cf resourcepack.zip -C resourcepack .` from repo
// root), kill + restart the local server (it hashes the pack once at boot -- ResourcePack.kt),
// reconnect the test client, and look at it. There's no way to preview this accurately inside
// Blockbench -- its native Display-preset preview needs a cube-only project format, and obj³ guns
// are meshes, so the real client is the only ground truth. To iterate the horizontal centering,
// re-run this script with a different --dx (binary-search style) and repeat the rebuild/restart/
// reconnect loop -- that IS the loop, there's no shortcut around needing the real client each time.
//
// Idempotent: re-running for the same base name replaces its existing "<base>_aiming" case in
// iron_ingot.json instead of duplicating it, so iterating dx/dy/dz is just re-run + rebuild + restart.

const fs = require('fs');
const path = require('path');

const REPO_ROOT = path.resolve(__dirname, '..');
const MODELS_DIR = path.join(REPO_ROOT, 'resourcepack/assets/objcubed/models/item');
const ITEM_SELECTOR_PATH = path.join(REPO_ROOT, 'resourcepack/assets/minecraft/items/iron_ingot.json');

function parseArgs(argv) {
  const positional = [];
  const flags = { dx: -8.5, dy: 2.5, dz: 5 };
  for (const arg of argv) {
    const m = arg.match(/^--(dx|dy|dz)=(-?[\d.]+)$/);
    if (m) {
      flags[m[1]] = parseFloat(m[2]);
    } else if (!arg.startsWith('--')) {
      positional.push(arg);
    } else {
      throw new Error(`Unrecognized flag: ${arg}`);
    }
  }
  if (positional.length !== 1) {
    throw new Error('Usage: node tools/gen-obj3-aiming-pose.js <base_custom_model_data> [--dx=N] [--dy=N] [--dz=N]');
  }
  return { baseName: positional[0], ...flags };
}

function loadJson(p) {
  return JSON.parse(fs.readFileSync(p, 'utf8'));
}

function writeJson(p, data) {
  fs.writeFileSync(p, JSON.stringify(data));
}

function generateAimingHandModel(baseName, hand, dx, dy, dz) {
  const srcPath = path.join(MODELS_DIR, `${baseName}_firstperson_${hand}.json`);
  if (!fs.existsSync(srcPath)) {
    throw new Error(`Missing base firstperson model: ${srcPath} -- has this gun been exported through obj³ yet?`);
  }
  const model = loadJson(srcPath);
  const key = `firstperson_${hand}`;
  const hip = model.display[key];
  if (!hip) throw new Error(`${srcPath} has no display.${key} entry`);

  const signedDx = hand === 'righthand' ? dx : -dx;
  model.display[key] = {
    rotation: hip.rotation,
    translation: [hip.translation[0] + signedDx, hip.translation[1] + dy, hip.translation[2] + dz],
  };

  const outPath = path.join(MODELS_DIR, `${baseName}_aiming_firstperson_${hand}.json`);
  writeJson(outPath, model);
  return { outPath, hipTranslation: hip.translation, aimTranslation: model.display[key].translation };
}

function upsertSelectorCase(baseName) {
  const selector = loadJson(ITEM_SELECTOR_PATH);
  const baseCase = selector.model.cases.find((c) => c.when === baseName);
  if (!baseCase) {
    throw new Error(`No "${baseName}" case in ${ITEM_SELECTOR_PATH} -- this gun isn't wired into the item selector yet`);
  }

  const aimingWhen = `${baseName}_aiming`;
  const aimingCase = JSON.parse(JSON.stringify(baseCase));
  aimingCase.when = aimingWhen;
  for (const sub of aimingCase.model.cases) {
    if (sub.when === 'firstperson_righthand') sub.model.model = `objcubed:item/${baseName}_aiming_firstperson_righthand`;
    if (sub.when === 'firstperson_lefthand') sub.model.model = `objcubed:item/${baseName}_aiming_firstperson_lefthand`;
  }
  // Everything else (thirdperson_*, ground, on_shelf, fallback) intentionally stays pointed at the
  // base gun's models -- aiming only changes the two firstperson hand poses.

  const existingIdx = selector.model.cases.findIndex((c) => c.when === aimingWhen);
  if (existingIdx >= 0) {
    selector.model.cases[existingIdx] = aimingCase;
  } else {
    const baseIdx = selector.model.cases.indexOf(baseCase);
    selector.model.cases.splice(baseIdx + 1, 0, aimingCase);
  }

  fs.writeFileSync(ITEM_SELECTOR_PATH, JSON.stringify(selector, null, 2) + '\n');
  return existingIdx >= 0 ? 'replaced' : 'inserted';
}

function main() {
  const { baseName, dx, dy, dz } = parseArgs(process.argv.slice(2));

  const rh = generateAimingHandModel(baseName, 'righthand', dx, dy, dz);
  const lh = generateAimingHandModel(baseName, 'lefthand', dx, dy, dz);
  const action = upsertSelectorCase(baseName);

  console.log(`${baseName}_aiming: ${action} case in iron_ingot.json`);
  console.log(`  righthand hip=${JSON.stringify(rh.hipTranslation)} -> aim=${JSON.stringify(rh.aimTranslation)}`);
  console.log(`  lefthand  hip=${JSON.stringify(lh.hipTranslation)} -> aim=${JSON.stringify(lh.aimTranslation)}`);
  console.log('Next: rebuild the pack, restart the server, reconnect, and look. Re-run with a');
  console.log('different --dx/--dy/--dz to iterate -- this replaces the same files/case in place.');
}

main();
