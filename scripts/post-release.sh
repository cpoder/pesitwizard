#!/bin/bash
# ===========================================================================
# PeSIT Wizard OSS - Post-Release Script
#
# Usage: ./scripts/post-release.sh <next-version>
#
# After a release, bumps versions to the next development iteration:
#   1. Sets Maven version to <next-version>-SNAPSHOT
#   2. Resets Helm chart versions to 0.0.0-dev
#   3. Commits and pushes
# ===========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

die()  { echo -e "${RED}ERROR:${NC} $1" >&2; exit 1; }
info() { echo -e "${GREEN}==>${NC} $1"; }

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
VERSION="${1:-}"
[ -z "$VERSION" ] && die "Usage: $0 <next-version>\n  Example: $0 1.3.0"

# Validate semver format
if ! echo "$VERSION" | grep -qE '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'; then
    die "Invalid semver: '$VERSION'. Expected format: MAJOR.MINOR.PATCH (e.g. 1.3.0)"
fi

SNAPSHOT="${VERSION}-SNAPSHOT"

# ---------------------------------------------------------------------------
# Validate preconditions
# ---------------------------------------------------------------------------
cd "$PROJECT_ROOT"

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "main" ]; then
    die "Must be on 'main' branch (currently on '$BRANCH')"
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
    die "Working tree is not clean. Commit or stash changes first."
fi

# ---------------------------------------------------------------------------
# Set Maven version to SNAPSHOT
# ---------------------------------------------------------------------------
info "Setting Maven version to ${SNAPSHOT}..."
mvn -B versions:set -DnewVersion="${SNAPSHOT}" -DgenerateBackupPoms=false

# ---------------------------------------------------------------------------
# Reset Helm chart versions to dev
# ---------------------------------------------------------------------------
info "Resetting Helm chart versions to development..."

HELM_DIR="$PROJECT_ROOT/pesitwizard-helm-charts"
for chart_yaml in "$HELM_DIR"/*/Chart.yaml; do
    chart_name=$(basename "$(dirname "$chart_yaml")")
    info "  Resetting $chart_name Chart.yaml"
    sed -i "s/^version:.*/version: 0.0.0-dev/" "$chart_yaml"
    sed -i "s/^appVersion:.*/appVersion: \"${SNAPSHOT}\"/" "$chart_yaml"
done

# ---------------------------------------------------------------------------
# Commit and push
# ---------------------------------------------------------------------------
info "Committing next development iteration..."
git add -A
git commit -m "Prepare next development iteration ${SNAPSHOT}"

info "Pushing to origin..."
git push origin main

echo ""
info "Development iteration ${SNAPSHOT} set successfully!"
