#!/usr/bin/env python3
"""
Regenerates assets/nodisium/sounds.json from whatever .ogg files exist under
assets/nodisium/sounds/guns/<gun_name>/<event>.ogg.

Convention this depends on (see modules/combat/objects/Gun.kt's soundFire/soundReload
defaults): a gun named "<gun_name>" plays sound event "<gun_name>.<event>" (e.g.
"musket.fire"). Sound event names map 1:1 to file paths here, so adding a new gun's
sounds is just: drop fire.ogg/reload.ogg under sounds/guns/<gun_name>/, rerun this
script. No manual JSON editing, no matter how many guns get added.

Run from anywhere; paths are resolved relative to this file's own directory.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOUNDS_DIR = ROOT / "assets" / "nodisium" / "sounds"
GUNS_DIR = SOUNDS_DIR / "guns"
OUTPUT = ROOT / "assets" / "nodisium" / "sounds.json"


def main() -> None:
    entries: dict[str, dict] = {}
    if GUNS_DIR.is_dir():
        for gun_dir in sorted(GUNS_DIR.iterdir()):
            if not gun_dir.is_dir():
                continue
            for ogg in sorted(gun_dir.glob("*.ogg")):
                event = f"{gun_dir.name}.{ogg.stem}"
                sound_path = f"nodisium:guns/{gun_dir.name}/{ogg.stem}"
                entries[event] = {"sounds": [sound_path]}

    OUTPUT.write_text(json.dumps(entries, indent=2, sort_keys=True) + "\n")
    print(f"Wrote {len(entries)} sound event(s) to {OUTPUT.relative_to(ROOT.parent)}")


if __name__ == "__main__":
    main()
