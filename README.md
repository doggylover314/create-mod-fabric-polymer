# Create (Polymer)

A server-side port of [Create](https://github.com/Creators-of-Create/Create) for **Fabric, Minecraft 26.3**, built on
[Polymer](https://github.com/Patbox/polymer). Only the server installs it. Players join with an unmodded
26.3 client and get Create's textures and models through a resource pack the server sends automatically.

This is an unofficial port and is not affiliated with or endorsed by the Create team.

## Status

Ported, with game tests:

| Area | What is in |
| --- | --- |
| Kinetics | Shafts, cogwheels, gearboxes, clutch, gearshift, sequenced gearshift, encased and chain drives, adjustable chain gearshift, rotation speed controller, hand crank, water wheels, creative motor, flywheel, turntable, cuckoo clock, speedometer and stressometer. Rotation propagation and the stress system are Create's code |
| Processing | Millstone, mechanical press, mixer, basin (with side output when wrenched), blaze burner, crushing wheels, encased fan and nozzle, mechanical saw, mechanical drill, deployer, mechanical crafter, mechanical arm, sequenced assembly, andesite and brass casings |
| Logistics | Belts and belt tunnels, chutes, funnels, depot, weighted ejector, item vault, item hatch, creative crate, filters |
| Fluids | Pipes (including glass and encased), pumps, valves, smart pipes, hose pulley, fluid tanks and boilers, item drain, spout, filling and emptying recipes |
| Redstone | Analog lever, redstone link, contact, content observer, stockpile switch, pulse repeater, extender and timer, powered latches, desk bell, nixie tube, rose quartz lamp |
| Decoration | The stone palettes with their stairs, slabs and walls, glass, windows and panes, industrial and weathered iron, metal bars, ladders and scaffolding, cardboard blocks |
| Materials | Zinc ore and its worldgen, brass, andesite alloy, sheets, nuggets, electron tubes and other recipe ingredients |
| Tools | Wrench (rotate, pick up, set basin output), Engineer's Goggles (kinetic and block information in the action bar) |
| Contraptions | In progress |

Machines are used by clicking, hoppers, belts or dropped items. Filters and value settings use server-side
chest-style menus.

Not ported: trains, schematics, Ponder, advancements, the config screen and recipe-viewer integration.

## Limitations for vanilla clients

Players run an unmodded client, so everything that needs client code is replaced or left out.

- No connected textures: connected glass, framed glass and similar blocks show their single-block texture.
- Stairs, walls and panes of the decoration palettes use the vanilla block shapes and models.
- Moving contraptions are in progress. Players and entities on them are limited by what a server can do without a
  client-side collision model.
- Animated textures such as the blaze burner flames are made with animated resource-pack textures, and fluids in
  tanks and basins are drawn with display entities. Models that Create loads as OBJ were converted to plain JSON
  models; models that need NeoForge loaders are not available.
- No Ponder, no goggle overlay: goggle information is shown in the action bar and cycles when it is long.

## How it works

- Blocks are real server blocks. Players see an invisible stand-in (a barrier, or an invisible lightning rod for
  shafts) with the right hitbox. Create's own block models are shown on top with item display entities.
- Spinning parts are re-sent every few ticks and the client interpolates between updates. The angle follows
  Create's formula (`time × RPM × 0.3 + offset`), so neighbouring cogs mesh.
- Andesite casing uses one of Polymer's spare vanilla block states, so it renders as a normal full block.
- Machine screens are not needed: everything here works by clicking, hoppers or dropped items.

## Screenshots (unmodded 26.3 client)

![Cogwheels](docs/screenshots/cogs.png)
![Hand cranks](docs/screenshots/cranks.png)

## Installing on a server

1. Fabric Loader ≥ 0.20.1 and Fabric API for 26.3.
2. Put `create-polymer-<version>.jar` in `mods/` (Polymer is bundled inside).
3. Start the server. On first start the mod turns on Polymer's resource-pack auto-hosting
   (`config/polymer/auto-host.json`) so players are offered the pack when they join.
   If your host blocks extra traffic on the game port, see Polymer's auto-host docs.

## Configuration

Server settings are in `config/create.json`, which is written with all defaults the first time the server starts.
The sections are named after Create's server config (`kinetics`, `logistics`, `fluids`, `equipment`, `trains`,
`recipes`) and the keys are Create's own, with Create's defaults, for example:

```json
{
  "kinetics": { "maxBeltLength": 20, "maxBlocksMoved": 2048, "movableObsidian": "UNMOVABLE" },
  "trains": { "maxBogeyCount": 20 },
  "stress": {
    "impact": { "millstone": 4.0 },
    "capacity": { "hand_crank": 8.0 }
  }
}
```

- Values outside Create's allowed range are clamped to it, and a value of the wrong type falls back to the default.
  Unknown keys are ignored. Each of these is reported as a warning in the server log.
- Keys missing from the file are added the next time it is loaded; your own values are kept.
- `stress` overrides the stress impact (`impact`) and the stress capacity (`capacity`) per Create block, using the block id
  without the `create:` prefix. The file lists every block that has a value.
- `/create config reload` (operator level 2) reads the file again. If the file cannot be parsed the current settings stay.
  Sizes that are fixed when a block is placed, such as `vaultCapacity` and `fluidTankCapacity`, only affect new blocks.
- Settings that only exist in Create's client config or for features this port does not have (schematics and the symmetry
  wand) are not in the file. `fanParticleDensity` is a client option in Create and is a server
  setting here, because the server sends the particles.

## Building and testing

Requires JDK 25.

```bash
./gradlew build          # jar in build/libs
./gradlew runGametest    # in-world game tests
./gradlew runServer      # dev server in ./run
```

### Regenerating assets and data

Recipes, loot tables, tags, models, textures and language files are generated from a Create checkout:

```bash
tools/asset-scripts/run_all.sh [CREATE_DIR]   # default /home/user/create-original
```

What gets copied is listed in `tools/asset-scripts/manifests/*.json` (items, blocks, models, blockstates, recipes,
tags, language keys). To port another block, add its ids to a manifest and run the script again; see
`tools/asset-scripts/README.md` for the manifest keys. The generated files are committed under `src/main/resources`.

## Licences

- Code is ported from Create, which is MIT-licensed (see `LICENSE.md`). New code in this repo is under the same licence.
- **Create's assets (textures, models, translations) are "All Rights Reserved" by the Create team.** This repo
  copies the subset needed for the ported blocks so the resource pack works. Distributing them publicly may need
  the Create team's permission.
