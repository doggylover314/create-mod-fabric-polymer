"""Models the tools need as block models, because display entities can only draw those (the server resource pack
bridges every model in models/block): the linked controller on a lectern, and Create's crafting blueprint panels.

The controller models are copied from the item models copy_assets.py wrote, which already have one atlas only. The
blueprint panels are entity models in Create, with textures in textures/entity; vanilla's block atlas does not take
those, so the textures move to textures/block.
Usage: make_tools_assets.py CREATE_DIR PORT_DIR   (run after copy_assets.py)
"""
import json, os, shutil, sys

src_root, out_root = sys.argv[1], sys.argv[2]
SRC_ASSETS = os.path.join(src_root, 'src/main/resources/assets/create')
ASSETS = os.path.join(out_root, 'src/main/resources/assets/create')


def dump(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=1)


# ---- linked controller (lectern, and the powered item) ----
for name in ('item', 'powered', 'button'):
    d = json.load(open(os.path.join(ASSETS, 'models/item/linked_controller', name + '.json')))
    dump(os.path.join(ASSETS, 'models/block/linked_controller', name + '.json'), d)
# the controller in a hand shows its lit antenna while somebody uses it (an item definition of its own)
dump(os.path.join(ASSETS, 'items/linked_controller_powered.json'),
     {'model': {'type': 'minecraft:model', 'model': 'create:item/linked_controller/powered'}})

# ---- clipboard: Create picks the item model with model predicates, here the item picks one of three item definitions ----
path = os.path.join(ASSETS, 'models/item/clipboard.json')
d = json.load(open(path))
d.pop('overrides', None)
dump(path, d)

# ---- crafting blueprint panels ----
for size in ('small', 'medium', 'large'):
    d = json.load(open(os.path.join(SRC_ASSETS, 'models/entity/crafting_blueprint_%s.json' % size)))
    d['textures'] = {k: v.replace('create:entity/', 'create:block/') for k, v in d['textures'].items()}
    dump(os.path.join(ASSETS, 'models/block/crafting_blueprint', size + '.json'), d)
    dst = os.path.join(ASSETS, 'textures/block/blueprint_%s.png' % size)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(os.path.join(SRC_ASSETS, 'textures/entity/blueprint_%s.png' % size), dst)
print('tools assets written')
