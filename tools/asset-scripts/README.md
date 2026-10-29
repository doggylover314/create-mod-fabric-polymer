# Asset/data generation scripts

Run from anywhere with the original Create checkout (`mc1.21.1/dev`) at `$CREATE` and this repo at `$PORT`:

```bash
python3 convert_recipes.py "$CREATE" "$PORT"       # data/create/recipe (1.21.1 -> 26.3 format, ported subset)
python3 copy_data.py "$CREATE" "$PORT"             # loot tables, mineable tags, c: tags
python3 copy_assets.py "$CREATE" "$PORT"           # models/textures/lang used by the ported blocks (+UV fixes)
python3 make_wheel_and_lang.py "$PORT"             # trims lang files, writes the water wheel model
```
