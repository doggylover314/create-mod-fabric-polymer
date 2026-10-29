"""Convert Create 1.21.1 (NeoForge) recipe JSON to Minecraft 26.3 format for the ported subset."""
import json, os, sys, glob, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from manifest import load
src_root, out_root = sys.argv[1], sys.argv[2]
R = os.path.join(src_root, 'src/generated/resources/data/create/recipe')
OUT = os.path.join(out_root, 'src/main/resources/data/create/recipe')
M = load(out_root)
shutil.rmtree(OUT, ignore_errors=True)

OURS = set(M['items']) | set(M['known_items']) | set(M['blocks'])
OUR_TAGS = set(M['known_tags'])
CRAFTING = M['crafting']

def known_item(i):
    ns, path = i.split(':')
    return ns == 'minecraft' or (ns == 'create' and path in OURS)

def ingredient(o):
    """Old {"item"/"tag"} or list -> new string / list of strings. None if unsupported."""
    if isinstance(o, list):
        parts = [ingredient(x) for x in o]
        if any(p is None for p in parts):
            return None
        flat = []
        for p in parts:
            flat.extend(p if isinstance(p, list) else [p])
        return flat
    if 'type' in o or 'amount' in o or 'fluid' in o:
        return None
    if 'item' in o:
        return o['item'] if known_item(o['item']) else None
    if 'tag' in o:
        t = o['tag']
        if t.startswith('create:') or (t.startswith('c:') and 'zinc' in t) or ('brass' in t):
            if t not in OUR_TAGS:
                return None
        return '#' + t
    return None

def result(o):
    if 'id' not in o or not known_item(o['id']) or 'amount' in o or 'fluid' in o:
        return None
    return o

written = 0
def write(rel, data):
    global written
    p = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(data, open(p, 'w'), indent=2)
    written += 1

for name in CRAFTING:
    d = json.load(open(os.path.join(R, 'crafting', name + '.json')))
    out = {'type': d['type'], 'category': d.get('category', 'misc')}
    if d['type'] == 'minecraft:crafting_shaped':
        out['pattern'] = d['pattern']
        out['key'] = {k: ingredient(v) for k, v in d['key'].items()}
        if any(v is None for v in out['key'].values()):
            print('skip (ingredient)', name); continue
    elif d['type'] == 'minecraft:crafting_shapeless':
        out['ingredients'] = [ingredient(v) for v in d['ingredients']]
        if any(v is None for v in out['ingredients']):
            print('skip (ingredient)', name); continue
    else:
        print('skip (type)', name, d['type']); continue
    out['result'] = {'id': d['result']['id'], 'count': d['result'].get('count', 1)}
    write('crafting/' + name + '.json', out)

for t in M['processing_types']:
    for f in sorted(glob.glob(os.path.join(R, t, '*.json'))):
        d = json.load(open(f))
        if 'neoforge:conditions' in d:
            continue
        ings = [ingredient(i) for i in d['ingredients']]
        res = [result(r) for r in d['results']]
        if not ings or any(i is None for i in ings) or any(r is None for r in res):
            continue
        out = {'type': d['type'], 'ingredients': ings, 'results': res}
        if 'processing_time' in d:
            out['processing_time'] = d['processing_time']
        if d.get('heat_requirement', 'none') != 'none':
            out['heat_requirement'] = d['heat_requirement']
        write(t + '/' + os.path.basename(f), out)

# andesite casing from stripped logs/wood (Create's item application, handled in code)
print('written', written)
