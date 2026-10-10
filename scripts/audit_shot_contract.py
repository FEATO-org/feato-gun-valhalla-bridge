#!/usr/bin/env python3
"""Read-only audit of the two pinned upstream ZIPs. No runtime success claim."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile


ARTIFACTS = {
    "gun_core": "43b4241835b15fddf0f85fa333ecef1bcac5e5598aefabbf3bd32c1506b6196f6db4e751b96a0026e49fc79670afbc44ec626d7b77b6f5cb2e692db8ec7e434c",
    "modern_guns": "008e481cf01aea693e30a9458cde060cda76961f3766f90415bd2503025758d289a97fde2e5a411b4a238f3811ca79114a2a8c523893aea135c750f52dcb8155",
}


def read_function(pack, name):
    return pack.read(f"data/gbg/function/{name}.mcfunction").decode("utf-8")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def audit(core_path, modern_path):
    for name, path in (("gun_core", core_path), ("modern_guns", modern_path)):
        require(hashlib.sha512(Path(path).read_bytes()).hexdigest() == ARTIFACTS[name],
                f"{name}: pinned artifact SHA-512 mismatch")
    with ZipFile(core_path) as core, ZipFile(modern_path) as modern:
        dispatch = read_function(core, "gun/shooting_projectile")
        require("matches 200.." in dispatch and "function #gbg:custom_raycast" in dispatch,
                "Custom raycast dispatch contract changed")
        require("scores={gbg.burst_duration=2}" in read_function(core, "player_loop"),
                "Burst contract changed")
        hit = read_function(core, "gun/slowcast/hit_entity")
        nearest = "@e[type=marker,tag=gbg.slowcast,sort=nearest,limit=1]"
        require(nearest + " gbg.id" in hit and "kill " + nearest in hit,
                "Nearest-marker source/cleanup finding changed")
        require(hit.index("function gbg:gun/shot/explosion") <
                hit.index("function #gbg:damage_type_entity"),
                "Explosion-before-hook finding changed")
        require(nearest + " gbg.slowcast.damage_type" in
                read_function(core, "gun/slowcast/hit_block"),
                "Block impact nearest-marker finding changed")

        weapons = []
        for filename in sorted(modern.namelist()):
            if not filename.startswith("data/modern_guns/loot_table/guns/") or not filename.endswith(".json"):
                continue
            definition = json.loads(modern.read(filename))
            # Find semantic components rather than relying on loot table array indexes.
            components = []

            def visit(value):
                if isinstance(value, dict):
                    if "minecraft:custom_data" in value:
                        components.append(value["minecraft:custom_data"])
                    for child in value.values():
                        visit(child)
                elif isinstance(value, list):
                    for child in value:
                        visit(child)

            visit(definition)
            guns = [value["gbg"] for value in components if value.get("gbg_is_gun") == 1]
            require(len(guns) == 1, f"Expected one gun definition: {filename}")
            gun = guns[0]
            weapon_id = gun["idle_model"]
            require(weapon_id == "modern_guns:gun/" + Path(filename).stem,
                    f"Unexpected stable weapon ID: {filename}")
            projectile = gun["projectile_type"]
            speed = gun["projectile_speed"]
            require(projectile < 200, f"Unexpected custom projectile: {filename}")
            weapons.append({"weapon_id": weapon_id, "projectile_type": projectile,
                            "projectile_speed": speed, "fire_rate": gun["fire_rate"],
                            "damage_type": gun["damage_type"],
                            "path": "slowcast" if speed >= 2 else "raycast",
                            "live_status": "Not yet live-tested"})
        require(len(weapons) == 27, "Expected exactly 27 fixed Modern Guns definitions")
        require(len({weapon["weapon_id"] for weapon in weapons}) == 27, "Duplicate weapon ID")
        return {"sha512": ARTIFACTS, "status": "Blocked: native slowcast attribution is not unique",
                "weapons": weapons}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("gun_core_zip", type=Path)
    parser.add_argument("modern_guns_zip", type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(audit(args.gun_core_zip, args.modern_guns_zip), indent=2))
    except (ValueError, KeyError, OSError) as error:
        parser.exit(1, f"Contract audit failed: {error}\n")
