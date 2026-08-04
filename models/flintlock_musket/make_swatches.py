from PIL import Image
import os

base = os.path.dirname(__file__)
tex_dir = os.path.join(base, 'textures')
os.makedirs(tex_dir, exist_ok=True)

swatches = {
    'wood.png': '#6B4426',
    'iron.png': '#7A7A7A',
    'brass.png': '#C28F23',
}

for name, hexcol in swatches.items():
    r, g, b = int(hexcol[1:3], 16), int(hexcol[3:5], 16), int(hexcol[5:7], 16)
    Image.new('RGB', (16, 16), (r, g, b)).save(os.path.join(tex_dir, name))
    print('wrote', name)
