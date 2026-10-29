"""Recipes and tags of the decoration blocks (manifests/decoration.json), converted from Create 1.21.1 to Minecraft 26.3.

- Every recipe in data/create/recipe whose result is one of the decoration blocks: the stair, slab and wall crafting,
  the slab recycling, the window and pane crafting and all stonecutting recipes (convert_recipes.py only does the
  recipes listed under "crafting" and the processing folders). The recipes keep Create's ids ("create:cut_limestone_slab").
  Also the smelting recipes that turn framed and tiled glass back into glass.
- Item and block tags that list decoration blocks (create:stone_types/*, minecraft:stairs/slabs/walls/climbable,
  c:glass_blocks, ...). Existing tag files are extended, not replaced, so run this after copy_data.py and
  convert_recipes.py (run_all.sh does).
"""
import glob, json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from manifest import load

src_root, out_root = sys.argv[1], sys.argv[2]
S = os.path.join(src_root, 'src/generated/resources/data')
D = os.path.join(out_root, 'src/main/resources/data')
M = load(out_root)

MINE = set(json.load(open(os.path.join(out_root, 'tools/asset-scripts/manifests/decoration.json')))['blocks'])
OURS = set(M['items']) | set(M['known_items']) | set(M['blocks'])
# tags the recipes may use: Fabric's convention tags and the ones this port or this script defines
KNOWN_TAGS = {'c:glass_blocks/colorless', 'c:glass_panes/colorless', 'c:ingots/iron', 'c:ingots/copper', 'c:strings'} \
    | set(M['known_tags'])


def known_item(i):
    ns, path = i.split(':')
    return ns == 'minecraft' or (ns == 'create' and path in OURS)


def ingredient(o):
    """1.21.1 {"item"} / {"tag"} (or a list of them) -> 26.3 "id" / "#tag" / list; None if it refers to something unported."""
    if isinstance(o, list):
        parts = [ingredient(x) for x in o]
        return None if any(p is None for p in parts) else parts
    if 'item' in o:
        return o['item'] if known_item(o['item']) else None
    if 'tag' in o:
        t = o['tag']
        if t.startswith('create:stone_types/') or t in KNOWN_TAGS:
            return '#' + t
    return None


def dump(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(data, open(path, 'w'), indent=2)


written = skipped = 0
RECIPES = os.path.join(S, 'create/recipe')
for f in sorted(glob.glob(os.path.join(RECIPES, '*.json')) + glob.glob(os.path.join(RECIPES, 'smelting', '*.json'))):
    d = json.load(open(f))
    name = os.path.relpath(f, RECIPES)
    if 'neoforge:conditions' in d:
        continue
    t = d['type']
    if t == 'minecraft:smelting':
        ing = d['ingredient']
        if 'item' not in ing or not ing['item'].startswith('create:') or ing['item'][7:] not in MINE:
            continue
        out = {'type': t, 'category': d.get('category', 'misc'), 'cookingtime': d['cookingtime'],
               'experience': d['experience'], 'ingredient': ingredient(ing),
               'result': {'id': d['result']['id'], 'count': d['result'].get('count', 1)}}
        dump(os.path.join(D, 'create/recipe', name), out)
        written += 1
        continue
    res = d.get('result')
    if not isinstance(res, dict) or not res.get('id', '').startswith('create:') or res['id'][7:] not in MINE:
        continue
    out = {'type': t}
    if t == 'minecraft:crafting_shaped':
        out['category'] = d.get('category', 'misc')
        if 'group' in d:
            out['group'] = d['group']
        out['pattern'] = d['pattern']
        out['key'] = {k: ingredient(v) for k, v in d['key'].items()}
        if any(v is None for v in out['key'].values()):
            print('skip (ingredient)', name); skipped += 1; continue
    elif t == 'minecraft:crafting_shapeless':
        out['category'] = d.get('category', 'misc')
        if 'group' in d:
            out['group'] = d['group']
        out['ingredients'] = [ingredient(v) for v in d['ingredients']]
        if any(v is None for v in out['ingredients']):
            print('skip (ingredient)', name); skipped += 1; continue
    elif t == 'minecraft:stonecutting':
        out['ingredient'] = ingredient(d['ingredient'])
        if out['ingredient'] is None:
            print('skip (ingredient)', name); skipped += 1; continue
    else:
        print('skip (type)', name, t); skipped += 1; continue
    out['result'] = {'id': res['id'], 'count': res.get('count', 1)}
    dump(os.path.join(D, 'create/recipe', name), out)
    written += 1
print('recipes written', written, 'skipped', skipped)


# ---- tags ----
def tag_entries(namespace_dir, kind):
    root = os.path.join(S, namespace_dir, 'tags', kind)
    for f in sorted(glob.glob(os.path.join(root, '**', '*.json'), recursive=True)):
        yield os.path.relpath(f, root)[:-5], f


tags_written = 0
for ns in ('minecraft', 'c', 'create'):
    for kind in ('block', 'item'):
        for rel, f in tag_entries(ns, kind):
            if rel.startswith('mineable/') or rel.startswith('needs_'):
                continue  # copy_data.py / copy_worldgen.py write these
            if ns == 'create' and not rel.startswith('stone_types/'):
                continue  # Create's own tags (fan_transparent, ...) belong to the features that read them
            keep_vanilla = ns == 'create' and rel.startswith('stone_types/')
            values = []
            for v in json.load(open(f))['values']:
                if not isinstance(v, str):
                    continue
                if (v.startswith('create:') and v[7:] in MINE) or (keep_vanilla and v.startswith('minecraft:')):
                    values.append(v)
            if not values:
                continue
            out = os.path.join(D, ns, 'tags', kind, rel + '.json')
            if os.path.exists(out):
                # keep what other scripts wrote, but not stale decoration entries of an earlier run
                for v in json.load(open(out))['values']:
                    if v not in values and not (isinstance(v, str) and v.startswith('create:') and v[7:] in MINE):
                        values.append(v)
            dump(out, {'values': values})
            tags_written += 1
print('tags written', tags_written)
