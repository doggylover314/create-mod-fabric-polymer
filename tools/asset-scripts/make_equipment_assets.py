"""Equipment assets and data that are not copied 1:1 from Create: armor equipment assets and textures, the potato
cannon projectile types, enchantments, the potato cannon damage type and the recipes with custom or smithing types."""
import json, os, shutil, sys

src_root, out_root = sys.argv[1], sys.argv[2]
SRC_ASSETS = os.path.join(src_root, 'src/main/resources/assets/create')
ASSETS = os.path.join(out_root, 'src/main/resources/assets/create')
DATA = os.path.join(out_root, 'src/main/resources/data/create')


def dump(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)


# ---- Armor: 26.3 equipment assets (assets/<ns>/equipment/<id>.json) and their textures ----
def armor_texture(layer_file, kind, name):
    dst = os.path.join(ASSETS, 'textures/entity/equipment', kind, name + '.png')
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(os.path.join(SRC_ASSETS, 'textures/models/armor', layer_file), dst)

armor_texture('copper_diving_layer_1.png', 'humanoid', 'copper_diving')
armor_texture('netherite_diving_layer_1.png', 'humanoid', 'netherite_diving')
armor_texture('netherite_diving_layer_2.png', 'humanoid_leggings', 'netherite_diving')
# Create's cardboard armour: the same texture for the body and the leggings layers
armor_texture('cardboard_layer_1.png', 'humanoid', 'cardboard')
armor_texture('cardboard_layer_2.png', 'humanoid_leggings', 'cardboard')
dump(os.path.join(ASSETS, 'equipment/cardboard.json'),
     {'layers': {'humanoid': [{'texture': 'create:cardboard'}],
                 'humanoid_leggings': [{'texture': 'create:cardboard'}]}})
dump(os.path.join(ASSETS, 'equipment/copper_diving.json'),
     {'layers': {'humanoid': [{'texture': 'create:copper_diving'}]}})
dump(os.path.join(ASSETS, 'equipment/netherite_diving.json'),
     {'layers': {'humanoid': [{'texture': 'create:netherite_diving'}],
                 'humanoid_leggings': [{'texture': 'create:netherite_diving'}]}})

# ---- Enchantments: Create's capacity and potato recovery (their effects are read by the code) ----
def enchantment(name, supported, slots):
    dump(os.path.join(DATA, 'enchantment', name + '.json'), {
        'anvil_cost': 1,
        'description': {'translate': 'enchantment.create.' + name},
        'max_cost': {'base': 45, 'per_level_above_first': 15},
        'max_level': 3,
        'min_cost': {'base': 15, 'per_level_above_first': 15},
        'slots': slots,
        'supported_items': supported,
        'weight': 10,
    })

enchantment('capacity', '#create:pressurized_air_sources', ['mainhand'])
enchantment('potato_recovery', 'create:potato_cannon', ['mainhand'])

dump(os.path.join(DATA, 'damage_type/potato_cannon.json'), {
    'exhaustion': 0.0, 'message_id': 'create.potato_cannon', 'scaling': 'when_caused_by_living_non_player'})

# ---- Potato cannon projectile types (Create's AllPotatoProjectileTypes) ----
def fire(seconds): return {'type': 'create:set_on_fire', 'ticks': seconds * 20}
def potion(effect, level, ticks, recoverable): return {'type': 'create:potion_effect', 'effect': 'minecraft:' + effect, 'level': level, 'ticks': ticks, 'recoverable': recoverable}
def eff(id, duration, amp): return {'id': 'minecraft:' + id, 'duration': duration, 'amplifier': amp}
def plant(block): return {'type': 'create:plant_crop', 'block': 'minecraft:' + block}
def place(block): return {'type': 'create:place_block_on_ground', 'block': 'minecraft:' + block}
TUMBLE = {'type': 'create:tumble'}
def toward(angle, spin): return {'type': 'create:toward_motion', 'sprite_angle_offset': angle, 'spin': float(spin)}

# (name, items, options). Defaults of the codec: reload 10, damage 1, split 1, knockback 1, velocity 1, pitch 1.
TYPES = [
    ('fallback', [], dict(damage=0)),
    ('potato', ['potato'], dict(damage=5, reload_ticks=15, velocity_multiplier=1.25, knockback=1.5, render_mode=TUMBLE, on_block_hit=plant('potatoes'))),
    ('baked_potato', ['baked_potato'], dict(damage=5, reload_ticks=15, velocity_multiplier=1.25, knockback=0.5, render_mode=TUMBLE, pre_entity_hit=fire(3))),
    ('carrot', ['carrot'], dict(damage=4, reload_ticks=12, velocity_multiplier=1.45, knockback=0.3, render_mode=toward(140, 1), sound_pitch=1.5, on_block_hit=plant('carrots'))),
    ('golden_carrot', ['golden_carrot'], dict(damage=12, reload_ticks=15, velocity_multiplier=1.45, knockback=0.5, render_mode=toward(140, 2), sound_pitch=1.5)),
    ('sweet_berry', ['sweet_berries'], dict(damage=3, reload_ticks=10, knockback=0.1, velocity_multiplier=1.05, render_mode=TUMBLE, split=3, sound_pitch=1.25)),
    ('glow_berry', ['glow_berries'], dict(damage=2, reload_ticks=10, knockback=0.05, velocity_multiplier=1.05, render_mode=TUMBLE, split=2, sound_pitch=1.2, on_entity_hit=potion('glowing', 1, 200, False))),
    ('poison_potato', ['poisonous_potato'], dict(damage=5, reload_ticks=15, knockback=0.05, velocity_multiplier=1.25, render_mode=TUMBLE, on_entity_hit=potion('poison', 1, 160, True))),
    ('chorus_fruit', ['chorus_fruit'], dict(damage=3, reload_ticks=15, velocity_multiplier=1.20, knockback=0.05, render_mode=TUMBLE, on_entity_hit={'type': 'create:chorus_teleport', 'teleport_diameter': 20.0})),
    ('apple', ['apple'], dict(damage=5, reload_ticks=10, velocity_multiplier=1.45, knockback=0.5, render_mode=TUMBLE, sound_pitch=1.1)),
    ('golden_apple', ['golden_apple'], dict(damage=1, reload_ticks=100, velocity_multiplier=1.45, knockback=0.05, render_mode=TUMBLE, sound_pitch=1.1, on_entity_hit={'type': 'create:cure_zombie_villager'})),
    ('enchanted_golden_apple', ['enchanted_golden_apple'], dict(damage=1, reload_ticks=100, velocity_multiplier=1.45, knockback=0.05, render_mode=TUMBLE, sound_pitch=1.1,
        on_entity_hit={'type': 'create:food_effects', 'recoverable': False, 'effects': [{'type': 'minecraft:apply_effects', 'effects': [
            eff('regeneration', 400, 1), eff('resistance', 6000, 0), eff('fire_resistance', 6000, 0), eff('absorption', 2400, 3)]}]})),
    ('beetroot', ['beetroot'], dict(damage=2, reload_ticks=5, velocity_multiplier=1.6, knockback=0.1, render_mode=toward(140, 2), sound_pitch=1.6)),
    ('melon_slice', ['melon_slice'], dict(damage=3, reload_ticks=8, knockback=0.1, velocity_multiplier=1.45, render_mode=TUMBLE, sound_pitch=1.5)),
    ('glistering_melon', ['glistering_melon_slice'], dict(damage=5, reload_ticks=8, knockback=0.1, velocity_multiplier=1.45, render_mode=TUMBLE, sound_pitch=1.5, on_entity_hit=potion('glowing', 1, 100, True))),
    ('melon_block', ['melon'], dict(damage=8, reload_ticks=20, knockback=2.0, velocity_multiplier=0.95, render_mode=TUMBLE, sound_pitch=0.9, on_block_hit=place('melon'))),
    ('pumpkin_block', ['pumpkin'], dict(damage=6, reload_ticks=15, knockback=2.0, velocity_multiplier=0.95, render_mode=TUMBLE, sound_pitch=0.9, on_block_hit=place('pumpkin'))),
    ('pumpkin_pie', ['pumpkin_pie'], dict(damage=7, reload_ticks=15, knockback=0.05, velocity_multiplier=1.1, render_mode=TUMBLE, sticky=True, sound_pitch=1.1)),
    ('cake', ['cake'], dict(damage=8, reload_ticks=15, knockback=0.1, velocity_multiplier=1.1, render_mode=TUMBLE, sticky=True)),
    ('fish', ['cod', 'cooked_cod', 'salmon', 'cooked_salmon', 'tropical_fish'], dict(damage=4, knockback=0.6, velocity_multiplier=1.3, render_mode=toward(140, 1), sticky=True, sound_pitch=1.3)),
    ('pufferfish', ['pufferfish'], dict(damage=4, knockback=0.4, velocity_multiplier=1.1, render_mode=toward(140, 1), sticky=True, sound_pitch=1.1,
        on_entity_hit={'type': 'create:food_effects', 'recoverable': False, 'effects': [{'type': 'minecraft:apply_effects', 'effects': [
            eff('poison', 1200, 1), eff('hunger', 300, 2), eff('nausea', 300, 0)]}]})),
    ('suspicious_stew', ['suspicious_stew'], dict(damage=3, reload_ticks=40, knockback=0.2, velocity_multiplier=0.8, render_mode=toward(140, 1), drop_stack={'id': 'minecraft:bowl', 'count': 1}, on_entity_hit={'type': 'create:suspicious_stew'})),
]
# Not ported: chocolate_berry (chocolate glazed berries), honeyed_apple, blaze_cake

type_dir = os.path.join(DATA, 'create/potato_projectile_type')
shutil.rmtree(type_dir, ignore_errors=True)
for name, items, opts in TYPES:
    d = {'items': ['minecraft:' + i for i in items]}
    d.update(opts)
    dump(os.path.join(type_dir, name + '.json'), d)

# ---- Recipes with custom or smithing types (written after convert_recipes.py, which clears the folder) ----
R = os.path.join(DATA, 'recipe/crafting')
dump(os.path.join(R, 'curiosities/toolbox_dyeing.json'), {'type': 'create:toolbox_dyeing', 'category': 'misc'})
dump(os.path.join(R, 'curiosities/item_copying.json'), {'type': 'create:item_copying', 'category': 'misc'})

def smithing(name, base, addition, result):
    dump(os.path.join(R, 'appliances', name + '.json'), {
        'type': 'minecraft:smithing_transform',
        'template': 'minecraft:netherite_upgrade_smithing_template',
        'base': base, 'addition': addition,
        'result': {'id': result, 'count': 1}})

smithing('netherite_backtank', 'create:copper_backtank', '#c:ingots/netherite', 'create:netherite_backtank')
smithing('netherite_backtank_from_netherite', 'minecraft:netherite_chestplate', 'create:copper_backtank', 'create:netherite_backtank')
smithing('netherite_diving_helmet', 'create:copper_diving_helmet', '#c:ingots/netherite', 'create:netherite_diving_helmet')
smithing('netherite_diving_helmet_from_netherite', 'minecraft:netherite_helmet', 'create:copper_diving_helmet', 'create:netherite_diving_helmet')
smithing('netherite_diving_boots', 'create:copper_diving_boots', '#c:ingots/netherite', 'create:netherite_diving_boots')
smithing('netherite_diving_boots_from_netherite', 'minecraft:netherite_boots', 'create:copper_diving_boots', 'create:netherite_diving_boots')
print('equipment assets written')
