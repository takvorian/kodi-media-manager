# Installation und Start

KMM gibt es als Windows-Installer und als Docker-Container.

## Windows-Installer

1. Starte die Datei `Kodi Media Manager-<Version>.msi`. Sie ist nicht signiert, Windows SmartScreen warnt deshalb. Wähle dort „Weitere Informationen“ und „Trotzdem ausführen“.
2. Stimme der Lizenz zu und wähle bei Bedarf einen anderen Installationsordner. Administratorrechte brauchst du nicht, KMM wird nur für deinen Benutzer installiert.
3. Starte KMM über die Desktop-Verknüpfung oder das Startmenü.

Es öffnet sich ein Konsolenfenster mit dem Protokoll. Warte, bis dort `Started KodiMediaManagerApplication` steht, und öffne dann <http://localhost:8080> im Browser.

**Beenden:** Schließe das Konsolenfenster.

**Aktualisieren:** Installiere einfach die neue MSI, sie ersetzt die alte Version.

**Deinstallieren:** Über die Windows-Einstellungen unter „Apps“. Deine Daten bleiben dabei erhalten.

### Wo die Daten liegen

Datenbank und Plugins liegen unter `%USERPROFILE%\.kodi-media-manager`. Deine Medien, NFOs und Bilder bleiben in deinen Medienordnern.

### Zugriff aus dem Heimnetz

Nach der Installation ist KMM nur auf dem eigenen Rechner erreichbar und fragt nicht nach einer Anmeldung. Das lässt sich über Umgebungsvariablen ändern, die du vor dem Start setzt:

| Variable | Bedeutung |
|---|---|
| `KMM_BIND_ADDRESS=0.0.0.0` | von anderen Geräten erreichbar |
| `KMM_SECURITY_ENABLED=true` | Anmeldung einschalten |
| `SPRING_SECURITY_USER_NAME` | Benutzername |
| `SPRING_SECURITY_USER_PASSWORD` | Passwort |
| `KMM_PORT` | anderer Port statt 8080 |
| `KMM_HOME` | anderer Datenordner |

Mache KMM **nie ohne Anmeldung** im Netz erreichbar, sonst kann jeder im Netz deine Medienordner verändern.

<a id="login"></a>
### Anmelden

Ist die Anmeldung eingeschaltet, zeigt KMM eine eigene Anmeldeseite mit Benutzername und Passwort. Nach dem Anmelden kommst du auf die Seite zurück, die du aufrufen wolltest.

- Ohne **Angemeldet bleiben** gilt die Anmeldung, bis du den Browser schließt oder KMM neu startet.
- Mit **Angemeldet bleiben** bleibst du auf diesem Gerät 30 Tage angemeldet, auch über einen Neustart oder ein Update von KMM hinweg. Änderst du das Passwort, musst du dich überall neu anmelden.
- **Abmelden** oben rechts beendet die Anmeldung auf diesem Gerät.

Skripte und Programme können die Schnittstelle weiter mit Benutzername und Passwort aufrufen (HTTP Basic), zum Beispiel `curl -u admin:passwort http://<Server>:8080/api/v1/movies`.

### Zusatzprogramme

Zwei Funktionen brauchen Programme, die nicht im Installer enthalten sind:

- **FFmpeg** für die [technischen Angaben](movies.md#technical-details): zum Beispiel mit `winget install Gyan.FFmpeg`. Den Pfad zu `ffprobe` trägst du unter [Einstellungen](settings.md#technische-angaben) ein, falls es nicht im Suchpfad liegt.
- **yt-dlp** für [Titelmusik von YouTube](scraping.md#titelmusik): zum Beispiel mit `winget install yt-dlp.yt-dlp`. Liegt es nicht im Suchpfad, setze `KMM_YTDLP` auf das Programm und `KMM_FFMPEG_LOCATION` auf den Ordner von FFmpeg.

## Docker

Im Docker-Image sind FFmpeg und yt-dlp schon enthalten. KMM läuft dort mit einer MariaDB-Datenbank und immer mit [Anmeldung](installation.md#login).

```bash
cd docker
cp .env.example .env     # Passwörter und Verzeichnisse eintragen
docker compose pull
docker compose up -d
```

Die Oberfläche ist dann unter `http://<Server>:8080` erreichbar. Benutzer und Passwort stehen in der `.env` (`KMM_ADMIN_USER`, `KMM_ADMIN_PASSWORD`).

| Verzeichnis im Container | Einstellung in `.env` | Zweck |
|---|---|---|
| `/config` | `KMM_CONFIG_DIR` | Plugins, Cache, Logs |
| `/media` | `KMM_MEDIA_DIR` | deine Medien |

`PUID` und `PGID` legen fest, mit welchem Benutzer KMM läuft und auf deine Medien zugreift. Trage hier den Besitzer deiner Mediendateien ein, ihn zeigt `ls -ln` auf dem Server. Dieser Benutzer braucht in `/media` Schreibrechte, sonst können weder NFOs noch Bilder gespeichert werden. Das Verzeichnis `/config` gibt KMM ihm beim Start selbst.

**Aktualisieren:** `docker compose pull` und danach `docker compose up -d`. Deine Daten bleiben dabei erhalten.

**Umziehen:** Von einer anderen KMM-Installation übernimmst du alles mit einer [Sicherung](backup.md), auch von Windows auf ein NAS.

**Feste Version:** Ohne weitere Angabe holt `pull` die neueste Version. Trage in der `.env` zum Beispiel `KMM_IMAGE_TAG=0.1` ein, um bei einer Version zu bleiben oder zu einer früheren zurückzukehren. Das Zurückgehen klappt nur, wenn die Datenbank noch nicht von einer neueren Version geändert wurde, sichere sie also vor Aktualisierungen.

## Version prüfen

Die aktuelle Version steht in der Kopfleiste neben dem Titel, ein Klick darauf öffnet die Seite **System**. Dort stehen auch die **Änderungen** jeder Version. Bei `dev` ist es ein selbst gebauter Stand ohne veröffentlichte Version.

Die Seite **System** zeigt außerdem die Java-Version, die verwendete Datenbank und die aktiven Profile. Steht dort, dass das Backend nicht erreichbar ist, läuft KMM nicht mehr, siehe [Fehlerbehebung](troubleshooting.md).
