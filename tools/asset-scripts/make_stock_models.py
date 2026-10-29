"""Splits the table cloth models into the pieces the server draws as separate display entities.

Create culls the drapes on the sides of a table cloth that touch another block, and adds a corner piece between two
drapes at run time (TableClothModel, a baked model wrapper). A display entity cannot do either, so each cloth gets a
base model, one model per drape and one per corner, and the server picks the visible ones.
Usage: make_stock_models.py CREATE_DIR PORT_DIR   (run after copy_assets.py)
"""
import json, os, sys

create, out_root = sys.argv[1], sys.argv[2]
SRC = os.path.join(create, 'src/main/resources/assets/create/models/block/table_cloth')
OUT = os.path.join(out_root, 'src/main/resources/assets/create/models/block/table_cloth')

COLOURS = 'white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black'.split()
TYPES = COLOURS + ['andesite', 'brass', 'copper']
# element indices of the block model: the top, then two elements per drape
PARTS = {'base': [0], 'flap_west': [1, 2], 'flap_north': [3, 4], 'flap_east': [5, 6], 'flap_south': [7, 8]}
CORNERS = {'corner_sw': 'south_west', 'corner_nw': 'north_west', 'corner_ne': 'north_east', 'corner_se': 'south_east'}

block = json.load(open(os.path.join(SRC, 'block.json')))
os.makedirs(OUT, exist_ok=True)


def write(name, elements, texture):
    d = {'parent': 'minecraft:block/block',
         'textures': {'0': texture, 'particle': '#0'},
         'render_type': 'minecraft:cutout',
         'elements': elements}
    json.dump(d, open(os.path.join(OUT, name + '.json'), 'w'), indent=1)


for t in TYPES:
    texture = 'create:block/table_cloth/' + t
    for part, indices in PARTS.items():
        write('%s_%s' % (t, part), [block['elements'][i] for i in indices], texture)
    for part, source in CORNERS.items():
        corner = json.load(open(os.path.join(SRC, source + '.json')))
        write('%s_%s' % (t, part), corner['elements'], texture)
print('table cloth models', len(TYPES) * (len(PARTS) + len(CORNERS)))
