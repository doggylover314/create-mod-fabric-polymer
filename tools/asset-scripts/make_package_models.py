"""Vanilla JSON versions of the stock link models, which Create writes as NeoForge composite models.

A composite model keeps its elements and textures in named children ("base" and "bulb"); vanilla cannot load that.
This merges the children into one plain model and copies the textures they use.
Usage: make_package_models.py CREATE_DIR PORT_DIR   (run after copy_assets.py)
"""
import json, os, shutil, sys

create, out_root = sys.argv[1], sys.argv[2]
SRC = [os.path.join(create, 'src/main/resources/assets/create'), os.path.join(create, 'src/generated/resources/assets/create')]
OUT = os.path.join(out_root, 'src/main/resources/assets/create')
MODELS = ['block/stock_link/block_vertical', 'block/stock_link/block_vertical_powered',
          'block/stock_link/block_horizontal', 'block/stock_link/block_horizontal_powered']


def find(rel):
    for s in SRC:
        p = os.path.join(s, rel)
        if os.path.exists(p):
            return p
    raise FileNotFoundError(rel)


def copy_texture(ref):
    ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    if ns != 'create':
        return
    for ext in ('.png', '.png.mcmeta'):
        try:
            src = find('textures/%s%s' % (path, ext))
        except FileNotFoundError:
            continue
        dst = os.path.join(OUT, 'textures', path + ext)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(src, dst)


for m in MODELS:
    d = json.load(open(find('models/%s.json' % m)))
    if d.get('loader') != 'neoforge:composite':
        continue
    out = {k: v for k, v in d.items() if k not in ('loader', 'children')}
    out['render_type'] = 'minecraft:cutout'
    textures = dict(out.get('textures', {}))
    elements = list(out.get('elements', []))
    for child in d['children'].values():
        textures.update(child.get('textures', {}))
        elements.extend(child.get('elements', []))
    out['textures'] = textures
    out['elements'] = elements
    dst = os.path.join(OUT, 'models', m + '.json')
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    json.dump(out, open(dst, 'w'), indent=1)
    for t in textures.values():
        if not t.startswith('#'):
            copy_texture(t)
print('stock link models flattened')
