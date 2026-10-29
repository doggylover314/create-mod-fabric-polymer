"""Copy the Create assets used by the ported blocks/items into the mod's resources (followed by parent/texture refs)."""
import json, os, sys, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from manifest import load
src_root, out_root = sys.argv[1], sys.argv[2]
SRC = [os.path.join(src_root, 'src/generated/resources/assets/create'), os.path.join(src_root, 'src/main/resources/assets/create')]
OUT = os.path.join(out_root, 'src/main/resources/assets/create')

M = load(out_root)
DISPLAY_MODELS = list(M['display_models'])
# Create blockstate files the server reads to pick casing models (bundled in the jar, not the pack)
BLOCKSTATES = M['blockstates']
BS_OUT = os.path.join(out_root, 'src/main/resources/create_polymer/blockstates')
ITEMS = M['items']
# Models we author ourselves (not copied from Create)
OWN_MODELS = set(M['own_models'])

def find(rel):
    for s in SRC:
        p = os.path.join(s, rel)
        if os.path.exists(p):
            return p
    return None

copied_models, copied_textures, missing = set(), set(), []

def copy_file(rel):
    p = find(rel)
    if p is None:
        missing.append(rel); return None
    dst = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(p, dst)
    return p

def ref_to_rel(ref, kind):
    if ':' in ref:
        ns, path = ref.split(':', 1)
    else:
        ns, path = 'minecraft', ref
    if ns != 'create':
        return None
    return f'{kind}/{path}'

def copy_model(model_path):
    if model_path in copied_models or model_path in OWN_MODELS:
        return
    copied_models.add(model_path)
    p = copy_file(f'models/{model_path}.json')
    if p is None:
        return
    d = json.load(open(p))
    if 'loader' in d:
        print('WARNING: model uses a NeoForge loader:', model_path)
    parent = d.get('parent')
    if parent:
        r = ref_to_rel(parent, 'models')
        if r:
            copy_model(r[len('models/'):])
    if model_path.startswith('item/'):
        # a model may use one atlas only: block textures used next to item textures move to the item atlas
        tex = d.get('textures', {})
        if any(v.startswith('create:item/') for v in tex.values()) and any(v.startswith('create:block/') for k, v in tex.items() if k != 'particle'):
            for k, v in list(tex.items()):
                if v.startswith('create:block/') and k != 'particle':
                    tex[k] = 'create:item/' + v[len('create:block/'):].replace('/', '_')
                    src_tex = find('textures/' + v.split(':', 1)[1] + '.png')
                    copied_textures.add('textures/' + tex[k].split(':', 1)[1])
                    if src_tex:
                        dst_tex = os.path.join(OUT, 'textures', tex[k].split(':', 1)[1] + '.png')
                        os.makedirs(os.path.dirname(dst_tex), exist_ok=True)
                        shutil.copyfile(src_tex, dst_tex)
            json.dump(d, open(os.path.join(OUT, f'models/{model_path}.json'), 'w'), indent=1)
    for v in d.get('textures', {}).values():
        if v.startswith('#'):
            continue
        r = ref_to_rel(v, 'textures')
        if r and r not in copied_textures:
            copied_textures.add(r)
            copy_file(r + '.png')
            if find(r + '.png.mcmeta'):
                copy_file(r + '.png.mcmeta')

shutil.rmtree(BS_OUT, ignore_errors=True)
os.makedirs(BS_OUT, exist_ok=True)
for b in BLOCKSTATES:
    src = os.path.join(SRC[0], 'blockstates', b + '.json')
    d = json.load(open(src))
    json.dump(d, open(os.path.join(BS_OUT, b + '.json'), 'w'), indent=1)
    for v in d['variants'].values():
        for x in (v if isinstance(v, list) else [v]):
            r = ref_to_rel(x['model'], 'models')
            if r:
                DISPLAY_MODELS.append(r[len('models/'):])

for m in DISPLAY_MODELS:
    copy_model(m)
for item in ITEMS:
    copy_model(f'item/{item}')
    os.makedirs(os.path.join(OUT, 'items'), exist_ok=True)
    json.dump({'model': {'type': 'minecraft:model', 'model': f'create:item/{item}'}},
              open(os.path.join(OUT, 'items', item + '.json'), 'w'), indent=2)

# Language files (all languages Create ships)
for s in SRC:
    ld = os.path.join(s, 'lang')
    if os.path.isdir(ld):
        for f in os.listdir(ld):
            if f.endswith('.json'):
                os.makedirs(os.path.join(OUT, 'lang'), exist_ok=True)
                dst = os.path.join(OUT, 'lang', f)
                data = json.load(open(os.path.join(ld, f)))
                if os.path.exists(dst):
                    old = json.load(open(dst)); old.update(data); data = old
                json.dump(data, open(dst, 'w'), ensure_ascii=False, indent=1)

print('models', len(copied_models), 'textures', len(copied_textures))
print('missing', missing)

# 26.3 clients refuse to bake faces whose UVs leave the 0..16 texture square
# (Create's creative motor has one). Shift such UVs back inside.
import glob
fixed = 0
for f in glob.glob(os.path.join(OUT, 'models', '**', '*.json'), recursive=True):
    d = json.load(open(f))
    changed = False
    for e in d.get('elements', []):
        for fc in e.get('faces', {}).values():
            uv = fc.get('uv')
            if not uv:
                continue
            for a, b in ((0, 2), (1, 3)):
                hi, lo = max(uv[a], uv[b]), min(uv[a], uv[b])
                shift = 16 - hi if hi > 16 else (-lo if lo < 0 else 0)
                if shift:
                    uv[a] += shift; uv[b] += shift; changed = True
    if changed:
        fixed += 1
        json.dump(d, open(f, 'w'), indent=1)
print('models with fixed UVs', fixed)
