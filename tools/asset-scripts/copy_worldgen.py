"""Materials data that the other scripts do not write, converted from Create 1.21.1 to Minecraft 26.3:

- zinc ore worldgen: a configured feature (data/create/worldgen/feature) and a placed feature
  (data/create/worldgen/placed_feature). The biome hookup is Java (dev.createpolymer.worldgen.CreateWorldgen).
- smelting and blasting recipes that take zinc or crushed raw ores (data/create/recipe/{smelting,blasting}).
- the vanilla needs_iron_tool block tag, with the manifest blocks that Create lists there.
- loot tables of the manifest blocks, rewritten to the 26.3 format (copy_data.py copies them as they are).

Run it after copy_data.py and convert_recipes.py (run_all.sh does): convert_recipes.py clears the recipe folder.
"""
import json, os, sys, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from manifest import load

src_root, out_root = sys.argv[1], sys.argv[2]
S = os.path.join(src_root, 'src/generated/resources/data')
D = os.path.join(out_root, 'src/main/resources/data')
M = load(out_root)

OURS = {'create:' + i for i in M['items'] + M['known_items'] + M['blocks']}
KNOWN_TAGS = set(M['known_tags'])
written = []


def dump(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(data, open(path, 'w'), indent=2)
    written.append(path)


def load_json(rel):
    return json.load(open(os.path.join(S, rel)))


# ---- zinc ore worldgen ----
# 1.21.1 wraps the ore settings in "config" and writes block states as {"Name", "Properties"}; 26.3 flattens the
# settings into the feature object and writes block states as "name[props]". The registry folder is now "feature".
def block_state(s):
    props = s.get('Properties', {})
    if not props:
        return s['Name']
    return s['Name'] + '[' + ','.join('%s=%s' % (k, v) for k, v in sorted(props.items())) + ']'


ZINC_FEATURE = load_json('create/worldgen/configured_feature/zinc_ore.json')
assert ZINC_FEATURE['type'] == 'minecraft:ore', ZINC_FEATURE['type']
cfg = ZINC_FEATURE['config']
assert set(cfg) == {'discard_chance_on_air_exposure', 'size', 'targets'}, sorted(cfg)
dump(os.path.join(D, 'create/worldgen/feature/zinc_ore.json'), {
    'type': 'minecraft:ore',
    'discard_chance_on_air_exposure': cfg['discard_chance_on_air_exposure'],
    'size': cfg['size'],
    'targets': [{'state': block_state(t['state']), 'target': t['target']} for t in cfg['targets']],
})

# Create's config_filter placement only checks Create's worldGen.disable option, which is off by default, so the
# port drops it and always generates zinc.
ZINC_PLACED = load_json('create/worldgen/placed_feature/zinc_ore.json')
PLACEMENTS_26 = {'minecraft:count', 'minecraft:in_square', 'minecraft:height_range', 'minecraft:biome'}
placement = []
for p in ZINC_PLACED['placement']:
    if p['type'] == 'create:config_filter':
        continue
    assert p['type'] in PLACEMENTS_26, p['type']
    placement.append(p)
dump(os.path.join(D, 'create/worldgen/placed_feature/zinc_ore.json'), {
    'feature': ZINC_PLACED['feature'],
    'placement': placement,
})


# ---- smelting and blasting of zinc and crushed ores ----
def ingredient(o):
    """1.21.1 {"item"} / {"tag"} -> 26.3 "id" / "#tag"; None when it refers to something this port does not have."""
    if 'item' in o:
        ref = o['item']
        return ref if ref in OURS or ref.startswith('minecraft:') else None
    if 'tag' in o:
        tag = o['tag']
        return '#' + tag if tag in KNOWN_TAGS or tag.startswith('minecraft:') else None
    return None


def is_zinc_or_crushed(o):
    ref = o.get('item') or o.get('tag') or ''
    return 'zinc' in ref or 'crushed_raw_' in ref


def cooking(name, d):
    if 'neoforge:conditions' in d or 'ingredient' not in d or not is_zinc_or_crushed(d['ingredient']):
        return None
    ing = ingredient(d['ingredient'])
    rid = d['result']['id']
    if ing is None or not (rid in OURS or rid.startswith('minecraft:')):
        return None
    out = {'type': d['type'], 'category': d.get('category', 'misc')}
    for k in ('cookingtime', 'experience'):
        out[k] = d[k]
    out['ingredient'] = ing
    out['result'] = {'id': rid, 'count': d['result'].get('count', 1)}
    return out


for t in ('smelting', 'blasting'):
    shutil.rmtree(os.path.join(D, 'create/recipe', t), ignore_errors=True)
    folder = os.path.join(S, 'create/recipe', t)
    for f in sorted(os.listdir(folder)):
        if not f.endswith('.json'):
            continue
        out = cooking(f, json.load(open(os.path.join(folder, f))))
        if out is None:
            print('skip (%s) %s' % (t, f))
            continue
        dump(os.path.join(D, 'create/recipe', t, f), out)


# ---- needs_iron_tool: vanilla's block tag, our blocks that Create lists in it ----
for tag in ('needs_iron_tool', 'needs_stone_tool'):
    vals = [v for v in load_json('minecraft/tags/block/%s.json' % tag)['values']
            if v.startswith('create:') and v.split(':')[1] in M['blocks']]
    if vals:
        dump(os.path.join(D, 'minecraft/tags/block', tag + '.json'), {'values': vals})


# ---- loot tables: 1.21.1 -> 26.3 ----
# 26.3 keeps one optional "condition" per pool or entry (several are wrapped in minecraft:all_of), uses "modifier"
# for functions and "type" as the function id. Silk touch is the vanilla named predicate minecraft:tool/can_silk_touch.
SILK_TOUCH = {'predicate': {'predicates': {'minecraft:enchantments': [
    {'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}


def loot_condition(c):
    """An inline 26.3 condition is an object keyed by "type" (a bare id string is a reference to a named predicate)."""
    if c['condition'] == 'minecraft:match_tool':
        if c.get('predicate') == SILK_TOUCH['predicate']:
            return 'minecraft:tool/can_silk_touch'
        raise ValueError('unsupported match_tool predicate')
    if c['condition'] == 'minecraft:block_state_property':
        # 26.3: block_state_property became match_block (a block predicate: "blocks" + "state")
        out = {'type': 'minecraft:match_block', 'blocks': c['block']}
        if c.get('properties'):
            out['state'] = c['properties']
        return out
    out = dict(c)
    out['type'] = out.pop('condition')
    if 'term' in out:
        out['term'] = loot_condition(out['term'])
    if 'terms' in out:
        out['terms'] = [loot_condition(t) for t in out['terms']]
    return out


def loot_function(f):
    f = loot_node(f)
    f['type'] = f.pop('function')
    return f


def loot_node(node):
    out = {}
    for k, v in node.items():
        if k == 'conditions':
            conds = [loot_condition(c) for c in v]
            if len(conds) == 1:
                out['condition'] = conds[0]
            elif conds:
                out['condition'] = {'type': 'minecraft:all_of', 'terms': conds}
        elif k == 'functions':
            out['modifier'] = [loot_function(f) for f in v]
        elif k in ('pools', 'entries', 'children'):
            out[k] = [loot_node(x) for x in v]
        else:
            out[k] = v
    return out


converted = 0
for b in M['blocks']:
    rel = 'create/loot_table/blocks/%s.json' % b
    p = os.path.join(D, rel)
    if not os.path.exists(p):
        print('missing loot table', rel)
        continue
    try:
        new = loot_node(json.load(open(p)))
    except ValueError as e:
        print('WARNING: loot table %s left in 1.21.1 format: %s' % (rel, e))
        continue
    dump(p, new)
    converted += 1

print('written', len(written), 'files; loot tables converted', converted)
