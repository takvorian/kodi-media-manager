#!/usr/bin/env bash
# ============================================================
#  build-push.sh <version>
#  Baut das Image takvorian/kodi-media-manager (linux/amd64) aus
#  dem Git-Tag v<version> und pusht :<version> + :latest zu
#  Docker Hub. Nutzt git worktree -> aktuelles Arbeitsverzeichnis/
#  Branch bleiben unangetastet, und im Build-Kontext liegen nur
#  versionierte Dateien. Im Repo-Verzeichnis ausfuehren.
#  Beispiel:  ./build-push.sh 0.1
# ============================================================
set -u
cd "$(dirname "$0")" || exit 1

fail() {
    echo ""
    echo "ABGEBROCHEN: $1"
    exit 1
}

[ -n "${1:-}" ] || fail "Version fehlt.  Beispiel:  ./build-push.sh 0.1"

VERSION="$1"
IMAGE="takvorian/kodi-media-manager"
TAG="v$VERSION"
WT="../kmm-build-$VERSION"

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null || fail "Git-Tag $TAG existiert nicht."
[ ! -e "$WT" ] || fail "$WT existiert bereits - bitte entfernen."

# Ab dem Anlegen des Worktrees bei Fehlern wieder aufraeumen.
cleanup_fail() {
    echo ""
    echo "ABBRUCH beim Build/Push - raeume Worktree auf ..."
    git worktree remove --force "$WT" 2>/dev/null
    fail "$1"
}

echo ""
echo "[1/5] Anmeldung bei Docker Hub ..."
docker login || fail "docker login"

echo ""
echo "[2/5] Worktree fuer $TAG anlegen ($WT) ..."
git worktree add "$WT" "$TAG" || fail "git worktree add"

echo ""
echo "[3/5] Image bauen: $IMAGE:$VERSION (+ :latest) ..."
docker build --platform linux/amd64 -f "$WT/docker/Dockerfile" \
    --label "org.opencontainers.image.version=$VERSION" \
    -t "$IMAGE:$VERSION" -t "$IMAGE:latest" "$WT" || cleanup_fail "docker build"

echo ""
echo "[4/5] Push zu Docker Hub ..."
docker push "$IMAGE:$VERSION" || cleanup_fail "docker push $VERSION"
docker push "$IMAGE:latest" || cleanup_fail "docker push latest"

echo ""
echo "[5/5] Worktree aufraeumen ..."
git worktree remove --force "$WT"

echo ""
echo "FERTIG. Auf dem Server:  docker compose pull  und dann  docker compose up -d"
