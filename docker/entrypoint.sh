#!/bin/sh
# Startet KMM als PUID:PGID. Docker legt ein fehlendes /config auf dem Host als root an; deshalb startet der
# Container als root, gibt /config dem Benutzer und wechselt dann zu ihm. /media bleibt unangetastet.
set -eu

CONFIG=/config

if [ "$(id -u)" != "0" ]; then
    if [ ! -w "$CONFIG" ]; then
        echo "FEHLER: $CONFIG ist für Benutzer $(id -u):$(id -g) nicht beschreibbar." >&2
        echo "Entferne 'user:' aus der Compose-Datei und setze PUID/PGID, oder gib ihm das Verzeichnis auf dem Host." >&2
        exit 1
    fi
    exec "$@"
fi

PUID="${PUID:-1000}"
PGID="${PGID:-1000}"

if [ "$(stat -c %u:%g "$CONFIG")" != "$PUID:$PGID" ]; then
    echo "Gebe $CONFIG dem Benutzer $PUID:$PGID"
    chown -R "$PUID:$PGID" "$CONFIG" || echo "WARNUNG: $CONFIG konnte nicht dem Benutzer $PUID:$PGID gegeben werden." >&2
fi

exec setpriv --reuid="$PUID" --regid="$PGID" --clear-groups "$@"
