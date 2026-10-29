# Create (Polymer)

A server-side port of [Create](https://github.com/Creators-of-Create/Create) for **Fabric, Minecraft 26.3**, built on
[Polymer](https://github.com/Patbox/polymer). Only the server installs it. Players join with an unmodded
26.3 client and get Create's textures and models through a resource pack the server sends automatically.

This is an unofficial port and is not affiliated with or endorsed by the Create team.

## Status: first slice ("the basics")

Ported and tested:

| Block / item | Notes |
| --- | --- |
| Shaft, Cogwheel, Large Cogwheel | Full rotation propagation, gear ratios, cog meshing |
| Gearbox | One item; placement follows the clicked face (covers Create's vertical gearbox too) |
| Hand Crank | 32 RPM, 8 su/RPM, costs hunger like Create |
| Water Wheel (small) | Flow detection as in Create; model is a hand-made approximation (see below) |
| Creative Motor | Right-click to raise speed, sneak + right-click to lower |
| Millstone | Milling recipes; throw items on top or use a hopper; empty hand takes outputs |
| Mechanical Press | Presses items lying under it; compacts in a basin (Create compacting + 2x2/3x3 crafting) |
| Mechanical Mixer | Needs ≥ 30 RPM; mixing + shapeless crafting in a basin |
| Basin | Throw items in; hoppers below pull results out |
| Andesite Casing | Use andesite alloy on a stripped log/wood |
| Andesite Alloy, Iron/Golden/Copper Sheet, Wheat Flour, Whisk | Materials |
| Wrench | Right-click rotates, sneak + right-click picks up |
| Engineer's Goggles | Wear or hold, look at a kinetic block: RPM and stress show in the action bar |

The stress system (capacity, impact, overstressing) is Create's code, ported. Stress values match Create's defaults.

Not ported yet: belts, depots, chutes, funnels, fluids (pipes, tanks, recipes that use fluids), blaze burners
(so heated recipes), encased shafts, chain drives, gearshifts/clutches, contraptions, trains, the large water
wheel, Ponder, JEI-style recipe viewing, and many more.

## How it works

- Blocks are real server blocks. Players see an invisible stand-in (a barrier, or an invisible lightning rod for
  shafts) with the right hitbox. Create's own block models are shown on top with item display entities.
- Spinning parts are re-sent every few ticks and the client interpolates between updates. The angle follows
  Create's formula (`time × RPM × 0.3 + offset`), so neighbouring cogs mesh.
- Andesite casing uses one of Polymer's spare vanilla block states, so it renders as a normal full block.
- Machine screens are not needed: everything here works by clicking, hoppers or dropped items.

## Installing on a server

1. Fabric Loader ≥ 0.20.1 and Fabric API for 26.3.
2. Put `create-polymer-<version>.jar` in `mods/` (Polymer is bundled inside).
3. Start the server. On first start the mod turns on Polymer's resource-pack auto-hosting
   (`config/polymer/auto-host.json`) so players are offered the pack when they join.
   If your host blocks extra traffic on the game port, see Polymer's auto-host docs.

## Building and testing

Requires JDK 25.

```bash
./gradlew build          # jar in build/libs
./gradlew runGametest    # 16 in-world tests (kinetics, gears, overstress, millstone, press, mixer, hoppers, water wheel)
./gradlew runServer      # dev server in ./run
```

## Licences

- Code is ported from Create, which is MIT-licensed (see `LICENSE.md`). New code in this repo is under the same licence.
- **Create's assets (textures, models, translations) are "All Rights Reserved" by the Create team.** This repo
  copies the subset needed for the ported blocks so the resource pack works. Distributing them publicly may need
  the Create team's permission.
- The Create repository asks that AI tools not be used on its code (`AGENTS.md` in the upstream repo).
  This port was produced with AI assistance.
