"""Fluid pipe models. Create builds a pipe from a multipart blockstate (the core faces, chosen by which sides connect)
plus the attachment models that PipeAttachmentModel adds around it (connections, rims, drains, the casing).
Vanilla clients have neither the NeoForge model loader nor multipart blocks in our display entities, so this script
merges those pieces into whole models:

  block/fluid_pipe/polymer_core/<down><up><north><south><west><east>   core faces (+ casing) for one connection state (64)
  block/fluid_pipe/polymer_attachment/<type>_<direction>              what is drawn at one side of a pipe (36)

Usage: make_pipe_models.py CREATE_DIR PORT_DIR   (run after copy_assets.py)
"""
import itertools, json, os, shutil, sys

src_root, out_root = sys.argv[1], sys.argv[2]
SRC = [os.path.join(src_root, 'src/generated/resources/assets/create'), os.path.join(src_root, 'src/main/resources/assets/create')]
OUT = os.path.join(out_root, 'src/main/resources/assets/create')

DIRECTIONS = ['down', 'up', 'north', 'south', 'west', 'east']
AXES = {'x': ['down', 'up', 'north', 'south'], 'y': ['north', 'south', 'west', 'east'], 'z': ['down', 'up', 'west', 'east']}
AXIS_OF = {'down': 'y', 'up': 'y', 'north': 'z', 'south': 'z', 'west': 'x', 'east': 'x'}

# AttachmentTypes -> models (ComponentPartials: CONNECTION = connection, RIM_CONNECTOR, RIM, DRAIN)
ATTACHMENTS = {
    'connection': ['connection'],
    'rim_connector': ['rim_connector'],
    'rim': ['rim_connector', 'rim'],
    'partial_rim': ['rim'],
    'drain': ['rim_connector', 'drain'],
    'partial_drain': ['drain'],
}


def find(rel):
    for s in SRC:
        p = os.path.join(s, rel)
        if os.path.exists(p):
            return p
    return None


def load(path):
    return json.load(open(find('models/' + path + '.json')))


def resolve(path):
    """(textures, elements) of a create: model, following its parents."""
    d = load(path)
    textures, elements = {}, d.get('elements')
    parent = d.get('parent')
    if parent and parent.startswith('create:'):
        ptex, pel = resolve(parent.split(':', 1)[1])
        textures.update(ptex)
        if elements is None:
            elements = pel
    textures.update(d.get('textures', {}))
    return textures, elements or []


def clamp_uvs(elements):
    # 26.3 clients refuse faces whose UVs leave the texture (see copy_assets.py)
    for e in elements:
        for f in e['faces'].values():
            uv = f['uv']
            for a, b in ((0, 2), (1, 3)):
                lo, hi = min(uv[a], uv[b]), max(uv[a], uv[b])
                shift = 16 - hi if hi > 16 else (-lo if lo < 0 else 0)
                if shift:
                    uv[a] += shift
                    uv[b] += shift


def copy_texture(ref):
    ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    if ns != 'create':
        return
    for ext in ('.png', '.png.mcmeta'):
        p = find('textures/' + path + ext)
        if p:
            dst = os.path.join(OUT, 'textures', path + ext)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(p, dst)


def write_model(rel, textures, elements):
    clamp_uvs(elements)
    for t in textures.values():
        copy_texture(t)
    p = os.path.join(OUT, 'models', rel + '.json')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump({'credit': 'Merged from the pipe models of Create for vanilla clients (create-polymer)',
               'textures': textures, 'elements': elements}, open(p, 'w'), indent=1)


def retexture(elements, old, new):
    out = json.loads(json.dumps(elements))
    for e in out:
        for f in e['faces'].values():
            if f['texture'] == old:
                f['texture'] = new
    return out


# ---- attachments
for name, parts in ATTACHMENTS.items():
    for d in DIRECTIONS:
        textures, elements = {}, []
        for part in parts:
            t, el = resolve('block/fluid_pipe/%s/%s' % (part, d))
            if textures and any(textures.get(k) != v for k, v in t.items() if k != 'particle'):
                raise SystemExit('attachment parts use different textures: ' + name)
            textures.update(t)
            elements += json.loads(json.dumps(el))
        write_model('block/fluid_pipe/polymer_attachment/%s_%s' % (name, d), textures, elements)

# ---- cores: which of the 30 generated models Create's multipart blockstate picks for each connection state
multipart = json.load(open(find('blockstates/fluid_pipe.json')))['multipart']
casing_tex, casing_el = resolve('block/fluid_pipe/casing')
count = 0
for bits in itertools.product([False, True], repeat=6):
    state = {d: str(b).lower() for d, b in zip(DIRECTIONS, bits)}
    textures, elements = {}, []
    for part in multipart:
        if all(state[k] == v for k, v in part['when'].items()):
            t, el = resolve(part['apply']['model'].split(':', 1)[1])
            textures.update({k: v for k, v in t.items() if k != 'particle'})
            elements += json.loads(json.dumps(el))
    # PipeAttachmentModel draws the casing when more than two sides connect around any axis
    casing = False
    for axis in 'xyz':
        if sum(1 for d in DIRECTIONS if AXIS_OF[d] != axis and state[d] == 'true') > 2:
            casing = True
    if casing:
        textures['1'] = casing_tex['0']
        elements += retexture(casing_el, '#0', '#1')
    textures['particle'] = casing_tex['particle']
    if not elements:
        continue
    write_model('block/fluid_pipe/polymer_core/' + ''.join('1' if b else '0' for b in bits), textures, elements)
    count += 1
print('pipe models: 36 attachments,', count, 'cores')
