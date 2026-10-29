"""Models of the contraption actors that vanilla clients cannot draw as Create ships them.

- The mechanical roller's wheel and item model are OBJ files (converted to cuboid elements, see obj_to_json.py).
- Contraptions show a block as one model, rotated by the facing of the block (they cannot move parts of it), so the
  parts that Create moves are baked at rest into one model: the roller (casing, frame and wheel at the place
  Create's renderer puts it) and the portable storage / fluid interfaces (base, middle piece 6 pixels up, head).
- The roller's damage type.

Usage: make_actor_models.py CREATE_DIR PORT_DIR   (run after copy_assets.py)
"""
import copy, json, math, os, shutil, sys, tempfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import obj_to_json

src_root, out_root = sys.argv[1], sys.argv[2]
SRC = [os.path.join(src_root, 'src/generated/resources/assets/create'), os.path.join(src_root, 'src/main/resources/assets/create')]
OUT = os.path.join(out_root, 'src/main/resources/assets/create')


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


def quads_of(obj):
    """parse_obj reads quads only: the triangles of the roller meshes become quads with a repeated corner (which the
    converter cuts into strips like the funnel of the burner's flame)."""
    with tempfile.TemporaryDirectory() as tmp:
        for f in os.listdir(os.path.dirname(obj)):
            if f.endswith('.mtl'):
                shutil.copyfile(os.path.join(os.path.dirname(obj), f), os.path.join(tmp, f))
        lines = []
        textured = False
        for line in open(obj):
            parts = line.split()
            if parts and parts[0] == 'usemtl':
                textured = True
            if parts and parts[0] == 'f':
                if not textured:
                    continue  # the bounding boxes of the model come before any material
                if len(parts) == 4:
                    line = ' '.join(parts + [parts[3]]) + '\n'
            lines.append(line)
        patched = os.path.join(tmp, os.path.basename(obj))
        open(patched, 'w').writelines(lines)
        return obj_to_json.parse_obj(patched)


LOW, HIGH = -16.0, 32.0


def fits(el):
    return all(LOW <= v <= HIGH for v in el['from'] + el['to'])


def relocate(el):
    """The converted quads are flat boxes centred on their rotation origin, so a box beside the model's edge pokes out
    of the -16..32 range 26.3 allows for 'from' and 'to'. The same rigid motion works with the box and the origin
    placed elsewhere: box centre b and origin o with R (b - o) + o = c (the centre where the quad belongs)."""
    r = el.get('rotation')
    if fits(el) or not r or 'x' not in r:
        return
    cols = obj_to_json.rot_zyx(r['x'], r['y'], r['z'])
    c = r['origin']
    half = [(el['to'][i] - el['from'][i]) / 2 for i in range(3)]
    best = None
    for ox in range(-16, 33, 8):
        for oy in range(-16, 33, 8):
            for oz in range(-16, 33, 8):
                d = [c[0] - ox, c[1] - oy, c[2] - oz]
                # b = o + R^T (c - o)
                b = [(ox, oy, oz)[i] + sum(cols[i][k] * d[k] for k in range(3)) for i in range(3)]
                lo = [b[i] - half[i] for i in range(3)]
                hi = [b[i] + half[i] for i in range(3)]
                if all(LOW <= v <= HIGH for v in lo + hi):
                    # prefer the origin closest to the centre of the range
                    score = sum((v - 8) ** 2 for v in (ox, oy, oz))
                    if best is None or score < best[0]:
                        best = (score, (ox, oy, oz), lo, hi)
    if best is None:
        print('WARNING: element cannot be moved into range', el['from'], el['to'])
        return
    _, o, lo, hi = best
    el['from'] = [rnd(v) for v in lo]
    el['to'] = [rnd(v) for v in hi]
    r['origin'] = [float(v) for v in o]


rnd = obj_to_json.rnd


def obj_model(name, transform=None):
    """A Create OBJ model as a vanilla JSON model; transform maps a position in block units."""
    d = json.load(open(find('models/block/' + name + '.json')))
    if d.get('loader') != 'neoforge:obj':
        raise SystemExit('expected an OBJ model: ' + name)
    obj = find(d['model'].split(':', 1)[1])
    elements = []
    for q in quads_of(obj):
        if transform:
            q = dict(q, pos=[transform(p) for p in q['pos']])
        for el, _ in obj_to_json.quad_to_elements(q):
            elements.append(el)
    clamp_uvs(elements)
    for el in elements:
        relocate(el)
    out = {'credit': 'Converted from the OBJ model of Create for vanilla clients (create-polymer)',
           'parent': d.get('parent', 'block/block'), 'textures': d['textures'], 'elements': elements}
    if 'display' in d:
        out['display'] = d['display']
    for t in d['textures'].values():
        copy_texture(t)
    return out


def load_model(name):
    return json.load(open(find('models/block/' + name + '.json')))


def shift_element(el, dx, dy, dz):
    el['from'] = [el['from'][0] + dx, el['from'][1] + dy, el['from'][2] + dz]
    el['to'] = [el['to'][0] + dx, el['to'][1] + dy, el['to'][2] + dz]
    r = el.get('rotation')
    if r and 'origin' in r:
        o = r['origin']
        r['origin'] = [o[0] + dx, o[1] + dy, o[2] + dz]


def merge(parts, particle=None):
    """parts: list of (model dict, (dx, dy, dz) in pixels). Texture variables are renamed per part."""
    textures, elements = {}, []
    for i, (model, (dx, dy, dz)) in enumerate(parts):
        prefix = 'p%d_' % i
        for k, v in model.get('textures', {}).items():
            if k == 'particle':
                textures.setdefault('particle', v)
            else:
                textures[prefix + k] = v
        for el in copy.deepcopy(model.get('elements', [])):
            for f in el['faces'].values():
                if f.get('texture', '').startswith('#'):
                    f['texture'] = '#' + prefix + f['texture'][1:]
            shift_element(el, dx, dy, dz)
            el.pop('name', None)
            elements.append(el)
        for t in model.get('textures', {}).values():
            copy_texture(t)
    out = {'credit': 'Several of Create\'s models baked into one for contraptions (create-polymer)',
           'parent': 'block/block', 'textures': textures, 'elements': elements}
    clamp_uvs(elements)
    for el in elements:
        relocate(el)
    return out


# ---- roller ----

item = obj_model('mechanical_roller/item')
write_json('models/block/mechanical_roller/item.json', item)

wheel_plain = obj_model('mechanical_roller/wheel')
write_json('models/block/mechanical_roller/wheel.json', wheel_plain)


def roller_wheel_position(p):
    """Where RollerRenderer puts the wheel of a roller facing north (block units), at rest.

    Create applies, from the last operation to the first: rotateYDegrees(90), translate(0, -.5, .5), rotateCentered
    by horizontalAngle(north) = 180 degrees about up, translate(facing * 17/16), then the pose stack's
    translate(0, -.25, 0).
    """
    x, y, z = p
    t = math.radians(90)
    x, z = x * math.cos(t) + z * math.sin(t), -x * math.sin(t) + z * math.cos(t)
    y, z = y - .5, z + .5
    x, z = 1 - x, 1 - z  # a half turn about the vertical axis through the centre (.5, .5)
    z -= 17 / 16
    y -= .25
    return [x, y, z]


wheel = obj_model('mechanical_roller/wheel', roller_wheel_position)
roller = merge([(load_model('mechanical_roller/block'), (0, 0, 0)), (load_model('mechanical_roller/frame'), (0, 0, 0)),
                (wheel, (0, 0, 0))])
write_json('models/block/mechanical_roller/assembled.json', roller)

# ---- portable storage and fluid interfaces ----

for folder in ('portable_storage_interface', 'portable_fluid_interface'):
    # the middle piece rests 6 pixels up (progress * 0.5 + 0.375 blocks)
    psi = merge([(load_model(folder + '/block'), (0, 0, 0)), (load_model(folder + '/block_middle'), (0, 6, 0)),
                 (load_model(folder + '/block_top'), (0, 0, 0))])
    write_json('models/block/' + folder + '/assembled.json', psi)

# ---- damage type ----

dst = os.path.join(out_root, 'src/main/resources/data/create/damage_type/mechanical_roller.json')
os.makedirs(os.path.dirname(dst), exist_ok=True)
shutil.copyfile(os.path.join(src_root, 'src/generated/resources/data/create/damage_type/mechanical_roller.json'), dst)

print('actor models: roller item, wheel, assembled roller, 2 assembled interfaces')
