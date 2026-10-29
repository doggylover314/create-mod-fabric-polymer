"""Coloured copies of the factory gauge arrow, line and dotted models.

Create tints the arrows of a gauge connection with the vertex colour and scrolls the texture while a request is
pending. A display entity cannot tint an item model per entity, so the pack gets one texture and one model per colour
a connection can show (see FactoryPanelModel): textures/block/factory_gauge_connections/<rgb>[_anim|_dotted].png and
models/block/factory_gauge/conn/<kind>_<direction>_<rgb>[_anim].json, each model a child of Create's own.
Usage: make_factory_gauge_models.py CREATE_DIR PORT_DIR   (run after copy_assets.py)
"""
import json, os, shutil, sys

from PIL import Image

create, out_root = sys.argv[1], sys.argv[2]
SRC = [os.path.join(create, 'src/main/resources/assets/create'), os.path.join(create, 'src/generated/resources/assets/create')]
OUT = os.path.join(out_root, 'src/main/resources/assets/create')

# the colours of FactoryPanelModel
COLORS = [0x888898, 0x5B3B3B, 0x9EFF7F, 0x22AFAF, 0x3D6EBD, 0xEAF2EC, 0xE5654B, 0xEF0000, 0x580101, 0x3C9852]
ANIMATED = [0x3D6EBD, 0x22AFAF, 0xEAF2EC, 0xE5654B]
DOTTED = [0x3C9852]
DIRECTIONS = ['north', 'east', 'south', 'west']


def find(rel):
    for s in SRC:
        p = os.path.join(s, rel)
        if os.path.exists(p):
            return p
    raise FileNotFoundError(rel)


def tint(source, color, dest):
    image = Image.open(find('textures/block/' + source + '.png')).convert('RGBA')
    r, g, b = (color >> 16) & 255, (color >> 8) & 255, color & 255
    pixels = [(pr * r // 255, pg * g // 255, pb * b // 255, pa) for pr, pg, pb, pa in image.get_flattened_data()]
    image.putdata(pixels)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    image.save(dest)


def model(kind, direction, name, texture):
    path = os.path.join(OUT, 'models/block/factory_gauge/conn', '%s_%s_%s.json' % (kind, direction, name))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    ref = 'create:block/factory_gauge_connections/' + texture
    json.dump({'parent': 'create:block/factory_gauge/connections/%s_%s' % (kind, direction),
               'textures': {'0': ref, 'particle': ref}}, open(path, 'w'), indent=1)


TEXTURES = os.path.join(OUT, 'textures/block/factory_gauge_connections')
shutil.rmtree(TEXTURES, ignore_errors=True)
shutil.rmtree(os.path.join(OUT, 'models/block/factory_gauge/conn'), ignore_errors=True)

for color in COLORS:
    name = '%06x' % color
    tint('factory_panel_connections', color, os.path.join(TEXTURES, name + '.png'))
    for d in DIRECTIONS:
        for kind in ('arrow', 'line'):
            model(kind, d, name, name)
for color in ANIMATED:
    name = '%06x' % color
    tint('factory_panel_connections_animated', color, os.path.join(TEXTURES, name + '_anim.png'))
    shutil.copyfile(find('textures/block/factory_panel_connections_animated.png.mcmeta'),
                    os.path.join(TEXTURES, name + '_anim.png.mcmeta'))
    for d in DIRECTIONS:
        for kind in ('arrow', 'line'):
            model(kind, d, name + '_anim', name + '_anim')
for color in DOTTED:
    name = '%06x' % color
    tint('factory_panel_dotted_connections', color, os.path.join(TEXTURES, name + '_dotted.png'))
    for d in DIRECTIONS:
        model('dotted', d, name, name + '_dotted')
print('factory gauge connection models: %d colours, %d animated, %d dotted' % (len(COLORS), len(ANIMATED), len(DOTTED)))
