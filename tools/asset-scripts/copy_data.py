import json, os, sys, shutil
src_root, out_root = sys.argv[1], sys.argv[2]
S = os.path.join(src_root, 'src/generated/resources/data')
D = os.path.join(out_root, 'src/main/resources/data')
blocks = ['shaft','cogwheel','large_cogwheel','gearbox','hand_crank','water_wheel','creative_motor','millstone',
          'mechanical_press','mechanical_mixer','basin','andesite_casing']
os.makedirs(os.path.join(D, 'create/loot_table/blocks'), exist_ok=True)
for b in blocks:
    shutil.copyfile(os.path.join(S, f'create/loot_table/blocks/{b}.json'), os.path.join(D, f'create/loot_table/blocks/{b}.json'))
for tool in ['pickaxe', 'axe']:
    d = json.load(open(os.path.join(S, f'minecraft/tags/block/mineable/{tool}.json')))
    vals = [v for v in d['values'] if isinstance(v, str) and v.startswith('create:') and v.split(':')[1] in blocks]
    p = os.path.join(D, f'minecraft/tags/block/mineable/{tool}.json')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump({'values': vals}, open(p, 'w'), indent=2)
tags = {'plates/iron': ['create:iron_sheet'], 'plates/gold': ['create:golden_sheet'], 'plates/copper': ['create:copper_sheet'],
        'flours/wheat': ['create:wheat_flour']}
for t, vals in tags.items():
    p = os.path.join(D, f'c/tags/item/{t}.json')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump({'values': vals}, open(p, 'w'), indent=2)
print('ok')
