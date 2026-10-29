"""Block and item tags of the sliding doors and the train trapdoors: Create puts them in the vanilla door and trapdoor tags."""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
src_root, out_root = sys.argv[1], sys.argv[2]
D = os.path.join(out_root, 'src/main/resources/data/minecraft/tags')

DOORS = ['create:andesite_door', 'create:brass_door', 'create:copper_door', 'create:train_door', 'create:framed_glass_door']
TRAPDOORS = ['create:train_trapdoor', 'create:framed_glass_trapdoor']

def write(kind, name, values):
    p = os.path.join(D, kind, name + '.json')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump({'values': values}, open(p, 'w'), indent=2)

write('block', 'doors', DOORS)
# Create tags them for the villagers' sake
write('block', 'wooden_doors', DOORS)
write('block', 'trapdoors', TRAPDOORS)
write('item', 'doors', DOORS)
write('item', 'trapdoors', TRAPDOORS)
print('door tags written')
