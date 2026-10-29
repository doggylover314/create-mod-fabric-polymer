"""Tags the fluid network reads: the fluids a hose pulley may drain endlessly and the blocks a pipe end does not
count as closed (Create's fan_transparent, filtered to blocks this port has).

Usage: make_pipe_data.py CREATE_DIR PORT_DIR
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from manifest import load
src_root, out_root = sys.argv[1], sys.argv[2]
S = os.path.join(src_root, 'src/generated/resources/data/create/tags')
D = os.path.join(out_root, 'src/main/resources/data/create/tags')
M = load(out_root)


def write(rel, values):
    p = os.path.join(D, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump({'values': values}, open(p, 'w'), indent=2)


write('fluid/bottomless/allow.json', json.load(open(os.path.join(S, 'fluid/bottomless/allow.json')))['values'])

ours = set(M['blocks'])
values = []
for v in json.load(open(os.path.join(S, 'block/fan_transparent.json')))['values']:
    if v.startswith('create:') and v.split(':', 1)[1] not in ours:
        continue
    values.append(v)
write('block/fan_transparent.json', values)
print('pipe tags ok')
