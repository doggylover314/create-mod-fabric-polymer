import json, os, sys, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from manifest import load
src_root, out_root = sys.argv[1], sys.argv[2]
S = os.path.join(src_root, 'src/generated/resources/data')
D = os.path.join(out_root, 'src/main/resources/data')
M = load(out_root)
blocks = M['blocks']
os.makedirs(os.path.join(D, 'create/loot_table/blocks'), exist_ok=True)
for b in blocks:
    shutil.copyfile(os.path.join(S, f'create/loot_table/blocks/{b}.json'), os.path.join(D, f'create/loot_table/blocks/{b}.json'))
for tool in ['pickaxe', 'axe']:
    d = json.load(open(os.path.join(S, f'minecraft/tags/block/mineable/{tool}.json')))
    vals = [v for v in d['values'] if isinstance(v, str) and v.startswith('create:') and v.split(':')[1] in blocks]
    p = os.path.join(D, f'minecraft/tags/block/mineable/{tool}.json')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump({'values': vals}, open(p, 'w'), indent=2)
for t, vals in M['tags'].items():
    ns, path = t.split(':', 1)
    p = os.path.join(D, f'{ns}/tags/item/{path}.json')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump({'values': vals}, open(p, 'w'), indent=2)
print('ok')
