"""Vanilla JSON versions of the redstone Create models that use NeoForge's composite model loader (nixie tube).

Vanilla ignores the loader and the "children" of such a model, so the result would be empty. The elements of all
children are merged into the model's own element list; the render type of a child only matters for translucent
textures, which vanilla picks per texture anyway.
Usage: make_redstone_models.py CREATE_DIR PORT_DIR   (run after copy_assets.py)
"""
import glob, json, os, shutil, sys

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

# Textures that only a child of a composite model referenced were not copied with the model: copy them now.
create_root = sys.argv[1]
copied = 0
for f in glob.glob(os.path.join(OUT, 'models', '**', '*.json'), recursive=True):
    for ref in json.load(open(f)).get('textures', {}).values():
        if not ref.startswith('create:'):
            continue
        rel = 'textures/' + ref.split(':', 1)[1] + '.png'
        dst = os.path.join(OUT, rel)
        if os.path.exists(dst):
            continue
        for base in ('src/main/resources', 'src/generated/resources'):
            src = os.path.join(create_root, base, 'assets/create', rel)
            if os.path.exists(src):
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copyfile(src, dst)
                copied += 1
                break
print('textures of composite model children copied', copied)
