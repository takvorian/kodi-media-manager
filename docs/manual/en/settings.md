# Settings

Changes only take effect when you click **Save** at the bottom of the page. **Discard** restores the saved state. The API keys have their own button.

If a field says “Fixed by the configuration”, someone has set the value through an environment variable, and it is locked here.

## Interface language

The interface is available in German and English. You can switch at any time at the top right next to the **?** (**DE**/**EN**); the page does not reload, and the manual switches language too. KMM remembers the choice in the browser.

When the page opens, this order applies:

1. the `lang` parameter in the address, for example `http://nas:8080/?lang=en`; `en_GB`, `en-GB`, `de` or `de_DE` work as well,
2. the language last chosen in this browser,
3. otherwise English.

Dates and numbers follow the interface language: British English like `25/10/2026`, German like `25.10.2026`. The metadata language KMM requests from the providers is independent of this, see the next section.

## Language

Applies to movies if neither the movie nor its source has a language of its own, see [Language per entry](scraping.md#language-per-source-and-entry).

- **Metadata:** for example `de-DE` or `en-US`.
- **Preferred artwork language:** two letters, for example `de`. Artwork in this language appears first in the selection.

## TV shows

The same two fields for TV shows, plus the **Episode order**: aired, DVD/Blu-ray or absolute (without seasons). This applies to scraping and only affects TVDB.

## Technical details

- **ffprobe:** the path to ffprobe from FFmpeg, or just `ffprobe` if it is on the search path.
- **Read after auto-scraping**.
- **Read new files without details in the NFO after the scan**.

## Rename

The patterns and the automatic triggers, explained in detail under [Renaming](renaming.md).

## Movie sets

- **Assign movies to their movie set when scraping:** replaces another set of the movie.
- **Store set artwork in the flat scheme:** like Ember, as `<folder>/<set title>-poster.jpg`.
- **Store set artwork in Kodi’s set folders:** as `<folder>/<set title>/poster.jpg`. The folder must be the “Movie set information folder” configured in Kodi.

There is more on this in [Movie sets](movie-sets.md).

## Scan

- **Video file extensions:** separated by commas, for example `mkv, mp4, avi`.
- **Excluded folders:** one full path per line. The scan skips them together with their contents.

## Proxy

For the case that KMM can only reach the internet through a proxy: host, port and, if needed, user name and password. Kodi is never contacted through the proxy.

## API keys

| Key | Used for | Where to get it |
|---|---|---|
| TMDB API key | movies, TV shows, artwork, trailers | <https://www.themoviedb.org/settings/api> |
| TVDB API key | TV shows, artwork | <https://thetvdb.com/api-information> |
| fanart.tv API key | artwork | <https://fanart.tv/get-an-api-key/> |
| OMDb API key | ratings, English details | <https://www.omdbapi.com/apikey.aspx> |
| Trakt client ID | community rating | <https://trakt.tv/oauth/applications> |

KMM stores the keys in its database and only shows their last characters. An empty field leaves the stored key unchanged; **Remove** deletes it. Finally, click **Save keys**.

Environment variables such as `KMM_TMDB_API_KEY` only fill in a key that has not been stored yet, at startup. After that the stored key applies, and a deleted key stays deleted.

## Environment variables

For movies, these variables take precedence over the fields above: `KMM_TMDB_LANGUAGE`, `KMM_ARTWORK_LANGUAGE`, `KMM_PROXY_HOST`, `KMM_PROXY_PORT`, `KMM_PROXY_USERNAME`, `KMM_PROXY_PASSWORD`. More are listed under [Installation](installation.md#access-from-your-home-network).
