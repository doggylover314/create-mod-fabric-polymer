"""Vanilla JSON versions of the redstone Create models that use NeoForge's composite model loader (nixie tube).

Vanilla ignores the loader and the "children" of such a model, so the result would be empty. The elements of all
children are merged into the model's own element list; the render type of a child only matters for translucent
textures, which vanilla picks per texture anyway.
Usage: make_redstone_models.py CREATE_DIR PORT_DIR   (run after copy_assets.py)
"""
import glob, json, os, sys

out_root = sys.argv[2]
OUT = os.path.join(out_root, 'src/main/resources/assets/create')

fixed = 0
for f in glob.glob(os.path.join(OUT, 'models', '**', '*.json'), recursive=True):
    d = json.load(open(f))
    if d.get('loader') != 'neoforge:composite':
        continue
    elements = list(d.get('elements', []))
    textures = dict(d.get('textures', {}))
    for child in d.get('children', {}).values():
        elements.extend(child.get('elements', []))
        for k, v in child.get('textures', {}).items():
            textures.setdefault(k, v)
    d.pop('loader')
    d.pop('children', None)
    d['textures'] = textures
    d['elements'] = elements
    json.dump(d, open(f, 'w'), indent=1)
    fixed += 1
print('composite models flattened', fixed)
