"""Item models of the handheld worldshaper and the wand of symmetry. Create draws the glowing core, the spinning
accelerator and the floating bits of these items with a client side renderer from separate partial models; here they
are merged into one static model with the rest of the item. Run after copy_assets.py."""
import json, os, sys

src_root, out_root = sys.argv[1], sys.argv[2]
SRC = os.path.join(src_root, 'src/main/resources/assets/create/models/item')
OUT = os.path.join(out_root, 'src/main/resources/assets/create/models/item')


def load(path):
    with open(os.path.join(SRC, path + '.json')) as f:
        return json.load(f)


def merge(item, parts):
    """item: the base model path; parts: [(prefix, partial model path)]. The partials only have elements and
    textures of their own (their parent contributes nothing), so their texture keys get a prefix."""
    base = load(item)
    model = {'credit': base.get('credit', ''), 'textures': dict(base['textures']),
             'elements': list(base['elements']), 'display': base['display']}
    for prefix, path in parts:
        part = load(path)
        for key, value in part['textures'].items():
            model['textures'][prefix + key] = value
        for element in part['elements']:
            for face in element['faces'].values():
                face['texture'] = '#' + prefix + face['texture'][1:]
            model['elements'].append(element)
    return model


def write(name, model):
    with open(os.path.join(OUT, name + '.json'), 'w') as f:
        json.dump(model, f, indent=1)


write('handheld_worldshaper', merge('handheld_worldshaper/item', [
    ('core_', 'handheld_worldshaper/core'),
    ('glow_', 'handheld_worldshaper/core_glow'),
    ('accel_', 'handheld_worldshaper/accelerator')]))
write('wand_of_symmetry', merge('wand_of_symmetry/item', [
    ('core_', 'wand_of_symmetry/core'),
    ('glow_', 'wand_of_symmetry/core_glow'),
    ('bits_', 'wand_of_symmetry/bits')]))
print('wands models written')
