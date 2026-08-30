"""Convert the raw SRTM15+ elevation GeoTIFF into a 16-bit grayscale heightmap PNG for
WorldPainter import, with an optional Gaussian smoothing pass.

Real elevation is clamped to [FLOOR_M, CEIL_M] and linearly normalized to the full 16-bit
pixel range (0=FLOOR_M, 65535=CEIL_M) -- this exact mapping is what agadir-import.js's
.fromLevels(0, 65535).toLevels(...) calls assume, so keep FLOOR_M/CEIL_M in sync between
this script and the .js scripts if you change them.

Usage: python convert_heightmap.py <src.tif> <out.png> [--sigma PX]
"""
import argparse

import numpy as np
import rasterio
from PIL import Image
from scipy.ndimage import gaussian_filter

FLOOR_M = -100.0  # deep-ocean clamp -- real trench depth is irrelevant, it's all just "underwater"
CEIL_M = 4700.0  # just above the trimmed box's real max (~4655m, near Mont Blanc)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("src", help="source GeoTIFF (e.g. from OpenTopography SRTM15+)")
    parser.add_argument("out", help="output 16-bit PNG path")
    parser.add_argument(
        "--sigma",
        type=float,
        default=3.0,
        help="Gaussian blur sigma in source pixels (~450m/px -> sigma=3 is ~1350m real radius). "
        "0 disables smoothing -- the unsmoothed first pass looked rough/jagged at block "
        "resolution, so 3.0 is the confirmed-good default.",
    )
    args = parser.parse_args()

    with rasterio.open(args.src) as ds:
        arr = ds.read(1).astype(np.float64)
        nodata_mask = np.isnan(arr)
        if nodata_mask.any():
            arr[nodata_mask] = FLOOR_M

    if args.sigma > 0:
        arr = gaussian_filter(arr, sigma=args.sigma, mode="nearest")

    clipped = np.clip(arr, FLOOR_M, CEIL_M)
    normalized = (clipped - FLOOR_M) / (CEIL_M - FLOOR_M)
    px16 = np.round(normalized * 65535).astype(np.uint16)

    Image.fromarray(px16, mode="I;16").save(args.out)
    print(f"saved {args.out}: floor={FLOOR_M}m ceil={CEIL_M}m sigma={args.sigma}px")
    print(f"real elevation range in source: {float(arr.min()):.1f}m .. {float(arr.max()):.1f}m")


if __name__ == "__main__":
    main()
