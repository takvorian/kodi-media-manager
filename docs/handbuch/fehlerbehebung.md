# Fehlerbehebung

## KMM startet nicht

**Unter Docker steht im Log `AccessDeniedException: /config/plugins` oder `/config ist nicht beschreibbar`.** Der Benutzer des Containers darf nicht in `/config` schreiben. Das passiert mit einer Compose-Datei von KMM vor Version 0.3, die noch eine Zeile `user:` enthält. Entferne diese Zeile, trage unter `environment` die Zeilen `PUID: ${PUID:-1000}` und `PGID: ${PGID:-1000}` ein und starte neu, siehe [Docker](installation.md#docker).

**Das Konsolenfenster schließt sich sofort oder zeigt einen Fehler.** Häufige Ursachen:

- **Port belegt:** Meldung `Port 8080 was already in use`. Beende das andere Programm oder setze `KMM_PORT` auf einen anderen Port.
- **KMM läuft schon:** Zwei Instanzen können nicht denselben Datenordner nutzen, die Meldung nennt dann eine gesperrte Datenbank. Schließe die erste.

**Der Browser findet die Seite nicht.** Warte, bis im Fenster `Started KodiMediaManagerApplication` steht. Von einem anderen Gerät aus ist KMM nur erreichbar, wenn du das eingerichtet hast, siehe [Installation](installation.md#zugriff-aus-dem-heimnetz).

**KMM ist nach einem Neustart des Rechners weg.** KMM hat unter Windows keinen Autostart. Starte es wieder über die Verknüpfung.

## Scan

**Die Quelle findet nichts.** Prüfe, ob das Verzeichnis aus Sicht von KMM stimmt, unter Docker also der Pfad im Container. Prüfe auch die [Dateiendungen und ausgeschlossenen Ordner](einstellungen.md#scan).

**Eine Serie wird falsch erkannt.** Jeder Ordner direkt unter der Quelle gilt als Serie. Die Quelle muss also auf den Ordner *über* den Serien zeigen.

**Episoden fehlen.** Staffel und Episode müssen im Dateinamen stehen, zum Beispiel `S01E05`.

## Scrapen

**„Es ist kein Metadaten-Scraper installiert oder aktiv“ oder der Suchknopf ist gesperrt.** Meist fehlt der [API-Key](einstellungen.md#api-keys) des Anbieters.

**Keine Treffer.** Versuche einen anderen Suchbegriff, den Originaltitel, oder lass das Jahr weg.

**Das automatische Scrapen lässt Titel aus.** Es übernimmt nur eindeutige Treffer. Suche diese Titel einzeln, siehe [Online-Suche](scrapen.md#online-suche). Danach kennt KMM die ID, und auch andere Anbieter finden den Titel.

**Moviepilot oder OFDb bringen nichts.** Sie brauchen die IMDb-ID und finden nicht jeden Film. Bauen die Seiten ihre Darstellung um, fallen diese Anbieter aus, im Log steht dann eine Warnung. Andere Anbieter sind davon nicht betroffen.

**Keine Titelmusik von YouTube.** yt-dlp fehlt oder ist veraltet, siehe [Zusatzprogramme](installation.md#zusatzprogramme).

**OMDb liefert plötzlich nichts mehr.** Der kostenlose Key erlaubt 1.000 Abfragen am Tag.

## Technische Angaben

**Das Auslesen schlägt fehl.** ffprobe wurde nicht gefunden. Prüfe den [Pfad](einstellungen.md#technische-angaben).

## Kodi

**Verbindung testen schlägt fehl.** Prüfe, ob Kodi läuft, ob die HTTP-Steuerung aktiviert ist, und ob Adresse (ohne `http://`), Port, Benutzer und Passwort stimmen. Siehe [Kodi vorbereiten](kodi.md#in-kodi-vorbereiten).

**Kodi aktualisiert nichts, obwohl die Verbindung steht.** Fast immer stimmt die [Pfad-Zuordnung](kodi.md#pfad-zuordnung) nicht. Der Pfad in KMM muss zur Quelle passen, der Pfad in Kodi genau zu dem, was Kodi unter *Videos → Dateien* zeigt. Das Ergebnis steht bei „Letzter Abgleich“ auf der Karte des Geräts.

**Titelmusik wird nicht abgespielt.** Kodi braucht dafür ein Add-on wie TvTunes.

**Trailer lassen sich nicht abspielen.** Kodi braucht dafür das YouTube-Add-on.

## Umbenennen

**Ein Eintrag steht auf „Konflikt“.** Das Ziel gibt es schon. Benenne das Vorhandene um oder ändere das Muster.

**Ein Ordner wird nicht umbenannt.** KMM benennt einen Filmordner nur um, wenn er allein diesem Film gehört.

## Neu anfangen

Auf der Seite **System** leert **Datenbank leeren** KMM, zum Beispiel wenn ein [Ember-Import](ember-import.md) mit falschen Pfaden gelaufen ist. Gib dazu das Wort `LEEREN` ein. Das geht nur, solange kein Job läuft, und lässt sich nicht rückgängig machen.

Gelöscht werden Quellen, Filme, Serien, Episoden, Filmsets, Filter und Kodi-Geräte. Medien, NFOs und Bilder bleiben unberührt. Was du in KMM bearbeitet hast und nicht in einer NFO steht, geht verloren.

Einstellungen und API-Keys bleiben erhalten, solange du den Haken nicht entfernst. Entfernst du ihn, gelten wieder die Standardwerte. API-Keys aus der Konfiguration trägt KMM beim nächsten Start wieder ein.

## Speichern schlägt fehl

KMM braucht Schreibrechte in deinen Medienordnern. Unter Docker bestimmen das `PUID` und `PGID`.

**Eine NFO wurde außerhalb von KMM geändert.** KMM liest sie beim Öffnen des Eintrags automatisch neu ein.

## Das Log finden

Unter Windows steht das Protokoll im Konsolenfenster. Mit dem Startparameter `--logging.file.name=<Datei>` schreibt KMM es zusätzlich in eine Datei. Unter Docker zeigt es `docker compose logs app` an.
