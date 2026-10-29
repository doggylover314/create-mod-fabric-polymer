"""Merged view of the port manifests in <out_root>/tools/asset-scripts/manifests/*.json.

Each manifest is one JSON object; every key is optional. List keys are concatenated across
manifests (sorted by file name, duplicates dropped, first occurrence kept). "tags" maps an
item tag key such as "c:plates/iron" to a list of item ids; values for the same key are merged.
"""
import glob, json, os

LIST_KEYS = ('items', 'known_items', 'blocks', 'display_models', 'blockstates', 'own_models', 'crafting',
             'processing_types', 'known_tags', 'lang_ids', 'lang_prefixes', 'lang_keys')
DICT_KEYS = ('tags',)


def _dedup(seq):
    seen, out = set(), []
    for x in seq:
        if x not in seen:
            seen.add(x)
            out.append(x)
    return out


def load(out_root):
    d = os.path.join(out_root, 'tools', 'asset-scripts', 'manifests')
    files = sorted(glob.glob(os.path.join(d, '*.json')))
    if not files:
        raise FileNotFoundError('no manifests in ' + d)
    merged = {k: [] for k in LIST_KEYS}
    merged['tags'] = {}
    for f in files:
        with open(f) as fh:
            m = json.load(fh)
        unknown = set(m) - set(LIST_KEYS) - set(DICT_KEYS)
        if unknown:
            raise ValueError('%s: unknown keys %s' % (f, sorted(unknown)))
        for k in LIST_KEYS:
            merged[k].extend(m.get(k, []))
        for tag, vals in m.get('tags', {}).items():
            merged['tags'].setdefault(tag, []).extend(vals)
    for k in LIST_KEYS:
        merged[k] = _dedup(merged[k])
    for tag in merged['tags']:
        merged['tags'][tag] = _dedup(merged['tags'][tag])
    return merged
