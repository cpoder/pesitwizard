#!/bin/bash
# ===========================================================================
# PeSIT Wizard OSS - Release Script
#
# Usage: ./scripts/release.sh <version> [--dry-run]
#
# Performs a release by:
#   1. Validating preconditions (branch, clean tree, semver, no existing tag)
#   2. Setting the Maven version via versions:set
#   3. Updating Helm chart versions
#   4. Committing, tagging, and pushing
#
# The resulting tag push triggers the release.yml CI workflow which:
#   - Runs the full test suite
#   - Deploys to Maven Central (GPG-signed with sources + javadoc)
#   - Creates a GitHub Release
#   - Dispatches oss-release event to enterprise repo
# ===========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

die()  { echo -e "${RED}ERROR:${NC} $1" >&2; exit 1; }
info() { echo -e "${GREEN}==>${NC} $1"; }
warn() { echo -e "${YELLOW}WARN:${NC} $1"; }

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
DRY_RUN=false
VERSION=""

for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=true ;;
        -*)        die "Unknown option: $arg" ;;
        *)         VERSION="$arg" ;;
    esac
done

[ -z "$VERSION" ] && die "Usage: $0 <version> [--dry-run]\n  Example: $0 1.2.0"

# ---------------------------------------------------------------------------
# Validate semver format (MAJOR.MINOR.PATCH, no leading zeros)
# ---------------------------------------------------------------------------
if ! echo "$VERSION" | grep -qE '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'; then
    die "Invalid semver: '$VERSION'. Expected format: MAJOR.MINOR.PATCH (e.g. 1.2.0)"
fi

# ---------------------------------------------------------------------------
# Validate preconditions
# ---------------------------------------------------------------------------
cd "$PROJECT_ROOT"

info "Validating preconditions..."

# Must be on main branch
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "main" ]; then
    die "Must be on 'main' branch (currently on '$BRANCH')"
fi

# Working tree must be clean
if ! git diff --quiet || ! git diff --cached --quiet; then
    die "Working tree is not clean. Commit or stash changes first."
fi

# No untracked files (that we care about)
UNTRACKED=$(git ls-files --others --exclude-standard)
if [ -n "$UNTRACKED" ]; then
    warn "Untracked files detected:\n$UNTRACKED"
    die "Clean up untracked files before releasing."
fi

# Tag must not already exist
TAG="v${VERSION}"
if git rev-parse "$TAG" >/dev/null 2>&1; then
    die "Tag '$TAG' already exists. Delete it first or choose a different version."
fi

# Ensure we're up to date with remote
git fetch origin main --tags
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse origin/main)
if [ "$LOCAL" != "$REMOTE" ]; then
    die "Local main ($LOCAL) differs from origin/main ($REMOTE). Pull or push first."
fi

info "All preconditions passed for $TAG"

# ---------------------------------------------------------------------------
# Set Maven version
# ---------------------------------------------------------------------------
info "Setting Maven version to ${VERSION}..."
mvn -B versions:set -DnewVersion="${VERSION}" -DgenerateBackupPoms=false

# ---------------------------------------------------------------------------
# Update Helm chart versions
# ---------------------------------------------------------------------------
info "Updating Helm chart versions..."

HELM_DIR="$PROJECT_ROOT/pesitwizard-helm-charts"
for chart_yaml in "$HELM_DIR"/*/Chart.yaml; do
    chart_name=$(basename "$(dirname "$chart_yaml")")
    info "  Updating $chart_name Chart.yaml"
    sed -i "s/^version:.*/version: ${VERSION}/" "$chart_yaml"
    sed -i "s/^appVersion:.*/appVersion: \"${VERSION}\"/" "$chart_yaml"
done

# ---------------------------------------------------------------------------
# Dry-run stops here
# ---------------------------------------------------------------------------
if [ "$DRY_RUN" = true ]; then
    warn "Dry-run mode — skipping commit, tag, and push"
    echo ""
    info "Changes that would be committed:"
    git diff --stat
    echo ""
    info "Files modified:"
    git diff --name-only
    exit 0
fi

# ---------------------------------------------------------------------------
# Commit, tag, push
# ---------------------------------------------------------------------------
info "Committing release ${TAG}..."
git add -A
git commit -m "Release ${TAG}"

info "Creating annotated tag ${TAG}..."
git tag -a "$TAG" -m "Release ${TAG}"

info "Pushing to origin..."
git push origin main "$TAG"

echo ""
info "Release ${TAG} pushed successfully!"
echo ""
echo "Next steps:"
echo "  1. Monitor CI: the release.yml workflow will test, deploy to Central, and build Docker images"
echo "  2. Wait for Maven Central sync (~10-30 min)"
echo "  3. Run enterprise release:  cd ../pesitwizard-enterprise && ./scripts/release.sh ${VERSION}"
echo "  4. After both releases, bump to next SNAPSHOT:  ./scripts/post-release.sh <next-version>"
