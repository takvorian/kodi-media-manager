# Troubleshooting

## KMM does not start

**Under Docker, the log shows `AccessDeniedException: /config/plugins` or `/config ist nicht beschreibbar` (“/config is not writable”).** The container’s user is not allowed to write to `/config`. This happens with a Compose file from KMM before version 0.3 that still contains a `user:` line. Remove this line, add the lines `PUID: ${PUID:-1000}` and `PGID: ${PGID:-1000}` under `environment` and restart, see [Docker](installation.md#docker).

**The console window closes immediately or shows an error.** Common causes:

- **Port in use:** message `Port 8080 was already in use`. Quit the other program or set `KMM_PORT` to a different port.
- **KMM is already running:** two instances cannot use the same data folder; the message then mentions a locked database. Close the first one.

**The browser cannot find the page.** Wait until the window shows `Started KodiMediaManagerApplication`. KMM can only be reached from another device if you have set that up, see [Installation](installation.md#access-from-your-home-network).

**KMM is gone after restarting the computer.** On Windows, KMM does not start automatically. Start it again from the shortcut.

## Actions fail

**“Der Server hat die Anfrage abgelehnt, weil das Sicherheits-Cookie fehlt oder nicht passt” (“The server rejected the request because the security cookie is missing or does not match”).** Reload the page. If that does not help, delete the cookies for KMM’s address in your browser. The log contains a line with `Rejected` for this.

## Scan

**The source finds nothing.** Check that the directory is correct as KMM sees it, so under Docker the path in the container. Also check the [file extensions and excluded folders](settings.md#scan).

**A TV show is recognised incorrectly.** Every folder directly below the source counts as a TV show. So the source must point to the folder *above* the TV shows.

**Episodes are missing.** The season and episode must be in the file name, for example `S01E05`.

## Scraping

**“No metadata scraper is installed or active”, or the search button is locked.** Usually the provider’s [API key](settings.md#api-keys) is missing.

**No results.** Try a different search term or the original title, or leave out the year.

**Automatic scraping skips titles.** It only applies unique matches. Search for these titles one by one, see [Online search](scraping.md#online-search). After that KMM knows the ID, and other providers find the title too.

**Moviepilot or OFDb deliver nothing.** They need the IMDb ID and do not find every movie. If the sites change their layout, these providers stop working, and the log then shows a warning. Other providers are not affected.

**No theme music from YouTube.** yt-dlp is missing or outdated, see [Additional programs](installation.md#additional-programs).

**OMDb suddenly delivers nothing.** The free key allows 1,000 requests per day.

## Technical details

**Reading fails.** ffprobe was not found. Check the [path](settings.md#technical-details).

## Kodi

**Test connection fails.** Check whether Kodi is running, whether HTTP control is turned on, and whether the address (without `http://`), port, user name and password are correct. See [Preparing Kodi](kodi.md#preparing-kodi).

**Kodi does not update anything even though the connection works.** Almost always the [path mapping](kodi.md#path-mapping) is wrong. The path in KMM must match the source, and the path in Kodi must match exactly what Kodi shows under *Videos → Files*. The result is shown under “Last sync” on the device’s card.

**Theme music is not played.** Kodi needs an add-on such as TvTunes for this.

**Trailers cannot be played.** Kodi needs the YouTube add-on for this.

## Renaming

**An entry shows “Conflict”.** The target already exists. Rename the existing item or change the pattern.

**A folder is not renamed.** KMM only renames a movie folder if it belongs to this movie alone.

## Starting over

On the **System** page, **Clear database** empties KMM, for example if an [Ember import](ember-import.md) ran with wrong paths. To do this, type the word `CLEAR`. This only works while no job is running, and it cannot be undone.

Sources, movies, TV shows, episodes, movie sets, filters and Kodi devices are deleted. Media, NFOs and artwork stay untouched. Anything you edited in KMM that is not in an NFO is lost.

Settings and API keys are kept as long as you do not untick the box. If you untick it, the default values apply again. KMM enters API keys from the configuration again at the next start.

## Saving fails

KMM needs write access to your media folders. Under Docker, `PUID` and `PGID` determine this.

**An NFO was changed outside of KMM.** KMM reads it again automatically when you open the entry.

## Finding the log

On Windows the log appears in the console window. With the start parameter `--logging.file.name=<file>`, KMM also writes it to a file. Under Docker, `docker compose logs app` shows it.
