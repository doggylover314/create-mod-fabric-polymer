#!/usr/bin/env bash
# Regenerate the Create-derived recipes, data and assets in this repo.
# Usage: run_all.sh [CREATE_DIR]   (default: $CREATE_DIR, else a Create checkout next to this repo)
# Which items/blocks/models/recipes get ported is read from manifests/*.json.
set -euo pipefail

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="$(cd "$SCRIPTS/../.." && pwd)"
CREATE="${1:-${CREATE_DIR:-$PORT/../Create}}"
[ -d "$CREATE/src/main/resources" ] || { echo "Create checkout not found at $CREATE (pass its path as the first argument)" >&2; exit 1; }
ASSETS="$PORT/src/main/resources/assets/create"

rm -rf "$ASSETS/models" "$ASSETS/textures" "$ASSETS/items" "$ASSETS/lang"

python3 -I "$SCRIPTS/convert_recipes.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/copy_data.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/copy_assets.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_wheel_and_lang.py" "$PORT"
python3 -I "$SCRIPTS/make_vault_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_burner_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/copy_worldgen.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_crushing_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_extra_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_belt_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_pipe_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_pipe_data.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_tank_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_decoration_data.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_extras_data.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_copycat_data.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_redstone_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_equipment_assets.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_steam_sounds.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_package_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_stock_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_chain_conveyor_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_factory_gauge_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_actor_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_track_models.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_doors_data.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_tools_assets.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_wands_models.py" "$CREATE" "$PORT"
