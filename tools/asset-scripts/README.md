# Asset/data generation scripts

Regenerate everything from the original Create checkout (default `/home/user/create-original`, or pass another path):

```bash
tools/asset-scripts/run_all.sh [CREATE_DIR]
```

This deletes `src/main/resources/assets/create/{models,textures,items,lang}` and regenerates them, along with
`data/create/recipe`, the loot tables, the mineable and `c:` tags, `create_polymer/blockstates`, the trimmed lang files
and the water wheel models. Run the four scripts yourself with `python3 -I`, in this order, if needed:

```bash
python3 -I convert_recipes.py "$CREATE" "$PORT"
python3 -I copy_data.py "$CREATE" "$PORT"
python3 -I copy_assets.py "$CREATE" "$PORT"
python3 -I make_wheel_and_lang.py "$PORT"
```

## Manifests

What is ported is listed in `manifests/*.json`. `base.json` holds the current set. To port another block or item,
add a manifest file (or extend one) instead of editing the scripts. `manifest.py` merges all files in name order,
dropping duplicates. Every key is optional and unknown keys are an error:

- `items`: item ids that get an item definition and item model copied
- `known_items`: other ids recipes may use as ours
- `blocks`: block ids that get loot tables, mineable tags and lang keys; recipes may use them too
- `display_models`, `blockstates`, `own_models`: model paths and blockstate files to bundle (`own_models` are not copied from Create)
- `crafting`: crafting recipe names (under `create/recipe/`)
- `processing_types`: recipe folders converted like milling and pressing
- `tags`: `{"c:plates/iron": ["create:iron_sheet"]}`, written to `data/<namespace>/tags/item/<path>.json`
- `known_tags`: tags recipes may use
- `lang_ids`: ids whose `block.create.<id>` and `item.create.<id>` lang keys are kept
- `lang_prefixes`, `lang_keys`: extra lang key prefixes and exact keys to keep
