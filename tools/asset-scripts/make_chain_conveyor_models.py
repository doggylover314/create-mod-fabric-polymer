"""Chain conveyor models: Create loads the wheel, the guards, the shaft and the item model from OBJ files, which
vanilla clients cannot read. Convert them to cuboid-element models (see obj_to_json.py) and copy their textures.

The meshes have triangles as well as quads. A triangle becomes a trapezoid with a side of length zero, which
obj_to_json cuts into strips like the flame of the blaze burner.
Usage: make_chain_conveyor_models.py CREATE_DIR PORT_DIR   (run after copy_assets.py)
"""
import json, os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import obj_to_json

src_root, out_root = sys.argv[1], sys.argv[2]
SRC = [os.path.join(src_root, 'src/generated/resources/assets/create'), os.path.join(src_root, 'src/main/resources/assets/create')]
OUT = os.path.join(out_root, 'src/main/resources/assets/create')
BASE = 'models/block/chain_conveyor'
OBJ_MODELS = ['guard', 'wheel', 'shaft', 'item']


def find(rel):
    for s in SRC:
        p = os.path.join(s, rel)
        if os.path.exists(p):
            return p
    return None


def write_json(rel, data):
    p = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(data, open(p, 'w'), indent=1)


def copy_texture(ref):
    ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    if ns != 'create':
        return
    for ext in ('.png', '.png.mcmeta'):
        p = find('textures/' + path + ext)
        if p:
            dst = os.path.join(OUT, 'textures', path + ext)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(p, dst)


def parse_obj(path):
    """obj_to_json.parse_obj for meshes with triangles: a triangle ABC becomes the quad ABCC."""
    v, vt, quads = [], [], []
    mats, cur = {}, None
    for line in open(path):
        parts = line.split()
        if not parts:
            continue
        k = parts[0]
        if k == 'mtllib':
            mats = obj_to_json.parse_mtl(os.path.join(os.path.dirname(path), parts[1]))
        elif k == 'v':
            v.append([float(x) for x in parts[1:4]])
        elif k == 'vt':
            vt.append([float(parts[1]), 1.0 - float(parts[2])])
        elif k == 'usemtl':
            cur = mats.get(parts[1], '0')
        elif k == 'f':
            idx = [p.split('/') for p in parts[1:]]
            if len(idx) == 3:
                idx.append(idx[2])
            elif len(idx) != 4:
                raise ValueError('only triangles and quads are supported: ' + line)
            quads.append({
                'pos': [v[int(i[0]) - 1] for i in idx],
                'uv': [vt[int(i[1]) - 1] for i in idx],
                'tex': cur or '0',
            })
    return quads


def clamp_uvs(elements):
    # 26.3 clients refuse faces whose UVs leave the texture (see copy_assets.py)
    for e in elements:
        for f in e['faces'].values():
            uv = f['uv']
            for a, b in ((0, 2), (1, 3)):
                lo, hi = min(uv[a], uv[b]), max(uv[a], uv[b])
                shift = 16 - hi if hi > 16 else (-lo if lo < 0 else 0)
                if shift:
                    uv[a] += shift
                    uv[b] += shift


def convert(name):
    d = json.load(open(find(BASE + '/' + name + '.json')))
    if d.get('loader') != 'neoforge:obj':
        raise SystemExit('expected an OBJ model: ' + name)
    parent = json.load(open(find(BASE + '/' + d['parent'].split('/')[-1] + '.json')))
    textures = dict(parent['textures'])
    textures.update(d.get('textures', {}))
    obj = find(d['model'].split(':', 1)[1])
    elements = []
    for q in parse_obj(obj):
        for el, _ in obj_to_json.quad_to_elements(q):
            elements.append(el)
    clamp_uvs(elements)
    out = {'credit': 'Converted from the OBJ model of Create for vanilla clients (create-polymer)',
           'parent': 'block/block', 'ambientocclusion': False, 'textures': textures, 'elements': elements}
    if 'display' in d:
        out['display'] = d['display']
    if 'render_type' in d:
        out['render_type'] = d['render_type']
    for t in textures.values():
        copy_texture(t)
    return out


for name in OBJ_MODELS:
    write_json(BASE + '/' + name + '.json', convert(name))
print('chain conveyor models:', len(OBJ_MODELS))
