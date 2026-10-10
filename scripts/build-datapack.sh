#!/usr/bin/env bash
set -euo pipefail

# Use the same verified packaging and version metadata as the full build/release.
repository_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd -- "${repository_root}"
exec ./gradlew datapackZip --no-daemon --rerun-tasks
