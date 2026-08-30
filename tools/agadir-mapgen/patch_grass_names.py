"""Patch every .mca region file in an exported WorldPainter world, renaming the palette block
name 'minecraft:grass' -> 'minecraft:short_grass'.

Real Minecraft renamed the short decorative grass block around 1.20.5; Minestom 26.2's
registry has no 'minecraft:grass' entry at all, which crashes the server with
"Unknown block minecraft:grass". WorldPainter 2.27.0's 26.1 export support still writes the
old name for its default grass-vegetation feature -- this recurs on EVERY export, re-run this
after every wpscript run before pointing AgadirWorld.kt at a new export.

Rebuilds each region file from scratch (proper sector-aligned layout) rather than patching in
place, since the renamed string is a different byte length.

Usage: python patch_grass_names.py <path-to-.../dimensions/minecraft/overworld/region>
"""
import argparse
import glob
import io
import zlib

import nbtlib

OLD_NAME = "minecraft:grass"
NEW_NAME = "minecraft:short_grass"


def rename_strings(tag):
    """Recursively rename OLD_NAME -> NEW_NAME wherever it appears as an exact
    String tag value. Returns True if anything was changed."""
    changed = False
    if isinstance(tag, nbtlib.Compound):
        for key in list(tag.keys()):
            value = tag[key]
            if isinstance(value, nbtlib.String) and str(value) == OLD_NAME:
                tag[key] = nbtlib.String(NEW_NAME)
                changed = True
            else:
                changed = rename_strings(value) or changed
    elif isinstance(tag, nbtlib.List):
        for i, value in enumerate(tag):
            if isinstance(value, nbtlib.String) and str(value) == OLD_NAME:
                tag[i] = nbtlib.String(NEW_NAME)
                changed = True
            else:
                changed = rename_strings(value) or changed
    return changed


def patch_region(path):
    with open(path, "rb") as f:
        data = bytearray(f.read())

    location_table = data[0:4096]
    timestamp_table = data[4096:8192]

    entries = []  # (idx, new_compressed_bytes_with_header) in original chunk order
    total_renamed = 0
    chunks_touched = 0

    for idx in range(1024):
        off = int.from_bytes(location_table[idx * 4 : idx * 4 + 3], "big")
        if off == 0:
            entries.append((idx, None))
            continue

        start = off * 4096
        length = int.from_bytes(data[start : start + 4], "big")
        comp_type = data[start + 4]
        payload = bytes(data[start + 5 : start + 4 + length])

        if comp_type != 2:
            entries.append((idx, bytes(data[start : start + 4 + length])))
            continue

        raw = zlib.decompress(payload)
        if OLD_NAME.encode() not in raw:
            entries.append((idx, bytes(data[start : start + 4 + length])))
            continue

        nbt = nbtlib.Compound.parse(io.BytesIO(raw))
        did_change = rename_strings(nbt)
        if not did_change:
            entries.append((idx, bytes(data[start : start + 4 + length])))
            continue

        out = io.BytesIO()
        nbt.write(out)
        new_raw = out.getvalue()
        new_payload = zlib.compress(new_raw, 6)
        new_length = len(new_payload) + 1
        header = new_length.to_bytes(4, "big") + bytes([2])
        entries.append((idx, header + new_payload))
        total_renamed += 1
        chunks_touched += 1

    if total_renamed == 0:
        return 0, 0

    new_location_table = bytearray(4096)
    body = bytearray()
    sector_cursor = 2

    for idx, raw_bytes in entries:
        if raw_bytes is None:
            continue
        sectors_needed = (len(raw_bytes) + 4095) // 4096
        padded = raw_bytes + b"\x00" * (sectors_needed * 4096 - len(raw_bytes))
        new_location_table[idx * 4 : idx * 4 + 3] = sector_cursor.to_bytes(3, "big")
        new_location_table[idx * 4 + 3] = sectors_needed
        body += padded
        sector_cursor += sectors_needed

    new_data = bytes(new_location_table) + bytes(timestamp_table) + bytes(body)
    with open(path, "wb") as f:
        f.write(new_data)

    return chunks_touched, total_renamed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("region_dir", help="path to the world's .../overworld/region directory")
    args = parser.parse_args()

    region_files = sorted(glob.glob(f"{args.region_dir}/r.*.*.mca"))
    print(f"Scanning {len(region_files)} region files for '{OLD_NAME}'...")
    total_chunks = 0
    total_renames = 0
    for path in region_files:
        chunks, renames = patch_region(path)
        if chunks:
            print(f"  {path.split('/')[-1]}: patched {chunks} chunks, {renames} renames")
        total_chunks += chunks
        total_renames += renames
    print(f"Done. {total_chunks} chunks patched, {total_renames} total renames.")


if __name__ == "__main__":
    main()
