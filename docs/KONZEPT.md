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
| Trailer/Themes | TMDB/YouTube/Apple/Davestrailer/Videobuster, TelevisionTunes | Trailer als Link von TMDB für Filme und Serien (umgesetzt), kein Download; Titelmusik als `theme.mp3` von TelevisionTunes und YouTube (umgesetzt) |
| Generische Addons | BulkRename, MediaFileManager, TagManager, FilterEditor, MetadataEditor, MovieExport, VideoSourceMapping | schrittweise |
| Interfaces | Kodi JSON-RPC, Trakt.tv | Kodi: ja. Trakt: nein, das Trakt-Plugin in Kodi gleicht Gesehen-Status, Sammlung und eigene Bewertungen ab; KMM holt den Gesehen-Status aus Kodi. Nur Trakts Community-Bewertung als Bewertungs-Scraper |
| Datei-Output | Kodi-NFO, Artwork nach Kodi-Namenskonvention, extrafanart, Trailer | ja, Kern-Feature |
| Medien-Info | MediaInfo/FFprobe | ja, über `ffprobe` (im Docker-Image enthalten) |

**NFO-Regeln (umgesetzt):**
- Dateiname: `Film.mkv` → `Film.nfo`; Stapel `Film-cd1.avi` → `Film.nfo`; DVD → `VIDEO_TS/VIDEO_TS.nfo`; Blu-ray → `BDMV/index.nfo`. Eine vorhandene `movie.nfo` im Filmordner wird gelesen und an Ort und Stelle aktualisiert.
- KMM verwaltet Titel, Original-/Sortiertitel, Jahr, Erstaufführung, Handlung, Kurzinhalt, Tagline, Laufzeit, Altersfreigabe, Genres, IMDb-/TMDB-IDs sowie – wie Ember, für Kodi ohne Bedeutung – Fassung (`<edition>`) und Videoquelle (`<videosource>`, auch bei Episoden) (bearbeitbar).
- Außerdem liest und schreibt KMM die weiteren Angaben (derzeit nur angezeigt): Darsteller mit Rolle, Reihenfolge und Bild, Regie (`director`), Drehbuch (`credits`), Studios, Länder, Tags, Bewertungen (`ratings`), Filmset (`set` mit `name`/`overview`, Sets sind über den Namen eindeutig), eigene Bewertung, Wiedergaben, zuletzt gesehen und Hinzugefügt-Datum (`dateadded`, lokale Zeit).
- Den Trailer-Link (`<trailer>` in Film-NFO und `tvshow.nfo`) liest und schreibt KMM ebenfalls; für Filme und Serien aus älteren Versionen liest es ihn beim Start aus der NFO nach.
- Alle anderen Elemente (`fileinfo`, Bild-URLs, IDs weiterer Anbieter …) bleiben beim Schreiben unverändert. Sind die weiteren Angaben eines Films noch nicht eingelesen (z. B. vor dem ersten Scan nach einem Update), lässt KMM auch diese Elemente unverändert.
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
├─ kmm-media                   Stream-Details mit ffprobe (Kodi-/Ember-Namen, Stapel, DVD-Titel, Blu-ray,
│                              externe Untertitel, HDR) und Zusammenführen mit vorhandenen Angaben
├─ kmm-plugins/                mitgelieferte Plugins: kmm-plugin-tmdb (Metadaten + Bilder), kmm-plugin-fanarttv (Bilder);
│                              kmm-plugin-testdata nur für Tests
├─ kmm-ui                      Angular-SPA → static/ im JAR
├─ kmm-app                     Spring Boot: REST, Security, Plugins, Persistenz, Profile
└─ docker/                     Dockerfile, docker-compose.yml
geplant: kmm-core; Scraper-Plugins omdb
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
- **Stream-Details** für Filme und Episoden, mit Dateigröße, 3D-Angaben und externen Untertiteln (Pfad neben der Videodatei im Zielsystem). Liefert Ember keine Spuren, bleiben die in KMM bekannten erhalten.
- **Einstellungen:** Sprache der Serien-Metadaten und -Bilder, die Episodenreihenfolge und ob beim Scrapen die Mediendaten ausgelesen werden (`MovieScraperMetaDataScan`/`TVScraperMetaDataScan`). API-Keys werden bewusst nicht übernommen; sie werden in KMM unter „Einstellungen → API-Keys“ gepflegt.
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
3. **Kodi-Integration** ✅: Kodi-Geräte mit Pfad-Zuordnung und Verbindungstest, Echtzeit-Sync wie in Ember (RefreshMovie bzw. Scan des Ordners, entprellt im Hintergrund), manueller Abgleich je Film und Quelle, Übernahme der Kodi-Geräte aus Ember. Gesehen-Status aus Kodi holen ✅ (je Quelle als Job oder je Film/Episode; Wiedergaben, letzte Wiedergabe und eigene Bewertung gehen in NFO und Datenbank, damit Kodi die Bewertung beim Neueinlesen nicht verliert; Kodi ergänzt nur mehr Wiedergaben, neuere Daten bzw. eine Bewertung und setzt nichts zurück; ein Gerät genügt, weil alle Geräte dieselbe Kodi-Datenbank nutzen). Später: Bibliothek bereinigen (`VideoLibrary.Clean`).
4. **Serien** (in Inkrementen):
   1. Modell, Scan, NFOs, Liste und Detailseiten ✅ (jeder Ordner unter der Quelle ist eine Serie, Episoden beliebig tief; Staffel/Episode aus dem Dateinamen; Mehrfachepisoden als mehrere Episoden mit derselben Datei; `tvshow.nfo` und Episoden-NFOs werden gelesen und beim Bearbeiten geschrieben)
   2. **Ember-Migration II** ✅: Serien, Staffeln, Episoden, Stream-Details (Filme und Episoden), Serien-Einstellungen, API-Keys im Einstellungsspeicher; Embers fehlende Episoden ohne Datei werden nicht übernommen
   3. Scraper und Bilder (Vorgabe TVDB, TMDB-TV wählbar; Episoden bekommen beim Übernehmen nur leere Felder gefüllt; fehlende Episoden werden nur angezeigt; Bilder von TMDB, TVDB und fanart.tv):
      - 3a ✅ Plugin-Schnittstelle `TvShowScraper` (Serie mit Staffeln und Episoden, Attribution je Scraper) und TMDB-TV-Scraper (nur Ausstrahlungsreihenfolge)
      - 3b ✅ TVDB-v4-Plugin (Login mit Key/PIN, Token-Cache, Übersetzungen, offizielle/DVD/absolute Reihenfolge, Attribution); Regie, Drehbuch und Gaststars liefert TVDB nur je Episode – KMM fragt sie nur für Episoden ab, denen Regie oder Drehbuch fehlen (Zwischenspeicher einen Tag)
      - 3c ✅ Online-Suche auf der Seriendetailseite (Vorgabe TVDB): Vorschlag ins Formular, beim Speichern bekommen Episoden leere Felder gefüllt (Titel nur ohne NFO), Darsteller/Bewertungen/Staffeltitel werden ergänzt, NFOs geschrieben; fehlende Episoden auf Knopfdruck; TVDB-Hinweis bei Serien mit TVDB-ID und in der Suche; geladene Serien 15 Minuten zwischengespeichert
      - 3d1 ✅ Bild-Scraper für Serien: Bildkandidaten tragen die Staffelnummer; TMDB (Serie und Staffelposter), TVDB (Artworks inkl. Staffelposter/-banner/-fanart, Sprachen nach ISO 639-1) und fanart.tv (über die TVDB-ID, inkl. Characterart und Staffelbilder)
      - 3d2 ✅ Bilder in App und Oberfläche: Serie (Poster, Fanart, Banner, Clearlogo, Clearart, Landscape, Characterart → `poster.jpg` … im Serienordner), Staffeln (Poster, Banner, Fanart, Landscape → `season01-poster.jpg`, `season-specials-poster.jpg`) und Episoden (Standbild der Serien-Scraper → `<episode>-thumb.jpg`); gemeinsame Bildergalerie und Dateiablage für Filme und Serien
      - 3e ✅ automatisches Scrapen einer Serienquelle: nicht zugeordnete Serien nur bei eindeutigem Treffer des bevorzugten Scrapers (`kmm.scraping.preferred-tv-show-scraper`, Vorgabe `tvdb`), leere Episodenfelder, fehlende Bilder von Serie, Staffeln mit Episoden und Standbilder; vorhandene Daten und Bilder bleiben
   4. Kodi-Sync für Serien ✅: Echtzeit-Sync wie bei Filmen – geänderte Serie → `VideoLibrary.RefreshTVShow` (ohne Episoden), geänderte Episode → `RefreshEpisode`, in Kodi unbekannt → `Scan` des Serienordners, neue Serien/Episoden nach dem Scan → `Scan` der Quelle; Knöpfe „In Kodi aktualisieren“ für Serie und Episode, „Kodi-Scan“ für Serienquellen
5. **Komfort** (in Inkrementen):
   1. Technische Angaben mit ffprobe ✅: Video (Codec, Auflösung, Seitenverhältnis, Laufzeit, Bitrate, Bildaufbau, 3D, HDR-Typ), Ton und Untertitel (eingebettet und als Dateien daneben, etwa `Film.de.srt` oder `Film-eng-forced.idx`) in Kodis bzw. Embers Namen (`dca`, `dtshd_ma`, `deu` …). Auslöser: je Film/Episode, je Quelle als Job (nur fehlende oder alle), nach dem automatischen Scrapen und nach dem Speichern einer Online-Suche (Filme; bei Serien Episoden ohne Angaben) und für neue Dateien ohne Angaben nach dem Scan – beides abschaltbar, ffprobe-Pfad einstellbar. Vorhandene Angaben werden ersetzt, Sprachen und Embers 3D-Angaben bleiben, wenn ffprobe keine liefert. In der NFO wird nur `<fileinfo>` im Format von Ember ersetzt (alphabetisch, mit `longlanguage`, `filesize`, Untertiteltyp); der Rest der Datei bleibt Zeichen für Zeichen erhalten. Mehrfachepisoden bekommen die Angaben in jedes `<episodedetails>`.
   2. Umbenennen wie Embers „Bulk Rename“, Filme und Serien ✅: Muster wie `{title}[ ({year})]` mit Formaten (`{season:2}`, `:first`, `:sort`, `:lower`, `:upper`), optionalen Teilen in `[ ]` und Unterordnern (`/`, relativ zur Quelle); Vorschau je Quelle mit Auswahl, dann Job, oder einzeln aus der Filmansicht. Umbenannt werden der Ordner (nur wenn er allein dem Film gehört), Video, Stapelteile und alle Begleitdateien mit dem Namen des Films; Discs nur den Ordner. Konflikte werden übersprungen, Fehler zurückgerollt, leere alte Ordner entfernt; Pfade in Datenbank sowie Untertitelpfade in Datenbank und NFO werden nachgezogen. Muster und Auslöser stehen in den Einstellungen; der Ember-Import übersetzt Embers Muster aus `AdvancedSettings.xml`. Automatisch (abschaltbar, anfangs aus) nach dem Speichern der Metadaten und nach dem automatischen Scrapen. Mit Echtzeit-Sync liest Kodi danach den neuen Ordner ein und bereinigt nur den alten (`VideoLibrary.Clean` mit Verzeichnis); Gesehen-Status und Bewertung kommen aus der NFO zurück. Serien: Episodendateien wandern mit NFO, Vorschaubild und Untertiteln in Staffelordner nach Muster (`Staffel {season}`), Mehrfachepisoden heißen `S01E01E02` mit allen Titeln, danach wird der Serienordner umbenannt; Staffelbilder bleiben nach Kodi-Konvention im Serienordner. Konflikte gelten beim Serienordner für die ganze Serie, sonst je Episodendatei. Automatisch (abschaltbar) nach dem Speichern einer Serie oder Episode, nach dem automatischen Scrapen und für Serien mit neuen Episoden nach dem Scan; Kodi-Abgleich wie bei Filmen.
   3. Tag-Manager ✅ wie in Ember (Filme und Serien): Tags mit Anzahl, umbenennen (gleichnamige werden zusammengeführt), löschen, zuweisen (auch neue Tags) und entfernen über eine Suche; Änderungen schreiben die NFOs der betroffenen Einträge als Hintergrund-Job, Kodi liest sie per Echtzeit-Sync neu ein.
   4. Filmset-Manager, Teil 1 ✅: Sets mit Anzahl der Filme, anlegen (Name eindeutig), Name/Beschreibung/TMDB-Sammlung/Reihenfolge (Jahr oder Titel wie Ember) bearbeiten, Filme hinzufügen (sie verlassen ihr bisheriges Set) und entfernen, löschen; Film-NFOs werden als Job neu geschrieben. Mit Echtzeit-Sync übernimmt Kodi Titel und Beschreibung per `SetMovieSetDetails` (Set über den bisherigen Titel oder einen enthaltenen Film gefunden). Der Ember-Import übernimmt alle Sets samt leeren und Sortierung.
   5. Filmset-Manager, Set-Bilder und Set-Scraping ✅: Einstellungen „Filmsets“ mit zwei einzeln abschaltbaren Ablagen – Embers flaches Schema `<Ordner>/<Settitel>-poster.jpg` (Dateiname wie Ember: unzulässige Zeichen → `-`) und Kodis „Ordner für Filmset-Informationen“ `<Ordner>/<Settitel>/poster.jpg` (Ordnername wie Kodis `MakeLegalFileName`); der Ember-Import übernimmt `MovieSetUseExtended`/`MovieSetPathExtended`, `MovieSetUseMatrix`/`MovieSetPathMatrix` und `MovieScraperCollectionsAuto`. Bildarten Poster, Fanart, Banner, Clearlogo, Clearart, Disc, Keyart, Landscape: anzeigen, bei TMDB und fanart.tv über die TMDB-ID der Sammlung suchen, in alle eingeschalteten Ablagen speichern, entfernen; beim Umbenennen eines Sets wandern die Bilder mit. Set-Scraping im Set-Manager: TMDB-Sammlungen suchen, ein Treffer übernimmt Titel, Beschreibung (englischer Rückfall) und ID ins Formular. Job „Alle Sets ergänzen“: fehlende Beschreibungen und Bilder für Sets mit TMDB-ID, vorhandene bleiben. „Filme beim Scrapen ihrem Filmset zuordnen“ (automatisch und nach dem Speichern eines Online-Vorschlags): Set über TMDB-ID, sonst Name, sonst neu; ein anderes Set wird ersetzt. Kodi bekommt per `SetMovieSetDetails` auch die Bilder (fehlende werden entfernt, Bild-Cache wird geleert); ohne Ablage bleiben Kodis Set-Bilder unberührt.
   6. Filter- und Metadaten-Editor ✅: Film- und Serienliste mit Filter-Baukasten statt Embers freiem SQL – Bedingungen aus Feld, Vergleich und Wert (Auswahl aus vorhandenen Werten), „alle“ oder „eine“; Serienfilter auch über Episoden (ungesehen, Auflösung, Codecs, Sprachen, fehlende technische Angaben). Mitgelieferte und gespeicherte Filter (Tabelle `saved_filter`); der Ember-Import übernimmt eigene Filmlisten (Views `movie-*`), soweit übersetzbar. Technische Angaben haben eine abfragbare Kurzform (Auflösungsstufe wie Kodi, Codecs, HDR, Sprachen). Statt Embers Codec-Tabelle übersetzt KMM Codec-Namen fest in Kodis Namen (MediaInfo-Rohnamen, Embers Standardzuordnungen). Stapel-Bearbeitung gefilterter Filme (Genres, Tags, Altersfreigabe, Fassung, Videoquelle) und Serien (Genres, Tags, Altersfreigabe, Status) als Job mit NFO-Schutz, ohne Umbenennen.
6. **Erweiterungen:**
   1. Trailer ✅ (Filme und Serien): nur als Link in der NFO, den Kodi über das YouTube-Addon abspielt (`plugin://plugin.video.youtube/?action=play_video&videoid=…`), kein Download. Neuer Plugin-Typ `TrailerScraper`; TMDB liefert die YouTube-Videos des Films in bevorzugter Sprache, Englisch und ohne Sprache. Reihenfolge: bevorzugte Sprache, Englisch, Trailer vor Teaser, offizielle, höhere Auflösung, neuere. Filmansicht: Vorschläge mit Vorschaubild ansehen und übernehmen, eigenen Link eintragen (`plugin://`, `http(s)://`, `smb://`, `nfs://`), entfernen. Das automatische Scrapen trägt bei Filmen ohne Trailer den besten Vorschlag ein; Job „Fehlende Trailer holen“ für die Auswahl der Filmliste; Filterfeld „Trailer“. Vorhandene Links bleiben; der Ember-Import übernimmt Embers Trailer-Spalte. Serien genauso: Trailer-Karte in der Serienansicht, automatisches Scrapen, Job „Fehlende Trailer holen“ in der Serienliste, Filterfeld „Trailer“; TMDB findet die Serie auch über TVDB- oder IMDb-ID (`/tv/{id}/videos`). Ember kennt keine Serien-Trailer. Suche und Reihenfolge teilen sich Filme und Serien (`TrailerFinder`). Beim Ändern des Trailers bearbeitet KMM in einer vorhandenen NFO nur die Zeile `<trailer>` als Text (`TrailerNfoWriter`); alles andere bleibt Zeichen für Zeichen. Serien aus dem Ember-Import tragen Embers Datenbankwerte (z. B. `FSK 16` statt `Germany:16`); vor dem ersten Schreiben ihrer `tvshow.nfo` liest KMM deshalb die NFO ein (`nfo_read`). Für Episoden aus dem Ember-Import gilt dasselbe vor dem ersten Neuschreiben ihrer NFO (Metadaten speichern, Episoden ergänzen); hat die NFO keine technischen Angaben, bleiben die aus Ember erhalten. Darsteller, die nur in Embers Daten stehen (etwa Gaststars), hängt KMM beim ersten Einlesen hinter die der NFO an; bei Serien ebenso. Damit Film-, Serien- und Episodenansicht keine veralteten Werte zeigen und beim Speichern zurückschreiben, liest KMM beim Öffnen eine außerhalb geänderte NFO ein, bei Serien und Episoden aus Ember auch eine noch nicht eingelesene. Beim Neuschreiben von NFOs bleiben Angaben an Darstellern erhalten, die KMM nicht kennt (z. B. Embers `<tmdbid>`). Titelmusik (TvTunes) vorerst nicht.
   2. Titelmusik ✅ (Filme und Serien): `theme.mp3` im Film- bzw. Serienordner, wie TvTunes es erwartet; Kodi spielt sie nur mit einem Add-on (TvTunes, Themerr) ab. Neuer Plugin-Typ `ThemeScraper` (Suche über den Titel, Laden als MP3). `kmm-plugin-televisiontunes` liest Suche und Serienseiten von TelevisionTunes.com (kein API, nur HTTP) und lädt nur MP3s der Seite selbst. `kmm-plugin-youtube` sucht und lädt über yt-dlp (Audio per ffmpeg als MP3; im Docker-Image enthalten, sonst `kmm.plugins.settings.youtube.yt-dlp`). Gesucht wird mit Titel und Originaltitel. Ansicht: Vorschläge anhören und übernehmen, vorhandene `theme.mp3` anhören und löschen. Job „Fehlende Titelmusik holen“ für die Auswahl der Listen und das automatische Scrapen übernehmen nur sichere Treffer: gleicher Titel beim Anbieter (`Titles.same`); YouTube-Videos gelten nie als sicher. Geladen wird über eine temporäre Datei; eine vorhandene `theme.mp3` bleibt beim Job und beim automatischen Scrapen unberührt.
   3. Trakt.tv: keine eigene Anbindung (Gesehen-Status, Sammlung, eigene Bewertungen), weil das Trakt-Plugin in Kodi sie mit Kodi abgleicht und KMM den Gesehen-Status aus Kodi holt. Embers Trakt-Anmeldung gehört zu Embers App und wird nicht übernommen. Übernommen wird nur Trakts öffentliche Community-Bewertung als Bewertung `trakt`.
   4. Ergänzungs-Scraper ✅: neuer Plugin-Typ `EnrichmentScraper` liefert in einem Abruf Bewertungen und, falls der Anbieter es kann, Angaben für leere Felder (über bekannte IDs). `kmm-plugin-trakt`: Trakts Community-Bewertung (`trakt`) über IMDb-, TMDB- oder TVDB-ID, mit der Client-ID einer eigenen Trakt-App, ohne Anmeldung. `kmm-plugin-omdb`: über die IMDb-ID die Bewertungen IMDb (`imdb`, mit Stimmen), Rotten Tomatoes (`tomatometerallcritics`, 0–100) und Metacritic (`metacritic`, 0–100) sowie englische Angaben für leere Felder: Handlung, Laufzeit, Erstaufführung, Freigabe, Genres, Regie, Drehbuch, Darsteller (ohne Rollen) und Länder; Serien ohne Regie, Drehbuch und Länder. Der kostenlose OMDb-Key erlaubt 1.000 Abfragen am Tag, deshalb ein Abruf je Eintrag und nur, wenn der Anbieter etwas beitragen kann. Das automatische Scrapen ergänzt nur Fehlendes; der Job „Bewertungen aktualisieren“ der Film- und Serienliste aktualisiert auch vorhandene Bewertungen (die Standard-Markierung bleibt). Vorhandene Felder bleiben immer. Episoden werden nicht ergänzt (eine Anfrage je Episode).
   5. Offen: Export, HTML-Scraper, Installer (jpackage).

## 8. Offene Punkte / Risiken

- **API-Keys:** TMDB, TVDB v4 (kostenpflichtig bzw. Projekt-Key), FanartTV, OMDb.
- **HTML-Scraper** sind rechtlich und technisch fragil.
- **Docker:** Dateirechte (`PUID`/`PGID`) und Pfad-Mapping Container ↔ Kodi (SMB/NFS).
- **Lizenz:** noch offen. Ember steht unter GPL-3.0. Werden Code oder Logik direkt übernommen, muss KMM ebenfalls GPL-3.0 sein.
- **Schriftarten** ✅: Roboto kommt als Paket `@fontsource/roboto` (SIL Open Font License) mit; die UI lädt nichts mehr von Google Fonts und funktioniert offline.
