#!/usr/bin/env python3
"""Prepare reproducible tagged sources and verify both Bridge release artifacts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

MAX_RELEASE_ID = 2_147_483_647
SEMVER = re.compile(
    r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
    r"(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?"
    r"(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?"
)
MARKER = Path("datapack/data/feato_gun_valhalla/function/bridge/marker.mcfunction")


def validate_version(value):
    match = SEMVER.fullmatch(value)
    if len(value) > 128 or match is None:
        raise ValueError("Use SemVer without a v prefix, e.g. 0.1.0-poc.2 or 1.0.0")
    prerelease = match.group(4)
    if prerelease and any(p.isdigit() and len(p) > 1 and p.startswith("0") for p in prerelease.split(".")):
        raise ValueError("Numeric prerelease identifiers cannot have leading zeros")
    return prerelease is not None


def git(root, *args):
    return subprocess.run(["git", *args], cwd=root, check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.strip()


def properties(text):
    result = {}
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith(("#", "!")):
            continue
        key, value = line.split("=", 1)
        if key in result:
            raise ValueError(f"Duplicate metadata field: {key}")
        result[key] = value
    return result


def release_id(value):
    if not re.fullmatch(r"[1-9][0-9]*", value) or int(value) > MAX_RELEASE_ID:
        raise ValueError("Invalid scoreboard release ID")
    return int(value)


def prepare(root, version):
    prerelease = validate_version(version)
    if git(root, "status", "--porcelain", "--untracked-files=no"):
        raise ValueError("Release preparation requires a clean tracked working tree")
    tag = f"v{version}"
    tags = git(root, "tag", "--list").splitlines()
    if tag in tags:
        raise ValueError(f"Tag already exists: {tag}")
    path = root / "bridge.properties"
    original = path.read_text()
    current = properties(original)
    highest = release_id(current["bridge.release"])
    previous_ids = set()
    for existing in tags:
        if not existing.startswith("v") or not SEMVER.fullmatch(existing[1:]):
            continue
        # Missing or inconsistent tagged metadata must stop allocation, not be ignored.
        metadata = properties(git(root, "show", f"{existing}:bridge.properties"))
        if metadata["bridge.version"] != existing[1:]:
            raise ValueError(f"Version mismatch in existing tag: {existing}")
        number = release_id(metadata["bridge.release"])
        if number in previous_ids:
            raise ValueError("Existing tags share a release ID; allocation stopped")
        previous_ids.add(number)
        highest = max(highest, number)
    number = highest + 1
    if number > MAX_RELEASE_ID:
        raise ValueError("Scoreboard release ID exhausted")
    marker_path = root / MARKER
    marker = marker_path.read_text()
    expected = f"scoreboard players set #release fgv_bridge {current['bridge.release']}"
    if marker.splitlines().count(expected) != 1:
        raise ValueError("Source release marker does not match bridge.properties")
    pack_path = root / "datapack/pack.mcmeta"
    pack = json.loads(pack_path.read_text())
    description = pack["pack"]["description"]
    if not isinstance(description, str) or description.count(current["bridge.version"]) != 1:
        raise ValueError("Datapack description must contain the current version exactly once")
    pack["pack"]["description"] = description.replace(current["bridge.version"], version, 1)
    updated = original
    for key, value in (("bridge.version", version), ("bridge.release", str(number))):
        updated, count = re.subn(rf"^{re.escape(key)}=.*$", f"{key}={value}", updated, flags=re.MULTILINE)
        if count != 1:
            raise ValueError(f"Missing or duplicate metadata field: {key}")
    # Validate all source inputs before writing any tracked file.
    path.write_text(updated)
    marker_path.write_text(marker.replace(expected, f"scoreboard players set #release fgv_bridge {number}", 1))
    pack_path.write_text(json.dumps(pack, ensure_ascii=False, indent=2) + "\n")
    return {"version": version, "tag": tag, "release_id": str(number),
            "prerelease": str(prerelease).lower()}


def verify(root, version):
    validate_version(version)
    source = (root / "bridge.properties").read_text()
    if properties(source)["bridge.version"] != version:
        raise ValueError("Source version differs from requested version")
    jar = root / f"plugin/build/libs/feato-gun-valhalla-bridge-plugin-{version}.jar"
    pack = root / f"plugin/build/distributions/feato-gun-valhalla-bridge-datapack-{version}.zip"
    with zipfile.ZipFile(jar) as archive:
        if archive.read("bridge.properties").decode() != source:
            raise ValueError("JAR compatibility metadata differs from tagged source")
        if f"version: '{version}'" not in archive.read("plugin.yml").decode().splitlines():
            raise ValueError("JAR plugin.yml version differs from requested version")
    with zipfile.ZipFile(pack) as archive:
        if archive.read(str(MARKER.relative_to("datapack"))).decode() != (root / MARKER).read_text():
            raise ValueError("Datapack release marker differs from tagged source")
        if json.loads(archive.read("pack.mcmeta")) != json.loads((root / "datapack/pack.mcmeta").read_text()):
            raise ValueError("Datapack metadata differs from tagged source")
    checksums = root / "plugin/build/release/SHA256SUMS"
    checksums.parent.mkdir(parents=True, exist_ok=True)
    checksums.write_text("".join(f"{hashlib.sha256(f.read_bytes()).hexdigest()}  {f.name}\n" for f in (jar, pack)))
    return {"jar": str(jar.relative_to(root)), "datapack": str(pack.relative_to(root)),
            "checksums": str(checksums.relative_to(root))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("prepare", "verify"))
    parser.add_argument("--version", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    try:
        result = prepare(root, args.version) if args.operation == "prepare" else verify(root, args.version)
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as failure:
        parser.exit(1, f"Release stopped: {failure}\n")
    print(json.dumps(result))
    if args.operation == "prepare" and os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            for key, value in result.items():
                output.write(f"{key}={value}\n")


if __name__ == "__main__":
    main()
