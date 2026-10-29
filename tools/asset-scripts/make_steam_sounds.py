"""Copy Create's whistle sound files and add their events to the pack's sounds.json.

The steam whistle plays them from the server (Create plays them from a client sound instance), so players need
the sounds in the resource pack. Existing entries in sounds.json are kept.
"""
import json, os, shutil, sys

src_root, out_root = sys.argv[1], sys.argv[2]
SRC = os.path.join(src_root, 'src/main/resources/assets/create')
EVENTS = os.path.join(src_root, 'src/generated/resources/assets/create/sounds.json')
OUT = os.path.join(out_root, 'src/main/resources/assets/create')

KEEP = ['whistle', 'whistle_high', 'whistle_low', 'chiff']

events = json.load(open(EVENTS))
out_file = os.path.join(OUT, 'sounds.json')
sounds = json.load(open(out_file)) if os.path.exists(out_file) else {}
os.makedirs(os.path.join(OUT, 'sounds'), exist_ok=True)
for name in KEEP:
    sounds[name] = events[name]
    for entry in events[name]['sounds']:
        if entry.get('type', 'file') == 'file' and entry['name'].startswith('create:'):
            f = entry['name'][len('create:'):] + '.ogg'
            shutil.copyfile(os.path.join(SRC, 'sounds', f), os.path.join(OUT, 'sounds', f))
json.dump(dict(sorted(sounds.items())), open(out_file, 'w'), indent=2)
print('sounds', sorted(sounds))
