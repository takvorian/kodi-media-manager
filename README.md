# Kodi Media Manager (KMM)

Deutsche Fassung: [README.de.md](README.de.md)

Media management for [Kodi](https://kodi.tv) as a Java successor to [Ember Media Manager](https://github.com/DanCooper/Ember-MM-Newscraper).
KMM runs as a Docker container (with MariaDB) or standalone (with an embedded H2 database) and comes with a web interface.

> **Status:** Movies and first TV show features – adding sources and scanning them in the background (with live progress), movie list, reading, editing and writing Kodi NFOs, searching for and applying metadata from TMDB and artwork from TMDB and fanart.tv, automatically scraping all movies of a source (missing data only), importing movies, sets, TV shows with episodes, stream details, settings and Kodi devices from Ember Media Manager (with dry run), and updating Kodi automatically after changes all work. TV show sources can be scanned; TV shows, seasons and episodes (including multi-episode files) are read from the NFOs, displayed and edited. Concept and roadmap: [docs/CONCEPT.md](docs/CONCEPT.md).

## Technology

- Java 25, Spring Boot 4.1, Spring Modulith, Hibernate 7, Flyway
- Scrapers as plugins ([PF4J](https://pf4j.org))
- Angular 22 + Angular Material
- MariaDB (Docker), H2 (standalone), PostgreSQL optional – all three tested via Testcontainers or H2

## Modules

| Module | Contents |
|---|---|
| `kmm-plugin-api` | Interface for scraper plugins |
| `kmm-scanner` | Finding movies and TV shows in the file system, title/year from names, season/episode like Kodi (`S01E02`, `1x02`, `…-102`) |
| `kmm-nfo` | Reading and writing Kodi NFOs for movies, TV shows and episodes (unknown elements are preserved) |
| `kmm-migration-ember` | Reading Ember profiles (database + `Settings.xml`), path mapping; import via the **Ember import** page |
| `kmm-kodi` | JSON-RPC client for Kodi, mapping of KMM paths to Kodi paths |
| `kmm-plugins` | Bundled plugins: `kmm-plugin-tmdb` (movie and TV show metadata, artwork), `kmm-plugin-tvdb` (TV show metadata and artwork), `kmm-plugin-fanarttv` (artwork for movies and TV shows), `kmm-plugin-televisiontunes` and `kmm-plugin-youtube` (theme music; YouTube needs yt-dlp), `kmm-plugin-trakt` and `kmm-plugin-omdb` (ratings, OMDb also fills empty fields), `kmm-plugin-moviepilot` and `kmm-plugin-ofdb` (German details for empty movie fields, no API key) |
| `kmm-ui` | Angular interface |
| `kmm-app` | Spring Boot application |

## Manual

The [manual](docs/manual/en/introduction.md) describes how to use KMM; the chapters are listed in `docs/manual/chapters.json`. The application shows the same texts on the **Help** page, and the question mark in the header opens the chapter for the current page. `npm run manual:pdf` in `kmm-ui` builds `kmm-ui/target/manual/KMM-Manual.pdf` from it (and the German version `KMM-Handbuch.pdf`) and checks all links, anchors and images along the way (requires Chrome); CI keeps the PDFs as the `manual` artifact.

## Building

Prerequisite: JDK 25. The build downloads Maven and Node.js itself.

```bash
./mvnw verify          # build incl. Java and Angular tests, coverage check (≥ 80 %)
./mvnw -DskipTests package
```

The test against MariaDB (Testcontainers) only runs with Docker and is skipped otherwise.

The `installer` profile builds the Windows installer. It requires [WiX 3](https://github.com/wixtoolset/wix3/releases) on the `PATH`; `wix314-binaries.zip` is enough:

```bash
./mvnw -Pinstaller -DskipTests package   # kmm-app/target/installer/Kodi Media Manager-<version>.msi
```

## Release

```bash
./release.sh --dry-run   # shows the next version and the changelog
./release.sh
```

`release.sh` increments the version from the Git tags (`v0.1`, `v0.2`, …) and generates `kmm-app/src/main/resources/changelog.md` from the commit subjects. It commits the changelog, sets the tag, pushes both and calls `build-push.sh <version>`. That script builds the image for `linux/amd64` from the tag and uploads it to Docker Hub as `takvorian/kodi-media-manager:<version>` and `:latest`.

Prerequisites: Docker and `docker login`, `gh` with login, branch `main` up to date with `origin` with a clean working tree, and green CI for the current commit. The app shows the topmost version of the changelog, or `dev` without a changelog; builds made between two releases therefore still show the last version.

If only the image build or the upload fails, the commit and tag have already been pushed; in that case repeat `./build-push.sh <version>` instead of `release.sh`, which would otherwise create the next version. This also applies if the Maven step in the image build is aborted after 30 minutes.

CI stamps the Windows MSI with the latest tag. A new MSI only replaces the old one if its version is higher, so only install the MSI of a new release.

## Running

### Windows installer

The MSI includes Java, installs for the logged-in user only and does not need administrator rights. It is not signed, so Windows SmartScreen shows a warning when you run it. The shortcut starts KMM in a console window; the interface is then available at <http://localhost:8080>. Closing the window stops KMM. A new version replaces the old one; the data under `~/.kodi-media-manager` is kept, even when uninstalling.

### Standalone

```bash
java -jar kmm-app/target/kmm-app.jar
```

The interface is then available at <http://localhost:8080>. Data is stored under `~/.kodi-media-manager` (can be changed via `KMM_HOME`).
A different database can be used like this, for example:

```bash
java -jar kmm-app/target/kmm-app.jar \
  --spring.datasource.url=jdbc:postgresql://localhost/kmm \
  --spring.datasource.username=kmm --spring.datasource.password=…
```

### Docker

```bash
cd docker
cp .env.example .env     # set passwords
docker compose pull      # ready-made image from Docker Hub; to build it yourself: docker compose up -d --build
docker compose up -d
```

`KMM_IMAGE_TAG` in the `.env` file pins a fixed version instead of the latest, for example `0.1`.

| Volume | Purpose |
|---|---|
| `/config` | plugins, cache, logs |
| `/media` | media library |

## API keys

The online search needs API keys. They are managed on the **Settings** page under “API keys”
and stored in the database; the interface only shows the last characters. Environment variables fill in a key
that has not been stored yet at startup (Docker: enter them in `docker/.env`); after that the stored key applies,
and a key deleted in the interface stays deleted:

```bash
export KMM_TMDB_API_KEY=…          # https://www.themoviedb.org/settings/api (v3 key or v4 read token)
export KMM_FANARTTV_API_KEY=…      # https://fanart.tv/get-an-api-key/
export KMM_TVDB_API_KEY=…          # https://thetvdb.com/api-information (v4), optional KMM_TVDB_PIN
```

### Data sources

Metadata and artwork come from [The Movie Database (TMDB)](https://www.themoviedb.org), [fanart.tv](https://fanart.tv)
and [TheTVDB](https://www.thetvdb.com). TV show metadata from TheTVDB:
*Metadata provided by TheTVDB. Please consider adding missing information or subscribing.*
The episode order (aired, DVD, absolute) only applies to TheTVDB; TMDB only knows the aired order.

## Settings

The language of metadata and artwork (separately for movies and TV shows), the episode order for TV shows,
video file extensions, excluded folders and a proxy are managed on the **Settings** page and stored in the
database. For movies, environment variables take precedence; values set this way are locked in the
interface:

```bash
export KMM_TMDB_LANGUAGE=de-DE     # metadata language
export KMM_ARTWORK_LANGUAGE=de     # preferred artwork language
export KMM_PROXY_HOST=proxy.local  # proxy for online services, plus KMM_PROXY_PORT/_USERNAME/_PASSWORD
```

## Kodi

Kodi devices are added on the **Kodi** page with address, port, credentials and path mapping (in Kodi:
*Settings → Services → Control → Allow remote control via HTTP*). The path mapping translates the directories
of the KMM sources into Kodi’s paths, e.g. `/media/filme` → `smb://nas/medien/filme/`.

- **Real-time sync:** after metadata or artwork has been saved, KMM makes Kodi read the movie again
  (`VideoLibrary.RefreshMovie`); if Kodi does not know it yet, Kodi scans its folder. If a scan in KMM finds new
  movies, Kodi scans the source. Several changes in quick succession trigger only one update
  (`kmm.kodi.sync-delay`, default 5 s).
- **Manual:** “Refresh in Kodi” on the detail page and “Kodi scan” per source affect all devices.
- Kodi is contacted without the proxy from the settings. Movies removed from KMM stay in Kodi until the
  library is cleaned there.

Note: Java only sends proxy credentials for HTTPS connections if the JVM is started with
`-Djdk.http.auth.tunneling.disabledSchemes=`.

## Development

```bash
./mvnw -pl kmm-app -am spring-boot:run   # backend on :8080
cd kmm-ui && npm start                   # Angular dev server on :4200, proxying to the backend
```

## API

- OpenAPI/Swagger UI: `/swagger-ui.html` (with login in the Docker profile)
- `GET /api/v1/system/info`, `GET /api/v1/system/changelog`
- `GET /api/v1/sources`, `POST /api/v1/sources`, `DELETE /api/v1/sources/{id}`
- `POST /api/v1/sources/{id}/scan` → `202 Accepted` with the started job (movie or TV show scan depending on the source)
- `POST /api/v1/sources/{id}/auto-scrape` → `202 Accepted`; fills in missing metadata and artwork for all movies or TV shows (incl. episodes and seasons) of the source
- `GET /api/v1/jobs`, `GET /api/v1/jobs/{id}`, `POST /api/v1/jobs/{id}/cancel`
- `GET /api/v1/jobs/events` (server-sent events, event `job`)
- `GET /api/v1/movies?search=…&page=…&size=…`
- `GET /api/v1/movies/{id}`, `PUT /api/v1/movies/{id}/metadata` (writes the NFO)
- `POST /api/v1/movies/export?format=HTML|CSV|JSON`, `POST /api/v1/tvshows/export?format=…` with the filter as body
  (`{"matchAll": true, "conditions": [], "search": null}`) → download; HTML is a list with posters as a ZIP
- `GET /api/v1/tvshows?search=…&page=…&size=…`, `GET /api/v1/tvshows/{id}` (with seasons and episodes),
  `PUT /api/v1/tvshows/{id}/metadata` (writes the `tvshow.nfo`)
- `GET /api/v1/scrapers/tvshows` (TV show scrapers with attribution), `GET /api/v1/tvshows/{id}/scraper-search?scraper=…`,
  `GET /api/v1/tvshows/{id}/scraper-proposal?scraper=…&providerId=…` (proposal for the form),
  `POST /api/v1/tvshows/{id}/scraper-episodes?scraper=…&providerId=…` (fills empty episode fields, writes NFOs),
  `GET /api/v1/tvshows/{id}/missing-episodes?scraper=…` (episodes at the provider without a file, display only)
- `GET /api/v1/tvshows/{id}/artwork[?season=…]`, `GET|PUT|DELETE /api/v1/tvshows/{id}/artwork/{type}[?season=…]`,
  `…/{type}/image`, `…/{type}/candidates` (artwork of the TV show or of a season), likewise
  `/api/v1/episodes/{id}/artwork/THUMB` (thumbnail of the episode)
- `GET /api/v1/episodes/{id}`, `PUT /api/v1/episodes/{id}/metadata` (writes the episode NFO, for multi-episode files
  all episodes of the file)
- `GET /api/v1/movies/{id}/scraper-search?scraper=tmdb&title=…&year=…`, `GET /api/v1/movies/{id}/scraper-proposal?scraper=tmdb&providerId=…`
- `GET /api/v1/movies/{id}/artwork`, `GET …/artwork/{TYPE}/image`, `GET …/artwork/{TYPE}/candidates`,
  `PUT …/artwork/{TYPE}` (`{"url": …}`), `DELETE …/artwork/{TYPE}` – TYPE: POSTER, FANART, CLEARLOGO, CLEARART, DISCART, BANNER, LANDSCAPE
- `GET /api/v1/scrapers/metadata`
- `GET /api/v1/settings`, `PUT /api/v1/settings` (proxy password `null` = unchanged)
- `GET /api/v1/settings/api-keys`, `PUT /api/v1/settings/api-keys` (`{"TMDB_API_KEY": "…"}`; an empty value deletes,
  missing keys stay)
- `GET /api/v1/plugins`, `POST /api/v1/plugins/{id}/enable|disable`
- `GET /api/v1/kodi/hosts`, `POST /api/v1/kodi/hosts`, `PUT|DELETE /api/v1/kodi/hosts/{id}` (password `null` = unchanged),
  `POST /api/v1/kodi/hosts/{id}/test`
- `POST /api/v1/kodi/movies/{id}/refresh`, `POST /api/v1/kodi/tvshows/{id}/refresh`, `POST /api/v1/kodi/episodes/{id}/refresh`,
  `POST /api/v1/kodi/sources/{id}/scan` (all Kodi devices, result per device)
- `POST /api/v1/migrations/ember/upload` (multipart, part `files`: `MyVideosNN.emm` and the settings files; returns the analysis), `DELETE …/upload`, `POST /api/v1/migrations/ember/import` → `202 Accepted` with the started job
  (`{"pathMappings": [{"emberPath": "a:\\filme", "kmmPath": "/media/filme"}], "importSettings": true, "importKodiHosts": true, "dryRun": true}`)

## License

KMM is licensed under the [GNU General Public License v3.0](LICENSE), like Ember Media Manager, from which it takes over logic.
