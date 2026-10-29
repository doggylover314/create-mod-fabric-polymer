"""Crushing wheel model for vanilla clients (Create's is an OBJ, which vanilla cannot load).

The wheel is a disc of radius 1 block (two blocks wide), 12/16 thick, with a log hub and an axle. The toothed rim is
built from four squares rotated by 0, 22.5, 45 and 67.5 degrees (vanilla only allows 22.5 degree steps; a square turned
by 67.5 degrees is the same as one turned by -22.5), which leaves 16 small teeth around the edge like Create's plates.
Authored for a wheel around Y, centred on the block; the server rotates it for other axes.

It also copies the two damage types (crush, mechanical_drill) that the drill and the crusher hurt entities with.

Usage: make_crushing_models.py CREATE_DIR PORT_DIR
"""
import json, math, os, shutil, sys

src_root, out_root = sys.argv[1], sys.argv[2]
A = os.path.join(out_root, 'src/main/resources/assets/create')
SRC = [os.path.join(src_root, 'src/generated/resources/assets/create'), os.path.join(src_root, 'src/main/resources/assets/create')]

for tex in ('crushing_wheel_insert', 'crushing_wheel_plates', 'axis', 'axis_top'):
    for s in SRC:
        p = os.path.join(s, 'textures/block', tex + '.png')
        if os.path.exists(p):
            dst = os.path.join(A, 'textures/block', tex + '.png')
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(p, dst)
            break
    else:
        raise FileNotFoundError(tex)

Y0, Y1 = 1.92, 14.08  # the plates span 0.12 to 0.88 of the block height in Create's model
HALF = 16 / math.sqrt(2)


def face(uv, tex):
    return {'uv': [round(v, 4) for v in uv], 'texture': tex}


def square(angle, layer):
    lo, hi = 8 - HALF, 8 + HALF
    # the insert texture covers the whole 2x2 block disc: world px -8..24 -> u 0..16
    u0, u1 = (lo + 8) / 2, (hi + 8) / 2
    top = face([u0, u0, u1, u1], '#insert')
    side = face([0, 2, 16, 14], '#plates')
    # a hair of height difference keeps the stacked squares from z-fighting on the flat faces
    y0, y1 = Y0 + 0.01 * layer, Y1 - 0.01 * layer
    e = {
        'name': 'disc',
        'from': [round(lo, 4), y0, round(lo, 4)],
        'to': [round(hi, 4), y1, round(hi, 4)],
        'faces': {'up': top, 'down': top, 'north': side, 'south': side, 'east': side, 'west': side},
    }
    if angle:
        e['rotation'] = {'origin': [8, 8, 8], 'axis': 'y', 'angle': angle}
    return e


def box(frm, to, tex_side, tex_end, name):
    def f(w, h, tex):
        return face([0, 0, min(16, w), min(16, h)], tex)
    sx, sy, sz = (to[0] - frm[0]), (to[1] - frm[1]), (to[2] - frm[2])
    return {
        'name': name, 'from': frm, 'to': to,
        'faces': {
            'north': f(sx, sy, tex_side), 'south': f(sx, sy, tex_side),
            'east': f(sz, sy, tex_side), 'west': f(sz, sy, tex_side),
            'up': f(sx, sz, tex_end), 'down': f(sx, sz, tex_end),
        },
    }


elements = [square(a, i) for i, a in enumerate((0, 22.5, 45, -22.5))]
elements.append(box([4, 1, 4], [12, 15, 12], '#log', '#log', 'hub'))
elements.append(box([6, 0, 6], [10, 16, 10], '#axis', '#axis_top', 'axle'))

wheel = {
    'credit': "Approximation of Create's crushing wheel for vanilla clients (create-polymer)",
    'textures': {
        'insert': 'create:block/crushing_wheel_insert', 'plates': 'create:block/crushing_wheel_plates',
        'axis': 'create:block/axis', 'axis_top': 'create:block/axis_top',
        'log': 'minecraft:block/spruce_log_top',
        'particle': 'create:block/crushing_wheel_plates',
    },
    'elements': elements,
}
os.makedirs(os.path.join(A, 'models/block/crushing_wheel'), exist_ok=True)
json.dump(wheel, open(os.path.join(A, 'models/block/crushing_wheel/polymer_wheel.json'), 'w'), indent=1)

item = {
    'parent': 'create:block/crushing_wheel/polymer_wheel',
    'display': {
        'gui': {'rotation': [30, 225, 0], 'scale': [0.45, 0.45, 0.45]},
        'ground': {'rotation': [90, 0, 0], 'scale': [0.25, 0.25, 0.25]},
        'fixed': {'rotation': [90, 0, 0], 'scale': [0.45, 0.45, 0.45]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.25, 0.25, 0.25]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [0.25, 0.25, 0.25]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'scale': [0.25, 0.25, 0.25]},
    },
}
os.makedirs(os.path.join(A, 'models/item'), exist_ok=True)
json.dump(item, open(os.path.join(A, 'models/item/crushing_wheel.json'), 'w'), indent=1)
print('crushing wheel elements', len(elements))

# damage types are data in 26.3 (Create registers them with a datagen builder)
for name in ('crush', 'mechanical_drill'):
    src = os.path.join(src_root, 'src/generated/resources/data/create/damage_type', name + '.json')
    dst = os.path.join(out_root, 'src/main/resources/data/create/damage_type', name + '.json')
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(src, dst)
