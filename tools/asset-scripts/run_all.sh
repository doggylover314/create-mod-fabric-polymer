#!/usr/bin/env bash
# Regenerate the Create-derived recipes, data and assets in this repo.
# Usage: run_all.sh [CREATE_DIR]   (default: /home/user/create-original)
# Which items/blocks/models/recipes get ported is read from manifests/*.json.
set -euo pipefail

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="$(cd "$SCRIPTS/../.." && pwd)"
CREATE="${1:-/home/user/create-original}"
ASSETS="$PORT/src/main/resources/assets/create"

rm -rf "$ASSETS/models" "$ASSETS/textures" "$ASSETS/items" "$ASSETS/lang"

python3 -I "$SCRIPTS/convert_recipes.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/copy_data.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/copy_assets.py" "$CREATE" "$PORT"
python3 -I "$SCRIPTS/make_wheel_and_lang.py" "$PORT"
python3 -I "$SCRIPTS/make_vault_models.py" "$CREATE" "$PORT"
