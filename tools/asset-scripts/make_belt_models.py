"""Scrolling belt textures and belt models for vanilla clients.

Create scrolls the belt texture with a client-side UV shift. A vanilla client can only play animated textures, so this
script makes, for the belt, offset-belt and diagonal-belt textures:

  textures/block/belt_scroll/<kind>_<p|n>_r<k>_<f>.png (+ .png.mcmeta)

a vertical strip whose frames are the texture shifted by k rows each (looping), shown f ticks per frame, in the
positive (p: the picture moves towards decreasing v, like Create's positive scroll) and negative (n) direction, and
one model per belt partial and strip:

  models/block/belt_scroll/<partial>_<p|n>_r<k>_<f>.json   (a child of Create's models/block/belt/<partial>)

plus copies of Create's belt casing models that use the andesite casing texture:

  models/block/belt_casing_andesite/<name>.json

RATES must match BeltScroll.RATES in the mod (kinetics/belt/BeltScroll.java): the server picks a model from the
belt's speed (see the documentation there).

Usage: make_belt_models.py CREATE_DIR PORT_DIR   (after copy_assets.py)
"""
import json, math, os, shutil, sys

from PIL import Image

create_dir, port_dir = sys.argv[1], sys.argv[2]
A = os.path.join(port_dir, 'src/main/resources/assets/create')
SRC = os.path.join(create_dir, 'src/main/resources/assets/create')

# {k, f}: rows per frame, ticks per frame
RATES = [(1, 32), (1, 16), (3, 32), (1, 8), (3, 16), (1, 4), (3, 8),
         (1, 2), (3, 4), (1, 1), (3, 2), (2, 1), (3, 1), (4, 1)]

# texture kind -> (source texture, loop length in rows, first row of the loop, rows the picture is offset by).
# Create scrolls the bottom run of a belt over the same picture as the top (belt_scroll.png) but half a loop
# further along, so "belt_offset" is the belt texture shifted by 8 rows.
KINDS = {
    'belt': ('block/belt', 16, 0, 0),
    'belt_offset': ('block/belt', 16, 0, 8),
    'belt_diagonal': ('block/belt_diagonal', 12, 4, 0),
}

# model partial -> (texture variable to replace, kind). Diagonal models also replace "particle".
PARTIALS = {
    'start': ('0', 'belt'), 'middle': ('0', 'belt'), 'end': ('0', 'belt'),
    'start_bottom': ('1', 'belt_offset'), 'middle_bottom': ('1', 'belt_offset'), 'end_bottom': ('1', 'belt_offset'),
    'diagonal_start': ('0', 'belt_diagonal'), 'diagonal_middle': ('0', 'belt_diagonal'),
    'diagonal_end': ('0', 'belt_diagonal'),
}

TEX_OUT = os.path.join(A, 'textures/block/belt_scroll')
MODEL_OUT = os.path.join(A, 'models/block/belt_scroll')
os.makedirs(TEX_OUT, exist_ok=True)
os.makedirs(MODEL_OUT, exist_ok=True)


def strip(image, loop, first, offset, k, positive):
    """The animation strip: frame i is the picture shifted by offset + i * k rows."""
    frames = loop // math.gcd(k, loop)
    out = Image.new('RGBA', (16, 16 * frames))
    for i in range(frames):
        shift = (i * k) % loop
        if not positive:
            shift = -shift
        shift += offset
        for y in range(16):
            row = first + ((y - first + shift) % loop)
            out.paste(image.crop((0, row, 16, row + 1)), (0, i * 16 + y))
    return out


count = 0
for kind, (source, loop, first, offset) in KINDS.items():
    image = Image.open(os.path.join(A, 'textures', source + '.png')).convert('RGBA')
    assert image.size == (16, 16), (kind, image.size)
    for positive in (True, False):
        for k, f in RATES:
            name = '%s_%s_r%d_%d' % (kind, 'p' if positive else 'n', k, f)
            strip(image, loop, first, offset, k, positive).save(os.path.join(TEX_OUT, name + '.png'))
            with open(os.path.join(TEX_OUT, name + '.png.mcmeta'), 'w') as fh:
                json.dump({'animation': {'frametime': f}}, fh)
            count += 1
print('belt scroll textures', count)

models = 0
for partial, (variable, kind) in PARTIALS.items():
    for positive in (True, False):
        for k, f in RATES:
            tex = 'create:block/belt_scroll/%s_%s_r%d_%d' % (kind, 'p' if positive else 'n', k, f)
            textures = {variable: tex}
            if kind == 'belt_diagonal':
                textures['particle'] = tex
            model = {'parent': 'create:block/belt/' + partial, 'textures': textures}
            name = '%s_%s_r%d_%d' % (partial, 'p' if positive else 'n', k, f)
            with open(os.path.join(MODEL_OUT, name + '.json'), 'w') as fh:
                json.dump(model, fh, indent=1)
            models += 1
print('belt scroll models', models)

# Andesite belt casing: Create's BeltModel swaps the brass casing sprite for this one on andesite-cased belts
BRASS = 'create:block/belt/brass_belt_casing'
ANDESITE = 'create:block/belt/andesite_belt_casing'
shutil.copyfile(os.path.join(SRC, 'textures/block/belt/andesite_belt_casing.png'),
                os.path.join(A, 'textures/block/belt/andesite_belt_casing.png'))
casing_src = os.path.join(A, 'models/block/belt_casing')
casing_out = os.path.join(A, 'models/block/belt_casing_andesite')
os.makedirs(casing_out, exist_ok=True)
casings = 0
for f in sorted(os.listdir(casing_src)):
    model = json.load(open(os.path.join(casing_src, f)))
    changed = False
    for key, value in model.get('textures', {}).items():
        if value == BRASS:
            model['textures'][key] = ANDESITE
            changed = True
    assert changed, f
    with open(os.path.join(casing_out, f), 'w') as fh:
        json.dump(model, fh, indent=1)
    casings += 1
print('andesite casing models', casings)
