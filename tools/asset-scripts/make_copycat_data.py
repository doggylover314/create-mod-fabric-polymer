"""Recipes and tags of the copycat blocks (manifests/copycat.json), converted from Create 1.21.1 to Minecraft 26.3.

- The stonecutting recipes (zinc ingot -> 4 copycat steps / panels). convert_recipes.py only does the recipes listed under
  "crafting", so these are converted here, keeping Create's recipe ids.
- Removes the loot tables copy_data.py copied for copycat_base and copycat_bars: Create's drop minecraft:air, which 26.3
  refuses to load; the two blocks are registered with noLootTable() instead.
- The block tags create:copycat_allow and create:copycat_deny, which decide what a copycat accepts.
Run after convert_recipes.py (it clears data/create/recipe); run_all.sh does.
"""
import json, os, sys

src_root, out_root = sys.argv[1], sys.argv[2]
S = os.path.join(src_root, 'src/generated/resources/data/create')
D = os.path.join(out_root, 'src/main/resources/data/create')


def dump(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(data, open(path, 'w'), indent=2)


for name in ('copycat_step_from_ingots_zinc_stonecutting', 'copycat_panel_from_ingots_zinc_stonecutting'):
    d = json.load(open(os.path.join(S, 'recipe', name + '.json')))
    assert d['type'] == 'minecraft:stonecutting' and list(d['ingredient']) == ['tag']
    dump(os.path.join(D, 'recipe', name + '.json'), {
        'type': d['type'],
        'ingredient': '#' + d['ingredient']['tag'],
        'result': {'id': d['result']['id'], 'count': d['result'].get('count', 1)},
    })

for name in ('copycat_base', 'copycat_bars'):
    path = os.path.join(D, 'loot_table/blocks', name + '.json')
    if os.path.exists(path):
        os.remove(path)

for tag in ('copycat_allow', 'copycat_deny'):
    d = json.load(open(os.path.join(S, 'tags/block', tag + '.json')))
    dump(os.path.join(D, 'tags/block', tag + '.json'), {'values': d['values']})
print('copycat data ok')
