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
| `kmm-plugins` | mitgelieferte Plugins: `kmm-plugin-tmdb` (Metadaten und Bilder), `kmm-plugin-fanarttv` (Bilder) |
| `kmm-ui` | Angular-Oberfläche |
| `kmm-app` | Spring-Boot-Anwendung |

## Bauen

Voraussetzung: JDK 25. Maven und Node.js lädt der Build selbst herunter.

```bash
./mvnw verify          # Build inkl. Java- und Angular-Tests, Coverage-Prüfung (≥ 80 %)
./mvnw -DskipTests package
```

Der Test gegen MariaDB (Testcontainers) läuft nur mit Docker und wird sonst übersprungen.

## Starten

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
docker compose up -d --build
```

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
- `GET /api/v1/system/info`
- `GET /api/v1/sources`, `POST /api/v1/sources`, `DELETE /api/v1/sources/{id}`
- `POST /api/v1/sources/{id}/scan` → `202 Accepted` mit dem gestarteten Job (Film- oder Serien-Scan je nach Quelle)
- `POST /api/v1/sources/{id}/auto-scrape` → `202 Accepted`; ergänzt fehlende Metadaten und Bilder aller Filme der Quelle
- `GET /api/v1/jobs`, `GET /api/v1/jobs/{id}`, `POST /api/v1/jobs/{id}/cancel`
- `GET /api/v1/jobs/events` (Server-Sent Events, Ereignis `job`)
- `GET /api/v1/movies?search=…&page=…&size=…`
- `GET /api/v1/movies/{id}`, `PUT /api/v1/movies/{id}/metadata` (schreibt die NFO)
- `GET /api/v1/tvshows?search=…&page=…&size=…`, `GET /api/v1/tvshows/{id}` (mit Staffeln und Episoden),
  `PUT /api/v1/tvshows/{id}/metadata` (schreibt die `tvshow.nfo`)
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
- `POST /api/v1/kodi/movies/{id}/refresh`, `POST /api/v1/kodi/sources/{id}/scan` (alle Kodi-Geräte, Ergebnis je Gerät)
- `GET /api/v1/migrations/ember/inspect?path=…`, `POST /api/v1/migrations/ember/import` → `202 Accepted` mit dem gestarteten Job
  (`{"profilePath": …, "pathMappings": [{"emberPath": "a:\\filme", "kmmPath": "/media/filme"}], "importSettings": true, "importKodiHosts": true, "dryRun": true}`)
