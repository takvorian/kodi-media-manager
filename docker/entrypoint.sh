#!/bin/sh
# Starts KMM as PUID:PGID. Docker creates a missing /config on the host as root; so the container starts as
# root, gives /config to the user and then switches to it. /media stays untouched.
set -eu

CONFIG=/config

if [ "$(id -u)" != "0" ]; then
    if [ ! -w "$CONFIG" ]; then
        echo "ERROR: $CONFIG is not writable for user $(id -u):$(id -g)." >&2
        echo "Remove 'user:' from the compose file and set PUID/PGID, or give the user the directory on the host." >&2
        exit 1
    fi
    exec "$@"
fi

PUID="${PUID:-1000}"
PGID="${PGID:-1000}"

if [ "$(stat -c %u:%g "$CONFIG")" != "$PUID:$PGID" ]; then
    echo "Giving $CONFIG to user $PUID:$PGID"
    chown -R "$PUID:$PGID" "$CONFIG" || echo "WARNING: $CONFIG could not be given to user $PUID:$PGID." >&2
fi

exec setpriv --reuid="$PUID" --regid="$PGID" --clear-groups "$@"
