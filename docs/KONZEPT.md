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

**NFO-Regeln (umgesetzt):**
- Dateiname: `Film.mkv` → `Film.nfo`; Stapel `Film-cd1.avi` → `Film.nfo`; DVD → `VIDEO_TS/VIDEO_TS.nfo`; Blu-ray → `BDMV/index.nfo`. Eine vorhandene `movie.nfo` im Filmordner wird gelesen und an Ort und Stelle aktualisiert.
- KMM verwaltet Titel, Original-/Sortiertitel, Jahr, Erstaufführung, Handlung, Kurzinhalt, Tagline, Laufzeit, Altersfreigabe, Genres sowie IMDb-/TMDB-IDs (bearbeitbar).
- Außerdem liest und schreibt KMM die weiteren Angaben (derzeit nur angezeigt): Darsteller mit Rolle, Reihenfolge und Bild, Regie (`director`), Drehbuch (`credits`), Studios, Länder, Tags, Bewertungen (`ratings`), Filmset (`set` mit `name`/`overview`, Sets sind über den Namen eindeutig), eigene Bewertung, Wiedergaben, zuletzt gesehen und Hinzugefügt-Datum (`dateadded`, lokale Zeit).
- Alle anderen Elemente (`fileinfo`, Trailer, Bild-URLs, IDs weiterer Anbieter …) bleiben beim Schreiben unverändert. Sind die weiteren Angaben eines Films noch nicht eingelesen (z. B. vor dem ersten Scan nach einem Update), lässt KMM auch diese Elemente unverändert.
- Beim Speichern wird zuerst die NFO geschrieben (atomar über eine temporäre Datei); scheitert das, bleibt auch die Datenbank unverändert.
- Ein erneuter Scan liest eine NFO nur ein, wenn sich ihre Änderungszeit geändert hat. Unlesbare NFOs werden gezählt und gemeldet, der Film bleibt erhalten.

**Bild-Regeln (umgesetzt):**
- Verwaltete Bildarten: Poster, Fanart, Clearlogo, Clearart, Disc, Banner, Landscape.
- Dateinamen: `Film-poster.jpg`, `Film-clearlogo.png` …; bei DVD-/Blu-ray-Ordnern `poster.jpg` … im Filmordner. Ein neues Bild ersetzt Varianten mit anderer Endung.
- Die Bildsuche fragt alle Bild-Scraper parallel über TMDB- bzw. IMDb-ID ab; Ausfälle einzelner Anbieter erscheinen als Hinweis. Sortierung: bevorzugte Sprache (Standard `de`), dann sprachneutral, Englisch, Rest; bei Fanart sprachneutrale (textlose) Bilder zuerst; innerhalb der Gruppen nach Beliebtheit.
- Download nur über http(s), nur JPEG/PNG, höchstens 25 MB, atomar gespeichert.

**Automatisches Scrapen (umgesetzt):**
- Läuft pro Filmquelle als Hintergrund-Job (`auto-scrape:<quelle>`) und füllt nur **fehlende** Daten; Gepflegtes bleibt unangetastet.
- Metadaten nur für Filme ohne TMDB-ID, über den bevorzugten Scraper (`kmm.scraping.preferred-metadata-scraper`, Standard `tmdb`). Übernommen wird nur ein sicherer Treffer (bekannte IMDb-ID oder genau passender Titel/Originaltitel mit Jahr); sonst landet der Film in der Liste „ohne Treffer“ zur Handsuche.
- Danach alle sieben Bildarten, soweit sie fehlen: eine Kandidatensuche je Film, je Art der beste Kandidat (Sortierung wie bei der Bildsuche). Scheitert ein Download, werden bis zu drei Kandidaten probiert.
- Fehler einzelner Filme brechen den Lauf nicht ab. Das Ergebnis enthält Zähler, Titel ohne Treffer, Fehler und Hinweise (z. B. nicht eingerichtete Anbieter).

**Einstellungen (umgesetzt):**
- Benutzereinstellungen liegen in der Tabelle `app_setting` (Schlüssel/Wert): Sprache der Metadaten (an Plugins als gemeinsame Einstellung `language`), bevorzugte Bildsprache, Video-Dateiendungen, ausgeschlossene Ordner, Proxy.
- Wirksam ist zuerst die Konfiguration (`kmm.settings.*` bzw. Umgebungsvariablen, in der UI gesperrt), dann der gespeicherte Wert, dann der Standard.
- Änderungen wirken ohne Neustart: PF4J erzeugt Extensions bei jeder Abfrage neu (mit aktuellem Plugin-Kontext), der HTTP-Client wählt den Proxy pro Verbindung, der Scanner wird pro Scan erzeugt.

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
  - Jobs: eigene Job-Verwaltung auf virtuellen Threads, Fortschritt und Ergebnis per REST (`/api/v1/jobs`) und SSE (`/api/v1/jobs/events`), gedrosselt auf höchstens eine Meldung je 250 ms und Job, ein Job je fachlichem Schlüssel (z. B. ein Scan pro Quelle). Laufende und die letzten 50 beendeten Jobs werden im Speicher gehalten.
- **Kodi:** JSON-RPC über HTTP (`kmm-kodi`, ohne Proxy): `VideoLibrary.GetMovies` (Suche über Verzeichnis und Datei, auch Stapel), `RefreshMovie`, `Scan`; Pfad-Zuordnung je Gerät (Container/KMM ↔ Kodi). Echtzeit-Sync über Ereignisse (`MovieFilesChangedEvent`, `MovieSourceScannedEvent`) nach dem Commit, verzögert und je Film entprellt auf einem eigenen virtuellen Thread.
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
- Plugins sehen den Spring-Context nicht. Dienste reicht die App über das schmale `PluginContext`-Interface herein: Einstellungen (`kmm.plugins.settings.<plugin-id>.<name>`), gemeinsamer `HttpClient`, User-Agent. Eine eigene `ExtensionFactory` ruft dafür `Scraper.initialize(context)` auf.
- Laufzeit-Plattform für Plugins: Plugin-API, PF4J, SLF4J und Jackson 3 stellt die App bereit (im Plugin `provided`); weitere Bibliotheken müssen Plugins selbst mitbringen.
- Mitgelieferte Plugins (aktuell TMDB) liegen als Datei unter `bundled-plugins/` im JAR und werden beim Start ins Plugin-Verzeichnis kopiert bzw. aktualisiert (`kmm.plugins.install-bundled`).
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
├─ kmm-scanner                 Film- und Seriensuche: Stapel (cd1/cd2), DVD/Blu-ray-Ordner, Titel/Jahr aus Namen,
│                              Staffel/Episode nach Kodis Regeln (S01E02, 1x02, …-102, Mehrfachepisoden),
│                              Kodi-Namen für Begleitdateien (NFO, später Artwork)
├─ kmm-nfo                     Kodi-NFO für Filme, Serien und Episoden lesen/schreiben (DOM, unbekannte Elemente
│                              bleiben erhalten, XXE-sicher, mehrere <episodedetails> je Datei)
├─ kmm-migration-ember         Lesen von Ember-Profilen (SQLite, Settings.xml, Interface.Kodi.xml), Pfad-Mapping
├─ kmm-kodi                    JSON-RPC-Client für Kodi (Version, Film suchen, RefreshMovie, Scan),
│                              Zuordnung KMM-Pfad → Kodi-Pfad (smb://, nfs://, Windows, POSIX)
├─ kmm-plugins/                mitgelieferte Plugins: kmm-plugin-tmdb (Metadaten + Bilder), kmm-plugin-fanarttv (Bilder);
│                              kmm-plugin-testdata nur für Tests
├─ kmm-ui                      Angular-SPA → static/ im JAR
├─ kmm-app                     Spring Boot: REST, Security, Plugins, Persistenz, Profile
└─ docker/                     Dockerfile, docker-compose.yml
geplant: kmm-core, kmm-media; Scraper-Plugins tvdb, omdb
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
- `EmberLibraryReader`: liest Quellen, Filme mit allen Verknüpfungen (IDs, Genres, Darsteller mit Rolle und Bild, Regie, Drehbuch, Studios, Länder, Tags, Bewertungen, Sets mit Reihenfolge, Nutzerdaten) und ausgeschlossene Ordner, je Tabelle mit einer Abfrage (1169 Filme in unter einer Sekunde).
- `EmberTvLibraryReader`: liest Serienquellen, Serien (IDs, Genres, Studios, Tags, Darsteller, Bewertungen, Staffeltitel), Episoden mit Datei (Pfad über `files`; Darsteller und Gaststars, Regie, Drehbuch, Bewertungen, Nutzerdaten) und zählt Embers Episoden ohne Datei. Stream-Details kommen für Filme und Episoden aus den Tabellen `MoviesVStreams`/`TVVStreams` usw.
- `GET /api/v1/migrations/ember/inspect?path=…`: Analyse ohne Import (Version, Umfang, Film- und Serienquellen, Einstellungen).

**Import (Ember-Migration I, umgesetzt):**
- `POST /api/v1/migrations/ember/import` startet einen Hintergrund-Job (`ember-import`) mit Live-Fortschritt; Assistent in der UI: Profil analysieren → Pfade zuordnen → Probelauf → Import → Bericht.
- **Ember-DB gewinnt, Dateien bleiben:** Metadaten und weitere Angaben kommen aus der Ember-Datenbank; NFOs und Bilder werden nicht verändert. Die NFO gilt nach dem Import als eingelesen, damit der nächste Scan die Ember-Daten nicht mit dem Datei-Inhalt überschreibt.
- **Quellen:** Embers Filmquellen werden über die Pfad-Zuordnung übernommen (ohne Regeln unverändert, z. B. bei KMM auf demselben Rechner). Das Verzeichnis muss nicht erreichbar sein (Hinweis im Bericht); vorhandene Quellen mit demselben Verzeichnis werden verwendet.
- **Idempotent:** Filme werden über den Dateipfad erkannt, Sets über den Namen; ein erneuter Import aktualisiert statt zu duplizieren. Kodi kennt nur ein Set je Film, übernommen wird das erste (Hinweis im Bericht).
- **Probelauf:** führt den Import vollständig in einer Transaktion aus und rollt sie zurück; Einstellungen werden nur berechnet. Der Bericht zeigt also genau, was der Import ändern würde.
- **Umrechnungen:** `Germany:16` → `FSK 16`; leere Bewertungen (0 ohne Stimmen) und eigene Bewertung 0 entfallen; Embers Zeitstempel (Sekunden seit 1970, aber aus der lokalen Uhrzeit berechnet) werden in der Zeitzone von KMM zu Hinzugefügt-Datum und „zuletzt gesehen“; Pfade, die auf dem Zielsystem ungültig sind, werden übersprungen und gemeldet.
- **Einstellungen:** Sprache der Film-Metadaten, erzwungene Bildsprache, Video-Dateiendungen, ausgeschlossene Ordner (über die Pfad-Zuordnung) und Proxy (ohne Passwort) gehen in den KMM-Einstellungsspeicher; durch die Konfiguration festgelegte Werte bleiben unverändert.
- **Kodi-Geräte** (`Interface.Kodi.xml`): Name, Adresse, Port, Zugangsdaten, Echtzeit-Sync und Pfad-Zuordnungen; Embers lokale Pfade laufen über die Pfad-Zuordnung des Imports, Zuordnungen ohne passende Regel entfallen mit Hinweis. Geräte werden über den Namen erkannt.

**Import (Ember-Migration II, umgesetzt):**
- **Serien** (abwählbar): Serienquellen über dieselbe Pfad-Zuordnung, Serien über den Ordner, Episoden über Datei, Staffel und Nummer erkannt; Mehrfachepisoden bleiben mehrere Episoden mit derselben Datei. Embers Platzhalter-Staffel −1 und leere Staffeltitel entfallen; Episoden ohne Datei werden nur gezählt. Wie bei Filmen gewinnt die Ember-DB, `tvshow.nfo` und Episoden-NFOs gelten danach als eingelesen.
- **Stream-Details** für Filme und Episoden; externe Untertitel (eigene Dateien) entfallen. Liefert Ember keine Spuren, bleiben die in KMM bekannten erhalten.
- **Einstellungen:** Sprache der Serien-Metadaten und -Bilder sowie die Episodenreihenfolge. API-Keys werden bewusst nicht übernommen; sie werden in KMM unter „Einstellungen → API-Keys“ gepflegt.
- **Nicht übernommen:** Embers Artwork-Pfade (KMM findet Bilder über die Kodi-Namen neben den Dateien), Sperren/Markierungen, Scraper-Keys, Kodi-Optionen zum Gesehen-Status.

**Schema-Versionen:** Direkt unterstützt wird v48. Ältere Profile werden einmal mit der letzten Ember-Version geöffnet, die sie selbst aktualisiert.

---

## 7. Roadmap

1. **Fundament** ✅: Projektgerüst, Profile H2/MariaDB, Flyway, Docker, Security, Plugin-Mechanismus, Analyse von Ember-Profilen.
2. **Filme MVP** (in Inkrementen):
   1. Quellen, Scan, Filmliste ✅
   2. NFO lesen/schreiben ✅ (`<dateiname>.nfo`, Kodi 19–21; Scan übernimmt NFO-Inhalte, Detailseite bearbeitet und schreibt sie zurück)
   3. Job-Framework + Fortschritt per SSE ✅ (Scan läuft als Hintergrund-Job, Abbruch möglich, Live-Fortschritt in der UI)
   4. TMDB-Plugin ✅ (API-Key aus `KMM_TMDB_API_KEY`; Online-Suche auf der Detailseite, Übernahme ins Formular)
   5. Bild-Plugins TMDB/FanartTV, Bildauswahl, Kodi-Bildnamen ✅ (`<dateiname>-poster.jpg` …, bei DVD/Blu-ray `poster.jpg` im Filmordner)
   6. Automatisches Scrapen aller Filme einer Quelle ✅ (nur fehlende Metadaten und Bilder, Bericht mit Titeln ohne Treffer)
   - **2a. Ember-Migration I** ✅: erweitertes Filmmodell (Personen, Sets, Studios, Länder, Tags, Bewertungen, Nutzerdaten), Einstellungsspeicher, Import von Quellen, Filmen, Sets und Einstellungen mit Probelauf und Bericht.
3. **Kodi-Integration** ✅: Kodi-Geräte mit Pfad-Zuordnung und Verbindungstest, Echtzeit-Sync wie in Ember (RefreshMovie bzw. Scan des Ordners, entprellt im Hintergrund), manueller Abgleich je Film und Quelle, Übernahme der Kodi-Geräte aus Ember. Später: Gesehen-Status aus Kodi holen, Bibliothek bereinigen (`VideoLibrary.Clean`).
4. **Serien** (in Inkrementen):
   1. Modell, Scan, NFOs, Liste und Detailseiten ✅ (jeder Ordner unter der Quelle ist eine Serie, Episoden beliebig tief; Staffel/Episode aus dem Dateinamen; Mehrfachepisoden als mehrere Episoden mit derselben Datei; `tvshow.nfo` und Episoden-NFOs werden gelesen und beim Bearbeiten geschrieben)
   2. **Ember-Migration II** ✅: Serien, Staffeln, Episoden, Stream-Details (Filme und Episoden), Serien-Einstellungen, API-Keys im Einstellungsspeicher; Embers fehlende Episoden ohne Datei werden nicht übernommen
   3. Scraper und Bilder (Vorgabe TVDB, TMDB-TV wählbar; Episoden bekommen beim Übernehmen nur leere Felder gefüllt; fehlende Episoden werden nur angezeigt; Bilder von TMDB, TVDB und fanart.tv):
      - 3a ✅ Plugin-Schnittstelle `TvShowScraper` (Serie mit Staffeln und Episoden, Attribution je Scraper) und TMDB-TV-Scraper (nur Ausstrahlungsreihenfolge)
      - 3b ✅ TVDB-v4-Plugin (Login mit Key/PIN, Token-Cache, Übersetzungen, offizielle/DVD/absolute Reihenfolge, Attribution); Regie/Drehbuch je Episode liefert TVDB nur über Einzelabfragen und fehlen daher
      - 3c ✅ Online-Suche auf der Seriendetailseite (Vorgabe TVDB): Vorschlag ins Formular, beim Speichern bekommen Episoden leere Felder gefüllt (Titel nur ohne NFO), Darsteller/Bewertungen/Staffeltitel werden ergänzt, NFOs geschrieben; fehlende Episoden auf Knopfdruck; TVDB-Hinweis bei Serien mit TVDB-ID und in der Suche; geladene Serien 15 Minuten zwischengespeichert
      - 3d1 ✅ Bild-Scraper für Serien: Bildkandidaten tragen die Staffelnummer; TMDB (Serie und Staffelposter), TVDB (Artworks inkl. Staffelposter/-banner/-fanart, Sprachen nach ISO 639-1) und fanart.tv (über die TVDB-ID, inkl. Characterart und Staffelbilder)
      - 3d2 Bilder für Serien, Staffeln und Episoden in App und Oberfläche
      - 3e automatisches Scrapen einer Serienquelle
   4. Kodi-Sync für Serien
5. **Komfort:** ffprobe-Streamdetails, Bulk-Rename, Tag-/Filmset-Manager, Filter- und Metadaten-Editor.
6. **Erweiterungen:** Trailer/Themes, Trakt.tv, Export, HTML-Scraper, Installer (jpackage).

## 8. Offene Punkte / Risiken

- **API-Keys:** TMDB, TVDB v4 (kostenpflichtig bzw. Projekt-Key), FanartTV, OMDb.
- **HTML-Scraper** sind rechtlich und technisch fragil.
- **Docker:** Dateirechte (`PUID`/`PGID`) und Pfad-Mapping Container ↔ Kodi (SMB/NFS).
- **Lizenz:** noch offen. Ember steht unter GPL-3.0. Werden Code oder Logik direkt übernommen, muss KMM ebenfalls GPL-3.0 sein.
- **Schriftarten:** Die UI lädt Roboto derzeit von Google Fonts. Für den Offline- bzw. NAS-Betrieb sollte die Schrift selbst gehostet werden.
