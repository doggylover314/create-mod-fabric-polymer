"""Blaze burner models: Create loads the cage, rods, flame and campfire models from OBJ files, which vanilla clients
cannot read. Convert them to cuboid-element models (see obj_to_json.py), copy their textures, and build the animated
flame textures that replace Create's UV scrolling.

Usage: make_burner_models.py CREATE_DIR PORT_DIR   (run after copy_assets.py)
"""
import json, math, os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import obj_to_json

src_root, out_root = sys.argv[1], sys.argv[2]
SRC = [os.path.join(src_root, 'src/generated/resources/assets/create'), os.path.join(src_root, 'src/main/resources/assets/create')]
OUT = os.path.join(out_root, 'src/main/resources/assets/create')
BASE = 'models/block/blaze_burner'

# models that are an OBJ in Create; the flame also gets one copy per heat level (the scroll speed differs)
OBJ_MODELS = ['block', 'block_with_blaze', 'block_with_fire', 'block_with_soul_fire', 'flame',
              'rods_small', 'rods_large', 'superheated_rods_small', 'superheated_rods_large']
# heat level ordinal in BlazeBurnerBlock.HeatLevel: scroll speed = 1/32 + ordinal/64 per tick
FLAMES = {'fading': (2, 'blaze_burner_flame_scroll'), 'kindled': (3, 'blaze_burner_flame_scroll'),
          'seething': (4, 'blaze_burner_flame_superheated_scroll')}


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


def convert(name):
    d = json.load(open(find(BASE + '/' + name + '.json')))
    if d.get('loader') != 'neoforge:obj':
        raise SystemExit('expected an OBJ model: ' + name)
    obj = find(d['model'].split(':', 1)[1])
    elements, _ = obj_to_json.convert(obj)
    clamp_uvs(elements)
    out = {'credit': 'Converted from the OBJ model of Create for vanilla clients (create-polymer)',
           'parent': d.get('parent', 'block/block'), 'textures': d['textures'], 'elements': elements}
    if 'display' in d:
        out['display'] = d['display']
    for t in d['textures'].values():
        copy_texture(t)
    return out


def make_flame_texture(name, speed, source):
    """Animated 16x16 frames of the 32x32 scroll texture: the window moves like Create's UV shift."""
    try:
        from PIL import Image
    except ImportError:
        print('WARNING: Pillow is missing, the flame stays a still picture')
        return None
    src = Image.open(find('textures/block/' + source + '.png')).convert('RGBA')
    n = round(2 / speed)  # ticks until u and v are back at the start (u scrolls at half the speed of v)
    sheet = Image.new('RGBA', (16, 16 * n))
    for i in range(n):
        t = i * 2 / (speed * n)
        v = (speed * t) % 1 * 16
        u = (speed * t / 2) % 1 * 16
        ou, ov = int(round(u)) % 16, int(round(v)) % 16
        sheet.paste(src.crop((ou, ov, ou + 16, ov + 16)), (0, 16 * i))
    dst = os.path.join(OUT, 'textures/block', name + '.png')
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    sheet.save(dst)
    json.dump({'animation': {'frametime': 1}}, open(dst + '.mcmeta', 'w'))
    return 'create:block/' + name


for name in OBJ_MODELS:
    write_json(BASE + '/' + name + '.json', convert(name))

flame = convert('flame')
for level, (ordinal, source) in FLAMES.items():
    speed = 1 / 32 + ordinal / 64
    tex = make_flame_texture('blaze_burner_flame_' + level, speed, source)
    if tex:
        flame['textures'] = dict(flame['textures'], **{'0': tex})
    write_json(BASE + '/flame_' + level + '.json', flame)

print('burner models:', len(OBJ_MODELS) + len(FLAMES))
