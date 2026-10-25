# Konzept: Kodi Media Manager (KMM)

Java-Port von [Ember Media Manager](https://github.com/DanCooper/Ember-MM-Newscraper) (VB.NET/WinForms, nicht mehr gepflegt).

| | Entscheidung |
|---|---|
| Sprache / Framework | Java 25 (LTS), Spring Boot 4.1 (Spring Framework 7, Hibernate 7, Jakarta EE 11) |
| Betrieb | Docker-Container (MariaDB) **und** Standalone (H2 oder beliebige DB über JPA/Flyway) |
| Backend | **B2** – modularer Monolith (Spring Modulith) + externe Scraper-Plugins über PF4J |
| Frontend | **Angular-SPA** mit REST-API, ins JAR gebündelt |
| Migration | Bestehende Ember-Daten (Datenbank + Einstellungen) werden übernommen |
| Base-Package | `de.tiktak.kmm` |

Neu gebaut wird die **Funktionalität**, der Code wird nicht 1:1 übersetzt. Die WinForms-UI und VB-spezifische Teile lassen sich nicht sinnvoll portieren.

---

## 1. Funktionsumfang des Originals

| Bereich | Ember-Komponenten | Übernahme |
|---|---|---|
| Kern-API | `EmberAPI/clsAPIScanner, clsAPINFO, clsAPIDatabase, clsAPIImages, clsAPIMediaInfo, clsAPIFFmpeg, clsAPITaskManager, clsAPISettings` | ja, neu in Java |
| Medientypen | Filme, Filmsets, Serien/Staffeln/Episoden | MVP: Filme, danach TV |
| Daten-Scraper (sequenziell, füllen leere Felder) | TMDB, IMDB, OMDb, TVDB, Trakt, OFDB, MoviepilotDE, XML | MVP: TMDB, TVDB, OMDb. HTML-Scraper (IMDB/OFDB/Moviepilot) sind fragil und kommen später |
| Bild-Scraper (parallel) | TMDB Poster, FanartTV, TVDB Poster | ja |
| Trailer/Themes | TMDB/YouTube/Apple/Davestrailer/Videobuster, TelevisionTunes | später, optional (yt-dlp extern) |
| Generische Addons | BulkRename, MediaFileManager, TagManager, FilterEditor, MetadataEditor, MovieExport, VideoSourceMapping | schrittweise |
| Interfaces | Kodi JSON-RPC, Trakt.tv | Kodi: ja, Trakt: später |
| Datei-Output | Kodi-NFO, Artwork nach Kodi-Namenskonvention, extrafanart, Trailer | ja, Kern-Feature |
| Medien-Info | MediaInfo/FFprobe | ja, über `ffprobe` (im Docker-Image enthalten) |

**Kernprinzip:** Die Wahrheit liegt im Dateisystem (NFO + Bilder neben der Mediendatei). Die Datenbank ist Index, Cache und Arbeitszustand und lässt sich per Rescan neu aufbauen. Deshalb ist die Wahl der Datenbank unkritisch.

---

## 2. Backend-Basis

- **Virtual Threads** für parallele Scraper-Aufrufe und Datei-I/O. Deshalb Spring MVC, kein WebFlux.
- **Persistenz:** Spring Data JPA + Hibernate 7, Schema über **Flyway** mit herstellerspezifischen Ordnern (`db/migration/{vendor}`: `h2`, `mariadb`, `postgresql`). `ddl-auto=validate`.
- **Profile:**
  - `standalone` (Default): H2 im File-Mode unter `~/.kodi-media-manager`, nur an `127.0.0.1` gebunden, ohne Anmeldung
  - `docker`: MariaDB 11, Anmeldung per HTTP Basic, Konfiguration über Umgebungsvariablen
  - beliebige andere Datenbank über `spring.datasource.*`
- **HTTP-Clients:** Spring `RestClient` / `@HttpExchange`, Resilience4j (Rate-Limits, Retry), Caffeine-Cache.
- **Bausteine:**
  - NFO: Jackson XML
  - Bilder: ImageIO/TwelveMonkeys
  - Scanner: `java.nio.file`
  - Jobs: eigene Job-Verwaltung mit Fortschritt über SSE
- **Kodi:** JSON-RPC (`VideoLibrary.Scan`/`Refresh`), Pfad-Mapping Container ↔ Kodi.
- **Security:** Spring Security, CSRF für die SPA (Cookie `XSRF-TOKEN`).
- **Betrieb:** Actuator (Health/Probes), OpenAPI (springdoc, `/swagger-ui.html`).

### Deployment
- **Docker:** Multi-Stage-Build (Temurin 25 JDK → JRE + ffmpeg), Layered JAR, `docker/docker-compose.yml` mit App + MariaDB. Volumes: `/config`, `/media`, `/import/ember` (read-only). Berechtigungen über `PUID`/`PGID`.
- **Standalone:** Fat-JAR `java -jar kmm-app.jar`. Später als Installer per `jpackage`.

---

## 3. Backend-Varianten (Entscheidungsgrundlage)

| | B1: Modularer Monolith | **B2: Monolith + PF4J-Plugins (gewählt)** | B3: Microservices |
|---|---|---|---|
| Beschreibung | ein Deployable, fachliche Module mit verifizierten Grenzen | wie B1, Scraper werden zur Laufzeit als JARs geladen | getrennte Services + Broker |
| + | einfach zu betreiben, ein Artefakt | Scraper ohne Rebuild austauschbar und nachrüstbar (wie Embers Addons) | unabhängige Skalierung |
| − | Plugins nur per Rebuild | Classloader-Komplexität | für eine Single-User-App überdimensioniert, standalone kaum machbar |

### Plugin-Architektur (B2)
- `kmm-plugin-api` ist die **einzige** Abhängigkeit für Plugins: Extension Points `MetadataScraper` und `ImageScraper` (erweitern `org.pf4j.ExtensionPoint`) plus Value Objects. Keine Spring-Abhängigkeit.
- `kmm-app` bindet PF4J direkt ein, nicht über das veraltete `pf4j-spring`:
  - `PluginManagerConfiguration` und `PluginLifecycle`: Laden und Entladen sind an den Spring-Lifecycle gebunden
  - `ScraperRegistry`: kapselt PF4J vor dem Rest der Anwendung
  - `PluginService` und `PluginController`: `GET /api/v1/plugins`, `POST /api/v1/plugins/{id}/enable|disable`. Der Zustand übersteht Neustarts.
- Plugins sehen den Spring-Context nicht. Dienste wie HTTP-Client und Settings reicht die App später über ein schmales `PluginContext`-Interface herein.
- Mitgelieferte Plugins liegen unter `kmm-plugins/`. Plugin-Verzeichnis: `/config/plugins` (Docker) bzw. `~/.kodi-media-manager/plugins` (Standalone).

---

## 4. Frontend-Varianten (Entscheidungsgrundlage)

| Kriterium | Thymeleaf + htmx | Vaadin Flow | **SPA + REST (gewählt: Angular)** | JavaFX | Hybrid (Web-UI + Desktop-Hülle) |
|---|---|---|---|---|---|
| Docker-tauglich | ++ | ++ | ++ | −− | ++ |
| Standalone-tauglich | ++ | ++ | ++ | ++ | ++ |
| Initialaufwand | gering | gering–mittel | hoch | mittel | +Packaging |
| Komplexe UI (Bildauswahl, Grids) | o | + | ++ | + | wie Basis |
| Tech-Stacks | 1 | 1 | 2 | 1 | 2–3 |
| Offene API (Kodi-Addon, Mobile, Skripte) | − | − | ++ | − | wie Basis |
| Wartbarkeit / Zukunftssicherheit | + | o (Lock-in) | ++ | o | wie Basis |

**Begründung:** Embers Kern-UX (gefilterte Medienlisten, Bildauswahl über mehrere Scraper, Bulk-Operationen, Live-Fortschritt) profitiert am stärksten von einer SPA. Die REST-API ist ein Mehrwert gegenüber dem Original. Im Standalone-Betrieb öffnet sich später der System-Browser (optional mit Tray-Icon).

---

## 5. Modulstruktur

```
kodi-media-manager/            groupId de.tiktak.kmm
├─ kmm-plugin-api              Plugin-Schnittstelle (PF4J Extension Points)
├─ kmm-scanner                 Filmsuche im Dateisystem: Stapel (cd1/cd2), DVD/Blu-ray-Ordner, Titel/Jahr aus Namen
├─ kmm-migration-ember         Lesen von Ember-Profilen (SQLite + Settings.xml), Pfad-Mapping
├─ kmm-plugins/                mitgelieferte Plugins (aktuell: kmm-plugin-testdata, nur für Tests)
├─ kmm-ui                      Angular-SPA → static/ im JAR
├─ kmm-app                     Spring Boot: REST, Security, Plugins, Persistenz, Profile
└─ docker/                     Dockerfile, docker-compose.yml
geplant: kmm-core, kmm-nfo, kmm-media, kmm-kodi; Scraper-Plugins tmdb, tvdb, omdb, fanarttv
```

Module werden erst angelegt, wenn sie Inhalt bekommen. Die Grenzen innerhalb von `kmm-app` prüft Spring Modulith (`ModularityTests`).

---

## 6. Ember-Migration (Datenbank + Einstellungen)

**Quelle** (aus dem Ember-Code analysiert):
- Profilordner `<Ember>\Profiles\Default\`.
- **SQLite**-Datenbank `MyVideos48.emm` (Schema-Version 48). Ältere Versionen (`MyVideos2..47.emm`) patcht Ember beim Öffnen selbst.
- Relevante Tabellen:
  - Medien: `movie`, `sets`/`setlinkmovie`, `tvshow`, `seasons`, `episode`, `files`
  - Quellen: `moviesource`, `tvshowsource`
  - Personen und Verknüpfungen: `actors` + `*link*`
  - Klassifizierung: `genre`/`country`/`studio`/`tag`
  - Sonstiges: `rating`, `uniqueid`, `art`, Stream-Tabellen
- `Settings.xml`: .NET-`XmlSerializer`, `<Settings version="1">`. Listen werden als `<Name><string>…</string></Name>` serialisiert.

**Umsetzung** (`kmm-migration-ember`, frameworkfrei, in `kmm-app` verdrahtet):
- `EmberProfileDetector`: findet das Profil (auch über das Installationsverzeichnis), prüft die Schema-Version und gibt verständliche Fehlermeldungen aus.
- `EmberDatabaseInspector`: öffnet die Datenbank **read-only** (das Ember-Profil bleibt unverändert), prüft die Tabellen und zählt die Bibliothek.
- `EmberSettingsReader`: liest `Settings.xml` in ein Teil-Modell. Unbekannte Elemente werden ignoriert. Das Proxy-Passwort wird bewusst nicht übernommen.
- `PathMapper`: übersetzt Windows- und UNC-Pfade (`D:\Filme`, `\\nas\serien`) per Präfix-Regel (längster Präfix gewinnt) in Zielpfade (`/media/filme`).
- `GET /api/v1/migrations/ember/inspect?path=…`: Analyse ohne Import (Version, Umfang, Einstellungen).

**Geplant:**
- `EmberMigrationJob` als Hintergrund-Job mit Fortschritt über SSE, Dry-Run-Modus und `MigrationReport`
- idempotente Läufe: Die Ember-IDs werden als `legacy_ember_id` gespeichert
- Wizard in der UI: Profil wählen → Pfad-Mapping → Dry-Run → Import

**Schema-Versionen:** Direkt unterstützt wird v48. Ältere Profile werden einmal mit der letzten Ember-Version geöffnet, die sie selbst aktualisiert.

---

## 7. Roadmap

1. **Fundament** ✅: Projektgerüst, Profile H2/MariaDB, Flyway, Docker, Security, Plugin-Mechanismus, Analyse von Ember-Profilen.
2. **Filme MVP** (in Inkrementen):
   1. Quellen, Scan, Filmliste ✅
   2. NFO lesen/schreiben (`<dateiname>.nfo`, Kodi 19–21)
   3. Job-Framework + Fortschritt per SSE
   4. TMDB-Plugin (API-Key aus `KMM_TMDB_API_KEY`)
   5. Bild-Plugins TMDB/FanartTV, Bildauswahl, Kodi-Bildnamen (`<dateiname>-poster.jpg` …)
   - **2a. Ember-Migration I:** Filme, Sets, Personen, Genres, Artwork, Quellen und Settings, mit Dry-Run und Report.
3. **Kodi-Integration:** JSON-RPC, Pfad-Mapping, Library-Update.
4. **Serien:** TVDB/TMDB-TV, Staffeln/Episoden. Dazu **Ember-Migration II** (Serien, Stream-Details, restliche Settings, Kodi-Hosts, Scraper-Keys).
5. **Komfort:** ffprobe-Streamdetails, Bulk-Rename, Tag-/Filmset-Manager, Filter- und Metadaten-Editor.
6. **Erweiterungen:** Trailer/Themes, Trakt.tv, Export, HTML-Scraper, Installer (jpackage).

## 8. Offene Punkte / Risiken

- **API-Keys:** TMDB, TVDB v4 (kostenpflichtig bzw. Projekt-Key), FanartTV, OMDb.
- **HTML-Scraper** sind rechtlich und technisch fragil.
- **Docker:** Dateirechte (`PUID`/`PGID`) und Pfad-Mapping Container ↔ Kodi (SMB/NFS).
- **Lizenz:** noch offen. Ember steht unter GPL-3.0. Werden Code oder Logik direkt übernommen, muss KMM ebenfalls GPL-3.0 sein.
- **Schriftarten:** Die UI lädt Roboto derzeit von Google Fonts. Für den Offline- bzw. NAS-Betrieb sollte die Schrift selbst gehostet werden.
