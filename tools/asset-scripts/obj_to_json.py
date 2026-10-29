"""Convert the Wavefront OBJ models Create loads through NeoForge's OBJ loader into vanilla cuboid-element models.

Vanilla clients cannot read OBJ models, so every quad of the mesh becomes a flat (zero thickness) element, rotated with
Euler angles (26.3 models allow arbitrary x/y/z rotations). Quads that are not rectangles (the flame is a funnel made of
trapezoids) are cut into a few strips, each approximated by one rectangle.

Library used by make_burner_models.py; run it directly for a numeric self-check:
    python3 -I obj_to_json.py <create-checkout>
"""
import json, math, os, sys

SLICES = 4


# ---- tiny vector helpers ----
def sub(a, b): return [a[i] - b[i] for i in range(len(a))]
def add(a, b): return [a[i] + b[i] for i in range(len(a))]
def mul(a, s): return [x * s for x in a]
def dot(a, b): return sum(x * y for x, y in zip(a, b))
def cross(a, b): return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
def length(a): return math.sqrt(dot(a, a))
def norm(a):
    l = length(a)
    return [x / l for x in a] if l > 1e-12 else [0.0] * len(a)
def lerp(a, b, t): return [a[i] + (b[i] - a[i]) * t for i in range(len(a))]


def parse_mtl(path):
    """material name -> texture variable ('#0' -> '0')"""
    mats, cur = {}, None
    if not os.path.exists(path):
        return mats
    for line in open(path):
        parts = line.split()
        if not parts:
            continue
        if parts[0] == 'newmtl':
            cur = parts[1]
        elif parts[0] == 'map_Kd' and cur:
            mats[cur] = parts[1].lstrip('#')
    return mats


def parse_obj(path):
    """-> list of quads: dict(pos=[4x3], uv=[4x2], tex=str) in block units (0..1), uv v already flipped (v' = 1 - v)."""
    v, vt, quads = [], [], []
    mats = {}
    cur = None
    for line in open(path):
        parts = line.split()
        if not parts:
            continue
        k = parts[0]
        if k == 'mtllib':
            mats = parse_mtl(os.path.join(os.path.dirname(path), parts[1]))
        elif k == 'v':
            v.append([float(x) for x in parts[1:4]])
        elif k == 'vt':
            vt.append([float(parts[1]), 1.0 - float(parts[2])])
        elif k == 'usemtl':
            cur = mats.get(parts[1], '0')
        elif k == 'f':
            idx = [p.split('/') for p in parts[1:]]
            if len(idx) != 4:
                raise ValueError('only quads are supported: ' + line)
            quads.append({
                'pos': [v[int(i[0]) - 1] for i in idx],
                'uv': [vt[int(i[1]) - 1] for i in idx],
                'tex': cur or '0',
            })
    return quads


def solve_2x2(m, b):
    det = m[0][0] * m[1][1] - m[0][1] * m[1][0]
    if abs(det) < 1e-12:
        return None
    return [(b[0] * m[1][1] - m[0][1] * b[1]) / det, (m[0][0] * b[1] - b[0] * m[1][0]) / det]


def bilerp(c, s, t):
    """corners c0..c3 as P0 (s=0,t=0), P1 (s=1,t=0), P2 (s=1,t=1), P3 (s=0,t=1)"""
    return lerp(lerp(c[0], c[1], s), lerp(c[3], c[2], s), t)


def euler_zyx(cols):
    """Rotation matrix with columns cols (images of x, y, z) -> degrees (x, y, z) with R = Rz(z) Ry(y) Rx(x)."""
    r = [[cols[c][r_] for c in range(3)] for r_ in range(3)]
    sy = -r[2][0]
    sy = max(-1.0, min(1.0, sy))
    y = math.asin(sy)
    if abs(sy) < 0.999999:
        x = math.atan2(r[2][1], r[2][2])
        z = math.atan2(r[1][0], r[0][0])
    else:  # gimbal lock
        x = math.atan2(-r[1][2], r[1][1])
        z = 0.0
    return [math.degrees(x), math.degrees(y), math.degrees(z)]


def rot_zyx(x, y, z):
    """matrix as columns (images of x, y, z axes)"""
    x, y, z = math.radians(x), math.radians(y), math.radians(z)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    # Rz * Ry * Rx
    m = [
        [cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx],
        [sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx],
        [-sy, cy * sx, cy * cx],
    ]
    return [[m[r][c] for r in range(3)] for c in range(3)]


def rnd(x, n=4):
    x = round(x, n)
    return 0.0 if x == 0 else x


def quad_to_elements(q, scale=16.0, tex_size=16.0):
    """-> list of (element dict, info dict). Positions in block units are scaled to model units."""
    P = [mul(p, scale) for p in q['pos']]
    UV = [mul(u, tex_size) for u in q['uv']]

    # choose which pair of opposite edges is parallel: that is the 'width' direction (P0P1 and P3P2)
    def para(a, b):
        return length(cross(norm(a), norm(b))) < 1e-3

    order = [0, 1, 2, 3]
    if not para(sub(P[1], P[0]), sub(P[2], P[3])):
        order = [1, 2, 3, 0]
    P = [P[i] for i in order]
    UV = [UV[i] for i in order]
    w0, w1 = length(sub(P[1], P[0])), length(sub(P[2], P[3]))
    rect = abs(w0 - w1) < 1e-3
    n_slices = 1 if rect else SLICES

    out = []
    outward = norm(cross(sub(P[1], P[0]), sub(P[3], P[0])))
    for k in range(n_slices):
        t0, t1, tm = k / n_slices, (k + 1) / n_slices, (k + 0.5) / n_slices
        sm = 0.5
        corners = [bilerp(P, 0, t0), bilerp(P, 1, t0), bilerp(P, 1, t1), bilerp(P, 0, t1)]
        ucorners = [bilerp(UV, 0, t0), bilerp(UV, 1, t0), bilerp(UV, 1, t1), bilerp(UV, 0, t1)]
        center = bilerp(P, sm, tm)
        ucenter = bilerp(UV, sm, tm)
        # Jacobians at the centre of the slice
        dPs = sub(bilerp(P, 1, tm), bilerp(P, 0, tm))
        dPt = sub(bilerp(P, sm, t1), bilerp(P, sm, t0))
        dUs = sub(bilerp(UV, 1, tm), bilerp(UV, 0, tm))
        dUt = sub(bilerp(UV, sm, t1), bilerp(UV, sm, t0))
        # P(u, v') : solve [dUs dUt] * [a; b] = e_u / e_v for the 3D vectors per uv unit
        # UV change = dUs * ds + dUt * dt  ->  (ds, dt) = inv(J) * (du, dv)
        J = [[dUs[0], dUt[0]], [dUs[1], dUt[1]]]
        i_u = solve_2x2(J, [1.0, 0.0])
        i_v = solve_2x2(J, [0.0, 1.0])
        if i_u is None:
            continue  # degenerate in uv space, nothing to show
        Du = add(mul(dPs, i_u[0]), mul(dPt, i_u[1]))      # 3D vector per +u pixel
        Dv = add(mul(dPs, i_v[0]), mul(dPt, i_v[1]))      # 3D vector per +v pixel (v pointing down in the image)
        xh = norm(Du)
        yh = norm(mul(Dv, -1.0))
        yh = norm(sub(yh, mul(xh, dot(yh, xh))))
        mirrored = False
        if dot(cross(xh, yh), outward) < 0:
            # The texture is mirrored as seen from the front of the quad (as in the OBJ): show the front side with swapped u.
            xh = mul(xh, -1.0)
            mirrored = True
        zh = cross(xh, yh)
        # size: extent of the slice in uv space
        us = [c[0] for c in ucorners]
        vs = [c[1] for c in ucorners]
        du, dv = max(us) - min(us), max(vs) - min(vs)
        w, h = length(Du) * du, length(Dv) * dv
        if w < 1e-4 or h < 1e-4:
            continue
        # ensure the uv window is centred on the slice
        uc = [(max(us) + min(us)) / 2, (max(vs) + min(vs)) / 2]
        shift = [uc[0] - ucenter[0], uc[1] - ucenter[1]]
        c3 = add(center, add(mul(Du, shift[0]), mul(Dv, shift[1])))
        e = euler_zyx([xh, yh, zh])
        el = {
            'from': [rnd(c3[0] - w / 2), rnd(c3[1] - h / 2), rnd(c3[2])],
            'to': [rnd(c3[0] + w / 2), rnd(c3[1] + h / 2), rnd(c3[2])],
            'rotation': {'origin': [rnd(c3[0]), rnd(c3[1]), rnd(c3[2])], 'x': rnd(e[0], 3), 'y': rnd(e[1], 3), 'z': rnd(e[2], 3)},
            'faces': {'south': {'uv': ([rnd(max(us)), rnd(min(vs)), rnd(min(us)), rnd(max(vs))] if mirrored else
                                    [rnd(min(us)), rnd(min(vs)), rnd(max(us)), rnd(max(vs))]), 'texture': '#' + q['tex']}},
        }
        out.append((el, {'normal': zh, 'outward': outward, 'center': c3, 'half': [w / 2, h / 2], 'axes': [xh, yh, zh]}))
    return out


def convert(obj_path, scale=16.0):
    elements, infos = [], []
    for q in parse_obj(obj_path):
        for el, info in quad_to_elements(q, scale):
            elements.append(el)
            infos.append(info)
    return elements, infos


def bake_south(el):
    """Vertices (3D, uv) of the element's south face as the 26.3 client bakes them."""
    f, t = el['from'], el['to']
    uv = el['faces']['south']['uv']
    corners = [([f[0], t[1], t[2]], [uv[0], uv[1]]), ([f[0], f[1], t[2]], [uv[0], uv[3]]),
               ([t[0], f[1], t[2]], [uv[2], uv[3]]), ([t[0], t[1], t[2]], [uv[2], uv[1]])]
    r = el['rotation']
    cols = rot_zyx(r['x'], r['y'], r['z'])
    o = r['origin']
    out = []
    for p, u in corners:
        d = sub(p, o)
        rp = [cols[0][i] * d[0] + cols[1][i] * d[1] + cols[2][i] * d[2] + o[i] for i in range(3)]
        out.append((rp, u))
    return out


def check(obj_path):
    """Numeric self-check: bake every rectangle like the client does and compare it with the OBJ quads."""
    quads = parse_obj(obj_path)
    elements, infos = convert(obj_path)
    flipped = sum(1 for i in infos if dot(i['normal'], i['outward']) < 0)  # must be 0
    verts = []
    for q in quads:
        for p, u in zip(q['pos'], q['uv']):
            verts.append((mul(p, 16.0), mul(u, 16.0)))
    worst_p = worst_u = 0.0
    for el in elements:
        for p, u in bake_south(el):
            best = min(verts, key=lambda v: length(sub(v[0], p)) + 0.01 * length(sub(v[1], u)))
            worst_p = max(worst_p, length(sub(best[0], p)))
            worst_u = max(worst_u, length(sub(best[1], u)))
    print('%s: %d quads -> %d elements, %d facing away, worst vertex distance %.3f, worst uv distance %.3f'
          % (os.path.basename(obj_path), len(quads), len(elements), flipped, worst_p, worst_u))


if __name__ == '__main__':
    base = os.path.join(sys.argv[1], 'src/main/resources/assets/create/models/block/blaze_burner')
    for f in sorted(os.listdir(base)):
        if f.endswith('.obj'):
            check(os.path.join(base, f))
