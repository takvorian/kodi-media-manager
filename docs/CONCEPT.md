# Concept: Kodi Media Manager (KMM)

Java port of [Ember Media Manager](https://github.com/DanCooper/Ember-MM-Newscraper) (VB.NET/WinForms, no longer maintained).

| | Decision |
|---|---|
| Language / framework | Java 25 (LTS), Spring Boot 4.1 (Spring Framework 7, Hibernate 7, Jakarta EE 11) |
| Operation | Docker container (MariaDB) **and** standalone (H2 or any database via JPA/Flyway) |
| Backend | **B2** – modular monolith (Spring Modulith) + external scraper plugins via PF4J |
| Frontend | **Angular SPA** with REST API, bundled into the JAR |
| Migration | Existing Ember data (database + settings) is taken over |
| Base package | `de.tiktak.kmm` |

What is rebuilt is the **functionality**; the code is not translated 1:1. The WinForms UI and VB-specific parts cannot be ported sensibly.

---

## 1. Scope of the original

| Area | Ember components | Adoption |
|---|---|---|
| Core API | `EmberAPI/clsAPIScanner, clsAPINFO, clsAPIDatabase, clsAPIImages, clsAPIMediaInfo, clsAPIFFmpeg, clsAPITaskManager, clsAPISettings` | yes, rebuilt in Java |
| Media types | Movies, movie sets, TV shows/seasons/episodes | MVP: movies, then TV |
| Data scrapers (sequential, fill empty fields) | TMDB, IMDB, OMDb, TVDB, Trakt, OFDB, MoviepilotDE, XML | TMDB, TVDB, OMDb, Trakt; Moviepilot and OFDb as HTML scrapers. IMDB is dropped (roadmap, extensions) |
| Image scrapers (parallel) | TMDB Poster, FanartTV, TVDB Poster | yes |
| Trailers/themes | TMDB/YouTube/Apple/Davestrailer/Videobuster, TelevisionTunes | Trailers as a link from TMDB for movies and TV shows (implemented), no download; theme music as `theme.mp3` from TelevisionTunes and YouTube (implemented) |
| Generic addons | BulkRename, MediaFileManager, TagManager, FilterEditor, MetadataEditor, MovieExport, VideoSourceMapping | step by step |
| Interfaces | Kodi JSON-RPC, Trakt.tv | Kodi: yes. Trakt: no, the Trakt plugin in Kodi syncs watched status, collection and personal ratings; KMM fetches the watched status from Kodi. Only Trakt’s community rating as a rating scraper |
| File output | Kodi NFO, artwork following Kodi’s naming convention, extrafanart, trailers | yes, core feature |
| Media info | MediaInfo/FFprobe | yes, via `ffprobe` (included in the Docker image) |

**NFO rules (implemented):**
- File name: `Film.mkv` → `Film.nfo`; stack `Film-cd1.avi` → `Film.nfo`; DVD → `VIDEO_TS/VIDEO_TS.nfo`; Blu-ray → `BDMV/index.nfo`. An existing `movie.nfo` in the movie folder is read and updated in place.
- KMM manages title, original/sort title, year, premiere date, plot, outline, tagline, runtime, certification, genres, IMDb/TMDB IDs and – like Ember, meaningless to Kodi – edition (`<edition>`) and video source (`<videosource>`, also for episodes) (editable).
- KMM also reads and writes the further details (currently display only): cast with role, order and image, director (`director`), writer (`credits`), studios, countries, tags, ratings (`ratings`), movie set (`set` with `name`/`overview`; sets are unique by name), user rating, play count, last played and date added (`dateadded`, local time).
- KMM also reads and writes the trailer link (`<trailer>` in movie NFOs and `tvshow.nfo`); for movies and TV shows from older versions it reads it from the NFO at startup.
- All other elements (`fileinfo`, image URLs, IDs of other providers …) stay unchanged when writing. If the further details of a movie have not been read yet (e.g. before the first scan after an update), KMM leaves these elements unchanged as well.
- When saving, the NFO is written first (atomically via a temporary file); if that fails, the database stays unchanged too.
- A repeated scan only reads an NFO if its modification time has changed. Unreadable NFOs are counted and reported; the movie is kept.

**Image rules (implemented):**
- Managed artwork types: poster, fanart, clearlogo, clearart, disc, banner, landscape.
- File names: `Film-poster.jpg`, `Film-clearlogo.png` …; for DVD/Blu-ray folders `poster.jpg` … in the movie folder. A new image replaces variants with a different extension.
- The image search queries all image scrapers in parallel via TMDB or IMDb ID; failures of individual providers appear as notes. Sort order: preferred language (default `de`), then language-neutral, English, rest; for fanart, language-neutral (textless) images first; within the groups by popularity.
- Download only via http(s), only JPEG/PNG, at most 25 MB, stored atomically.

**Automatic scraping (implemented):**
- Runs per movie source as a background job (`auto-scrape:<source>`) and only fills **missing** data; curated data stays untouched.
- Metadata only for movies without a TMDB ID, via the preferred scraper (`kmm.scraping.preferred-metadata-scraper`, default `tmdb`). Only a reliable match is applied (known IMDb ID or exactly matching title/original title with year); otherwise the movie ends up in the “no match” list for manual search.
- Then all seven artwork types, as far as they are missing: one candidate search per movie, the best candidate per type (sorted as in the image search). If a download fails, up to three candidates are tried.
- Errors for individual movies do not abort the run. The result contains counters, titles without a match, errors and notes (e.g. providers not set up).

**Settings (implemented):**
- User settings live in the `app_setting` table (key/value): metadata language (passed to plugins as the shared setting `language`), preferred artwork language, video file extensions, excluded folders, proxy.
- The configuration takes effect first (`kmm.settings.*` or environment variables, locked in the UI), then the stored value, then the default.
- Changes take effect without a restart: PF4J creates extensions anew for every request (with the current plugin context), the HTTP client chooses the proxy per connection, the scanner is created per scan.

**Core principle:** the truth lives in the file system (NFO + artwork next to the media file). The database is index, cache and working state and can be rebuilt by rescanning. That is why the choice of database is not critical.

---

## 2. Backend foundation

- **Virtual threads** for parallel scraper calls and file I/O. Hence Spring MVC, not WebFlux.
- **Persistence:** Spring Data JPA + Hibernate 7, schema via **Flyway** with vendor-specific folders (`db/migration/{vendor}`: `h2`, `mariadb`, `postgresql`). `ddl-auto=validate`.
- **Profiles:**
  - `standalone` (default): H2 in file mode under `~/.kodi-media-manager`, bound to `127.0.0.1` only, without login
  - `docker`: MariaDB 11, login via HTTP Basic, configuration via environment variables
  - any other database via `spring.datasource.*`
- **HTTP clients:** Spring `RestClient` / `@HttpExchange`, Resilience4j (rate limits, retry), Caffeine cache.
- **Building blocks:**
  - NFO: Jackson XML
  - Images: ImageIO/TwelveMonkeys
  - Scanner: `java.nio.file`
  - Jobs: custom job management on virtual threads, progress and result via REST (`/api/v1/jobs`) and SSE (`/api/v1/jobs/events`), throttled to at most one message per 250 ms and job, one job per business key (e.g. one scan per source). Running jobs and the last 50 finished ones are kept in memory.
- **Kodi:** JSON-RPC over HTTP (`kmm-kodi`, without proxy): `VideoLibrary.GetMovies` (search by directory and file, also stacks), `RefreshMovie`, `Scan`; path mapping per device (container/KMM ↔ Kodi). Real-time sync via events (`MovieFilesChangedEvent`, `MovieSourceScannedEvent`) after commit, delayed and debounced per movie on a dedicated virtual thread.
- **Security:** Spring Security, CSRF for the SPA (cookie `KMM-XSRF-TOKEN`, a custom name because cookies apply to all ports of an address).
- **Operations:** Actuator (health/probes), OpenAPI (springdoc, `/swagger-ui.html`).

### Deployment
- **Docker:** multi-stage build (Temurin 25 JDK → JRE + ffmpeg), layered JAR, `docker/docker-compose.yml` with app + MariaDB. Published on Docker Hub as `takvorian/kodi-media-manager` (only `linux/amd64`), tags `<version>` and `latest`; `release.sh` increments `v0.x` from the Git tags, generates the changelog from the commit subjects and builds via `build-push.sh` from a worktree of the tag. It aborts if CI is not green for the commit, because the image build skips the tests. The app shows version and changelog in the header and on the System page (`ReleaseInfoService`). Volumes: `/config`, `/media`. Permissions via `PUID`/`PGID`: the container starts as root, hands `/config` over to this user and switches to it via `setpriv` (`docker/entrypoint.sh`), because Docker creates a missing directory on the host as root. `/media` stays untouched.
- **Standalone:** fat JAR `java -jar kmm-app.jar` or Windows installer (MSI via `jpackage`, Maven profile `installer`, includes Java).
- **Backup and moving:** package `backup`, “Backup” page. Gzip JSON of all tables, generic via JDBC and therefore interchangeable between H2, MariaDB and PostgreSQL; `BackupTables` knows the order, foreign keys and keys, and a test checks completeness against the schema. The import replaces or merges, rewrites paths including hashes, runs as an exclusive job in one transaction and saves the previous state beforehand. Timestamps must be preserved exactly, otherwise the next scan reads all NFOs again.

---

## 3. Backend variants (basis for the decision)

| | B1: Modular monolith | **B2: Monolith + PF4J plugins (chosen)** | B3: Microservices |
|---|---|---|---|
| Description | one deployable, business modules with verified boundaries | like B1, scrapers are loaded as JARs at runtime | separate services + broker |
| + | easy to operate, one artifact | scrapers replaceable and addable without a rebuild (like Ember’s addons) | independent scaling |
| − | plugins only via rebuild | class loader complexity | oversized for a single-user app, hardly feasible standalone |

### Plugin architecture (B2)
- `kmm-plugin-api` is the **only** dependency for plugins: extension points `MetadataScraper` and `ImageScraper` (extending `org.pf4j.ExtensionPoint`) plus value objects. No Spring dependency.
- `kmm-app` integrates PF4J directly, not via the outdated `pf4j-spring`:
  - `PluginManagerConfiguration` and `PluginLifecycle`: loading and unloading are tied to the Spring lifecycle
  - `ScraperRegistry`: shields the rest of the application from PF4J
  - `PluginService` and `PluginController`: `GET /api/v1/plugins`, `POST /api/v1/plugins/{id}/enable|disable`. The state survives restarts.
- Plugins do not see the Spring context. The app passes services in through the narrow `PluginContext` interface: settings (`kmm.plugins.settings.<plugin-id>.<name>`), shared `HttpClient`, user agent. A custom `ExtensionFactory` calls `Scraper.initialize(context)` for this.
- Runtime platform for plugins: the app provides the plugin API, PF4J, SLF4J and Jackson 3 (`provided` in the plugin); plugins must bring any further libraries themselves.
- Bundled plugins (currently TMDB) are stored as files under `bundled-plugins/` in the JAR and are copied or updated into the plugin directory at startup (`kmm.plugins.install-bundled`).
- Bundled plugins live under `kmm-plugins/`. Plugin directory: `/config/plugins` (Docker) or `~/.kodi-media-manager/plugins` (standalone).

---

## 4. Frontend variants (basis for the decision)

| Criterion | Thymeleaf + htmx | Vaadin Flow | **SPA + REST (chosen: Angular)** | JavaFX | Hybrid (web UI + desktop shell) |
|---|---|---|---|---|---|
| Suitable for Docker | ++ | ++ | ++ | −− | ++ |
| Suitable for standalone | ++ | ++ | ++ | ++ | ++ |
| Initial effort | low | low–medium | high | medium | +packaging |
| Complex UI (image selection, grids) | o | + | ++ | + | as base |
| Tech stacks | 1 | 1 | 2 | 1 | 2–3 |
| Open API (Kodi addon, mobile, scripts) | − | − | ++ | − | as base |
| Maintainability / future-proofing | + | o (lock-in) | ++ | o | as base |

**Rationale:** Ember’s core UX (filtered media lists, image selection across several scrapers, bulk operations, live progress) benefits most from an SPA. The REST API is an added value compared to the original. In standalone mode, the system browser will open later (optionally with a tray icon).

---

## 5. Module structure

```
kodi-media-manager/            groupId de.tiktak.kmm
├─ kmm-plugin-api              plugin interface (PF4J extension points)
├─ kmm-scanner                 finding movies and TV shows: stacks (cd1/cd2), DVD/Blu-ray folders, title/year from names,
│                              season/episode following Kodi's rules (S01E02, 1x02, …-102, multi-episode files),
│                              Kodi names for companion files (NFO, later artwork)
├─ kmm-nfo                     reading/writing Kodi NFOs for movies, TV shows and episodes (DOM, unknown elements
│                              are preserved, XXE-safe, several <episodedetails> per file)
├─ kmm-migration-ember         reading Ember profiles (SQLite, Settings.xml, Interface.Kodi.xml), path mapping
├─ kmm-kodi                    JSON-RPC client for Kodi (version, find movie, RefreshMovie, Scan),
│                              mapping KMM path → Kodi path (smb://, nfs://, Windows, POSIX)
├─ kmm-media                   stream details with ffprobe (Kodi/Ember names, stacks, DVD titles, Blu-ray,
│                              external subtitles, HDR) and merging with existing details
├─ kmm-plugins/                bundled plugins: kmm-plugin-tmdb (metadata + artwork), kmm-plugin-fanarttv (artwork);
│                              kmm-plugin-testdata for tests only
├─ kmm-ui                      Angular SPA → static/ in the JAR
├─ kmm-app                     Spring Boot: REST, security, plugins, persistence, profiles
└─ docker/                     Dockerfile, docker-compose.yml
planned: kmm-core; scraper plugins omdb
```

Modules are only created once they get content. Spring Modulith checks the boundaries within `kmm-app` (`ModularityTests`).

---

## 6. Ember migration (database + settings)

**Source** (analyzed from the Ember code):
- Profile folder `<Ember>\Profiles\Default\`.
- **SQLite** database `MyVideos48.emm` (schema version 48). Ember patches older versions (`MyVideos2..47.emm`) itself when opening them.
- Relevant tables:
  - Media: `movie`, `sets`/`setlinkmovie`, `tvshow`, `seasons`, `episode`, `files`
  - Sources: `moviesource`, `tvshowsource`
  - People and links: `actors` + `*link*`
  - Classification: `genre`/`country`/`studio`/`tag`
  - Other: `rating`, `uniqueid`, `art`, stream tables
- `Settings.xml`: .NET `XmlSerializer`, `<Settings version="1">`. Lists are serialized as `<Name><string>…</string></Name>`.

**Implementation** (`kmm-migration-ember`, framework-free, wired up in `kmm-app`):
- `EmberProfileDetector`: finds the profile (also via the installation directory), checks the schema version and produces understandable error messages.
- `EmberDatabaseInspector`: opens the database **read-only** (the Ember profile stays unchanged), checks the tables and counts the library.
- `EmberSettingsReader`: reads `Settings.xml` into a partial model. Unknown elements are ignored. The proxy password is deliberately not taken over.
- `PathMapper`: translates Windows and UNC paths (`D:\Filme`, `\\nas\serien`) into target paths (`/media/filme`) by prefix rule (longest prefix wins).
- `EmberLibraryReader`: reads sources, movies with all links (IDs, genres, cast with role and image, director, writer, studios, countries, tags, ratings, sets with order, user data) and excluded folders, with one query per table (1169 movies in under a second).
- `EmberTvLibraryReader`: reads TV show sources, TV shows (IDs, genres, studios, tags, cast, ratings, season titles), episodes with a file (path via `files`; cast and guest stars, director, writer, ratings, user data) and counts Ember’s episodes without a file. Stream details for movies and episodes come from the tables `MoviesVStreams`/`TVVStreams` etc.
- `POST /api/v1/migrations/ember/upload`: Ember usually runs on a different computer than KMM (PC and NAS), so the browser chooses the Ember folder and uploads only the newest `MyVideosNN.emm`, `Settings.xml`, `AdvancedSettings.xml` and `Interface.Kodi.xml`. `EmberProfileUpload` keeps them under `${kmm.home}/ember-upload` and only allows these names; a new profile replaces the old one. They contain credentials and are deleted: after the real import (also on error or cancellation), for an unusable profile, on request (`DELETE …/upload`) and at the next start. The dry run keeps them for the import that follows. The response is the analysis without import (version, size, movie and TV show sources, settings).

**Import (Ember migration I, implemented):**
- `POST /api/v1/migrations/ember/import` starts a background job (`ember-import`) with live progress; wizard in the UI: upload profile → map paths → dry run → import → report.
- **Ember DB wins, files stay:** metadata and further details come from the Ember database; NFOs and artwork are not changed. After the import the NFO counts as read, so that the next scan does not overwrite the Ember data with the file content.
- **Sources:** Ember’s movie sources are taken over via the path mapping (unchanged without rules, e.g. with KMM on the same computer). The directory does not have to be reachable (note in the report); existing sources with the same directory are reused.
- **Idempotent:** movies are recognized by file path, sets by name; importing again updates instead of duplicating. Kodi only knows one set per movie; the first one is taken (note in the report).
- **Dry run:** runs the import completely in one transaction and rolls it back; settings are only computed. So the report shows exactly what the import would change.
- **Conversions:** `Germany:16` → `FSK 16`; empty ratings (0 without votes) and user rating 0 are dropped; Ember’s timestamps (seconds since 1970, but computed from local time) become date added and “last played” in KMM’s time zone; paths that are invalid on the target system are skipped and reported.
- **Settings:** language of movie metadata, forced artwork language, video file extensions, excluded folders (via the path mapping) and proxy (without password) go into KMM’s settings store; values fixed by the configuration stay unchanged.
- **Kodi devices** (`Interface.Kodi.xml`): name, address, port, credentials, real-time sync and path mappings; Ember’s local paths go through the import’s path mapping, mappings without a matching rule are dropped with a note. Devices are recognized by name.

**Import (Ember migration II, implemented):**
- **TV shows** (can be deselected): TV show sources via the same path mapping, TV shows recognized by folder, episodes by file, season and number; multi-episode files stay several episodes with the same file. Ember’s placeholder season −1 and empty season titles are dropped; episodes without a file are only counted. As with movies, the Ember DB wins; `tvshow.nfo` and episode NFOs then count as read.
- **Stream details** for movies and episodes, with file size, 3D details and external subtitles (path next to the video file on the target system). If Ember has no tracks, the ones known in KMM are kept.
- **Settings:** language of TV show metadata and artwork, the episode order, and whether media data is read during scraping (`MovieScraperMetaDataScan`/`TVScraperMetaDataScan`). API keys are deliberately not taken over; they are managed in KMM under “Settings → API keys”.
- **Not taken over:** Ember’s artwork paths (KMM finds artwork via the Kodi names next to the files), locks/markers, scraper keys, Kodi options for the watched status.

**Schema versions:** v48 is supported directly. Older profiles are opened once with the latest Ember version, which updates them itself.

---

## 7. Roadmap

1. **Foundation** ✅: project skeleton, H2/MariaDB profiles, Flyway, Docker, security, plugin mechanism, analysis of Ember profiles.
2. **Movies MVP** (in increments):
   1. Sources, scan, movie list ✅
   2. Reading/writing NFOs ✅ (`<filename>.nfo`, Kodi 19–21; the scan takes over NFO contents, the detail page edits them and writes them back)
   3. Job framework + progress via SSE ✅ (the scan runs as a background job, can be cancelled, live progress in the UI)
   4. TMDB plugin ✅ (API key from `KMM_TMDB_API_KEY`; online search on the detail page, applied to the form)
   5. Image plugins TMDB/FanartTV, image selection, Kodi image names ✅ (`<filename>-poster.jpg` …, for DVD/Blu-ray `poster.jpg` in the movie folder)
   6. Automatic scraping of all movies of a source ✅ (only missing metadata and artwork, report with titles without a match)
   - **2a. Ember migration I** ✅: extended movie model (people, sets, studios, countries, tags, ratings, user data), settings store, import of sources, movies, sets and settings with dry run and report.
3. **Kodi integration** ✅: Kodi devices with path mapping and connection test, real-time sync as in Ember (RefreshMovie or scan of the folder, debounced in the background), manual sync per movie and source, import of the Kodi devices from Ember. Fetching the watched status from Kodi ✅ (per source as a job or per movie/episode; play count, last played and user rating go into the NFO and the database so that Kodi does not lose the rating when it reads the entry again; Kodi only adds higher play counts, more recent dates or a rating and never resets anything; one device is enough because all devices use the same Kodi database). Later: cleaning the library (`VideoLibrary.Clean`).
4. **TV shows** (in increments):
   1. Model, scan, NFOs, list and detail pages ✅ (every folder below the source is a TV show, episodes at any depth; season/episode from the file name; multi-episode files as several episodes with the same file; `tvshow.nfo` and episode NFOs are read and written when editing)
   2. **Ember migration II** ✅: TV shows, seasons, episodes, stream details (movies and episodes), TV show settings, API keys in the settings store; Ember’s missing episodes without a file are not taken over
   3. Scrapers and artwork (default TVDB, TMDB TV selectable; episodes only get empty fields filled when applying; missing episodes are only displayed; artwork from TMDB, TVDB and fanart.tv):
      - 3a ✅ plugin interface `TvShowScraper` (TV show with seasons and episodes, attribution per scraper) and TMDB TV scraper (aired order only)
      - 3b ✅ TVDB v4 plugin (login with key/PIN, token cache, translations, official/DVD/absolute order, attribution); TVDB only delivers director, writer and guest stars per episode – KMM only requests them for episodes that lack director or writer (cached for one day)
      - 3c ✅ online search on the TV show detail page (default TVDB): proposal into the form, on saving episodes get empty fields filled (title only without NFO), cast/ratings/season titles are completed, NFOs written; missing episodes at the push of a button; TVDB notice for TV shows with a TVDB ID and in the search; loaded TV shows cached for 15 minutes
      - 3d1 ✅ image scrapers for TV shows: image candidates carry the season number; TMDB (TV show and season posters), TVDB (artworks incl. season posters/banners/fanart, languages per ISO 639-1) and fanart.tv (via the TVDB ID, incl. characterart and season artwork)
      - 3d2 ✅ artwork in app and interface: TV show (poster, fanart, banner, clearlogo, clearart, landscape, characterart → `poster.jpg` … in the show folder), seasons (poster, banner, fanart, landscape → `season01-poster.jpg`, `season-specials-poster.jpg`) and episodes (thumbnail from the TV show scrapers → `<episode>-thumb.jpg`); shared artwork gallery and file storage for movies and TV shows
      - 3e ✅ automatic scraping of a TV show source: unmatched TV shows only on a unique match of the preferred scraper (`kmm.scraping.preferred-tv-show-scraper`, default `tvdb`), empty episode fields, missing artwork of the TV show, seasons with episodes and thumbnails; existing data and artwork stay
   4. Kodi sync for TV shows ✅: real-time sync as for movies – changed TV show → `VideoLibrary.RefreshTVShow` (without episodes), changed episode → `RefreshEpisode`, unknown in Kodi → `Scan` of the show folder, new TV shows/episodes after the scan → `Scan` of the source; “Refresh in Kodi” buttons for TV show and episode, “Kodi scan” for TV show sources
5. **Convenience** (in increments):
   1. Technical details with ffprobe ✅: video (codec, resolution, aspect ratio, runtime, bit rate, scan type, 3D, HDR type), audio and subtitles (embedded and as files next to the video, such as `Film.de.srt` or `Film-eng-forced.idx`) in Kodi’s or Ember’s names (`dca`, `dtshd_ma`, `deu` …). Triggers: per movie/episode, per source as a job (only missing or all), after automatic scraping and after saving an online search (movies; for TV shows, episodes without details), and for new files without details after the scan – both can be turned off, ffprobe path configurable. Existing details are replaced; languages and Ember’s 3D details are kept if ffprobe does not deliver any. In the NFO only `<fileinfo>` is replaced, in Ember’s format (alphabetical, with `longlanguage`, `filesize`, subtitle type); the rest of the file is preserved character for character. Multi-episode files get the details in every `<episodedetails>`.
   2. Renaming like Ember’s “Bulk Rename”, movies and TV shows ✅: patterns such as `{title}[ ({year})]` with formats (`{season:2}`, `:first`, `:sort`, `:lower`, `:upper`), optional parts in `[ ]` and subfolders (`/`, relative to the source); preview per source with selection, then a job, or individually from the movie view. What gets renamed: the folder (only if it belongs to the movie alone), video, stack parts and all companion files with the movie’s name; for discs only the folder. Conflicts are skipped, errors rolled back, empty old folders removed; paths in the database and subtitle paths in the database and NFO are updated. Patterns and triggers are in the settings; the Ember import translates Ember’s patterns from `AdvancedSettings.xml`. Automatically (can be turned off, off at first) after saving the metadata and after automatic scraping. With real-time sync, Kodi then reads the new folder and cleans up only the old one (`VideoLibrary.Clean` with directory); watched status and rating come back from the NFO. TV shows: episode files move together with NFO, thumbnail and subtitles into season folders according to a pattern (`Staffel {season}`), multi-episode files are named `S01E01E02` with all titles, then the show folder is renamed; season artwork stays in the show folder following Kodi’s convention. Conflicts apply to the whole TV show for the show folder, otherwise per episode file. Automatically (can be turned off) after saving a TV show or episode, after automatic scraping, and for TV shows with new episodes after the scan; Kodi sync as for movies.
   3. Tag manager ✅ as in Ember (movies and TV shows): tags with count, rename (tags with the same name are merged), delete, assign (also new tags) and remove via a search; changes write the NFOs of the affected entries as a background job, Kodi reads them again via real-time sync.
   4. Movie set manager, part 1 ✅: sets with the number of movies, create (unique name), edit name/description/TMDB collection/order (year or title as in Ember), add movies (they leave their previous set) and remove them, delete; movie NFOs are rewritten as a job. With real-time sync, Kodi takes over title and description via `SetMovieSetDetails` (set found via the previous title or a contained movie). The Ember import takes over all sets, including empty ones and sort order.
   5. Movie set manager, set artwork and set scraping ✅: “Movie sets” settings with two locations that can be turned off individually – Ember’s flat scheme `<folder>/<set title>-poster.jpg` (file name as in Ember: invalid characters → `-`) and Kodi’s “Movie set information folder” `<folder>/<set title>/poster.jpg` (folder name as Kodi’s `MakeLegalFileName`); the Ember import takes over `MovieSetUseExtended`/`MovieSetPathExtended`, `MovieSetUseMatrix`/`MovieSetPathMatrix` and `MovieScraperCollectionsAuto`. Artwork types poster, fanart, banner, clearlogo, clearart, disc, keyart, landscape: display, search at TMDB and fanart.tv via the collection’s TMDB ID, save to all enabled locations, remove; when a set is renamed, its artwork moves along. Set scraping in the set manager: search TMDB collections; a match puts title, description (English fallback) and ID into the form. Job “Complete all sets”: missing descriptions and artwork for sets with a TMDB ID; existing ones stay. “Assign movies to their movie set when scraping” (automatically and after saving an online proposal): set via TMDB ID, otherwise name, otherwise new; another set is replaced. Kodi also gets the artwork via `SetMovieSetDetails` (missing ones are removed, the image cache is cleared); without a location, Kodi’s set artwork stays untouched.
   6. Filter and metadata editor ✅: movie and TV show list with a filter builder instead of Ember’s free-form SQL – conditions made of field, comparison and value (chosen from existing values), “all” or “any”; TV show filters also via episodes (unwatched, resolution, codecs, languages, missing technical details). Built-in and saved filters (table `saved_filter`); the Ember import takes over custom movie lists (views `movie-*`) as far as they can be translated. Technical details have a queryable short form (resolution tier as in Kodi, codecs, HDR, languages). Instead of Ember’s codec table, KMM translates codec names into Kodi’s names in code (MediaInfo raw names, Ember’s default mappings). Batch edit of filtered movies (genres, tags, certification, edition, video source) and TV shows (genres, tags, certification, status) as a job with NFO protection, without renaming.
6. **Extensions:**
   1. Trailers ✅ (movies and TV shows): only as a link in the NFO, which Kodi plays via the YouTube add-on (`plugin://plugin.video.youtube/?action=play_video&videoid=…`), no download. New plugin type `TrailerScraper`; TMDB delivers the movie’s YouTube videos in the preferred language, English and without language. Order: preferred language, English, trailer before teaser, official, higher resolution, newer. Movie view: view suggestions with a thumbnail and apply them, enter a custom link (`plugin://`, `http(s)://`, `smb://`, `nfs://`), remove. Automatic scraping adds the best suggestion for movies without a trailer; job “Fetch missing trailers” for the selection of the movie list; filter field “Trailer”. Existing links stay; the Ember import takes over Ember’s trailer column. TV shows likewise: trailer card in the TV show view, automatic scraping, job “Fetch missing trailers” in the TV show list, filter field “Trailer”; TMDB also finds the TV show via TVDB or IMDb ID (`/tv/{id}/videos`). Ember has no TV show trailers. Movies and TV shows share search and order (`TrailerFinder`). When the trailer changes, KMM only edits the `<trailer>` line as text in an existing NFO (`TrailerNfoWriter`); everything else stays character for character. TV shows from the Ember import carry Ember’s database values (e.g. `FSK 16` instead of `Germany:16`); before writing their `tvshow.nfo` for the first time, KMM therefore reads the NFO (`nfo_read`). The same applies to movies from the Ember import (`movie.nfo_read`, V20); cast, technical details and trailers known only to Ember are kept if they are missing in the NFO. The same applies to episodes from the Ember import before their NFO is rewritten for the first time (saving metadata, completing episodes); if the NFO has no technical details, those from Ember are kept. Cast members that exist only in Ember’s data (such as guest stars) are appended after those of the NFO when reading for the first time; likewise for TV shows. So that the movie, TV show and episode views do not show outdated values and write them back on saving, KMM reads an NFO changed outside KMM when opening the entry, and for TV shows and episodes from Ember also one that has not been read yet. When rewriting NFOs, details on cast members that KMM does not know (e.g. Ember’s `<tmdbid>`) are preserved. Theme music (TvTunes) not for now.
   2. Theme music ✅ (movies and TV shows): `theme.mp3` in the movie or show folder, as TvTunes expects it; Kodi only plays it with an add-on (TvTunes, Themerr). New plugin type `ThemeScraper` (search by title, download as MP3). `kmm-plugin-televisiontunes` reads search and show pages from TelevisionTunes.com (no API, HTTP only) and only downloads MP3s from the site itself. `kmm-plugin-youtube` searches and downloads via yt-dlp (audio via ffmpeg as MP3; included in the Docker image, otherwise `kmm.plugins.settings.youtube.yt-dlp`). The search uses title and original title. View: listen to suggestions and apply them, listen to and delete an existing `theme.mp3`. The job “Fetch missing theme music” for the selection of the lists and automatic scraping only apply reliable matches: the same title at the provider (`Titles.same`); YouTube videos never count as reliable. Downloads go through a temporary file; an existing `theme.mp3` stays untouched by the job and by automatic scraping.
   3. Trakt.tv: no dedicated integration (watched status, collection, personal ratings), because the Trakt plugin in Kodi syncs them with Kodi and KMM fetches the watched status from Kodi. Ember’s Trakt login belongs to Ember’s app and is not taken over. Only Trakt’s public community rating is taken over, as rating `trakt`.
   4. Enrichment scrapers ✅: the new plugin type `EnrichmentScraper` delivers ratings in one request and, if the provider can, details for empty fields (via known IDs). `kmm-plugin-trakt`: Trakt’s community rating (`trakt`) via IMDb, TMDB or TVDB ID, with the client ID of your own Trakt app, without login. `kmm-plugin-omdb`: via the IMDb ID the ratings IMDb (`imdb`, with votes), Rotten Tomatoes (`tomatometerallcritics`, 0–100) and Metacritic (`metacritic`, 0–100) as well as English details for empty fields: plot, runtime, premiere date, certification, genres, director, writer, cast (without roles) and countries; TV shows without director, writer and countries. The free OMDb key allows 1,000 requests per day, hence one request per entry and only if the provider can contribute something. Automatic scraping only adds what is missing; the job “Refresh ratings” of the movie and TV show lists also updates existing ratings (the default flag stays). Existing fields always stay. Episodes are not enriched (one request per episode).
   5. Export ✅ (movies and TV shows): “Export” button in the movie and TV show lists; the currently selected filter including the search is exported as a download in the browser. Formats: CSV for Excel (UTF-8 with BOM, `;`, decimal comma, ja/nein), JSON (keys and types, lists as arrays) and a standalone HTML list with a search field as a ZIP with scaled-down posters (`posters/<id>.jpg`, 160 px wide). Movie columns: title, original title, year, genres, runtime, certification, rating, user rating, watched, resolution, video codec, audio tracks, subtitles, movie set, tags, IMDb, TMDB, file, added; TV shows: title, original title, year, status, genres, network, seasons, episodes, unwatched episodes, ratings, tags, TVDB, TMDB, IMDb, folder, added. The export is created in a temporary file that is deleted after sending. Ember’s “MovieExport” with custom templates is not rebuilt.
   6. HTML scrapers ✅ (movies only) as enrichment scrapers for empty fields, without ratings; the German providers come before OMDb (`EnrichmentScraper.order`). Both only use pages that `robots.txt` allows, identify themselves as KMM and keep one second between requests (`request-delay-ms`). A page is only applied if its IMDb ID matches; without an IMDb ID there is no search. This rules out wrong matches, but not every movie finds an entry. `kmm-plugin-moviepilot` derives the address from the title (`/movies/2001-odyssee-im-weltraum`), the site’s search is blocked; it delivers plot, runtime, cinema release, FSK, countries, director, writer, cast. Moviepilot’s “genres” are keywords and are not taken over. `kmm-plugin-ofdb` finds movies via the site’s sitemaps (around 400,000 addresses, loaded on first use, at most one week old, only checksum and ID kept in memory); the search is blocked here too; it delivers plot, FSK (only if all editions have the same one), genres, countries, director, cast. **IMDb is dropped:** the site answers automated requests with a bot check (`x-amzn-waf-action: challenge`) and prohibits scraping without permission; the check is not circumvented. The IMDb rating comes from OMDb; only the Top 250 ranking is lost. The official IMDb datasets would add hardly anything.
   7. Windows installer ✅: MSI via `jpackage` and WiX 3 (Maven profile `installer`), with its own Java, Start menu entry and desktop shortcut. The installation is for the logged-in user only and does not need administrator rights. KMM runs in the console window and ends when it is closed. A new version replaces the old one (fixed upgrade ID); data under `~/.kodi-media-manager` is kept, even when uninstalling. CI builds the MSI on a Windows runner and keeps it as an artifact for 7 days. Windows only, because operation on Linux is via Docker.
   8. Manual and help ✅: one source in `docs/manual/<language>/` (Markdown, order and assignment to the pages in `docs/manual/chapters.json`), directly readable on GitHub. The “Help” page shows the chapters with a table of contents and a search across all chapters; the question mark in the header opens the chapter for the current page in a new tab so that input is preserved. The script `copy-help.mjs` copies the texts into the interface before the build, since Angular only serves files inside `kmm-ui`; `marked` is only loaded with the help page. Short explanations on fields and links to the matching sections (`HelpLink`). `manual-pdf.mjs` builds one PDF per language with Chrome, aborts on dead links, missing anchors or missing images, and runs in CI. Screenshots come from a test instance with made-up titles and hand-drawn posters, without real paths.

## 8. Open issues / risks

- **API keys:** TMDB, TVDB v4 (paid or project key), FanartTV, OMDb.
- **Installer** is not signed: Windows SmartScreen warns when running the MSI.
- **HTML scrapers** are technically fragile: if Moviepilot or OFDb change their pages, they stop working (warning in the log, no other consequences); can be turned off via the plugin management.
- **Docker:** file permissions (`PUID`/`PGID`) and path mapping container ↔ Kodi (SMB/NFS).
- **License** ✅: GPL-3.0 like Ember, from which KMM takes over logic (`LICENSE`).
- **Fonts** ✅: Roboto ships as the package `@fontsource/roboto` (SIL Open Font License); the UI no longer loads anything from Google Fonts and works offline.
