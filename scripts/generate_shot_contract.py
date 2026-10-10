#!/usr/bin/env python3
"""Generate/check the raycast allowlist from the two SHA-512-pinned upstream ZIPs."""
import argparse
from pathlib import Path
from audit_shot_contract import audit

ROOT = Path(__file__).resolve().parent.parent


def generated(core, modern):
    weapons = [w for w in audit(core, modern)["weapons"] if w["path"] == "raycast"]
    properties = "# Generated from fixed Modern Guns V1.9.3 loot tables by scripts/generate_shot_contract.py\n"
    for weapon in weapons:
        properties += weapon["weapon_id"].replace(":", "\\:") + "=" + str(weapon["projectile_type"]) + "\n"
    function = "# Explicit administrator opt-in only. No tick-time inventory mutation.\n"
    function += "execute unless score #ready fgv_shot matches 1 run return fail\n"
    function += "execute unless entity @s[type=minecraft:player] run return fail\n"
    for weapon in weapons:
        function += ("execute if items entity @s weapon.mainhand *[minecraft:custom_data~{gbg:{idle_model:\"" +
                     weapon["weapon_id"] + "\",projectile_type:" + str(weapon["projectile_type"]) +
                     ",projectile_speed:1,damage_type:" + str(weapon["damage_type"]) + "}}] run function feato_gun_valhalla:shot/adapt_" +
                     str(weapon["projectile_type"]) + "\n")
    return {ROOT / "plugin/src/main/resources/gun-core-weapons.properties": properties,
            ROOT / "datapack/data/feato_gun_valhalla/function/shot/adapt.mcfunction": function}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("gun_core_zip", type=Path)
    parser.add_argument("modern_guns_zip", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for path, content in generated(args.gun_core_zip, args.modern_guns_zip).items():
        if args.check:
            if not path.is_file() or path.read_text() != content:
                parser.exit(1, f"Stale generated contract: {path.relative_to(ROOT)}\n")
        else:
            path.write_text(content)
    print("Fixed raycast weapon contract " + ("verified" if args.check else "generated"))
