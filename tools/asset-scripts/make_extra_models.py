"""Vanilla JSON versions of the Create models that are NeoForge OBJ files (flywheel wheel, valve handle).

Vanilla cannot load OBJ models, so these are rebuilt from the OBJ geometry as boxes: the axis-aligned boxes are
taken straight from the OBJ (with their UVs), the octagonal parts (rim, hub) are approximated by rotated boxes.
Usage: make_extra_models.py CREATE_DIR PORT_DIR   (run after copy_assets.py)
"""
import collections, json, math, os, shutil, sys

create, out_root = sys.argv[1], sys.argv[2]
SRC = [os.path.join(create, 'src/main/resources/assets/create'), os.path.join(create, 'src/generated/resources/assets/create')]
OUT = os.path.join(out_root, 'src/main/resources/assets/create')

DIRS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0), 'down': (0, -1, 0), 'up': (0, 1, 0)}
# the directions along which u and v grow for a face without "rotation" (vanilla's default face UVs)
DEFAULT_UV = {
    'down': ((1, 0, 0), (0, 0, -1)), 'up': ((1, 0, 0), (0, 0, 1)),
    'north': ((-1, 0, 0), (0, -1, 0)), 'south': ((1, 0, 0), (0, -1, 0)),
    'west': ((0, 0, 1), (0, -1, 0)), 'east': ((0, 0, -1), (0, -1, 0)),
}


def find(rel):
    for s in SRC:
        p = os.path.join(s, rel)
        if os.path.exists(p):
            return p
    raise FileNotFoundError(rel)


def parse_obj(path):
    verts, uvs, faces, mat = [], [], [], None
    for line in open(path):
        p = line.split()
        if not p:
            continue
        if p[0] == 'v':
            verts.append(tuple(float(x) * 16 for x in p[1:4]))
        elif p[0] == 'vt':
            uvs.append((float(p[1]) * 16, (1 - float(p[2])) * 16))
        elif p[0] == 'usemtl':
            mat = p[1]
        elif p[0] == 'f':
            idx = []
            for t in p[1:]:
                a = t.split('/')
                idx.append((int(a[0]) - 1, int(a[1]) - 1))
            faces.append((mat, idx))
    return verts, uvs, faces


def components(verts, faces):
    parent = list(range(len(verts)))

    def find_root(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x
    for _, idx in faces:
        for a, b in zip(idx, idx[1:] + idx[:1]):
            parent[find_root(a[0])] = find_root(b[0])
    comps = collections.defaultdict(list)
    for f in faces:
        comps[find_root(f[1][0][0])].append(f)
    return list(comps.values())


def sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def unit_axis(v):
    """The axis-aligned unit vector v points along, or None."""
    big = max(range(3), key=lambda i: abs(v[i]))
    if any(abs(v[i]) > 1e-3 for i in range(3) if i != big):
        return None
    return tuple((1 if v[big] > 0 else -1) if i == big else 0 for i in range(3))


def axis_box(comp, verts, uvs):
    """Element for a component that is an axis-aligned box (any subset of its faces), or None."""
    pts = [verts[i] for _, idx in comp for i, _ in idx]
    frm = [min(p[i] for p in pts) for i in range(3)]
    to = [max(p[i] for p in pts) for i in range(3)]
    faces = {}
    for mat, idx in comp:
        if len(idx) != 4:
            return None
        p = [verts[i] for i, _ in idx]
        e1, e2 = sub(p[1], p[0]), sub(p[3], p[0])
        a1, a2 = unit_axis(e1), unit_axis(e2)
        if a1 is None or a2 is None:
            return None
        normal = (a1[1] * a2[2] - a1[2] * a2[1], a1[2] * a2[0] - a1[0] * a2[2], a1[0] * a2[1] - a1[1] * a2[0])
        c = tuple(sum(q[i] for q in p) / 4 for i in range(3))
        mid = tuple((frm[i] + to[i]) / 2 for i in range(3))
        if sum(normal[i] * (c[i] - mid[i]) for i in range(3)) < 0:
            normal = tuple(-x for x in normal)
        name = next(n for n, d in DIRS.items() if d == normal)
        # a flat quad lies on a face of the bounding box
        axis = max(range(3), key=lambda i: abs(normal[i]))
        if abs(c[axis] - (to[axis] if normal[axis] > 0 else frm[axis])) > 1e-3:
            return None
        q = [uvs[t] for _, t in idx]
        du1, du2 = q[1][0] - q[0][0], q[3][0] - q[0][0]
        dv1, dv2 = q[1][1] - q[0][1], q[3][1] - q[0][1]
        gu = tuple(x * (1 if du1 > 0 else -1) for x in a1) if abs(du1) > abs(du2) else tuple(x * (1 if du2 > 0 else -1) for x in a2)
        gv = tuple(x * (1 if dv1 > 0 else -1) for x in a1) if abs(dv1) > abs(dv2) else tuple(x * (1 if dv2 > 0 else -1) for x in a2)
        r_dir, d_dir = DEFAULT_UV[name]
        neg = lambda v: tuple(-x for x in v)
        found = None
        for rot in range(4):
            u_dirs = [r_dir, d_dir, neg(r_dir), neg(d_dir)]
            v_dirs = [d_dir, neg(r_dir), neg(d_dir), r_dir]
            ud, vd = u_dirs[rot], v_dirs[rot]
            for fu in (1, -1):
                for fv in (1, -1):
                    if tuple(fu * x for x in ud) == gu and tuple(fv * x for x in vd) == gv:
                        found = (rot, fu, fv)
        if found is None:
            return None
        rot, fu, fv = found
        u0, u1 = min(x for x, _ in q), max(x for x, _ in q)
        v0, v1 = min(y for _, y in q), max(y for _, y in q)
        uv = [u0 if fu > 0 else u1, v0 if fv > 0 else v1, u1 if fu > 0 else u0, v1 if fv > 0 else v0]
        faces[name] = (mat, uv, rot * 90)
    return {'from': frm, 'to': to, 'faces': faces}


def rn(x):
    return round(x + 0.0, 4)


def element(frm, to, faces, texture, rotation=None, name=None):
    """faces: {direction: uv or (uv, rotation)}; texture: '#n' or {direction: '#n'}."""
    e = {}
    if name:
        e['name'] = name
    e['from'] = [rn(x) for x in frm]
    e['to'] = [rn(x) for x in to]
    if rotation:
        axis, angle, origin = rotation
        e['rotation'] = {'origin': [rn(x) for x in origin], 'axis': axis, 'angle': angle}
    e['faces'] = {}
    for d, spec in faces.items():
        uv, rot = (spec if isinstance(spec[0], (list, tuple)) else (spec, 0))
        tex = texture[d] if isinstance(texture, dict) else texture
        f = {'uv': [rn(x) for x in uv], 'texture': tex}
        if rot:
            f['rotation'] = rot
        e['faces'][d] = f
    return e


def from_axis_box(box, tex_of_mat, extra_rotation=None, name=None):
    faces = {d: (uv, rot) for d, (mat, uv, rot) in box['faces'].items()}
    mats = {d: tex_of_mat[mat] for d, (mat, uv, rot) in box['faces'].items()}
    return element(box['from'], box['to'], faces, mats, extra_rotation, name)


def rotated_about_y(e, angle, shift=0.0):
    """Copy of an element rotated about the block's vertical axis; the y range shrinks by `shift` to avoid z-fighting."""
    c = json.loads(json.dumps(e))
    c['rotation'] = {'origin': [8, 8, 8], 'axis': 'y', 'angle': angle}
    c['from'][1] += shift
    c['to'][1] -= shift
    return c


# ---- flywheel ----
verts, uvs, faces = parse_obj(find('models/block/flywheel/flywheel.obj'))
comps = components(verts, faces)
FLY_TEX = {'m_0': '#0', 'm_1': '#1', 'm_2': '#2'}
fly_boxes = []
for comp in comps:
    box = axis_box(comp, verts, uvs)
    if box:
        fly_boxes.append(box)


def box_at(frm, to):
    for b in fly_boxes:
        if all(abs(b['from'][i] - frm[i]) < 1e-2 and abs(b['to'][i] - to[i]) < 1e-2 for i in range(3)):
            return b
    raise KeyError((frm, to))


shaft_el = from_axis_box(box_at((6, 0, 6), (10, 16, 10)), FLY_TEX, name='shaft')

wheel = []
# hub: an octagon of apothem 7 made of four bands
HUB_SIDE, HUB_CAP = [9, 5, 11, 8], [13, 0, 16, 3.5]
hub = element((1, 6, 5.1), (15, 10, 10.9), {'west': HUB_SIDE, 'east': HUB_SIDE, 'up': HUB_CAP, 'down': HUB_CAP}, '#0', name='hub')
hub90 = element((5.1, 6, 1), (10.9, 10, 15), {'north': HUB_SIDE, 'south': HUB_SIDE, 'up': HUB_CAP, 'down': HUB_CAP}, '#0', name='hub')
wheel.append(hub)
wheel.append(rotated_about_y(hub90, 0, 0.02))
wheel.append(rotated_about_y(hub, 45, 0.04))
wheel.append(rotated_about_y(hub, -45, 0.06))
# the small bolts and the two plates around the hub, as they are in the OBJ
for box in fly_boxes:
    size = [box['to'][i] - box['from'][i] for i in range(3)]
    inside = all(0 <= box['from'][i] and box['to'][i] <= 16 for i in range(3))
    if inside and box is not box_at((6, 0, 6), (10, 16, 10)) and box['from'][1] >= 3 and box['to'][1] <= 13 and \
            (size[0] <= 8 and size[2] <= 8):
        wheel.append(from_axis_box(box, FLY_TEX, name='plate'))
# spokes (y 6..10, 4 wide) along z, the two along x have thicker roots
spoke_pz = from_axis_box(box_at((6, 6, 15), (10, 10, 25)), FLY_TEX, name='spoke')
spoke_mz = from_axis_box(box_at((6, 6, -9), (10, 10, 1)), FLY_TEX, name='spoke')
wheel += [spoke_pz, spoke_mz]
for s in (spoke_pz, spoke_mz):
    wheel.append(rotated_about_y(s, 45, 0.02))
    wheel.append(rotated_about_y(s, -45, 0.02))
SPOKE_TB, SPOKE_SIDE = [9, 0, 11, 5], [11, 0, 13, 5]
ROOT_EW, ROOT_UD, ROOT_NS = [0.5, 11, 2, 12], [2, 11, 3.5, 12], [2, 9.5, 3.5, 11]
for name, (x0, x1) in (('spoke', (15, 25)), ('spoke', (-9, 1))):
    wheel.append(element((x0, 6, 6), (x1, 10, 10), {'up': SPOKE_TB, 'down': SPOKE_TB, 'north': SPOKE_SIDE, 'south': SPOKE_SIDE}, '#0', name=name))
for (x0, x1) in ((12, 15), (1, 4)):
    for (y0, y1) in ((3, 6), (10, 13)):
        wheel.append(element((x0, y0, 6), (x1, y1, 10), {'up': ROOT_UD, 'down': ROOT_UD, 'north': ROOT_NS, 'south': ROOT_NS,
                                                          'east': ROOT_EW, 'west': ROOT_EW}, '#0', name='root'))
# rim: eight segments around apothem 17..22, built as boxes
RIM_OUT, RIM_IN, RIM_TOP = [0, 0, 9, 3], [1, 5.5, 8, 8.5], [0, 3, 9, 5.5]
rim_px = element((25, 5, -0.1), (29.97, 11, 16.1), {'east': RIM_OUT, 'west': RIM_IN, 'up': RIM_TOP, 'down': RIM_TOP}, '#0', name='rim')
rim_mx = element((-13.97, 5, -0.1), (-9, 11, 16.1), {'west': RIM_OUT, 'east': RIM_IN, 'up': RIM_TOP, 'down': RIM_TOP}, '#0', name='rim')
rim_pz = element((-0.1, 5, 25), (16.1, 11, 29.97), {'south': RIM_OUT, 'north': RIM_IN, 'up': RIM_TOP, 'down': RIM_TOP}, '#0', name='rim')
rim_mz = element((-0.1, 5, -13.97), (16.1, 11, -9), {'north': RIM_OUT, 'south': RIM_IN, 'up': RIM_TOP, 'down': RIM_TOP}, '#0', name='rim')
wheel += [rim_px, rim_mx, rim_pz, rim_mz]
for s in (rim_px, rim_mx):
    wheel.append(rotated_about_y(s, 45, 0.03))
    wheel.append(rotated_about_y(s, -45, 0.03))

fly_textures = {'0': 'create:block/flywheel', '1': 'create:block/axis', '2': 'create:block/axis_top', 'particle': 'create:block/flywheel'}
os.makedirs(os.path.join(OUT, 'models/block/flywheel'), exist_ok=True)
os.makedirs(os.path.join(OUT, 'models/item'), exist_ok=True)
json.dump({'credit': "Approximation of Create's flywheel for vanilla clients (create-polymer)", 'parent': 'block/block',
           'ambientocclusion': False, 'textures': fly_textures, 'elements': wheel},
          open(os.path.join(OUT, 'models/block/flywheel/polymer_wheel.json'), 'w'), indent=1)
json.dump({'credit': "Approximation of Create's flywheel for vanilla clients (create-polymer)", 'parent': 'block/block',
           'ambientocclusion': False, 'textures': fly_textures, 'elements': [shaft_el] + wheel,
           'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
                       'fixed': {'rotation': [90, 0, 0], 'translation': [0, 0, -1], 'scale': [0.375, 0.375, 0.375]}}},
          open(os.path.join(OUT, 'models/item/flywheel.json'), 'w'), indent=1)
os.makedirs(os.path.join(OUT, 'textures/block'), exist_ok=True)
shutil.copyfile(find('textures/block/flywheel.png'), os.path.join(OUT, 'textures/block/flywheel.png'))
print('flywheel elements', len(wheel))

# ---- valve handle ----
verts, uvs, faces = parse_obj(find('models/block/valve_handle/valve_handle.obj'))
comps = components(verts, faces)
VH_TEX = {'Material': '#3'}
vh = []
for comp in comps:
    box = axis_box(comp, verts, uvs)
    if box and len(comp) in (6,):
        vh.append(from_axis_box(box, VH_TEX, name='part'))
# the octagonal rim (apothem 5..7, y 5..8) as eight boxes
RING_OUT, RING_TOP, RING_IN = [0, 5, 6, 8], [0, 0, 6, 2], [0.86, 2, 5.14, 5]
ring_pz = element((5.5, 5, 13), (10.5, 8, 15), {'south': RING_OUT, 'north': RING_IN, 'up': RING_TOP, 'down': RING_TOP}, '#3', name='rim')
ring_mz = element((5.5, 5, 1), (10.5, 8, 3), {'north': RING_OUT, 'south': RING_IN, 'up': RING_TOP, 'down': RING_TOP}, '#3', name='rim')
ring_px = element((13, 5, 5.5), (15, 8, 10.5), {'east': RING_OUT, 'west': RING_IN, 'up': RING_TOP, 'down': RING_TOP}, '#3', name='rim')
ring_mx = element((1, 5, 5.5), (3, 8, 10.5), {'west': RING_OUT, 'east': RING_IN, 'up': RING_TOP, 'down': RING_TOP}, '#3', name='rim')
vh += [ring_pz, ring_mz, ring_px, ring_mx]
for s in (ring_pz, ring_mz):
    vh.append(rotated_about_y(s, 45, 0.02))
    vh.append(rotated_about_y(s, -45, 0.02))
# four 2x2x6 arms tilted 45 degrees from the hub up to the rim
ARM = [7, 0, 9, 6]
arm_faces = {'up': ARM, 'down': ARM, 'east': ARM, 'west': ARM}
cy = 3.585
vh.append(element((7, cy - 1, 4.415 - 3), (9, cy + 1, 4.415 + 3), arm_faces, '#3', ('x', 45, (8, cy, 4.415)), 'arm'))
vh.append(element((7, cy - 1, 11.585 - 3), (9, cy + 1, 11.585 + 3), arm_faces, '#3', ('x', -45, (8, cy, 11.585)), 'arm'))
vh.append(element((4.415 - 3, cy - 1, 7), (4.415 + 3, cy + 1, 9), arm_faces, '#3', ('z', -45, (4.415, cy, 8)), 'arm'))
vh.append(element((11.585 - 3, cy - 1, 7), (11.585 + 3, cy + 1, 9), arm_faces, '#3', ('z', 45, (11.585, cy, 8)), 'arm'))
os.makedirs(os.path.join(OUT, 'models/block'), exist_ok=True)
json.dump({'credit': "Approximation of Create's valve handle for vanilla clients (create-polymer)", 'parent': 'block/block',
           'textures': {'3': 'create:block/valve_handle/valve_handle_copper', 'particle': '#3'},
           'elements': vh,
           'display': {'fixed': {'rotation': [270, 0, 0], 'translation': [0, 0, -2], 'scale': [0.5, 0.5, 0.5]}}},
          open(os.path.join(OUT, 'models/block/valve_handle.json'), 'w'), indent=1)
print('valve handle elements', len(vh))
