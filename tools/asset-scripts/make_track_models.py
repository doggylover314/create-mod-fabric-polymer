"""Train track models: Create loads the sloped, diagonal and crossing track pieces and the curve segments (tie and
rails) from OBJ files, which vanilla clients cannot read. Convert them to cuboid-element models (see obj_to_json.py)
and copy their textures.

Usage: make_track_models.py CREATE_DIR PORT_DIR   (run after copy_assets.py)
"""
import json, os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import obj_to_json

src_root, out_root = sys.argv[1], sys.argv[2]
SRC = [os.path.join(src_root, 'src/generated/resources/assets/create'), os.path.join(src_root, 'src/main/resources/assets/create')]
OUT = os.path.join(out_root, 'src/main/resources/assets/create')
BASE = 'models/block/track'

OBJ_MODELS = ['ascending', 'diag', 'diag_2', 'cross_diag', 'cross_d1_xo', 'cross_d1_zo', 'cross_d2_xo', 'cross_d2_zo',
              'tie', 'segment_left', 'segment_right',
              # the bogeys of the trains
              'bogey/bogey_frame', 'bogey/bogey_wheel', 'bogey/bogey_drive', 'bogey/bogey_drive_wheel',
              'bogey/bogey_drive_wheel_pin', 'bogey/bogey_drive_piston', 'bogey/bogey_drive_belt']


def find(rel):
    for s in SRC:
        p = os.path.join(s, rel)
        if os.path.exists(p):
            return p
    return None


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


def fits(e):
    f, t = e['from'], e['to']
    return all(-16 <= v <= 32 for v in f) and all(-16 <= v <= 32 for v in t)


def re_anchor(e):
    """Vanilla models may only reach from -16 to 32 before rotating, but a rotated flat piece can lie further out. Move
    the piece's unrotated rectangle towards the middle of the model and move the rotation origin so that the rotated
    result stays exactly where it was. Returns False if the rectangle cannot be brought inside."""
    r = e['rotation']
    rx, ry, rz = r['x'], r['y'], r['z']
    cols = obj_to_json.rot_zyx(rx, ry, rz)
    R = [[cols[c][i] for c in range(3)] for i in range(3)]
    f, t = e['from'], e['to']
    w2, h2 = (t[0] - f[0]) / 2, (t[1] - f[1]) / 2
    p = r['origin']
    trace = R[0][0] + R[1][1] + R[2][2]
    c = max(-1.0, min(1.0, (trace - 1) / 2))
    raw = [R[2][1] - R[1][2], R[0][2] - R[2][0], R[1][0] - R[0][1]]  # 2 sin(theta) * axis
    if obj_to_json.length(raw) > 1e-6:
        a = obj_to_json.norm(raw)
        sn = obj_to_json.length(raw) / 2
    elif c < 0:  # half turn: the axis is in R + I
        best = max(range(3), key=lambda i: sum((R[j][i] + (1 if i == j else 0)) ** 2 for j in range(3)))
        a = obj_to_json.norm([R[j][best] + (1 if best == j else 0) for j in range(3)])
        sn = 0.0
    else:
        return False  # no rotation: nothing to move
    mid = [8.0, 8.0, 8.0]
    d = obj_to_json.dot(obj_to_json.sub(mid, p), a)
    q = obj_to_json.sub(mid, obj_to_json.mul(a, d))
    if not (-16 <= q[0] - w2 and q[0] + w2 <= 32 and -16 <= q[1] - h2 and q[1] + h2 <= 32 and -16 <= q[2] <= 32):
        return False
    # (I - R) o = p - R q, solved in the plane perpendicular to the axis
    Rq = [sum(R[i][j] * q[j] for j in range(3)) for i in range(3)]
    m = obj_to_json.sub(p, Rq)
    e1 = obj_to_json.norm(obj_to_json.cross(a, [1.0, 0.0, 0.0] if abs(a[0]) < 0.9 else [0.0, 1.0, 0.0]))
    e2 = obj_to_json.cross(a, e1)
    m1, m2 = obj_to_json.dot(m, e1), obj_to_json.dot(m, e2)
    det = (1 - c) ** 2 + sn ** 2
    o1 = ((1 - c) * m1 - sn * m2) / det
    o2 = (sn * m1 + (1 - c) * m2) / det
    o = obj_to_json.add(obj_to_json.mul(e1, o1), obj_to_json.mul(e2, o2))
    e['from'] = [round(q[0] - w2, 4), round(q[1] - h2, 4), round(q[2], 4)]
    e['to'] = [round(q[0] + w2, 4), round(q[1] + h2, 4), round(q[2], 4)]
    r['origin'] = [round(v, 4) for v in o]
    return True


def clip_to_bounds(elements, name):
    out, moved, cut = [], 0, 0
    for e in elements:
        if not fits(e):
            before = [bp for bp, _ in obj_to_json.bake_south(e)]
            old = json.loads(json.dumps(e))
            if re_anchor(e):
                after = [bp for bp, _ in obj_to_json.bake_south(e)]
                err = max(obj_to_json.length(obj_to_json.sub(x, y)) for x, y in zip(before, after))
                if err < 0.01 and fits(e):
                    moved += 1
                    out.append(e)
                    continue
                e.clear()
                e.update(old)
            # Cut off the part outside, with its texture window.
            f, t = e['from'], e['to']
            face = next(iter(e['faces'].values()))
            uv = face['uv']
            if not -16 <= f[2] <= 32:
                cut += 1
                continue
            nf, nt = [max(f[0], -16), max(f[1], -16)], [min(t[0], 32), min(t[1], 32)]
            if nf[0] >= nt[0] or nf[1] >= nt[1]:
                cut += 1
                continue
            w, h = t[0] - f[0], t[1] - f[1]
            u0, v1, u1, v0 = uv[0], uv[1], uv[2], uv[3]  # u0 at from.x, u1 at to.x; v1 at to.y, v0 at from.y
            def u(x): return u0 + (x - f[0]) / w * (u1 - u0)
            def v(y): return v0 + (y - f[1]) / h * (v1 - v0)
            face['uv'] = [round(u(nf[0]), 4), round(v(nt[1]), 4), round(u(nt[0]), 4), round(v(nf[1]), 4)]
            e['from'] = [round(nf[0], 4), round(nf[1], 4), f[2]]
            e['to'] = [round(nt[0], 4), round(nt[1], 4), t[2]]
            cut += 1
        out.append(e)
    print('%s: %d pieces re-anchored, %d trimmed' % (name, moved, cut))
    return out


def textures_of(d):
    textures = {}
    parent = d.get('parent')
    if parent and parent.startswith('create:'):
        p = find('models/' + parent.split(':', 1)[1] + '.json')
        if p:
            textures.update(textures_of(json.load(open(p))))
    textures.update(d.get('textures', {}))
    return textures


def convert(name):
    d = json.load(open(find(BASE + '/' + name + '.json')))
    if d.get('loader') != 'neoforge:obj':
        raise SystemExit('expected an OBJ model: ' + name)
    obj = find(d['model'].split(':', 1)[1])
    elements, _ = obj_to_json.convert(obj)
    clamp_uvs(elements)
    elements = clip_to_bounds(elements, name)
    textures = textures_of(d)
    out = {'credit': 'Converted from the OBJ model of Create for vanilla clients (create-polymer)',
           'ambientocclusion': False, 'textures': textures, 'elements': elements}
    for t in textures.values():
        copy_texture(t)
    return out


for name in OBJ_MODELS:
    p = os.path.join(OUT, BASE, name + '.json')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(convert(name), open(p, 'w'), indent=1)

print('track models:', len(OBJ_MODELS))
