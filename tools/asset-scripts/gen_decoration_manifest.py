"""Writes manifests/decoration.json: the ids and models of the decoration blocks (palettes, glass, windows, bars,
ladders, scaffolding, cardboard, girders, placards, train trapdoors).

The stone-type ids follow Create's PaletteBlockPattern.createName / PalettesVariantEntry. The manifest is checked in;
run this script again after changing which blocks the port has:

    python3 -I tools/asset-scripts/gen_decoration_manifest.py
"""
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'manifests', 'decoration.json')

STONE_TYPES = ['granite', 'diorite', 'andesite', 'calcite', 'dripstone', 'deepslate', 'tuff',
               'asurine', 'crimsite', 'limestone', 'ochrum', 'scoria', 'scorchia', 'veridium']
NATURAL = ['asurine', 'crimsite', 'ochrum', 'veridium']   # four random model variants
NEW_TYPES = STONE_TYPES[7:]                                # types with their own base block
# cut stairs and slabs that get Create's models through Polymer's few stair/slab states (see DecorationRegistry)
TEXTURED_STAIR_SLAB_TYPES = ['limestone', 'scoria', 'ochrum']

# (pattern id, name type, partials)
PATTERNS = [
    ('cut', 'prefix', 'all'),
    ('polished_cut', 'prefix', 'polished'),
    ('cut_bricks', 'wrap', 'all'),
    ('small_bricks', 'wrap', 'all'),
    ('layered', 'prefix', None),
    ('pillar', 'suffix', None),
]

WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'crimson', 'warped', 'cherry', 'bamboo']
METALS = ['andesite', 'brass', 'copper']


def create_name(pattern, name_type, variant):
    if name_type == 'wrap':
        a, b = pattern.split('_')
        return '%s_%s_%s' % (a, variant, b)
    return ('%s_%s' % (variant, pattern)) if name_type == 'suffix' else ('%s_%s' % (pattern, variant))


def non_plural(name):
    return name[:-1] if name.endswith('s') else name


blocks = []          # every block id (all of them also get an item)
models = []          # block models shown through Polymer block states
blockstates = []     # Create blockstate files the code reads
palette_ids = []

for t in STONE_TYPES:
    if t in NEW_TYPES:
        blocks.append(t)
        palette_ids.append(t)
        if t in NATURAL:
            models += ['block/%s_natural_%d' % (t, i) for i in range(4)]
        else:
            models.append('block/' + t)
    for pattern, name_type, partials in PATTERNS:
        name = create_name(pattern, name_type, t)
        blocks.append(name)
        palette_ids.append(name)
        if pattern == 'pillar':
            models += ['block/' + name, 'block/%s_horizontal' % name]
        else:
            models.append('block/' + name)
        if partials:
            for partial in ('stairs', 'slab', 'wall'):
                blocks.append('%s_%s' % (non_plural(name), partial))
            if partials == 'polished':
                models.append('block/%s_slab_double' % name)

for t in TEXTURED_STAIR_SLAB_TYPES:
    for partial in ('stairs', 'slab'):
        blockstates.append('cut_%s_%s' % (t, partial))

# glass, windows, panes
glass = ['tiled_glass', 'framed_glass', 'horizontal_framed_glass', 'vertical_framed_glass']
windows = ['%s_window' % w for w in WOODS] + ['ornate_iron_window', 'industrial_iron_window', 'weathered_iron_window']
for g in glass + windows:
    blocks.append(g)
    if g == 'weathered_iron_window':
        models += ['block/weathered_iron_window_%d' % i for i in range(1, 5)]
    else:
        models.append('block/' + g)
for g in glass + windows:
    blocks.append(g + '_pane')

# iron blocks
for b in ('industrial_iron_block', 'weathered_iron_block'):
    blocks.append(b)
    models.append('block/' + b)

# metals
for m in METALS:
    blocks += [m + '_bars', m + '_ladder', m + '_scaffolding']
    models += ['block/%s_%s' % (m, part) for part in ('post_ends', 'post', 'cap', 'cap_alt', 'side', 'side_alt')]
    models += ['block/%s_scaffolding' % m, 'block/%s_scaffolding_horizontal' % m]

# cardboard
blocks += ['cardboard_block', 'bound_cardboard_block']
models += ['block/cardboard_block', 'block/bound_cardboard_block']


def dedup(seq):
    seen, out = set(), []
    for x in seq:
        if x not in seen:
            seen.add(x)
            out.append(x)
    return out


blocks = dedup(blocks)
manifest = {
    'items': blocks,
    'blocks': blocks,
    'display_models': dedup(models),
    'blockstates': dedup(blockstates),
    'crafting': ['materials/cardboard_block', 'materials/bound_cardboard_block'],
    'lang_ids': blocks,
}
with open(OUT, 'w') as f:
    json.dump(manifest, f, indent=2)
    f.write('\n')
print('blocks', len(blocks), 'models', len(manifest['display_models']))
