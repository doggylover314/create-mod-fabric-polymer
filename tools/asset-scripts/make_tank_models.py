"""Fluid tank models, fluid boxes and fluid data for vanilla clients.

Create draws fluid tanks with connected textures, which need a client mod. Like the vault models, every tank block is
shown as one model whose faces use the connected-texture tile Create's FluidTankModel/FluidTankCTBehaviour would pick,
and whose walls towards neighbouring tank blocks are left out (FluidTankModel culls those too). They are built from
Create's own block/fluid_tank/block_<y>[_window[_<corner>]] models and named

    block/<fluid_tank|creative_fluid_tank>/polymer_<y>[_window[_<corner>]]_<x-><x+><z-><z+>      (1 = neighbour present)

where <y> is single, bottom, middle or top, as Create's FluidTankGenerator. The naming mirrors
FluidTankBlock.Model.modelName in the code.

Also written here:
  * fluid boxes: the cube models and item definitions (items/fluid_box_<name>.json, tinted where needed) that display
    entities scale into the fluid in tanks, basins, spouts and drains (FluidDisplay.java),
  * the fluid tags c:honey, c:chocolate and c:tea and the block tag create:passive_boiler_heaters.

Usage: make_tank_models.py CREATE_DIR PORT_DIR
"""
import glob, json, os, shutil, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from manifest import load

# Face -> (direction of the texture's "up", direction of its "right"): ConnectedTextureBehaviour.buildContext for
# the default getUpDirection/getRightDirection (a tank is no special case).
FACES = {
    'north': ('up', 'west'),
    'south': ('up', 'east'),
    'east': ('up', 'north'),
    'west': ('up', 'south'),
    'up': ('north', 'east'),
    'down': ('south', 'east'),
}
OPPOSITE = {'up': 'down', 'down': 'up', 'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
CULL_DIRECTIONS = ('north', 'south', 'east', 'west')

# Texture variables of Create's tank models that get connected textures, per kind of tank:
# (side "1", top "0", inner "4") -> connected sheets. The window textures ("3", "5") are not connected.
KINDS = {
    'fluid_tank': {
        'textures': {
            '0': 'create:block/fluid_tank_top_connected',
            '1': 'create:block/fluid_tank_connected',
            '4': 'create:block/fluid_tank_inner_connected',
            'particle': 'create:block/fluid_tank',
        },
        'sheets': ['fluid_tank_connected', 'fluid_tank_top_connected', 'fluid_tank_inner_connected', 'fluid_tank',
                   'fluid_tank_window', 'fluid_tank_window_single'],
    },
    'creative_fluid_tank': {
        'textures': {
            '0': 'create:block/creative_casing_connected',
            '1': 'create:block/creative_fluid_tank_connected',
            '4': 'create:block/creative_casing_connected',
            '3': 'create:block/creative_fluid_tank_window',
            '5': 'create:block/creative_fluid_tank_window_single',
            'particle': 'create:block/creative_fluid_tank',
        },
        'sheets': ['creative_casing_connected', 'creative_fluid_tank_connected', 'creative_fluid_tank',
                   'creative_fluid_tank_window', 'creative_fluid_tank_window_single'],
    },
}


def tile(flags, up, right):
    """Create's AllCTTypes.RECTANGLE.getTextureIndex as (column, row) in the 4x4 sheet."""
    u, d = flags[up], flags[OPPOSITE[up]]
    r, l = flags[right], flags[OPPOSITE[right]]
    x = 2 if l and r else 3 if l else 1 if r else 0
    y = 1 if u and d else 2 if u else 0 if d else 3
    return x, y


def model_name(kind, width, height, dx, dy, dz, window):
    """The same as FluidTankBlock.Model.modelName."""
    y_low, y_high = dy > 0, dy < height - 1
    y = ('middle' if y_high else 'top') if y_low else ('bottom' if y_high else 'single')
    shape = ''
    if window:
        if width == 1:
            shape = '_window'
        if width == 2:
            shape = ('_window_nw' if dz == 0 else '_window_sw') if dx == 0 else ('_window_ne' if dz == 0 else '_window_se')
        if width == 3 and abs(dx - dz) == 1:
            shape = '_window'
    bits = ''.join('1' if b else '0' for b in (dx > 0, dx < width - 1, dz > 0, dz < width - 1))
    return '%s/polymer_%s%s_%s' % (kind, y, shape, bits), y + shape, bits


def names():
    """Every distinct (kind, create model, bits) a tank block can show: width 1-3, height 1-3 (taller tanks repeat
    the middle), windows on or off."""
    seen = {}
    for kind in KINDS:
        for w in (1, 2, 3):
            for h in (1, 2, 3):
                for dy in range(h):
                    for dx in range(w):
                        for dz in range(w):
                            for window in (True, False):
                                name, base, bits = model_name(kind, w, h, dx, dy, dz, window)
                                seen[name] = (kind, base, bits, dy > 0, dy < h - 1)
    return seen


def build(create_models, kind, base, bits, y_low, y_high):
    src = json.load(open(os.path.join(create_models, 'fluid_tank', 'block_%s.json' % base)))
    x_low, x_high, z_low, z_high = (b == '1' for b in bits)
    flags = {'west': x_low, 'east': x_high, 'north': z_low, 'south': z_high, 'down': y_low, 'up': y_high}
    connected_cull = {'west': x_low, 'east': x_high, 'north': z_low, 'south': z_high}

    textures = dict(src['textures'])
    textures.update(KINDS[kind]['textures'])
    elements = []
    for element in src['elements']:
        faces = {}
        for name, face in element['faces'].items():
            cull = face.get('cullface')
            if cull in CULL_DIRECTIONS and connected_cull[cull]:
                continue
            face = dict(face)
            if face['texture'] in ('#0', '#1', '#4'):
                tx, ty = tile(flags, *FACES[name])
                u0, v0, u1, v1 = face['uv']
                face['uv'] = [tx * 4 + u0 / 4, ty * 4 + v0 / 4, tx * 4 + u1 / 4, ty * 4 + v1 / 4]
            # culling against neighbours is done by leaving the faces out, the client does not cull display entities
            face.pop('cullface', None)
            faces[name] = face
        if not faces:
            continue
        e = {k: v for k, v in element.items() if k != 'faces'}
        # the no-op rotations of Blockbench have origins far outside the block
        if e.get('rotation', {}).get('angle', 0) == 0:
            e.pop('rotation', None)
        e['faces'] = faces
        elements.append(e)
    return {
        'credit': 'Connected-texture tiles of a fluid tank block baked into one model (create-polymer)',
        'parent': src.get('parent', 'block/block'),
        'textures': textures,
        'elements': elements,
    }


FLUID_BOXES = {
    # name: (texture, tint item tint source or None, texture to copy from Create or None)
    'water': ('minecraft:block/water_still', {'type': 'minecraft:constant', 'value': 0x3F76E4}, None),
    'lava': ('minecraft:block/lava_still', None, None),
    'potion': ('create:block/fluid_box/potion', {'type': 'minecraft:potion', 'default': 0x385DC6}, 'potion_still'),
    'honey': ('create:block/fluid_box/honey', None, 'honey_still'),
    'chocolate': ('create:block/fluid_box/chocolate', None, 'chocolate_still'),
    'tea': ('create:block/fluid_box/tea', None, 'tea_still'),
    'generic': ('minecraft:block/water_still', {'type': 'minecraft:custom_model_data', 'index': 0, 'default': 0x3F76E4}, None),
}


def fluid_box_model(texture, tinted):
    face = {'uv': [0, 0, 16, 16], 'texture': '#fluid'}
    if tinted:
        face['tintindex'] = 0
    return {
        'credit': 'A unit cube of fluid; display entities scale it (create-polymer)',
        'textures': {'fluid': texture, 'particle': texture},
        'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {d: dict(face) for d in
                                                                         ('north', 'south', 'east', 'west', 'up', 'down')}}],
    }


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as fh:
        json.dump(data, fh, separators=(",", ":"))


if __name__ == '__main__':
    create, port = sys.argv[1], sys.argv[2]
    assets = os.path.join(port, 'src/main/resources/assets/create')
    data = os.path.join(port, 'src/main/resources/data')
    create_assets = os.path.join(create, 'src/main/resources/assets/create')
    create_models = os.path.join(create_assets, 'models/block')

    # ---- connected tank models ----
    for kind in KINDS:
        for old in glob.glob(os.path.join(assets, 'models/block', kind, 'polymer_*.json')):
            os.remove(old)
    generated = names()
    for name, (kind, base, bits, y_low, y_high) in generated.items():
        write_json(os.path.join(assets, 'models/block', name + '.json'), build(create_models, kind, base, bits, y_low, y_high))

    textures = os.path.join(assets, 'textures/block')
    os.makedirs(textures, exist_ok=True)
    for kind in KINDS.values():
        for sheet in kind['sheets']:
            shutil.copyfile(os.path.join(create_assets, 'textures/block', sheet + '.png'), os.path.join(textures, sheet + '.png'))

    listed = set(load(port)['own_models'])
    missing = sorted('block/' + n for n in generated if 'block/' + n not in listed)
    if missing:
        raise SystemExit('manifest own_models is missing %d tank models, first: %s' % (len(missing), missing[0]))

    # ---- fluid boxes ----
    fluid_textures = os.path.join(textures, 'fluid_box')
    shutil.rmtree(fluid_textures, ignore_errors=True)
    os.makedirs(fluid_textures)
    for name, (texture, tint, source) in FLUID_BOXES.items():
        write_json(os.path.join(assets, 'models/block/fluid_box', name + '.json'), fluid_box_model(texture, tint is not None))
        model = {'type': 'minecraft:model', 'model': 'create:block/fluid_box/' + name}
        if tint is not None:
            model['tints'] = [tint]
        write_json(os.path.join(assets, 'items', 'fluid_box_' + name + '.json'), {'model': model})
        if source:
            for suffix in ('.png', '.png.mcmeta'):
                p = os.path.join(create_assets, 'textures/fluid', source + suffix)
                if os.path.exists(p):
                    shutil.copyfile(p, os.path.join(fluid_textures, name + suffix))

    listed_boxes = {'block/fluid_box/' + n for n in FLUID_BOXES}
    unlisted = sorted(listed_boxes - listed)
    if unlisted:
        raise SystemExit('manifest own_models is missing the fluid box models %s' % unlisted)

    # ---- data ----
    for fluid in ('honey', 'chocolate', 'tea'):
        write_json(os.path.join(data, 'c/tags/fluid', fluid + '.json'),
                   {'values': ['create:flowing_' + fluid, 'create:' + fluid]})
    heaters = json.load(open(os.path.join(create, 'src/generated/resources/data/create/tags/block/passive_boiler_heaters.json')))
    write_json(os.path.join(data, 'create/tags/block/passive_boiler_heaters.json'), heaters)

    print('tank models', len(generated), 'fluid boxes', len(FLUID_BOXES))
