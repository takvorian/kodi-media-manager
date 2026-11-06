#!/usr/bin/env bash
# ============================================================
#  release.sh [--dry-run]
#  Determines the next minor version of the 0. line (starting at
#  v0.1), generates kmm-app/src/main/resources/changelog.md from
#  the git logs per version (version commits "Release vX.Y" are
#  filtered out), commits and tags it, pushes branch and tag to
#  origin and calls build-push.sh <version> (Docker build/push).
#  --dry-run only shows the next version and a changelog preview.
#  Run in the repository directory (on branch main, clean working
#  tree, CI green for the current commit).
# ============================================================
set -u
cd "$(dirname "$0")" || exit 1

DRYRUN=""
[ "${1:-}" = "--dry-run" ] && DRYRUN=1

fail() {
    echo ""
    echo "ABORTED: $1"
    exit 1
}

# Appends the commit lines of a range; filters out version commits ("Release vX.Y").
log_lines() {
    git log "$@" --no-merges --invert-grep --grep='^Release v[0-9]' --pretty=tformat:'- %s (%h)'
}

# Result of the CI run for the current commit, e.g. "completed/success"; empty without a run.
ci_state() {
    gh run list --commit "$(git rev-parse HEAD)" --workflow Build --limit 1 \
        --json status,conclusion --jq '.[0] | .status + "/" + .conclusion' 2>/dev/null
}

# --- Safety: on branch main ---
BRANCH="$(git rev-parse --abbrev-ref HEAD)" || fail "not a git repository."
[ "$BRANCH" = "main" ] || fail "not on branch main (current: $BRANCH)."

# --- Safety: clean working tree, same as origin, green CI and Docker (only for the real run) ---
# Checked up front so the run does NOT fail only after commit/tag/push. The image build
# skips the tests, so CI must have checked the commit before.
if [ -z "$DRYRUN" ]; then
    # Stale stat cache entries would make diff --quiet report changes that are not there
    git update-index -q --refresh
    git diff --quiet || fail "uncommitted changes in the working tree."
    git diff --cached --quiet || fail "staged changes present."
    git fetch -q --tags origin main || fail "git fetch origin main"
    [ "$(git rev-parse HEAD)" = "$(git rev-parse FETCH_HEAD)" ] || fail "main differs from origin/main."
    [ "$(ci_state)" = "completed/success" ] || fail "CI not green for this commit (status: $(ci_state))."
    docker info >/dev/null 2>&1 || fail "Docker daemon not reachable - is Docker running?"
fi

# --- Determine the next version of the 0. line ---
HIGH="$(git tag --list 'v0.*' --sort=-version:refname | head -n1)"
if [ -z "$HIGH" ]; then
    NEWVER="0.1"
else
    NEWVER="0.$(( ${HIGH#v0.} + 1 ))"
fi
NEWTAG="v$NEWVER"
git rev-parse -q --verify "refs/tags/$NEWTAG" >/dev/null && fail "tag $NEWTAG already exists."

RELDATE="$(date +%F)"

# --- Output file: the real resource, or a temporary file in a dry run ---
if [ -n "$DRYRUN" ]; then
    CHANGELOG="$(mktemp -t kmm-changelog-preview.XXXXXX)"
else
    CHANGELOG="kmm-app/src/main/resources/changelog.md"
fi

# --- Existing tags (newest first) ---
mapfile -t TAGS < <(git tag --list 'v*' --sort=-version:refname)

# --- Regenerate the whole changelog ---
{
    echo "# Changelog"
    echo ""
    echo "## $NEWTAG - $RELDATE"
    if [ "${#TAGS[@]}" -eq 0 ]; then
        log_lines
    else
        log_lines "${TAGS[0]}..HEAD"
    fi
    echo ""
    for i in "${!TAGS[@]}"; do
        CUR="${TAGS[$i]}"
        TDATE="$(git log -1 --format=%cs "$CUR")"
        echo "## $CUR - $TDATE"
        NEXT=$(( i + 1 ))
        if [ "$NEXT" -lt "${#TAGS[@]}" ]; then
            log_lines "${TAGS[$NEXT]}..$CUR"
        else
            log_lines "$CUR"
        fi
        echo ""
    done
} > "$CHANGELOG"

if [ -n "$DRYRUN" ]; then
    echo ""
    echo "=== DRY RUN: next version = $NEWTAG ($RELDATE) ==="
    echo "--- Changelog preview ---"
    cat "$CHANGELOG"
    rm -f "$CHANGELOG"
    echo ""
    if docker info >/dev/null 2>&1; then
        echo "Docker daemon: reachable"
    else
        echo "Docker daemon: NOT reachable - start Docker before the real run"
    fi
    echo "CI for this commit: $(ci_state)"
    echo "(dry run: no commit/tag/push/build)"
    exit 0
fi

# --- Commit and tag ---
echo ""
echo "[1/4] Committing the changelog ..."
git add "$CHANGELOG" || fail "git add"
git commit -m "Release $NEWTAG" || fail "git commit"

echo ""
echo "[2/4] Setting tag $NEWTAG ..."
git tag -a "$NEWTAG" -m "Release $NEWTAG" || fail "git tag"

echo ""
echo "[3/4] Pushing branch main and tag $NEWTAG to origin ..."
git push origin main || fail "git push origin main"
git push origin "$NEWTAG" || fail "git push origin $NEWTAG"

echo ""
echo "[4/4] Building and pushing the image (build-push.sh $NEWVER) ..."
./build-push.sh "$NEWVER"
