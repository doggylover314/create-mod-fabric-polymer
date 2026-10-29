"""Item vault models for vanilla clients (Create draws vaults with connected textures, which need a client mod).

Every vault block in a multiblock is shown as one cube whose six faces use the connected-texture tile that Create's
CT behaviour would pick, so the whole vault looks connected. The cubes are authored for a vault along Z (Create's own
blockstate rotates it by 90 degrees for X, and the server does the same with the display entity). A cube is named by
the sheet size (m = 2x2 and smaller, l = 3x3) and by which of its six neighbours belong to the same vault:

    block/item_vault/polymer_<m|l>_<x-><x+><y-><y+><z-><z+>      (1 = neighbour present)

Usage: make_vault_models.py CREATE_DIR PORT_DIR
"""
import json, os, shutil, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from manifest import load

# Face -> (texture, uv, rotation, direction of the texture's "up", direction of its "right") taken from Create's
# item_vault model and ItemVaultCTBehaviour (vault along Z). Directions are the neighbour a texture-space step reaches.
FACES = {
    'north': ('#1', [0, 0, 16, 16], 0, 'up', 'west'),
    'south': ('#1', [0, 0, 16, 16], 0, 'up', 'east'),
    'east': ('#2', [0, 0, 16, 16], 90, 'north', 'down'),
    'west': ('#2', [0, 16, 16, 0], 90, 'north', 'down'),
    'up': ('#3', [0, 0, 16, 16], 0, 'north', 'east'),
    'down': ('#0', [0, 0, 16, 16], 0, 'south', 'east'),
}
OPPOSITE = {'up': 'down', 'down': 'up', 'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
# neighbour direction -> index into the flag tuple (x-, x+, y-, y+, z-, z+)
FLAG = {'west': 0, 'east': 1, 'down': 2, 'up': 3, 'north': 4, 'south': 5}


def tile(flags, up, right):
    """Create's AllCTTypes.RECTANGLE.getTextureIndex as (column, row) in the 4x4 sheet."""
    u, d = flags[FLAG[up]], flags[FLAG[OPPOSITE[up]]]
    r, l = flags[FLAG[right]], flags[FLAG[OPPOSITE[right]]]
    x = 2 if l and r else 3 if l else 1 if r else 0
    y = 1 if u and d else 2 if u else 0 if d else 3
    return x, y


def model(flags, size):
    sheet = 'medium' if size == 'm' else 'large'
    tex = {
        '0': 'create:block/vault/vault_bottom_' + sheet,
        '1': 'create:block/vault/vault_front_' + sheet,
        '2': 'create:block/vault/vault_side_' + sheet,
        '3': 'create:block/vault/vault_top_' + sheet,
        'particle': 'create:block/vault/vault_top_small',
    }
    faces = {}
    for name, (texture, uv, rotation, up, right) in FACES.items():
        tx, ty = tile(flags, up, right)
        u0, v0, u1, v1 = uv
        f = {'uv': [tx * 4 + u0 / 4, ty * 4 + v0 / 4, tx * 4 + u1 / 4, ty * 4 + v1 / 4], 'texture': texture}
        if rotation:
            f['rotation'] = rotation
        faces[name] = f
    return {
        'credit': 'Connected-texture tiles of a vault block baked into one cube (create-polymer)',
        'textures': tex,
        'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces}],
    }


def names():
    """Every distinct (size, flags) a vault block can have: width 1-3, length 1 to 3 * width."""
    seen = {}
    for w in (1, 2, 3):
        for length in range(1, 3 * w + 1):
            for x in range(w):
                for y in range(w):
                    for z in range(length):
                        flags = (x > 0, x < w - 1, y > 0, y < w - 1, z > 0, z < length - 1)
                        size = 'l' if w > 2 else 'm'
                        seen[(size, flags)] = 'item_vault/polymer_%s_%s' % (size, ''.join('1' if f else '0' for f in flags))
    return seen


if __name__ == '__main__':
    create, port = sys.argv[1], sys.argv[2]
    assets = os.path.join(port, 'src/main/resources/assets/create')
    out = os.path.join(assets, 'models/block/item_vault')
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out)
    generated = names()
    for (size, flags), name in generated.items():
        with open(os.path.join(assets, 'models/block', name + '.json'), 'w') as fh:
            json.dump(model(flags, size), fh, indent=1)

    src = os.path.join(create, 'src/main/resources/assets/create/textures/block/vault')
    dst = os.path.join(assets, 'textures/block/vault')
    os.makedirs(dst, exist_ok=True)
    for part in ('top', 'front', 'side', 'bottom'):
        for sheet in ('medium', 'large'):
            shutil.copyfile(os.path.join(src, 'vault_%s_%s.png' % (part, sheet)), os.path.join(dst, 'vault_%s_%s.png' % (part, sheet)))
    shutil.copyfile(os.path.join(src, 'vault_top_small.png'), os.path.join(dst, 'vault_top_small.png'))

    listed = set(load(port)['own_models'])
    missing = sorted('block/' + n for n in generated.values() if 'block/' + n not in listed)
    if missing:
        raise SystemExit('manifest own_models is missing %d vault models, first: %s' % (len(missing), missing[0]))
    print('vault models', len(generated))
