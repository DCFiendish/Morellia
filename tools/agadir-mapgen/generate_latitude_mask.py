"""Generate a 16-bit grayscale latitude gradient mask, same pixel dimensions as the source
elevation GeoTIFF, for latitude-based biome zoning (elevation alone puts pine forest on
Moroccan mountains and deciduous forest on Mediterranean coastline, which is wrong -- real
Europe's vegetation is latitude-driven as much as elevation-driven).

Pixel value maps linearly: 0 = SOUTH bound, 65535 = NORTH bound. Row 0 = north (standard
north-up GeoTIFF convention, matches the source data).

Usage: python generate_latitude_mask.py <out.png> --width W --height H [--south S] [--north N]
"""
import argparse

import numpy as np
from PIL import Image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("out")
    parser.add_argument("--width", type=int, required=True)
    parser.add_argument("--height", type=int, required=True)
    parser.add_argument("--south", type=float, default=33.0)
    parser.add_argument("--north", type=float, default=58.5)
    args = parser.parse_args()

    rows = np.arange(args.height).reshape(-1, 1)
    lat = args.north - (rows / (args.height - 1)) * (args.north - args.south)
    lat_grid = np.repeat(lat, args.width, axis=1)

    px16 = np.round((lat_grid - args.south) / (args.north - args.south) * 65535).astype(np.uint16)
    Image.fromarray(px16, mode="I;16").save(args.out)
    print(f"saved {args.out}: row0={lat_grid[0, 0]:.2f}N rowLast={lat_grid[-1, 0]:.2f}N")


if __name__ == "__main__":
    main()
