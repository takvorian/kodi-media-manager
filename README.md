# Kodi Media Manager (KMM)

Medienverwaltung für [Kodi](https://kodi.tv) als Java-Nachfolger von [Ember Media Manager](https://github.com/DanCooper/Ember-MM-Newscraper).
KMM läuft als Docker-Container (mit MariaDB) oder standalone (mit eingebetteter H2-Datenbank) und bringt eine Weboberfläche mit.

> **Status:** Filme und erste Serienfunktionen – Quellen anlegen und im Hintergrund scannen (mit Live-Fortschritt), Filmliste, Kodi-NFOs lesen, bearbeiten und schreiben, Metadaten von TMDB sowie Bilder von TMDB und fanart.tv suchen und übernehmen alle Filme einer Quelle automatisch scrapen (nur fehlende Daten), Filme, Sets, Serien mit Episoden, Stream-Details, Einstellungen und Kodi-Geräte aus Ember Media Manager übernehmen (mit Probelauf) sowie Kodi nach Änderungen automatisch aktualisieren funktionieren. Serienquellen lassen sich scannen; Serien, Staffeln und Episoden (auch Mehrfachepisoden) werden aus den NFOs gelesen, angezeigt und bearbeitet. Konzept und Roadmap: [docs/KONZEPT.md](docs/KONZEPT.md).

## Technik

- Java 25, Spring Boot 4.1, Spring Modulith, Hibernate 7, Flyway
- Scraper als Plugins ([PF4J](https://pf4j.org))
- Angular 22 + Angular Material
- MariaDB (Docker), H2 (standalone), PostgreSQL optional – alle drei per Testcontainers bzw. H2 getestet

## Module

| Modul | Inhalt |
|---|---|
| `kmm-plugin-api` | Schnittstelle für Scraper-Plugins |
| `kmm-scanner` | Film- und Seriensuche im Dateisystem, Titel/Jahr aus Namen, Staffel/Episode wie Kodi (`S01E02`, `1x02`, `…-102`) |
| `kmm-nfo` | Kodi-NFOs für Filme, Serien und Episoden lesen und schreiben (unbekannte Elemente bleiben erhalten) |
| `kmm-migration-ember` | Lesen von Ember-Profilen (Datenbank + `Settings.xml`), Pfad-Mapping; Import über die Seite **Ember-Import** |
| `kmm-kodi` | JSON-RPC-Client für Kodi, Zuordnung von KMM-Pfaden zu Kodi-Pfaden |
| `kmm-plugins` | mitgelieferte Plugins: `kmm-plugin-tmdb` (Film- und Serien-Metadaten, Bilder), `kmm-plugin-tvdb` (Serien-Metadaten und -Bilder), `kmm-plugin-fanarttv` (Bilder für Filme und Serien), `kmm-plugin-televisiontunes` und `kmm-plugin-youtube` (Titelmusik; YouTube braucht yt-dlp), `kmm-plugin-trakt` und `kmm-plugin-omdb` (Bewertungen, OMDb füllt auch leere Felder), `kmm-plugin-moviepilot` und `kmm-plugin-ofdb` (deutsche Angaben für leere Felder von Filmen, ohne API-Key) |
| `kmm-ui` | Angular-Oberfläche |
| `kmm-app` | Spring-Boot-Anwendung |

## Handbuch

Das [Handbuch](docs/handbuch/einfuehrung.md) beschreibt die Bedienung; die Kapitel stehen in `docs/handbuch/kapitel.json`. Dieselben Texte zeigt die Anwendung auf der Seite **Hilfe**, das Fragezeichen in der Kopfleiste öffnet das Kapitel zur aktuellen Seite. `npm run handbuch:pdf` in `kmm-ui` baut daraus ein PDF und prüft dabei alle Links und Bilder (braucht Chrome); die CI hebt das PDF als Artefakt `handbuch` auf.

## Bauen

Voraussetzung: JDK 25. Maven und Node.js lädt der Build selbst herunter.

```bash
./mvnw verify          # Build inkl. Java- und Angular-Tests, Coverage-Prüfung (≥ 80 %)
./mvnw -DskipTests package
```

Der Test gegen MariaDB (Testcontainers) läuft nur mit Docker und wird sonst übersprungen.

Den Windows-Installer baut das Profil `installer`. Dafür muss [WiX 3](https://github.com/wixtoolset/wix3/releases) auf dem `PATH` liegen, die `wix314-binaries.zip` genügt:

```bash
./mvnw -Pinstaller -DskipTests package   # kmm-app/target/installer/Kodi Media Manager-<Version>.msi
```

## Release

```bash
./release.sh --dry-run   # zeigt die nächste Version und das Changelog
./release.sh
```

`release.sh` zählt die Version aus den Git-Tags weiter (`v0.1`, `v0.2`, …) und erzeugt `kmm-app/src/main/resources/changelog.md` aus den Commit-Betreffs. Es committet das Changelog, setzt den Tag, pusht beides und ruft `build-push.sh <version>` auf. Das Skript baut das Image für `linux/amd64` aus dem Tag und lädt es als `takvorian/kodi-media-manager:<version>` und `:latest` zu Docker Hub.

Voraussetzungen: Docker und `docker login`, `gh` mit Anmeldung, Branch `main` auf dem Stand von `origin` mit sauberem Arbeitsbaum und grüner CI für den aktuellen Commit. Die App zeigt die oberste Version des Changelogs an, ohne Changelog `dev`; zwischen zwei Releases gebaute Stände zeigen also noch die letzte Version.

Scheitert nur der Image-Build oder der Upload, sind Commit und Tag schon gepusht; dann `./build-push.sh <version>` wiederholen statt `release.sh`, das sonst die nächste Version anlegt. Das gilt auch, wenn der Maven-Schritt im Image-Build nach 30 Minuten abbricht.

Die CI versieht die Windows-MSI mit dem neuesten Tag. Eine neue MSI ersetzt die alte nur bei höherer Version, also nur die MSI eines neuen Releases installieren.

## Starten

### Windows-Installer

Die MSI bringt Java mit, installiert nur für den angemeldeten Benutzer und braucht keine Administratorrechte. Sie ist nicht signiert, Windows SmartScreen warnt deshalb beim Ausführen. Die Verknüpfung startet KMM in einem Konsolenfenster, die Oberfläche ist dann unter <http://localhost:8080> erreichbar. Schließen des Fensters beendet KMM. Eine neue Version ersetzt die alte; die Daten unter `~/.kodi-media-manager` bleiben auch beim Deinstallieren erhalten.

### Standalone

```bash
java -jar kmm-app/target/kmm-app.jar
```

Die Oberfläche ist dann unter <http://localhost:8080> erreichbar. Daten liegen unter `~/.kodi-media-manager` (änderbar über `KMM_HOME`).
Eine andere Datenbank lässt sich z. B. so verwenden:

```bash
java -jar kmm-app/target/kmm-app.jar \
  --spring.datasource.url=jdbc:postgresql://localhost/kmm \
  --spring.datasource.username=kmm --spring.datasource.password=…
```

### Docker

```bash
cd docker
cp .env.example .env     # Passwörter setzen
docker compose pull      # fertiges Image von Docker Hub; selbst bauen: docker compose up -d --build
docker compose up -d
```

Eine feste Version statt der neuesten legt `KMM_IMAGE_TAG` in der `.env` fest, z. B. `0.1`.

| Volume | Zweck |
|---|---|
| `/config` | Plugins, Cache, Logs |
| `/media` | Medienbibliothek |
| `/import/ember` | Ember-Profil für die Migration (read-only) |

## API-Keys

Für die Online-Suche werden API-Keys benötigt. Sie werden auf der Seite **Einstellungen** unter „API-Keys“ gepflegt
und in der Datenbank gespeichert; die Oberfläche zeigt nur die letzten Zeichen. Umgebungsvariablen füllen einen
noch nicht gespeicherten Key beim Start (Docker: in `docker/.env` eintragen); danach gilt der gespeicherte Key,
und ein in der Oberfläche gelöschter Key bleibt gelöscht:

```bash
export KMM_TMDB_API_KEY=…          # https://www.themoviedb.org/settings/api (v3-Key oder v4-Lese-Token)
export KMM_FANARTTV_API_KEY=…      # https://fanart.tv/get-an-api-key/
export KMM_TVDB_API_KEY=…          # https://thetvdb.com/api-information (v4), optional KMM_TVDB_PIN
```

### Datenquellen

Metadaten und Bilder stammen von [The Movie Database (TMDB)](https://www.themoviedb.org), [fanart.tv](https://fanart.tv)
und [TheTVDB](https://www.thetvdb.com). Serien-Metadaten von TheTVDB:
*Metadata provided by TheTVDB. Please consider adding missing information or subscribing.*
Die Episodenreihenfolge (ausgestrahlt, DVD, absolut) wirkt nur bei TheTVDB; TMDB kennt nur die Ausstrahlungsreihenfolge.

## Einstellungen

Sprache der Metadaten und Bilder (getrennt für Filme und Serien), Episodenreihenfolge für Serien,
Video-Dateiendungen, ausgeschlossene Ordner und ein Proxy werden auf der Seite **Einstellungen** gepflegt und in der
Datenbank gespeichert. Für Filme haben Umgebungsvariablen Vorrang; damit festgelegte Werte sind in der Oberfläche
gesperrt:

```bash
export KMM_TMDB_LANGUAGE=de-DE     # Sprache der Metadaten
export KMM_ARTWORK_LANGUAGE=de     # bevorzugte Sprache der Bilder
export KMM_PROXY_HOST=proxy.local  # Proxy für Online-Dienste, dazu KMM_PROXY_PORT/_USERNAME/_PASSWORD
```

## Kodi

Auf der Seite **Kodi** werden Kodi-Geräte mit Adresse, Port, Zugangsdaten und Pfad-Zuordnung angelegt (in Kodi:
*Einstellungen → Dienste → Steuerung → Steuerung über HTTP erlauben*). Die Pfad-Zuordnung übersetzt die Verzeichnisse
der KMM-Quellen in Kodis Pfade, z. B. `/media/filme` → `smb://nas/medien/filme/`.

- **Echtzeit-Sync:** Nach dem Speichern von Metadaten oder Bildern lässt KMM Kodi den Film neu einlesen
  (`VideoLibrary.RefreshMovie`); kennt Kodi ihn noch nicht, scannt Kodi seinen Ordner. Findet ein Scan in KMM neue
  Filme, durchsucht Kodi die Quelle. Mehrere Änderungen kurz hintereinander lösen nur eine Aktualisierung aus
  (`kmm.kodi.sync-delay`, Standard 5 s).
- **Manuell:** „In Kodi aktualisieren“ auf der Detailseite und „Kodi-Scan“ je Quelle wirken auf alle Geräte.
- Kodi wird ohne den Proxy aus den Einstellungen angesprochen. Aus KMM entfernte Filme bleiben in Kodi, bis dort die
  Bibliothek bereinigt wird.

Hinweis: Java sendet Proxy-Zugangsdaten bei HTTPS-Verbindungen nur, wenn die JVM mit
`-Djdk.http.auth.tunneling.disabledSchemes=` gestartet wird.

## Entwicklung

```bash
./mvnw -pl kmm-app -am spring-boot:run   # Backend auf :8080
cd kmm-ui && npm start                   # Angular-Dev-Server auf :4200, Proxy auf das Backend
```

## API

- OpenAPI/Swagger UI: `/swagger-ui.html` (im Docker-Profil mit Anmeldung)
- `GET /api/v1/system/info`, `GET /api/v1/system/changelog`
- `GET /api/v1/sources`, `POST /api/v1/sources`, `DELETE /api/v1/sources/{id}`
- `POST /api/v1/sources/{id}/scan` → `202 Accepted` mit dem gestarteten Job (Film- oder Serien-Scan je nach Quelle)
- `POST /api/v1/sources/{id}/auto-scrape` → `202 Accepted`; ergänzt fehlende Metadaten und Bilder aller Filme bzw. Serien (inkl. Episoden und Staffeln) der Quelle
- `GET /api/v1/jobs`, `GET /api/v1/jobs/{id}`, `POST /api/v1/jobs/{id}/cancel`
- `GET /api/v1/jobs/events` (Server-Sent Events, Ereignis `job`)
- `GET /api/v1/movies?search=…&page=…&size=…`
- `GET /api/v1/movies/{id}`, `PUT /api/v1/movies/{id}/metadata` (schreibt die NFO)
- `POST /api/v1/movies/export?format=HTML|CSV|JSON`, `POST /api/v1/tvshows/export?format=…` mit dem Filter als Body
  (`{"matchAll": true, "conditions": [], "search": null}`) → Download; HTML ist eine Liste mit Postern als ZIP
- `GET /api/v1/tvshows?search=…&page=…&size=…`, `GET /api/v1/tvshows/{id}` (mit Staffeln und Episoden),
  `PUT /api/v1/tvshows/{id}/metadata` (schreibt die `tvshow.nfo`)
- `GET /api/v1/scrapers/tvshows` (Serien-Scraper mit Attribution), `GET /api/v1/tvshows/{id}/scraper-search?scraper=…`,
  `GET /api/v1/tvshows/{id}/scraper-proposal?scraper=…&providerId=…` (Vorschlag für das Formular),
  `POST /api/v1/tvshows/{id}/scraper-episodes?scraper=…&providerId=…` (ergänzt leere Episodenfelder, schreibt NFOs),
  `GET /api/v1/tvshows/{id}/missing-episodes?scraper=…` (Episoden beim Anbieter ohne Datei, nur Anzeige)
- `GET /api/v1/tvshows/{id}/artwork[?season=…]`, `GET|PUT|DELETE /api/v1/tvshows/{id}/artwork/{type}[?season=…]`,
  `…/{type}/image`, `…/{type}/candidates` (Bilder der Serie bzw. einer Staffel), ebenso
  `/api/v1/episodes/{id}/artwork/THUMB` (Standbild der Episode)
- `GET /api/v1/episodes/{id}`, `PUT /api/v1/episodes/{id}/metadata` (schreibt die Episoden-NFO, bei Mehrfachepisoden
  alle Episoden der Datei)
- `GET /api/v1/movies/{id}/scraper-search?scraper=tmdb&title=…&year=…`, `GET /api/v1/movies/{id}/scraper-proposal?scraper=tmdb&providerId=…`
- `GET /api/v1/movies/{id}/artwork`, `GET …/artwork/{TYP}/image`, `GET …/artwork/{TYP}/candidates`,
  `PUT …/artwork/{TYP}` (`{"url": …}`), `DELETE …/artwork/{TYP}` – TYP: POSTER, FANART, CLEARLOGO, CLEARART, DISCART, BANNER, LANDSCAPE
- `GET /api/v1/scrapers/metadata`
- `GET /api/v1/settings`, `PUT /api/v1/settings` (Proxy-Passwort `null` = unverändert)
- `GET /api/v1/settings/api-keys`, `PUT /api/v1/settings/api-keys` (`{"TMDB_API_KEY": "…"}`; leerer Wert löscht,
  fehlende Keys bleiben)
- `GET /api/v1/plugins`, `POST /api/v1/plugins/{id}/enable|disable`
- `GET /api/v1/kodi/hosts`, `POST /api/v1/kodi/hosts`, `PUT|DELETE /api/v1/kodi/hosts/{id}` (Passwort `null` = unverändert),
  `POST /api/v1/kodi/hosts/{id}/test`
- `POST /api/v1/kodi/movies/{id}/refresh`, `POST /api/v1/kodi/tvshows/{id}/refresh`, `POST /api/v1/kodi/episodes/{id}/refresh`,
  `POST /api/v1/kodi/sources/{id}/scan` (alle Kodi-Geräte, Ergebnis je Gerät)
- `GET /api/v1/migrations/ember/inspect?path=…`, `POST /api/v1/migrations/ember/import` → `202 Accepted` mit dem gestarteten Job
  (`{"profilePath": …, "pathMappings": [{"emberPath": "a:\\filme", "kmmPath": "/media/filme"}], "importSettings": true, "importKodiHosts": true, "dryRun": true}`)

## Lizenz

KMM steht unter der [GNU General Public License v3.0](LICENSE), wie Ember Media Manager, aus dem es Logik übernimmt.
