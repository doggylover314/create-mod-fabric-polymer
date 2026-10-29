import json, math, os, sys, glob
out_root = sys.argv[1]
A = os.path.join(out_root, 'src/main/resources/assets/create')

# ---- trim language files to keys this port uses ----
ids = ['wrench','goggles','shaft','cogwheel','large_cogwheel','gearbox','hand_crank','water_wheel','creative_motor','millstone',
       'mechanical_press','mechanical_mixer','basin','andesite_casing','andesite_alloy','whisk','iron_sheet','golden_sheet',
       'copper_sheet','wheat_flour']
keep_exact = set()
for i in ids:
    keep_exact |= {f'block.create.{i}', f'item.create.{i}'}
keep_prefix = ('itemGroup.create.base', 'create.gui.stressometer.overstressed', 'create.kinetics.creative_motor.rotation_speed')
total = 0
for f in glob.glob(os.path.join(A, 'lang', '*.json')):
    d = json.load(open(f))
    t = {k: v for k, v in d.items() if k in keep_exact or k.startswith(keep_prefix)}
    if not t:
        os.remove(f); continue
    json.dump(t, open(f, 'w'), ensure_ascii=False, indent=1)
    total += os.path.getsize(f)
print('lang bytes', total)

# ---- water wheel model (Create's is an OBJ, which vanilla cannot load) ----
def face(uv, tex, cull=None):
    f = {'uv': uv, 'texture': tex}
    return f

def box(frm, to, tex_side, tex_end, angle=0.0, name=None):
    sx, sy, sz = (to[0]-frm[0]), (to[1]-frm[1]), (to[2]-frm[2])
    def uv(w, h):
        return [0, 0, min(16, w), min(16, h)]
    e = {
        'from': frm, 'to': to,
        'faces': {
            'north': face(uv(sx, sy), tex_side), 'south': face(uv(sx, sy), tex_side),
            'east': face(uv(sz, sy), tex_side), 'west': face(uv(sz, sy), tex_side),
            'up': face(uv(sx, sz), tex_end), 'down': face(uv(sx, sz), tex_end),
        }
    }
    if angle:
        e['rotation'] = {'origin': [8, 8, 8], 'axis': 'y', 'angle': angle}
    if name:
        e['name'] = name
    return e

els = []
els.append(box([6, 0, 6], [10, 16, 10], '#axis', '#axis_top', name='axle'))
els.append(box([4, 2, 4], [12, 14, 12], '#log', '#log', name='hub'))
for k in range(8):
    a = k * 45.0
    # spokes on both faces of the wheel
    els.append(box([12, 2.5, 7], [22, 4.5, 9], '#planks', '#planks', a, 'spoke'))
    els.append(box([12, 11.5, 7], [22, 13.5, 9], '#planks', '#planks', a, 'spoke'))
    # rim segments
    els.append(box([21, 1.5, 2.2], [22.5, 4.5, 13.8], '#log', '#log', a, 'rim'))
    els.append(box([21, 11.5, 2.2], [22.5, 14.5, 13.8], '#log', '#log', a, 'rim'))
    # paddles, between the spokes
    els.append(box([17, 1, 7.25], [24, 15, 8.75], '#planks', '#planks', a + 22.5, 'paddle'))

wheel = {
    'credit': 'Approximation of Create\'s water wheel for vanilla clients (create-polymer)',
    'textures': {
        'axis': 'create:block/axis', 'axis_top': 'create:block/axis_top',
        'log': 'minecraft:block/stripped_spruce_log', 'planks': 'minecraft:block/spruce_planks',
        'particle': 'minecraft:block/spruce_planks'
    },
    'elements': els
}
os.makedirs(os.path.join(A, 'models/block/water_wheel'), exist_ok=True)
json.dump(wheel, open(os.path.join(A, 'models/block/water_wheel/polymer_wheel.json'), 'w'), indent=1)
item = {
    'parent': 'create:block/water_wheel/polymer_wheel',
    'display': {
        'gui': {'rotation': [60, 0, 45], 'scale': [0.4, 0.4, 0.4]},
        'ground': {'rotation': [90, 0, 0], 'scale': [0.25, 0.25, 0.25]},
        'fixed': {'rotation': [90, 0, 0], 'scale': [0.4, 0.4, 0.4]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.25, 0.25, 0.25]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [0.25, 0.25, 0.25]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'scale': [0.25, 0.25, 0.25]}
    }
}
os.makedirs(os.path.join(A, 'models/item'), exist_ok=True)
json.dump(item, open(os.path.join(A, 'models/item/water_wheel.json'), 'w'), indent=1)
print('wheel elements', len(els))
