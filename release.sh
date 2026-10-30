#!/usr/bin/env bash
# ============================================================
#  release.sh [--dry-run]
#  Bestimmt die naechste Minor-Version der 0.-Linie (Start v0.1),
#  erzeugt kmm-app/src/main/resources/changelog.md aus den
#  Git-Logs je Version (Versions-Commits "Release vX.Y" werden
#  ausgefiltert), committet + taggt sie, pusht Branch+Tag nach
#  origin und ruft build-push.sh <version> auf (Docker-Build/-Push).
#  --dry-run zeigt nur die naechste Version + Changelog-Vorschau.
#  Im Repo-Verzeichnis (auf Branch main, sauberer Arbeitsbaum,
#  CI fuer den aktuellen Commit gruen) starten.
# ============================================================
set -u
cd "$(dirname "$0")" || exit 1

DRYRUN=""
[ "${1:-}" = "--dry-run" ] && DRYRUN=1

fail() {
    echo ""
    echo "ABGEBROCHEN: $1"
    exit 1
}

# Commit-Zeilen eines Bereichs anhaengen; Versions-Commits ("Release vX.Y") ausfiltern.
log_lines() {
    git log "$@" --no-merges --invert-grep --grep='^Release v[0-9]' --pretty=tformat:'- %s (%h)'
}

# Ergebnis des CI-Laufs fuer den aktuellen Commit, z. B. "completed/success"; leer ohne Lauf.
ci_state() {
    gh run list --commit "$(git rev-parse HEAD)" --workflow Build --limit 1 \
        --json status,conclusion --jq '.[0] | .status + "/" + .conclusion' 2>/dev/null
}

# --- Sicherheit: auf Branch main ---
BRANCH="$(git rev-parse --abbrev-ref HEAD)" || fail "kein Git-Repository."
[ "$BRANCH" = "main" ] || fail "nicht auf Branch main (aktuell: $BRANCH)."

# --- Sicherheit: sauberer Arbeitsbaum, Stand von origin, gruene CI + Docker (nur fuer den echten Lauf) ---
# Vorab pruefen, damit der Lauf NICHT erst nach Commit/Tag/Push scheitert. Der Image-Build
# ueberspringt die Tests, deshalb muss die CI den Commit vorher geprueft haben.
if [ -z "$DRYRUN" ]; then
    # Veraltete Stat-Cache-Eintraege wuerden diff --quiet faelschlich anschlagen lassen
    git update-index -q --refresh
    git diff --quiet || fail "ungespeicherte Aenderungen im Arbeitsbaum."
    git diff --cached --quiet || fail "gestagte Aenderungen vorhanden."
    git fetch -q --tags origin main || fail "git fetch origin main"
    [ "$(git rev-parse HEAD)" = "$(git rev-parse FETCH_HEAD)" ] || fail "main weicht von origin/main ab."
    [ "$(ci_state)" = "completed/success" ] || fail "CI fuer diesen Commit nicht gruen (Status: $(ci_state))."
    docker info >/dev/null 2>&1 || fail "Docker-Daemon nicht erreichbar - laeuft Docker?"
fi

# --- Naechste Version der 0.-Linie bestimmen ---
HIGH="$(git tag --list 'v0.*' --sort=-version:refname | head -n1)"
if [ -z "$HIGH" ]; then
    NEWVER="0.1"
else
    NEWVER="0.$(( ${HIGH#v0.} + 1 ))"
fi
NEWTAG="v$NEWVER"
git rev-parse -q --verify "refs/tags/$NEWTAG" >/dev/null && fail "Tag $NEWTAG existiert bereits."

RELDATE="$(date +%F)"

# --- Ausgabedatei: echte Ressource bzw. Temp-Datei im Dry-Run ---
if [ -n "$DRYRUN" ]; then
    CHANGELOG="$(mktemp -t kmm-changelog-preview.XXXXXX)"
else
    CHANGELOG="kmm-app/src/main/resources/changelog.md"
fi

# --- Bestehende Tags (neueste zuerst) ---
mapfile -t TAGS < <(git tag --list 'v*' --sort=-version:refname)

# --- Changelog voll neu erzeugen ---
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
    echo "=== DRY-RUN: naechste Version = $NEWTAG ($RELDATE) ==="
    echo "--- Changelog-Vorschau ---"
    cat "$CHANGELOG"
    rm -f "$CHANGELOG"
    echo ""
    if docker info >/dev/null 2>&1; then
        echo "Docker-Daemon: erreichbar"
    else
        echo "Docker-Daemon: NICHT erreichbar - vor dem echten Lauf Docker starten"
    fi
    echo "CI fuer diesen Commit: $(ci_state)"
    echo "(Dry-Run: kein commit/tag/push/build)"
    exit 0
fi

# --- Commit + Tag ---
echo ""
echo "[1/4] Changelog committen ..."
git add "$CHANGELOG" || fail "git add"
git commit -m "Release $NEWTAG" || fail "git commit"

echo ""
echo "[2/4] Tag $NEWTAG setzen ..."
git tag -a "$NEWTAG" -m "Release $NEWTAG" || fail "git tag"

echo ""
echo "[3/4] Branch main + Tag $NEWTAG nach origin pushen ..."
git push origin main || fail "git push origin main"
git push origin "$NEWTAG" || fail "git push origin $NEWTAG"

echo ""
echo "[4/4] Image bauen und pushen (build-push.sh $NEWVER) ..."
./build-push.sh "$NEWVER"
