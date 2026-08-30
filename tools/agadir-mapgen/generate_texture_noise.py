"""Generate a smoothed random noise mask (natural patch-sized blobs, not salt-and-pepper
speckle) for scattering ground-texture variety (e.g. Dirt/Gravel/Sand patches within a
uniform Grass/Rock terrain band) so large elevation-band terrain assignments don't read as
flat, monotone single-material fields.

Usage: python generate_texture_noise.py <out.png> --width W --height H [--seed N] [--sigma PX]
"""
import argparse

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("out")
    parser.add_argument("--width", type=int, required=True)
    parser.add_argument("--height", type=int, required=True)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--sigma", type=float, default=8.0, help="blob size -- higher = larger patches")
    args = parser.parse_args()

    rng = np.random.default_rng(args.seed)
    noise = rng.random((args.height, args.width))
    smoothed = gaussian_filter(noise, sigma=args.sigma)
    smoothed = (smoothed - smoothed.min()) / (smoothed.max() - smoothed.min())
    px16 = np.round(smoothed * 65535).astype(np.uint16)
    Image.fromarray(px16, mode="I;16").save(args.out)
    print(f"saved {args.out}")


if __name__ == "__main__":
    main()
