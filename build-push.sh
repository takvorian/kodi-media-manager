#!/usr/bin/env bash
# ============================================================
#  build-push.sh <version>
#  Builds the image takvorian/kodi-media-manager (linux/amd64)
#  from the git tag v<version> and pushes :<version> and :latest
#  to Docker Hub. Uses git worktree, so the current working tree
#  and branch stay untouched and the build context only contains
#  versioned files. Run in the repository directory.
#  Example:  ./build-push.sh 0.1
# ============================================================
set -u
cd "$(dirname "$0")" || exit 1

fail() {
    echo ""
    echo "ABORTED: $1"
    exit 1
}

[ -n "${1:-}" ] || fail "Version fehlt.  Beispiel:  ./build-push.sh 0.1"

VERSION="$1"
IMAGE="takvorian/kodi-media-manager"
TAG="v$VERSION"
WT="../kmm-build-$VERSION"

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null || fail "git tag $TAG does not exist."
[ ! -e "$WT" ] || fail "$WT already exists - please remove it."

# Once the worktree exists, clean it up again on errors.
cleanup_fail() {
    echo ""
    echo "ABORTED during build/push - cleaning up the worktree ..."
    git worktree remove --force "$WT" 2>/dev/null
    fail "$1"
}

echo ""
echo "[1/5] Logging in to Docker Hub ..."
docker login || fail "docker login"

echo ""
echo "[2/5] Creating the worktree for $TAG ($WT) ..."
git worktree add "$WT" "$TAG" || fail "git worktree add"

echo ""
echo "[3/5] Building the image: $IMAGE:$VERSION (+ :latest) ..."
docker build --platform linux/amd64 -f "$WT/docker/Dockerfile" \
    --label "org.opencontainers.image.version=$VERSION" \
    -t "$IMAGE:$VERSION" -t "$IMAGE:latest" "$WT" || cleanup_fail "docker build"

echo ""
echo "[4/5] Pushing to Docker Hub ..."
docker push "$IMAGE:$VERSION" || cleanup_fail "docker push $VERSION"
docker push "$IMAGE:latest" || cleanup_fail "docker push latest"

echo ""
echo "[5/5] Cleaning up the worktree ..."
git worktree remove --force "$WT"

echo ""
echo "DONE. On the server:  docker compose pull  and then  docker compose up -d"
