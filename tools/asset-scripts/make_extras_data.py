"""Recipes of the extras (manifests/extras.json) that Create has outside the crafting and processing folders: the
rose quartz block and the two rose quartz tile blocks are cut with the stonecutter. The recipes keep Create's ids."""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from manifest import load

src_root, out_root = sys.argv[1], sys.argv[2]
S = os.path.join(src_root, 'src/generated/resources/data/create/recipe')
D = os.path.join(out_root, 'src/main/resources/data/create/recipe')
M = load(out_root)
OURS = set(M['items']) | set(M['known_items']) | set(M['blocks'])
SRC_TEX = os.path.join(src_root, 'src/main/resources/assets/create/textures/block/palettes')
ASSETS = os.path.join(out_root, 'src/main/resources/assets/create')

NAMES = [
    'rose_quartz_block_from_rose_quartz_stonecutting',
    'rose_quartz_tiles_from_polished_rose_quartz_stonecutting',
    'small_rose_quartz_tiles_from_polished_rose_quartz_stonecutting',
]

for name in NAMES:
    d = json.load(open(os.path.join(S, name + '.json')))
    if d['type'] != 'minecraft:stonecutting' or 'neoforge:conditions' in d:
        raise SystemExit('unexpected recipe ' + name)
    ing = d['ingredient']['item']
    res = d['result']
    if ing.split(':')[1] not in OURS or res['id'].split(':')[1] not in OURS:
        print('skip', name)
        continue
    out = {'type': d['type'], 'ingredient': ing, 'result': {'id': res['id'], 'count': res.get('count', 1)}}
    os.makedirs(D, exist_ok=True)
    json.dump(out, open(os.path.join(D, name + '.json'), 'w'), indent=2)
    print('written', name)

# The rose quartz tile blocks are plain cubes of their texture. Create's datagen wrote their blockstates but not the
# cube models, so the cube_all models are written here, with the textures (listed under "own_models" in the manifest).
for name in ['rose_quartz_tiles', 'small_rose_quartz_tiles']:
    model = os.path.join(ASSETS, 'models/block/palettes', name + '.json')
    os.makedirs(os.path.dirname(model), exist_ok=True)
    json.dump({'parent': 'minecraft:block/cube_all', 'textures': {'all': 'create:block/palettes/' + name}},
              open(model, 'w'), indent=2)
    tex_dir = os.path.join(ASSETS, 'textures/block/palettes')
    os.makedirs(tex_dir, exist_ok=True)
    import shutil
    shutil.copyfile(os.path.join(SRC_TEX, name + '.png'), os.path.join(tex_dir, name + '.png'))
    print('cube model', name)
